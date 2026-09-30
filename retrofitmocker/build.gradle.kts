plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
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

        //未使用任何 Android API，避免 AAR metadata 預設以 compileSdk 要求使用者的 compileSdk
        aarMetadata {
            minCompileSdk = 21
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            //僅用於本機 publishToMavenLocal；JitPack 發佈時會覆寫為 com.github.<USER>:<REPO>:<git tag>
            groupId = "com.github.noel77543"
            artifactId = "retrofitmocker"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }
        }
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
