plugins {
    id("com.android.library")
    id("kotlin-android")
}

version = 1

cloudstream {
    //name = "Fmftp BDIX"
    description = "Movies from fmftp.net – Hollywood, Bollywood, Hindi Dubbed, Indian Bangla"
    authors = listOf("mpshimul")
    status = 1
    tvTypes = listOf("Movie")
    language = "bn"
    requiresResources = false
}

android {
    namespace = "com.fmftp"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
        targetSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + "-Xskip-metadata-version-check"
    }
}

dependencies {
    implementation("com.github.Blatzar:NiceHttp:0.4.11")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}