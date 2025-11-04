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

// SonarQube Configuration
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

// Task to analyze injector module separately
tasks.register("sonarInjector") {
    dependsOn("sonarqube")
    doFirst {
        sonar {
            properties {
                property("sonar.projectKey", "android_injector_injector")
                property("sonar.projectName", "Android Injector - Injector Module")
                property("sonar.host.url", "http://100.65.127.12:9000")
                property("sonar.token", project.findProperty("SONAR_TOKEN_LOCAL")?.toString() ?: System.getenv("SONAR_TOKEN_LOCAL") ?: "")
                property("sonar.sourceEncoding", "UTF-8")

                property("sonar.sources", "${project.rootDir}/injector/src/main/java,${project.rootDir}/injector/src/main/kotlin")
                property("sonar.tests", "${project.rootDir}/injector/src/test/java,${project.rootDir}/injector/src/test/kotlin")

                property("sonar.coverage.jacoco.xmlReportPaths", "${project.rootDir}/injector/build/reports/jacoco/testDebugUnitTest/testDebugUnitTest.xml")
                property("sonar.androidLint.reportPaths", "${project.rootDir}/injector/build/reports/lint-results-debug.xml")

                property("sonar.exclusions", listOf(
                    "**/R.class", "**/R\$*.class", "**/Manifest*.*", "**/BuildConfig.*",
                    "**/*Binding.*", "**/databinding/*", "**/*_MembersInjector.*",
                    "**/*_Factory.*", "**/Dagger*Component.*", "**/Hilt_*",
                    "**/*Directions.*", "**/*Args.*", "**/*_Impl.*", "**/*_Dao.*",
                    "**/build/**", "**/generated/**", "**/test/**", "**/androidTest/**"
                ).joinToString(","))

                property("sonar.coverage.exclusions", listOf(
                    "**/InjectorApplication.kt", "**/MainActivity.kt",
                    "**/data/model/**/*.kt", "**/domain/models/**/*.kt",
                    "**/ui/screens/**/*.kt", "**/ui/components/**/*.kt", "**/ui/theme/**/*.kt",
                    "**/di/**/*.kt", "**/*Module.kt", "**/dao/**/*Dao.kt"
                ).joinToString(","))
            }
        }
    }
}

// Task to analyze keyreceiver module separately
tasks.register("sonarKeyReceiver") {
    dependsOn("sonarqube")
    doFirst {
        sonar {
            properties {
                property("sonar.projectKey", "android_injector_keyreceiver")
                property("sonar.projectName", "Android Injector - KeyReceiver Module")
                property("sonar.host.url", "http://100.65.127.12:9000")
                property("sonar.token", project.findProperty("SONAR_TOKEN_LOCAL")?.toString() ?: System.getenv("SONAR_TOKEN_LOCAL") ?: "")
                property("sonar.sourceEncoding", "UTF-8")

                property("sonar.sources", "${project.rootDir}/keyreceiver/src/main/java,${project.rootDir}/keyreceiver/src/main/kotlin")
                property("sonar.tests", "${project.rootDir}/keyreceiver/src/test/java,${project.rootDir}/keyreceiver/src/test/kotlin")

                property("sonar.coverage.jacoco.xmlReportPaths", "${project.rootDir}/keyreceiver/build/reports/jacoco/testDebugUnitTest/testDebugUnitTest.xml")
                property("sonar.androidLint.reportPaths", "${project.rootDir}/keyreceiver/build/reports/lint-results-debug.xml")

                property("sonar.exclusions", listOf(
                    "**/R.class", "**/R\$*.class", "**/Manifest*.*", "**/BuildConfig.*",
                    "**/*Binding.*", "**/databinding/*", "**/*_MembersInjector.*",
                    "**/*_Factory.*", "**/Dagger*Component.*", "**/Hilt_*",
                    "**/*Directions.*", "**/*Args.*", "**/*_Impl.*", "**/*_Dao.*",
                    "**/build/**", "**/generated/**", "**/test/**", "**/androidTest/**"
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
}