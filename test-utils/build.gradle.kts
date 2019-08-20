val kotlintestVersion: String by project
val log4jCoreVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api(project(":model"))

    api("io.kotlintest:kotlintest-core:$kotlintestVersion")

    // kotlintest uses slf4j 1.7, so route these calls to log4j to avoid the slf4j warning about no logger being bound.
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jCoreVersion")
}
