plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nautilus.ideas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nautilus.ideas"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        // Без этой строки Android Studio не может собрать и запустить
        // инструментальные тесты из app/src/androidTest.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    buildFeatures {
        // ViewBinding вместо findViewById: опечатка в идентификаторе
        // разметки не доживает до запуска, её ловит компилятор.
        viewBinding = true
    }
}

dependencies {
    // --- Базовые библиотеки Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)

    // --- Навигация: переключение вкладок нижнего меню ---
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- Архитектура MVVM: понадобится со 2-3 недели ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // --- Тесты ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
