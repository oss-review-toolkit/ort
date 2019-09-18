val commonsCompressVersion: String by project
val disklrucacheVersion: String by project
val jacksonVersion: String by project
val log4jApiKotlinVersion: String by project
val okhttpVersion: String by project
val semverVersion: String by project
val xzVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api("org.apache.logging.log4j:log4j-api-kotlin:$log4jApiKotlinVersion")

    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    api("com.squareup.okhttp3:okhttp:$okhttpVersion")
    api("com.vdurmont:semver4j:$semverVersion")

    implementation(project(":spdx-utils"))

    implementation("com.jakewharton:disklrucache:$disklrucacheVersion")
    implementation("org.apache.commons:commons-compress:$commonsCompressVersion")
    implementation("org.tukaani:xz:$xzVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223-embeddable")
}
