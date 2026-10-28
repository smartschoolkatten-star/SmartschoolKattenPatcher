package be.smartschoolkatten.patcher

import android.content.Context
import android.os.Build
import be.smartschoolkatten.patcher.engine.ApkSet
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

object SmartschoolDownload {
    fun download(context: Context, progress: (String) -> Unit): File {
        // Fetch configurations without q: the API's older device-spec validator can
        // reject newer SDK levels/nonstandard densities. Select the ABI locally.
        progress("Downloadgegevens ophalen…")
        val metadata = read("https://ws75.aptoide.com/api/7/app/get/app_id=76524701/aab=1", "Downloadgegevens") {
            JSONObject(it.bufferedReader().use { reader -> reader.readText() })
        }
        require(metadata.getJSONObject("info").getString("status") == "OK") { "Aptoide biedt deze download momenteel niet aan." }
        val nodes = metadata.getJSONObject("nodes")
        val app = nodes.getJSONObject("meta").getJSONObject("data")
        val base = app.getJSONObject("file")
        require(base.getString("vername") == "12.3.0") { "Alleen Smartschool 12.3.0 wordt ondersteund." }
        val downloads = linkedMapOf(base.getString("path") to base)
        // Different API versions put the AAB object in meta.data or in nodes.aab.data.
        val candidates = mutableListOf<JSONObject>()
        collectSplits(nodes.optJSONObject("aab"), candidates)
        collectSplits(app.optJSONObject("aab"), candidates)
        val abiSplits = candidates.filter { it.optString("type").equals("ABI", true) }
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull { abi ->
            abiSplits.any { matchesAbi(it, abi) }
        }
        for (split in candidates) {
            if (split.optString("type").equals("ABI", true) &&
                (preferredAbi == null || !matchesAbi(split, preferredAbi))) continue
            // Density and language configurations can coexist. Preserve all returned by the API.
            downloads[split.getString("path")] = split
        }
        val working = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "smartschool-set-").toFile()
        var result: File? = null
        try {
            val parts = downloads.entries.mapIndexed { index, (path, data) ->
                progress("Smartschool downloaden (${index + 1}/${downloads.size})…")
                val target = File(working, "part-$index.apk")
                read(path, "APK-onderdeel ${index + 1}/${downloads.size}") { input -> target.outputStream().use { input.copyTo(it) } }
                val expected = data.optString("md5sum")
                if (expected.isNotEmpty()) {
                    val digest = MessageDigest.getInstance("MD5")
                    target.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                    require(actual.equals(expected, true)) { "Een download is onvolledig of beschadigd. Probeer opnieuw." }
                }
                target
            }
            val actualBase = ApkSet.validate(parts)
            require(ApkSet.info(actualBase)["versionCode"] == base.getLong("vercode").toString()) {
                "De gedownloade versie komt niet overeen met de aangeboden versie."
            }
            val directory = File(context.filesDir, "apks").apply { mkdirs() }
            result = File.createTempFile("Smartschool-12.3.0-", ".apks", directory)
            ApkSet.pack(parts, result)
            return result
        } catch (failure: Exception) { result?.delete(); throw failure }
        finally { ApkSet.deleteTree(working) }
    }

    private fun matchesAbi(split: JSONObject, abi: String): Boolean =
        split.optString("name").lowercase().replace('-', '_').endsWith(abi.replace('-', '_'))

    private fun collectSplits(value: JSONObject?, output: MutableList<JSONObject>) {
        if (value == null) return
        val splits = value.optJSONArray("splits") ?: value.optJSONArray("list")
        if (splits != null) for (i in 0 until splits.length()) {
            val split = splits.optJSONObject(i) ?: continue
            if (split.has("path")) output.add(split)
        }
        collectSplits(value.optJSONObject("data"), output)
    }

    private fun <T> read(address: String, stage: String, consume: (java.io.InputStream) -> T): T {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https") { "De downloadlink gebruikt geen HTTPS." }
            val connection = url.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            try {
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Downloadverwijzing ontbreekt."))
                } else {
                    if (code !in 200..299) {
                        val description = try {
                            val body = connection.errorStream?.bufferedReader()?.use { reader ->
                                val chars = CharArray(4096)
                                val count = reader.read(chars)
                                if (count > 0) String(chars, 0, count) else ""
                            }
                            val error = body?.let { JSONObject(it).optJSONArray("errors")?.optJSONObject(0) }
                            error?.optString("description")?.take(250)?.takeIf { it.isNotBlank() }
                        } catch (_: Exception) { null }
                        error("$stage: server antwoordt met HTTP $code" + (description?.let { ": $it" } ?: "."))
                    }
                    return connection.inputStream.use(consume)
                }
            } finally { connection.disconnect() }
        }
        error("Te veel downloadverwijzingen.")
    }
}
