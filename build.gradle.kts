// Used in publishing
group = project.property("group") as String
version = project.property("version") as String

plugins {
    // This is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader

    // alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false

    // Support Maven for publishing
    `maven-publish`

//    alias(libs.plugins.spotless)
}

subprojects {
    apply(plugin = "maven-publish")

    afterEvaluate {
        val isSnapshot = version.toString().endsWith("-SNAPSHOT")

        publishing {
            repositories {
                // Only allow mavenLocal for SNAPSHOT versions
                if (isSnapshot) {
                    mavenLocal()
                }
            }
        }

        // Disable mavenLocal publish tasks for non-SNAPSHOT versions
        if (!isSnapshot) {
            tasks.matching { it.name.contains("MavenLocal") }.configureEach {
                enabled = false
            }
        }
    }
}

//spotless {
//    kotlin {
//        target("**/*.kt")
//        targetExclude("**/build/**")
//        ktlint()
//    }
//
//    kotlinGradle {
//        target("**/*.kts")
//        targetE
//        xclude("**/build/**")
//        ktlint()
//    }
//
//    json {
//        target("**/*.json")
//        targetExclude("**/build/**", "**/.build/**", "**/.kotlin/**", "**/.claude/**")
//        simple().indentWithSpaces(2)
//    }
//
//    yaml {
//        target("**/*.yml", "**/*.yaml")
//        targetExclude("**/build/**")
//        jackson()
//    }
//}
