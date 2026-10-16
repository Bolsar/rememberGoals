plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bolsar.remembergoals"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bolsar.remembergoals"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        // Debug = test app next to the real one (own data), so testing never touches real goals.
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        // ponytail: release signed with the debug key, the key the installed real app already has, so updates keep its data.
        release { signingConfig = signingConfigs.getByName("debug") }
    }

    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)

    testImplementation(libs.junit)
}
