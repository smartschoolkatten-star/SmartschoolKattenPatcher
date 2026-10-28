package be.smartschoolkatten.patcher

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.systemBarsPadding
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val REQUIRED_SMARTSCHOOL_VERSION = "12.3.0"

class MainActivity : ComponentActivity() {

    private lateinit var patchModel: PatchViewModel
    private var installationMessage by mutableStateOf<String?>(null)
    private val installationListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == "result") runOnUiThread { installationMessage = preferences.getString("result", null) }
    }
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) installPatchedApk()
        else Toast.makeText(this, "Installeren is niet toegestaan voor deze patcher", Toast.LENGTH_LONG).show()
    }

    private var pendingExportUri: Uri? = null
    private var savingApk by mutableStateOf(false)
    private val apkExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { destination ->
        val source = pendingExportUri
        pendingExportUri = null
        if (destination != null && source != null) exportApk(source, destination)
    }

    private var showPatchPreparation by mutableStateOf(false)

    private var selectedApkUri: Uri? = null
    private var apkError by mutableStateOf<String?>(null)
    private var checkingApk by mutableStateOf(false)

    private var selectedApkName by mutableStateOf<String?>(null)

    private var showDownloadDialog by mutableStateOf(false)

    private var selectedApkVersion by mutableStateOf<String?>(null)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        patchModel = ViewModelProvider(this)[PatchViewModel::class.java]
        getSharedPreferences("installation", MODE_PRIVATE).apply {
            installationMessage = getString("result", null)
            registerOnSharedPreferenceChangeListener(installationListener)
        }
        pendingExportUri = savedInstanceState?.getString("exportUri")?.let(Uri::parse)
        selectedApkName = savedInstanceState?.getString("apkName")
        selectedApkUri = savedInstanceState?.getString("apkUri")?.let(Uri::parse)
        apkError = savedInstanceState?.getString("apkError")
        showDownloadDialog = savedInstanceState?.getBoolean("downloadDialog") ?: false
        selectedApkVersion = savedInstanceState?.getString("apkVersion")
        if (selectedApkUri == null) {
            val previous = getSharedPreferences("download", MODE_PRIVATE)
            selectedApkUri = previous.getString("uri", null)?.let(Uri::parse)
            selectedApkName = previous.getString("name", null)
            selectedApkVersion = previous.getString("version", null)
        }
        if (selectedApkVersion != REQUIRED_SMARTSCHOOL_VERSION || selectedApkUri?.path?.endsWith(".apks") != true) {
            selectedApkName = null
            selectedApkUri = null
            selectedApkVersion = null
        }
        if (selectedApkUri?.path?.let { File(it).isFile } != true) {
            selectedApkUri = null; selectedApkName = null; selectedApkVersion = null
        }
        if (selectedApkUri == null) {
            // Recover downloads made by older patcher versions, which only kept
            // the selection in Activity state. Inspect the ZIP directory without
            // unpacking APKs on the UI thread; patching validates the actual version.
            val cached = File(filesDir, "apks").listFiles()?.asSequence()
                ?.filter { it.name.startsWith("Smartschool-12.3.0-") && it.name.endsWith(".apks") }
                ?.sortedByDescending { it.lastModified() }
                ?.firstOrNull { candidate ->
                    try { java.util.zip.ZipFile(candidate).use { it.getEntry("base.apk") != null } }
                    catch (_: java.io.IOException) { false }
                }
            if (cached != null) {
                selectedApkUri = Uri.fromFile(cached)
                selectedApkName = cached.name
                selectedApkVersion = REQUIRED_SMARTSCHOOL_VERSION
            }
        }

        showPatchPreparation = savedInstanceState?.getBoolean("patchPreparation") == true
            && selectedApkUri != null

        setContent {

            MaterialTheme {

                if (showPatchPreparation) {
                    androidx.activity.compose.BackHandler { if (!patchModel.running) showPatchPreparation = false }
                    PatchPreparationScreen(
                        version = selectedApkVersion ?: REQUIRED_SMARTSCHOOL_VERSION,
                        onBack = { showPatchPreparation = false },
                        savingApk = savingApk,
                        onSaveApk = { openApkExport() },
                        running = patchModel.running,
                        patchProgress = patchModel.progress,
                        patchStatus = installationMessage ?: patchModel.status,
                        patchError = patchModel.error,
                        hasOutput = patchModel.output != null,
                        onPatch = {
                            getSharedPreferences("installation", MODE_PRIVATE).edit().remove("result").apply()
                            selectedApkUri?.path?.let { patchModel.patch(File(it)) }
                        },
                        onInstall = { installPatchedApk() },
                        onSavePatched = { openPatchedExport() }
                    )
                } else PatcherScreen(
                    selectedApkName = selectedApkName,
                    apkError = apkError,
                    checkingApk = checkingApk,

                    showDownloadDialog = showDownloadDialog,

                    selectedApkVersion = selectedApkVersion,

                    canContinue = selectedApkUri != null && selectedApkVersion == REQUIRED_SMARTSCHOOL_VERSION && !checkingApk && !savingApk,
                    onContinue = { showPatchPreparation = true },
                    savingApk = savingApk,
                    onSaveApk = { openApkExport() },
                    onDownload = {
                        showDownloadDialog = true
                    },

                    onConfirmDownload = {

                        showDownloadDialog = false

                        downloadSmartschool()
                    },

                    onCancelDownload = {
                        showDownloadDialog = false
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportUri", pendingExportUri?.toString())
        outState.putBoolean("patchPreparation", showPatchPreparation)
        outState.putString("apkName", selectedApkName)
        outState.putString("apkUri", selectedApkUri?.toString())
        outState.putString("apkError", apkError)
        outState.putBoolean("downloadDialog", showDownloadDialog)
        outState.putString("apkVersion", selectedApkVersion)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        getSharedPreferences("installation", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(installationListener)
        super.onDestroy()
    }

    private fun openPatchedExport() {
        if (savingApk || patchModel.running || pendingExportUri != null) return
        val output = patchModel.output ?: return
        pendingExportUri = Uri.fromFile(output)
        apkExporter.launch(output.name)
    }

    private fun installPatchedApk() {
        if (android.os.Build.VERSION.SDK_INT < 32) {
            Toast.makeText(this, "Smartschool 12.3.0 vereist Android 12L of nieuwer", Toast.LENGTH_LONG).show()
            return
        }
        val output = patchModel.output ?: return
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return
            }
            if (savingApk) return
            getSharedPreferences("installation", MODE_PRIVATE).edit().remove("result").apply()
            savingApk = true
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { KattenInstaller.install(this@MainActivity, output) }
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    Toast.makeText(this@MainActivity, "De installatie kan niet worden voorbereid. Probeer opnieuw.", Toast.LENGTH_LONG).show()
                } finally { savingApk = false }
            }
        } catch (failure: Exception) {
            Toast.makeText(this, "De installatie kan niet worden geopend. Probeer opnieuw.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openApkExport() {
        if (checkingApk || savingApk || pendingExportUri != null) return
        val source = selectedApkUri ?: return
        pendingExportUri = source
        apkExporter.launch("Smartschool-$REQUIRED_SMARTSCHOOL_VERSION.apks")
    }

    private fun exportApk(source: Uri, destination: Uri) {
        savingApk = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(source)
                        ?: error("De gedownloade APK kan niet worden geopend.")
                    input.use { apk ->
                        val output = contentResolver.openOutputStream(destination, "wt")
                            ?: error("De gekozen locatie kan niet worden beschreven.")
                        output.use { apk.copyTo(it) }
                    }
                }
                Toast.makeText(this@MainActivity, "Installatiepakket opgeslagen", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Toast.makeText(
                    this@MainActivity,
                    "Het pakket kan niet worden opgeslagen. Kies een andere locatie of maak ruimte vrij.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                savingApk = false
            }
        }
    }

    private fun downloadSmartschool() {
        if (checkingApk) return
        checkingApk = true
        apkError = null
        selectedApkUri = null
        selectedApkVersion = null
        selectedApkName = "Download voorbereiden…"
        lifecycleScope.launch {
            try {
                val downloaded = withContext(Dispatchers.IO) {
                    SmartschoolDownload.download(this@MainActivity) { message ->
                        runOnUiThread { selectedApkName = message }
                    }
                }

                selectedApkUri = Uri.fromFile(downloaded)
                selectedApkName = downloaded.name
                selectedApkVersion = REQUIRED_SMARTSCHOOL_VERSION
                getSharedPreferences("download", MODE_PRIVATE).edit()
                    .putString("uri", selectedApkUri.toString())
                    .putString("name", selectedApkName)
                    .putString("version", selectedApkVersion).apply()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                selectedApkName = null
                apkError = "Smartschool kan niet worden gedownload. Controleer je internetverbinding en probeer opnieuw."
            } finally {
                checkingApk = false
            }
        }
    }


}

