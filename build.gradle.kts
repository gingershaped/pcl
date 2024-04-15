plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "pcl"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    
    implementation("com.github.ajalt.clikt:clikt:4.2.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("pcl.MainKt")
}