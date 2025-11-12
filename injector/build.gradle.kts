plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("jacoco")
}

android {
    namespace = "com.vigatec.injector"
    compileSdk = 35

    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    defaultConfig {
        applicationId = "com.vigatec.injector"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Forzar solo la ABI que tiene tu librería .so crítica
            abiFilters.clear() // Limpia cualquier filtro anterior
            abiFilters.add("armeabi-v7a")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        create("qa") {
            dimension = "environment"
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
        }
        create("prod") {
            dimension = "environment"
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
        debug {
            enableUnitTestCoverage = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.ignoreFailures = true
            }
        }
    }

    lint {
        abortOnError = false
        xmlReport = true
        htmlReport = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "../shared-libs", "include" to listOf("*.jar"), "exclude" to listOf("core-3.2.1.jar"))))


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Hilt
    implementation(libs.hilt.android)
    implementation(project(":utils"))
    implementation(project(":config"))
    implementation(project(":communication"))
    implementation(project(":format"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    implementation(project(":persistence"))
    implementation(project(":manufacturer"))
    
    // Gson para importar llaves de prueba
    implementation(libs.gson)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// JaCoCo Configuration
jacoco {
    toolVersion = "0.8.12"
}

// Create JaCoCo tasks for each flavor
listOf("dev", "qa", "prod").forEach { flavor ->
    val flavorCapitalized = flavor.replaceFirstChar { it.uppercase() }
    tasks.register<JacocoReport>("jacoco${flavorCapitalized}TestReport") {
        description = "Generate JaCoCo coverage report for $flavor flavor debug unit tests"
        group = "verification"
        dependsOn("test${flavorCapitalized}DebugUnitTest")

        reports {
            xml.required.set(true)
            html.required.set(true)
            xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test${flavorCapitalized}DebugUnitTest/test${flavorCapitalized}DebugUnitTest.xml"))
        }

        val fileFilter = listOf(
            "**/R.class", "**/R\$*.class", "**/BuildConfig.*",
            "**/Manifest*.*", "**/*Test*.*", "android/**/*.*",
            "**/*\$ViewInjector*.*", "**/*Dagger*.*",
            "**/*MembersInjector*.*", "**/*_Factory.*",
            "**/*_Provide*Factory*.*", "**/*_ViewBinding*.*",
            "**/AutoValue_*.*", "**/R2.class", "**/R2\$*.class",
            "**/*Directions\$*", "**/*Directions.class",
            "**/*Binding.*",
            "**/com/vigatec/injector/data/model/**",
            "**/MainActivity*.class"
        )

        val debugTree = fileTree("${project.layout.buildDirectory.get()}/tmp/kotlin-classes/${flavor}Debug") {
            exclude(fileFilter)
        }

        val mainSrc = "${project.projectDir}/src/main/java"

        sourceDirectories.setFrom(files(mainSrc))
        classDirectories.setFrom(files(debugTree))
        executionData.setFrom(fileTree(project.layout.buildDirectory.get()) {
            include("jacoco/test${flavorCapitalized}DebugUnitTest.exec", "outputs/unit_test_code_coverage/${flavor}DebugUnitTest/test${flavorCapitalized}DebugUnitTest.exec")
        })
    }
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}