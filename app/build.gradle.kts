plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.coursetable"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file("../course-table.keystore")
            storePassword = "coursetable123"
            keyAlias = "coursetable"
            keyPassword = "coursetable123"
        }
    }

    defaultConfig {
        applicationId = "com.example.coursetable"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.4.4-lite"
        resourceConfigurations.addAll(listOf("zh", "en"))
    }

    packaging {
        // .so 为 Compose PathIterator 的 API<34 后端; 应用只用 drawPath, 冻结版 Lite 已验证可安全排除
        jniLibs {
            excludes += "lib/**/libandroidx.graphics.path.so"
        }
        resources {
            excludes += listOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "META-INF/version-control-info.textproto",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
