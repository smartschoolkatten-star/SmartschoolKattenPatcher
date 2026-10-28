package be.smartschoolkatten.patcher

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import be.smartschoolkatten.patcher.engine.ApkSet
import be.smartschoolkatten.patcher.engine.KattenPatcher
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/** Holds patch work across Activity recreation without retaining an Activity. */
class PatchViewModel(application: Application) : AndroidViewModel(application) {
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val directory = File(application.filesDir, "patched").apply { mkdirs() }
    private val outputName = "Smartschool-Katten-12.3.0-r${KattenPatcher.PATCH_REVISION}.apks"
    var output by mutableStateOf<File?>(File(directory, outputName).takeIf { it.exists() })
        private set
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var smoothing: Runnable? = null
    private var reportedStage: String? = null

    private fun stopSmoothing() {
        smoothing?.let { main.removeCallbacks(it) }
        smoothing = null
    }

    private fun userStatus(message: String): String = when {
        message.startsWith("Kattenprofielen aanpassen") -> "Kattenprofielfoto’s toevoegen…"
        message.startsWith("Smartschool Katten staat klaar") -> "Smartschool Katten is klaar om te installeren."
        message.startsWith("APK-onderdeel") -> "Installatiepakket voorbereiden…"
        message.startsWith("APK ondertekenen") -> "Installatiepakket afronden…"
        message.startsWith("Veilige opstart") || message.startsWith("Volledig installatiepakket") -> "Installatiepakket controleren…"
        else -> message
    }

    private fun updateProgress(message: String, fraction: Float) {
        stopSmoothing()
        val newStage = reportedStage != message
        reportedStage = message
        status = userStatus(message)
        progress = maxOf(progress, fraction.coerceIn(0f, 0.99f))
        if (!newStage || !message.matches(Regex("Kattenprofielen aanpassen \\(classes(3|4)?\\.dex\\)…"))) return
        val started = android.os.SystemClock.elapsedRealtime()
        val beginning = fraction
        // Visual interpolation approaches the end of this stage, but cannot complete it.
        val ceiling = minOf(beginning + 0.245f, 0.99f)
        val tick = object : Runnable {
            override fun run() {
                if (!running) return
                val elapsed = android.os.SystemClock.elapsedRealtime() - started
                val estimated = beginning + (ceiling - beginning) *
                    (1.0 - kotlin.math.exp(-elapsed / 15000.0)).toFloat()
                progress = maxOf(progress, estimated.coerceAtMost(ceiling))
                main.postDelayed(this, 150)
            }
        }
        smoothing = tick
        main.postDelayed(tick, 150)
    }

    fun patch(source: File) {
        if (running) return
        running = true
        stopSmoothing()
        reportedStage = null
        progress = 0f
        error = null
        status = "Patch voorbereiden…"
        worker.execute {
            val application = getApplication<Application>()
            var staging: File? = null
            try {
                require(source.name.endsWith(".apks")) { "Download Smartschool opnieuw om alle vereiste APK-onderdelen op te halen." }
                val assets = File(application.cacheDir, "patch-assets").apply { mkdirs() }
                // Copy the bundled directory so newly added patch assets are included too.
                val assetNames = requireNotNull(application.assets.list("patch")) {
                    "Patchbestanden ontbreken in de patcher."
                }
                require(assetNames.isNotEmpty()) { "Patchbestanden ontbreken in de patcher." }
                for (name in assetNames) {
                    application.assets.open("patch/$name").use { input ->
                        File(assets, name).outputStream().use { input.copyTo(it) }
                    }
                }
                staging = File.createTempFile("katten-", ".apks", directory)
                ApkSet.patch(source, staging, assets, File(application.filesDir, "patch-signing"), object : KattenPatcher.Progress {
                    override fun update(message: String) {main.post { status = userStatus(message) }}
                    override fun update(message: String, fraction: Float) {
                        main.post { updateProgress(message, fraction) }
                    }
                })
                val target = File(directory, outputName)
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                main.post { stopSmoothing(); output = target; status = "Smartschool Katten is klaar om te installeren."; progress = 1f; running = false }
            } catch (failure: OutOfMemoryError) {
                staging?.delete()
                main.post { stopSmoothing(); error = "Niet genoeg werkgeheugen om deze APK te patchen. Sluit andere apps en probeer opnieuw."; status = null; running = false }
            } catch (failure: Exception) {
                staging?.delete()
                main.post {
                    stopSmoothing()
                    error = "De kattenversie kan niet worden gemaakt. Probeer opnieuw. Blijft dit gebeuren, download Smartschool opnieuw."
                    status = null
                    running = false
                }
            }
        }
    }
    override fun onCleared() {
        stopSmoothing()
        worker.shutdownNow()
    }
}
