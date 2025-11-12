plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vigatec.communication"
    compileSdk = 35

    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/**")
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // Forzar solo la ABI que tiene tu librería .so crítica
            abiFilters.clear() // Limpia cualquier filtro anterior
            abiFilters.add("armeabi-v7a")
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "../shared-libs", "include" to listOf("*.jar"), "exclude" to listOf("core-3.2.1.jar", "CH34xUARTDriver.jar"))))
    // Note: Using usb-serial-for-android instead of legacy CH34xUARTDriver

    // USB Serial for Android - Modern CH340 support with proper Android 12+ PendingIntent handling
    implementation("com.github.mik3y:usb-serial-for-android:3.9.0")

    implementation(project(":config"))
    implementation(":urovo-sdk-v1.0.20:@aar")


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(project(":format"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}