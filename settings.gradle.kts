rootProject.name = "pcl"

include(":annotationProcessor")
include(":pcl")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}
