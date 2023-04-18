plugins {
    kotlin("multiplatform") version "1.8.20"
    kotlin("native.cocoapods") version "1.8.20"
    id("maven-publish")
    id("com.android.library")
    id("convention.publication")
}

group = "net.iriscan"
version = "0.3.0"

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

    ios()
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    cocoapods {
        name = "BiometricSdk"
        summary = "Biometric SDK"
        homepage = "https://iriscan.net"

        license = "{ :type => 'GPL-3.0', :text => 'GNU General Public License v3.0' }"
        source = "{ :git => 'https://github.com/biometric-technologies/biometric-sdk.git', :tag => '$version' }"
        authors = "Slava Gornostal"

        ios.deploymentTarget = "11.0"
        framework {
            baseName = "BiometricSdk"
            isStatic = true
        }
        pod(name = "TensorFlowLiteObjC", moduleName = "TFLTensorFlowLite", version = "2.11.0")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-io:0.1.16")
                implementation("org.jetbrains.kotlinx:multik-core:0.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("com.soywiz.korlibs.korio:korio:2.4.11")
                implementation("io.ktor:ktor-client-core:2.2.1")
                implementation("io.github.aakira:napier:2.6.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.bytedeco:tensorflow-lite:2.10.0-1.5.8")
                implementation("org.bytedeco:tensorflow-lite-platform:2.10.0-1.5.8")
                implementation("org.bytedeco:opencv:4.6.0-1.5.8")
                implementation("org.bytedeco:opencv-platform:4.6.0-1.5.8")
                implementation("io.ktor:ktor-client-okhttp:2.2.1")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.tensorflow:tensorflow-lite:2.10.0")
                implementation("org.tensorflow:tensorflow-lite-gpu:2.10.0")
                implementation("org.tensorflow:tensorflow-lite-support:0.4.3")
                implementation("com.google.mlkit:face-detection:16.1.5")
                implementation("io.ktor:ktor-client-okhttp:2.2.1")
            }
        }
        val iosArm64Main by getting
        val iosX64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.2.1")
            }
            iosArm64Main.dependsOn(this)
            iosX64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
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

kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    binaries.all {
        freeCompilerArgs += "-Xdisable-phases=EscapeAnalysis"
    }
}