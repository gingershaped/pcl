plugins {
    id("pcl.jvm-conventions")
}

group = "pcl"
version = "1.0"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.ksp.api)
            }
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }
    }
}