plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // KSP генерирует код Room во время сборки.
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nautilus.ideas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nautilus.ideas"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room выгружает схему базы в JSON. Это позволяет видеть изменения
    // структуры таблиц в истории git и писать тесты миграций.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
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
        // Позволяет пользоваться java.time (LocalDate) начиная с Android 7.0.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // ViewBinding вместо findViewById: обращение к разметке проверяет
        // компилятор, опечатка в идентификаторе не доживает до запуска.
        viewBinding = true
    }
}

dependencies {
    // --- Базовые библиотеки Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)

    // --- Навигация: переключение вкладок нижнего меню ---
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- Архитектура MVVM ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- База данных ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Асинхронность: работа с БД в фоновом потоке ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Поддержка java.time на старых версиях Android ---
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    // --- Тесты ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}
