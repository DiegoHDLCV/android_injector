// En app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("jacoco")
}

android {
    namespace = "com.vigatec.keyreceiver"
    compileSdk = 35

    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    defaultConfig {
        applicationId = "com.vigatec.keyreceiver"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- CORRECCIÓN AQUÍ ---
        // Mueve el bloque ndk dentro de ESTE defaultConfig
        // y usa listOf() o setOf() para addAll
        ndk {
            // Forzar solo la ABI que tiene tu librería .so crítica
            abiFilters.clear() // Limpia cualquier filtro anterior
            abiFilters.add("armeabi-v7a")
        }
        // --- FIN CORRECCIÓN ---
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // DEV: Componentes pre-rellenos para agilizar pruebas
            buildConfigField("String", "DEFAULT_COMPONENT_1", "\"0123456789ABCDEF0123456789ABCDEF\"")
            buildConfigField("String", "DEFAULT_COMPONENT_2", "\"FEDCBA9876543210FEDCBA9876543210\"")
        }
        create("qa") {
            dimension = "environment"
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            // QA: Componentes vacíos para que el usuario los ingrese manualmente
            buildConfigField("String", "DEFAULT_COMPONENT_1", "\"\"")
            buildConfigField("String", "DEFAULT_COMPONENT_2", "\"\"")
        }
        create("prod") {
            dimension = "environment"
            // PROD: Componentes vacíos por defecto
            buildConfigField("String", "DEFAULT_COMPONENT_1", "\"\"")
            buildConfigField("String", "DEFAULT_COMPONENT_2", "\"\"")
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

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.ignoreFailures = true
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    lint {
        disable.add("UnrememberedMutableState")
        checkReleaseBuilds = false
        abortOnError = false
        xmlReport = true
        htmlReport = true
    }
}

dependencies {
    // ... (tus dependencias permanecen igual) ...

    implementation(fileTree(mapOf("dir" to "../shared-libs", "include" to listOf("*.jar"), "exclude" to listOf("core-3.2.1.jar"))))

    implementation(libs.hilt.android)
    implementation(project(":format"))
    implementation(project(":utils"))
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.security.crypto)

    implementation(project(":manufacturer"))
    implementation(project(":config"))
    implementation(project(":persistence"))
    implementation(project(":communication"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
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
            "**/com/vigatec/keyreceiver/data/model/**",
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