plugins {
    // Usamos IDs manuales para evitar errores de versiones cruzadas
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.0.21"
    // ESTE PLUGIN ES NUEVO Y OBLIGATORIO PARA KOTLIN 2.0 + COMPOSE
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

android {
    namespace = "com.toka.taskflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.toka.taskflow"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
        compose = true
    }

    // HE BORRADO EL BLOQUE 'composeOptions' PORQUE YA NO HACE FALTA EN KOTLIN 2.0
}

dependencies {
    // --- PRINCIPALES ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // --- PROYECTO FINAL (ROOM, NAV, VIEWMODEL) ---
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Fotos
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // --- TESTING ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}