plugins {
    // Apply core plugins.
    id("java-library")
}

dependencies {
    api(project(":model"))

    implementation(project(":utils"))
}
