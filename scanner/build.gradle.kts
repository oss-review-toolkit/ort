val kotlinxCoroutinesVersion: String by project

plugins {
    // Apply core plugins.
    id("java-library")
}

dependencies {
    api(project(":model"))

    implementation(project(":downloader"))
    implementation(project(":utils"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
}
