val jcommanderVersion: String by project
val reflectionsVersion: String by project

plugins {
    // Apply core plugins.
    application
}

application {
    applicationName = "orth"
    mainClassName = "com.here.ort.helper.Main"
}

repositories {
    jcenter()

    // Need to repeat the analyzer's custom repository definition here, see
    // https://github.com/gradle/gradle/issues/4106.
    maven("https://repo.gradle.org/gradle/libs-releases-local/")
}

dependencies {
    compile(project(":cli"))
}
