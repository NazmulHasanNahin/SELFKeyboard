plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mykb.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mykb.keyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        create("mykey") {
            storeFile = file("mykey.jks")
            storePassword = "mykeyboard123"
            keyAlias = "mykey"
            keyPassword = "mykeyboard123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("mykey")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
