plugins { id("com.android.application") }
// The release version lives in /VERSION (major.minor.patch). versionCode is
// derived from it, so minor and patch must stay below 100.
val preconditioningVersion = rootProject.file("VERSION").readText().trim()
val versionParts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(preconditioningVersion)
    ?.destructured?.toList()?.map { it.toInt() }
    ?.takeIf { it[1] < 100 && it[2] < 100 }
    ?: error("VERSION must be major.minor.patch with minor and patch below 100, got '$preconditioningVersion'")
android {
    namespace = "kr.neev.preconditioning"
    compileSdk = 37
    defaultConfig {
        // Independent Korean build: Android and Java use the same namespace.
        applicationId = "kr.neev.preconditioning"
        minSdk = 19
        targetSdk = 19
        versionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
        versionName = preconditioningVersion
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    lint {
        // This is a locally installed app for an Android 4.4 vehicle, not Play.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        abortOnError = true
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }
