plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("dev.myco.fakesse.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}
