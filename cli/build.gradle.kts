val config4kVersion: String by project
val jcommanderVersion: String by project
val kotlintestVersion: String by project
val log4jCoreVersion: String by project
val reflectionsVersion: String by project

plugins {
    // Apply core plugins.
    application
}

application {
    applicationName = "ort"
    mainClassName = "com.here.ort.OrtMain"
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
    implementation(project(":evaluator"))
    implementation(project(":model"))
    implementation(project(":reporter"))
    implementation(project(":scanner"))
    implementation(project(":utils"))

    implementation("com.beust:jcommander:$jcommanderVersion")
    implementation("io.github.config4k:config4k:$config4kVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jCoreVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.reflections:reflections:$reflectionsVersion")

    testImplementation(project(":test-utils"))

    testImplementation("io.kotlintest:kotlintest-core:$kotlintestVersion")
    testImplementation("io.kotlintest:kotlintest-assertions:$kotlintestVersion")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:$kotlintestVersion")

    funTestImplementation(sourceSets["main"].output)
    funTestImplementation(sourceSets["test"].output)
}

configurations["funTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["funTestRuntime"].extendsFrom(configurations.testRuntime.get())
