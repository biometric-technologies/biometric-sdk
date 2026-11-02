import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("native.cocoapods") version "2.2.20"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("maven-publish")
    id("com.android.library")
    id("convention.publication")
}

group = "net.iriscan"
version = "0.7.0"

repositories {
    google()
    mavenCentral()
}

nexusPublishing {
    repositories {
        // Maven Central Portal, OSSRH-compatible staging API (legacy OSSRH was shut down in June 2025).
        // OSSRH_USERNAME / OSSRH_PASSWORD must hold a Central Portal user token.
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("OSSRH_USERNAME") ?: "")
            password.set(System.getenv("OSSRH_PASSWORD") ?: "")
        }
    }
}

kotlin {
    androidTarget {
        publishLibraryVariants("debug", "release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    cocoapods {
        name = "BiometricSdk"
        summary = "Biometric SDK"
        homepage = "https://github.com/biometric-technologies/biometric-sdk"

        license = "{ :type => 'GPL-3.0', :text => 'GNU General Public License v3.0' }"
        source =
            "{ :git => 'https://github.com/biometric-technologies/biometric-sdk-cocoapods-release.git', :tag => '${project.version}' }"
        authors = "Slava Gornostal"

        ios.deploymentTarget = "13.0"
        framework {
            baseName = "BiometricSdk"
            isStatic = true
        }
        pod(name = "TensorFlowLiteObjC", moduleName = "TFLTensorFlowLite", version = "2.12.0")
    }

    sourceSets {
        // Opt-ins must be the same in a source set and the ones depending on it
        all {
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("io.github.aakira:napier:2.7.1")
                implementation("io.ktor:ktor-client-core:3.2.3")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                implementation("org.bytedeco:tensorflow-lite:2.10.0-1.5.8")
                implementation("org.bytedeco:tensorflow-lite-platform:2.10.0-1.5.8")
                implementation("org.bytedeco:opencv:4.6.0-1.5.8")
                implementation("org.bytedeco:opencv-platform:4.6.0-1.5.8")
                implementation("io.ktor:ktor-client-okhttp:3.2.3")
            }
        }
        androidMain {
            dependencies {
                implementation("org.tensorflow:tensorflow-lite:2.12.0")
                implementation("org.tensorflow:tensorflow-lite-support:0.4.3")
                implementation("com.google.mlkit:face-detection:16.1.5")
                implementation("io.ktor:ktor-client-okhttp:3.2.3")
            }
        }
        iosMain {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.2.3")
            }
        }
    }
}

android {
    namespace = "net.iriscan.sdk"
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.register("printVersion") {
    doLast {
        println("${project.version}")
    }
}