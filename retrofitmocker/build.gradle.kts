plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.noelsung.retrofitmocker"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 21

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.retrofit)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.retrofit.adapter.rxjava3)
    testImplementation(libs.rxjava3)
}