@androidx.compose.runtime.Composable
fun PatcherScreen(
    selectedApkName: String?,
    apkError: String?,
    checkingApk: Boolean,
    showDownloadDialog: Boolean,
    selectedApkVersion: String?,
    savingApk: Boolean,
    onSaveApk: () -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
    onDownload: () -> Unit,
    onConfirmDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {

    var extraOptions by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val kattenGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF8A3D),
            Color(0xFFFF3B30)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kattenGradient)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "🐈",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Smartschool Katten",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Text(
            text = "PATCHER",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),

            shape = RoundedCornerShape(24.dp),

            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {

            Column(
                modifier = Modifier.padding(20.dp),

                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Smartschool $REQUIRED_SMARTSCHOOL_VERSION",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = if (checkingApk) selectedApkName ?: "Download voorbereiden…"
                        else if (selectedApkVersion != null) "Klaar om de kattenversie te maken"
                        else "Download Smartschool om te beginnen",

                    style = MaterialTheme.typography.bodyMedium
                )

            }
        }

        if (apkError != null) {
            Text(text = apkError, color = Color.White, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(if (canContinue) "Klaar! Ga verder om de kattenversie te maken." else "Stap 1: Download Smartschool.", color = Color.White)
        Button(onClick = if (canContinue) onContinue else onDownload,
            enabled = !checkingApk && !savingApk, modifier = Modifier.fillMaxWidth()) {
            Text(if (checkingApk) "Downloaden…" else if (canContinue) "Doorgaan" else "Smartschool downloaden")
        }
        if (canContinue) {
            TextButton(onClick = { extraOptions = !extraOptions }, enabled = !savingApk && !checkingApk) {
                Text(if (extraOptions) "Extra opties ▴" else "Extra opties ▾", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            if (extraOptions) {
                TextButton(onClick = onSaveApk, enabled = !savingApk && !checkingApk) {
                    Text("Origineel pakket opslaan", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDownload, enabled = !savingApk && !checkingApk) {
                    Text("Opnieuw downloaden", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showDownloadDialog) {

        AlertDialog(
            onDismissRequest = onCancelDownload,

            title = {
                Text(
                    text = "Smartschool downloaden"
                )
            },

            text = {
                Text(
                    text =
                        "Smartschool $REQUIRED_SMARTSCHOOL_VERSION wordt via Aptoide gedownload. Alle benodigde bestanden worden automatisch opgehaald."
                )
            },

            confirmButton = {
                Button(
                    onClick = onConfirmDownload,

                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00AEEF),
                        contentColor = Color.White
                    )
                ) {
                    Text("Smartschool downloaden")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = onCancelDownload
                ) {
                    Text("Annuleren")
                }
            }
        )
    }

}

@androidx.compose.runtime.Composable
fun PatchPreparationScreen(
    version: String,
    onBack: () -> Unit,
    savingApk: Boolean,
    onSaveApk: () -> Unit,
    running: Boolean,
    patchProgress: Float,
    patchStatus: String?,
    patchError: String?,
    hasOutput: Boolean,
    onPatch: () -> Unit,
    onInstall: () -> Unit,
    onSavePatched: () -> Unit
) {
    var showInstallExplanation by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var extraOptions by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🐈", style = MaterialTheme.typography.displayLarge)
        Text("Smartschool Katten maken", style = MaterialTheme.typography.headlineMedium)
        Text("Smartschool $version staat klaar.", modifier = Modifier.padding(top = 12.dp))
        Text(
            "Maak een tweede app met kattenprofielfoto’s, je favoriete kat en een dagelijkse katten-check-in met streak en record. De officiële Smartschool-app blijft staan.",
            modifier = Modifier.padding(top = 16.dp)
        )
        if (patchStatus != null) Text(patchStatus, modifier = Modifier.padding(top = 16.dp))
        if (running) WavyPatchProgress(patchProgress, Modifier.padding(top = 16.dp).fillMaxWidth())
        if (patchError != null) Text(patchError, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 16.dp))
        Text(if (hasOutput && !running) "Stap 3: Installeer de kattenversie." else "Stap 2: Maak de kattenversie.", modifier = Modifier.padding(top = 16.dp))
        Button(onClick = if (hasOutput && !running) ({ showInstallExplanation = true }) else onPatch,
            enabled = !running && !savingApk, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text(when {
                running -> "Patch maken…"
                savingApk -> "Even wachten…"
                hasOutput -> "Smartschool Katten installeren"
                else -> "Kattenpatch maken"
            })
        }
        TextButton(onClick = { extraOptions = !extraOptions }, enabled = !running && !savingApk) {
            Text(if (extraOptions) "Extra opties ▴" else "Extra opties ▾", style = MaterialTheme.typography.bodySmall)
        }
        if (extraOptions) {
            if (hasOutput) {
                TextButton(onClick = onSavePatched, enabled = !savingApk && !running) {
                    Text("Installatiepakket opslaan", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onPatch, enabled = !savingApk && !running) {
                    Text("Nieuwe patch maken", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onSaveApk, enabled = !savingApk && !running) {
                Text("Origineel pakket opslaan", style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onBack, enabled = !running && !savingApk) { Text("Terug") }
    }
    if (showInstallExplanation) {
        AlertDialog(
            onDismissRequest = { showInstallExplanation = false },
            title = { Text("Voor je installeert") },
            text = {
                Text(
                    "Smartschool Katten is lokaal gemaakt en komt niet uit de Play Store. " +
                        "Play Protect kan daarom melden dat de app of uitgever nog niet bekend is. Laat een aangeboden scan uitvoeren.\n\n" +
                        "Bij een melding over een onbekende app kan ‘Toch installeren’ onder ‘Meer details’ staan. " +
                        "Kies dit alleen als je deze zelfgemaakte versie vertrouwt. De tekst kan per telefoon verschillen.\n\n" +
                        "Meldt Play Protect dat de app schadelijk is? Stop dan de installatie."
                )
            },
            confirmButton = {
                Button(onClick = { showInstallExplanation = false; onInstall() }) {
                    Text("Installatie openen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallExplanation = false }) { Text("Annuleren") }
            }
        )
    }

}
