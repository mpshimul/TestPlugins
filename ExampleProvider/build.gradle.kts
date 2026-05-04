// This file: ExampleProvider/build.gradle.kts

version = 3

cloudstream {
    // name is set in the Kotlin class, not here
    description = "Stream movies from DhakaMovie BDIX"
    authors = listOf("mpshimul")
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
    language = "en"
    requiresResources = false
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + "-Xskip-metadata-version-check"
    }
}

dependencies {
    // Required for the template's sample UI code
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}