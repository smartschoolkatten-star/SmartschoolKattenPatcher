import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
}

val revision = Regex("PATCH_REVISION\\s*=\\s*(\\d+)").find(
    rootProject.file("app/src/main/java/be/smartschoolkatten/patcher/engine/KattenPatcher.java").readText()
)!!.groupValues[1].toInt()

android {
    namespace = "be.smartschoolkatten.runtime"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "be.smartschoolkatten.mobile"
        minSdk = 32
        targetSdk = 37
        versionCode = 120300000 + revision
        versionName = "12.3.0-katten.r$revision"
    }
    buildTypes {
        debug { applicationIdSuffix = ".test" }
    }
    sourceSets {
        getByName("main") {
            java.directories.add("../patch-runtime/src")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

/** Supplies the actual app APK to the standard Android Studio Run/Debug pipeline. */
abstract class BuildKattenApk @Inject constructor(private val commands: ExecOperations) : DefaultTask() {
    @get:Input abstract val javaHome: Property<String>
    @get:Input abstract val sdkPath: Property<String>
    @get:Input abstract val mode: Property<String>
    @get:Input @get:Optional abstract val signingDirectory: Property<String>
    @get:Internal abstract val checkout: DirectoryProperty
    @get:InputDirectory abstract val packagedApk: DirectoryProperty
    @get:OutputDirectory abstract val apkDirectory: DirectoryProperty

    @TaskAction fun build() {
        val command = mutableListOf("python3", checkout.file("tools/build-apk.py").get().asFile.absolutePath,
            "--java-home", javaHome.get(), "--sdk", sdkPath.get(),
            "--studio-output", apkDirectory.get().asFile.absolutePath, "--studio-variant", mode.get())
        if (mode.get() == "debug") command += "--debug-test"
        else {
            check(signingDirectory.isPresent) { "Geef voor een release de bestaande app-sleutel op met -PkattenSigningDir=/privé/map." }
            command += listOf("--signing-dir", signingDirectory.get())
        }
        commands.exec { workingDir(checkout.get().asFile); commandLine(command) }
    }
}

abstract class VerifyStudioApk : DefaultTask() {
    @get:InputDirectory abstract val apkDirectory: DirectoryProperty
    @get:Input abstract val applicationId: Property<String>
    @get:Internal abstract val artifactsLoader: Property<BuiltArtifactsLoader>
    @TaskAction fun verify() {
        val built = checkNotNull(artifactsLoader.get().load(apkDirectory.get())) { "Android Studio kan de APK-uitvoer niet lezen." }
        check(built.applicationId == applicationId.get()) { "Android Studio zou de verkeerde app installeren." }
        check(built.elements.size == 1 && built.elements.all { File(it.outputFile).isFile }) { "De installeerbare kattenapp ontbreekt." }
        logger.lifecycle("Android Studio APK gecontroleerd: ${built.applicationId}")
    }
}

val sdkProperties = Properties()
rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { sdkProperties.load(it) }
val androidSdkPath = sdkProperties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT") ?: error("Android SDK ontbreekt; stel sdk.dir in local.properties in.")

androidComponents.onVariants { variant ->
    val producer = tasks.register<BuildKattenApk>("build${variant.name.replaceFirstChar { it.uppercase() }}KattenApk") {
        javaHome.set(System.getProperty("java.home"))
        sdkPath.set(androidSdkPath)
        mode.set(variant.name)
        signingDirectory.set(providers.gradleProperty("kattenSigningDir"))
        checkout.set(rootProject.layout.projectDirectory)
        inputs.dir(rootProject.file("patch-runtime/src"))
        inputs.dir(rootProject.file("tools"))
        inputs.dir(rootProject.file("app/src/main/java/be/smartschoolkatten/patcher/engine"))
        inputs.dir(rootProject.file("assets/branding"))
        inputs.file(rootProject.file("docs/extension-assets.json"))
    }
    variant.artifacts.use(producer)
        .wiredWithDirectories(BuildKattenApk::packagedApk, BuildKattenApk::apkDirectory)
        .toTransform(SingleArtifact.APK)
    // Studio's APK listing redirect uses the conventional output location.
    producer.configure { apkDirectory.set(layout.buildDirectory.dir("outputs/apk/${variant.name}")) }
    tasks.matching { it.name == "create${variant.name.replaceFirstChar { it.uppercase() }}ApkListingFileRedirect" }
        .configureEach { dependsOn(producer) }
    tasks.register<VerifyStudioApk>("verify${variant.name.replaceFirstChar { it.uppercase() }}StudioApk") {
        apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
        applicationId.set(variant.applicationId)
        artifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
    }
}
