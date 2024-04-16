plugins {
    kotlin("jvm") version "1.9.23"
    id("com.github.gmazzo.buildconfig") version "5.3.5"
    application
}

group = ""
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    
    implementation("com.github.ajalt.clikt:clikt:4.2.2")
    implementation("org.jline:jline-reader:3.25.1")
    implementation("org.jline:jline-terminal-jansi:3.25.1")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("pcl.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

buildConfig {
    buildConfigField("VERSION", provider { project.version.toString() })
}