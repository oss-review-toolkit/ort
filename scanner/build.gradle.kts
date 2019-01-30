val kotlinxCoroutinesVersion: String by project
val postgresVersion: String by project
val postgresEmbeddedVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api(project(":model"))

    implementation(project(":downloader"))
    implementation(project(":utils"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")

    funTestImplementation("com.opentable.components:otj-pg-embedded:$postgresEmbeddedVersion")
}
