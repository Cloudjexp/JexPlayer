plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.cloudjex.jexplayer.benchmark"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            // El APK de test se ejecuta con el build type "benchmark" de :app.
            matchingFallbacks += listOf("release")
            // Sin esta firma el APK de test queda sin certificado y el
            // dispositivo lo rechaza con INSTALL_PARSE_FAILED_NO_CERTIFICATES.
            // Se usa la misma clave de depuracion que :app para que ambos APK
            // compartan identidad y se puedan instalar en paralelo.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
}
