plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zain.zhacker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zain.zhacker"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "**/*.version",
                "kotlin/**"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        viewBinding = true
        buildConfig = false
        aidl = false
        renderScript = false
        shaders = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

val forbiddenReleasePermissions = setOf(
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.ACCESS_MEDIA_LOCATION"
)

fun sanitizeManifestFile(manifest: java.io.File): Int {
    if (!manifest.isFile) return 0
    var text = manifest.readText()
    var removed = 0
    for (permission in forbiddenReleasePermissions) {
        val quoted = Regex.escape(permission)
        val before = text
        text = text.replace(
            Regex("""(?s)s*<uses-permission(?:-[A-Za-z0-9_-]+)?(?=[^>]*android:name=["']$quoted["'])[^>]*/>"""),
            ""
        )
        text = text.replace(
            Regex("""(?s)s*<uses-permission(?:-[A-Za-z0-9_-]+)?(?=[^>]*android:name=["']$quoted["'])[^>]*>.*?</uses-permission(?:-[A-Za-z0-9_-]+)?>"""),
            ""
        )
        if (text != before) removed++
    }
    if (removed > 0) manifest.writeText(text)
    return removed
}

val sanitizeReleaseManifestPermissions = tasks.register("sanitizeReleaseManifestPermissions") {
    doLast {
        val intermediates = layout.buildDirectory.dir("intermediates").get().asFile
        val manifests = fileTree(intermediates) { include("**/AndroidManifest.xml") }.files
        val changed = manifests.sumOf { sanitizeManifestFile(it) }
        if (changed > 0) {
            println("Removed forbidden storage/media permissions from merged release manifest files.")
        }
    }
}

tasks.matching {
    it.name == "processReleaseMainManifest" ||
        it.name == "processReleaseManifest" ||
        it.name == "processReleaseManifestForPackage"
}.configureEach {
    val manifestTask = this
    sanitizeReleaseManifestPermissions.configure { mustRunAfter(manifestTask) }
}

tasks.matching {
    it.name == "processReleaseResources" || it.name == "packageRelease" || it.name == "assembleRelease"
}.configureEach {
    dependsOn(sanitizeReleaseManifestPermissions)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.12.1")
}
