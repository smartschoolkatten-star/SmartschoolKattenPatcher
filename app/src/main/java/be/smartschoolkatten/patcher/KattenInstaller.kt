package be.smartschoolkatten.patcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import be.smartschoolkatten.patcher.engine.ApkSet
import be.smartschoolkatten.patcher.engine.KattenPatcher
import be.smartschoolkatten.patcher.engine.PatchValidation
import java.io.File

object KattenInstaller {
    /** Call off the main thread: unpack and stream every APK into one atomic install session. */
    fun install(context: Context, archive: File) {
        val working = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "katten-install-").toFile()
        val installer = context.packageManager.packageInstaller
        var sessionId: Int? = null
        var committed = false
        try {
            val parts = ApkSet.unpack(archive, working)
            val base = ApkSet.validate(parts)
            require(ApkSet.info(base)["package"] == KattenPatcher.PACKAGE) { "Dit pakket is nog niet gepatcht." }
            PatchValidation.verify(base)
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(KattenPatcher.PACKAGE)
                setSize(parts.sumOf { it.length() })
                if (android.os.Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            val id = installer.createSession(params)
            sessionId = id
            installer.openSession(id).use { session ->
                parts.forEachIndexed { index, file ->
                    session.openWrite(if (file == base) "base.apk" else "split-$index.apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out) }; session.fsync(out)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java).setAction("${context.packageName}.INSTALL.$id")
                val callback = PendingIntent.getBroadcast(context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(callback.intentSender)
                committed = true
            }
        } finally {
            if (!committed) sessionId?.let { installer.abandonSession(it) }
            ApkSet.deleteTree(working)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            try {
                requireNotNull(confirmation) { "Android gaf geen installatiebevestiging." }
                context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (failure: Exception) { store(context, "De installatiebevestiging kan niet worden geopend. Probeer opnieuw.") }
        } else {
            val message = if (status == PackageInstaller.STATUS_SUCCESS) "Smartschool Katten is geïnstalleerd."
            else when (status) {
                PackageInstaller.STATUS_FAILURE_ABORTED -> "Installatie geannuleerd."
                PackageInstaller.STATUS_FAILURE_STORAGE -> "Er is onvoldoende opslagruimte. Maak ruimte vrij en probeer opnieuw."
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Deze versie kan niet op dit toestel worden geïnstalleerd."
                PackageInstaller.STATUS_FAILURE_CONFLICT -> "Er staat een andere versie van Smartschool Katten op dit toestel die niet kan worden bijgewerkt."
                PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android heeft de installatie geblokkeerd. Controleer de installatietoestemming."
                else -> "Smartschool Katten kan niet worden geïnstalleerd. Probeer opnieuw."
            }
            store(context, message)
        }
    }
    private fun store(context: Context, message: String) {
        context.getSharedPreferences("installation", Context.MODE_PRIVATE).edit().putString("result", message).apply()
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
