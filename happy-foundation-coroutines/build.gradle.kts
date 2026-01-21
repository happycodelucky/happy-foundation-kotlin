import com.android.build.api.dsl.androidLibrary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private val PACKAGE_NAMESPACE = "com.happycodelucky.foundation.coroutines"
private val ANDROID_JAVA_COMPAT = JavaVersion.VERSION_17
private val ANDROID_JVM = JvmTarget.JVM_17


plugins {
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    androidLibrary {
        namespace = PACKAGE_NAMESPACE
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget.set(ANDROID_JVM)

            freeCompilerArgs.addAll(listOf(
                // Add arguments here
                "-opt-in=kotlinx.atomicfu.ExperimentalAtomicApi"
            ))
        }
    }

    // Apple
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    // Desktop & multi-platform testing
    jvm()

    // // Web
    // js {
    //     outputModuleName = "foundation"
    //     browser()
    //     binaries.library()
    //     generateTypeScriptDefinitions()
    //     compilerOptions {
    //         target = "es2025"
    //     }
    // }

    sourceSets {
        // KMP-NativeCoroutines uses experimental @ObjcName annotation
        all {
            languageSettings {
                optIn("ExperimentalAtomicApi")
                optIn("ExperimentalCoroutinesApi")
                optIn("kotlin.experimental.ExperimentalObjCName")
            }
        }

        //
        // Common
        //

        commonMain.dependencies {
            implementation(project.dependencies.project(":happy-foundation"))

            // Logging
            implementation(libs.touchlabs.kermit)

            // Coroutines
            implementation(project.dependencies.platform(libs.kotlinx.coroutines.bom))
            implementation(libs.kotlinx.coroutines)
        }

        //
        // Android
        //

        androidMain.dependencies {
            implementation(libs.androidx.annotation.jvm)
            implementation(libs.androidx.core)
        }

        //
        // Apple
        //

        appleMain.dependencies {

        }

        //
        // Testing
        //

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}