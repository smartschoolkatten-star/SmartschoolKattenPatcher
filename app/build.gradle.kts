import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "be.smartschoolkatten.patcher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "be.smartschoolkatten.patcher"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                // Partial optimization breaks Kotlin collection subclass access in release builds.
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation(files(
        "libs/smali-dexlib2-3.0.9.jar",
        "libs/apksig-9.4.1.jar",
        "libs/guava-33.4.0-jre.jar",
        "libs/failureaccess-1.0.2.jar"
    ))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// Guava includes ListenableFuture; AndroidX sometimes brings the standalone copy.
configurations.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

// Runtime assets must match the engine when Android Studio installs the patcher.
val buildKattenPayload by tasks.registering(Exec::class) {
    val payloadScript = rootProject.file("tools/build-payload.py")
    inputs.dir(rootProject.file("patch-runtime/src"))
    inputs.file(payloadScript)
    inputs.file(rootProject.file("tools/BuildBranding.java"))
    inputs.file(rootProject.file("docs/extension-assets.json"))
    inputs.dir(rootProject.file("tools/icon-res"))
    inputs.file(rootProject.file("tools/icon-manifest.xml"))
    inputs.file(rootProject.file("tools/icon-ids.txt"))
    inputs.file(rootProject.file("assets/branding/smartschool-katten-icon-source.jpg"))
    outputs.dir(project.file("src/main/assets/patch"))
    outputs.file(project.file("src/main/java/be/smartschoolkatten/patcher/engine/RuntimePayload.java"))
    // Configure Exec now; execution actions must not capture the build script.
    val sdkProperties = Properties()
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) localProperties.inputStream().use { sdkProperties.load(it) }
    val sdkPath = sdkProperties.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Android SDK ontbreekt: stel sdk.dir in local.properties in.")
    commandLine("python3", payloadScript.absolutePath, "--java-home", System.getProperty("java.home"), "--sdk", sdkPath)
}
tasks.matching { it.name == "preBuild" || it.name.endsWith("Assets") && it.name.startsWith("merge") }.configureEach {
    dependsOn(buildKattenPayload)
}
