val kotlintestVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api(project(":model"))

    api("io.kotlintest:kotlintest-core:$kotlintestVersion")
}
