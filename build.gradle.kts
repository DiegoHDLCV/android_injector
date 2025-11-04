// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.sonarqube) apply true
    id("jacoco")
    alias(libs.plugins.owasp.dependencycheck) apply false
}

// Configure JaCoCo for all subprojects
subprojects {
    apply(plugin = "jacoco")

    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }
}

// Configuration for module-specific analysis
val sonarModule: String = System.getProperty("sonarModule") ?: ""

// SonarQube Configuration - only global if no specific module is selected
if (sonarModule.isEmpty()) {
    sonar {
        properties {
            // Common properties
            property("sonar.host.url", "http://100.65.127.12:9000")
            property("sonar.token", project.findProperty("SONAR_TOKEN_LOCAL")?.toString() ?: System.getenv("SONAR_TOKEN_LOCAL") ?: "")
            property("sonar.sourceEncoding", "UTF-8")
            property("sonar.verbose", "false")

            // Exclusions - same for all modules
            property("sonar.exclusions", listOf(
                "**/R.class", "**/R\$*.class", "**/Manifest*.*", "**/BuildConfig.*",
                "**/*Binding.*", "**/databinding/*", "**/*_MembersInjector.*",
                "**/*_Factory.*", "**/Dagger*Component.*", "**/Hilt_*",
                "**/*Directions.*", "**/*Args.*", "**/*_Impl.*", "**/*_Dao.*",
                "**/build/**", "**/generated/**",
                "**/test/**", "**/androidTest/**",
                "**/dev_injector/**"  // Completely exclude dev_injector module
            ).joinToString(","))

            // Coverage exclusions
            property("sonar.coverage.exclusions", listOf(
                // Application classes
                "**/Application.kt", "**/MainActivity.kt",
                // Data models
                "**/data/model/**/*.kt", "**/domain/models/**/*.kt",
                // UI Composables
                "**/ui/screens/**/*.kt", "**/ui/components/**/*.kt", "**/ui/theme/**/*.kt",
                // DI modules
                "**/di/**/*.kt", "**/*Module.kt",
                // DAOs
                "**/dao/**/*Dao.kt",
                // Hardware wrappers and communication
                "**/manufacturer/**/*.kt",
                "**/communication/**/*.kt",
                // Complete dev_injector exclusion
                "**/dev_injector/**/*.kt"
            ).joinToString(","))
        }
    }
}

// Update SonarQube configuration based on which module is being analyzed
if (sonarModule == "injector") {
    sonar {
        properties {
            property("sonar.projectKey", "android_injector_injector")
            property("sonar.projectName", "Android Injector - Injector Module")
            property("sonar.host.url", "http://100.65.127.12:9000")
            property("sonar.token", project.findProperty("SONAR_TOKEN_LOCAL")?.toString() ?: System.getenv("SONAR_TOKEN_LOCAL") ?: "")
            property("sonar.sourceEncoding", "UTF-8")

            property("sonar.coverage.jacoco.xmlReportPaths", "injector/build/reports/jacoco/testProdDebugUnitTest/testProdDebugUnitTest.xml")
            property("sonar.androidLint.reportPaths", "injector/build/reports/lint-results-prodDebug.xml")

            // Exclude everything except injector sources
            property("sonar.exclusions", listOf(
                // Exclude all other modules
                "keyreceiver/**", "dev_injector/**", "communication/**", "format/**",
                "manufacturer/**", "persistence/**", "config/**", "utils/**",
                // Exclude generated and test files
                "**/R.class", "**/R\$*.class", "**/Manifest*.*", "**/BuildConfig.*",
                "**/*Binding.*", "**/databinding/*", "**/*_MembersInjector.*",
                "**/*_Factory.*", "**/Dagger*Component.*", "**/Hilt_*",
                "**/*Directions.*", "**/build/**", "**/generated/**"
            ).joinToString(","))

            property("sonar.coverage.exclusions", listOf(
                "**/InjectorApplication.kt", "**/MainActivity.kt",
                "**/data/model/**/*.kt", "**/domain/models/**/*.kt",
                "**/ui/screens/**/*.kt", "**/ui/components/**/*.kt", "**/ui/theme/**/*.kt",
                "**/di/**/*.kt", "**/*Module.kt", "**/dao/**/*Dao.kt"
            ).joinToString(","))
        }
    }
} else if (sonarModule == "keyreceiver") {
    sonar {
        properties {
            property("sonar.projectKey", "android_injector_keyreceiver")
            property("sonar.projectName", "Android Injector - KeyReceiver Module")
            property("sonar.host.url", "http://100.65.127.12:9000")
            property("sonar.token", project.findProperty("SONAR_TOKEN_LOCAL")?.toString() ?: System.getenv("SONAR_TOKEN_LOCAL") ?: "")
            property("sonar.sourceEncoding", "UTF-8")

            property("sonar.coverage.jacoco.xmlReportPaths", "keyreceiver/build/reports/jacoco/testProdDebugUnitTest/testProdDebugUnitTest.xml")
            property("sonar.androidLint.reportPaths", "keyreceiver/build/reports/lint-results-prodDebug.xml")

            // Exclude everything except keyreceiver sources
            property("sonar.exclusions", listOf(
                // Exclude all other modules
                "injector/**", "dev_injector/**", "communication/**", "format/**",
                "manufacturer/**", "persistence/**", "config/**", "utils/**",
                // Exclude generated and test files
                "**/R.class", "**/R\$*.class", "**/Manifest*.*", "**/BuildConfig.*",
                "**/*Binding.*", "**/databinding/*", "**/*_MembersInjector.*",
                "**/*_Factory.*", "**/Dagger*Component.*", "**/Hilt_*",
                "**/*Directions.*", "**/build/**", "**/generated/**"
            ).joinToString(","))

            property("sonar.coverage.exclusions", listOf(
                "**/KeyReceiverApplication.kt", "**/MainActivity.kt",
                "**/data/model/**/*.kt", "**/domain/models/**/*.kt",
                "**/ui/screens/**/*.kt", "**/ui/components/**/*.kt", "**/ui/theme/**/*.kt",
                "**/di/**/*.kt", "**/*Module.kt"
            ).joinToString(","))
        }
    }
}

