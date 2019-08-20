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
    mainClassName = "com.here.ort.Main"
}

repositories {
    jcenter()

    // Need to repeat the analyzer's custom repository definition here, see
    // https://github.com/gradle/gradle/issues/4106.
    maven("https://repo.gradle.org/gradle/libs-releases-local/")
}

dependencies {
    compile(project(":analyzer"))
    compile(project(":downloader"))
    compile(project(":evaluator"))
    compile(project(":model"))
    compile(project(":reporter"))
    compile(project(":scanner"))
    compile(project(":utils"))

    compile("com.beust:jcommander:$jcommanderVersion")
    compile("org.apache.logging.log4j:log4j-core:$log4jCoreVersion")
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compile("org.jetbrains.kotlin:kotlin-reflect")
    compile("org.reflections:reflections:$reflectionsVersion")

    testCompile(project(":test-utils"))

    testCompile("io.kotlintest:kotlintest-core:$kotlintestVersion")
    testCompile("io.kotlintest:kotlintest-assertions:$kotlintestVersion")
    testCompile("io.kotlintest:kotlintest-runner-junit5:$kotlintestVersion")

    funTestCompile(sourceSets["main"].output)
    funTestCompile(sourceSets["test"].output)
}

configurations["funTestCompile"].extendsFrom(configurations.testCompile.get())
configurations["funTestRuntime"].extendsFrom(configurations.testRuntime.get())
