plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    id("com.github.gmazzo.buildconfig") version "5.3.5"
    application
}

group = ""
version = "1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        withJava()
    }
    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.github.ajalt.mordant:mordant:2.3.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:4.2.2")
                implementation("org.jline:jline-reader:3.25.1")
                implementation("org.jline:jline-terminal-jansi:3.25.1")
            }
        }
    }
}

application {
    mainClass.set("pcl.MainKt")
}

dependencies {
    val processor = project(":annotationProcessor")
    add("kspCommonMainMetadata", processor)
    add("kspJvm", processor)
    add("kspJvmTest", processor)
    // add("kspJs", processor)
    // add("kspJsTest", processor)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

buildConfig {
    buildConfigField("VERSION", provider { project.version.toString() })
}