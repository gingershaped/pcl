plugins {
    id("pcl.kotlin-conventions")
}


kotlin {
    jvm {
        withJava()
    }
    jvmToolchain(17)
}