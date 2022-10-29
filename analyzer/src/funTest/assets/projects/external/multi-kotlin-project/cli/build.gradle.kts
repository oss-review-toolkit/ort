plugins {
    application
    kotlin("jvm")
}

application {
    mainClassName = "cli.Main"
}

dependencies {
    implementation(project(":core"))
    implementation(kotlin("stdlib"))
}
