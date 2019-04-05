val kotlintestVersion: String by project

plugins {
    // Apply core plugins.
    id("java-library")
}

dependencies {
    api(project(":model"))

    api("io.kotlintest:kotlintest-core:$kotlintestVersion")
}
