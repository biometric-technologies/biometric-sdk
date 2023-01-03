plugins {
    kotlin("multiplatform") version "1.7.10"
    // kotlin("native.cocoapods") version "1.7.10"
    id("maven-publish")
    id("com.android.library")
    id("convention.publication")
}

group = "net.iriscan"
version = "0.1"

repositories {
    google()
    mavenCentral()
}

kotlin {
    android {
        publishLibraryVariants("debug", "release")
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }
    /*iosX64()
    iosArm64()
    iosSimulatorArm64()
    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        ios.deploymentTarget = "15.0"
        framework {
            baseName = "BiometricSdk"
        }
    }
    js(IR) {
        binaries.executable()
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    nativeTarget.apply {
        binaries {
            sharedLib {
                baseName = when (isMingwX64) {
                    true -> "libbiometric"
                    false -> "biometric"
                }
            }
        }
    }*/

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-io:0.1.16")
                implementation("org.jetbrains.kotlinx:multik-core:0.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("com.soywiz.korlibs.korio:korio:2.2.0")
                implementation("io.ktor:ktor-client-okhttp:2.2.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.bytedeco:tensorflow-lite:2.10.0-1.5.8")
                implementation("org.bytedeco:tensorflow-lite-platform:2.10.0-1.5.8")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.tensorflow:tensorflow-lite:2.10.0")
                implementation("org.tensorflow:tensorflow-lite-gpu:2.10.0")
                implementation("org.tensorflow:tensorflow-lite-support:0.4.3")
                implementation("com.google.mlkit:face-detection:16.1.5")
            }
        }
        /*
        val jsMain by getting
        val jsTest by getting
        val nativeMain by getting
        val nativeTest by getting*/
    }
}

android {
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    compileSdk = 33

    defaultConfig {
        minSdk = 24
        targetSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    dependencies {
    }
}