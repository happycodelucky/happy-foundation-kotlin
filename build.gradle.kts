plugins {
    // This is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader

    // alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    // alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false

    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }

    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint()
    }

    json {
        target("**/*.json")
        targetExclude("**/build/**", "**/.kotlin/**", "**/.claude/**")
        simple().indentWithSpaces(2)
    }

    yaml {
        target("**/*.yml", "**/*.yaml")
        targetExclude("**/build/**")
        jackson()
    }
}
