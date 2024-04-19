plugins {
    application
    id("pcl.jvm-conventions")
    id("pcl.js-conventions")
    alias(libs.plugins.ksp)
    alias(libs.plugins.buildconfig)
}

group = ""
version = "1.0"


kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.mordant)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.clikt)
                implementation(libs.jline.reader)
                implementation(libs.jline.jansi)
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
    add("kspJs", processor)
    add("kspJsTest", processor)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

buildConfig {
    buildConfigField("VERSION", provider { project.version.toString() })
}