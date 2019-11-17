val jcommanderVersion: String by project
val log4jCoreVersion: String by project
val reflectionsVersion: String by project

plugins {
    // Apply core plugins.
    application
}

application {
    applicationName = "orth"
    mainClassName = "com.here.ort.helper.Main"
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        // Work around the command line length limit on Windows when passing the classpath to Java, see
        // https://github.com/gradle/gradle/issues/1989#issuecomment-395001392.
        windowsScript.writeText(windowsScript.readText().replace(Regex("set CLASSPATH=.*"),
            "set CLASSPATH=%APP_HOME%\\\\lib\\\\*"))
    }
}

repositories {
    jcenter()

    // Need to repeat the analyzer's custom repository definition here, see
    // https://github.com/gradle/gradle/issues/4106.
    maven("https://repo.gradle.org/gradle/libs-releases-local/")
}

dependencies {
    implementation(project(":analyzer"))
    implementation(project(":downloader"))
    implementation(project(":reporter"))
    implementation(project(":utils"))

    implementation("com.beust:jcommander:$jcommanderVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jCoreVersion")
}
