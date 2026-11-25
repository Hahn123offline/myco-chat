plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
}

group = "dev.myco"
version = "0.1.0-SNAPSHOT"

subprojects {
    group = "dev.myco"
    version = rootProject.version
}
