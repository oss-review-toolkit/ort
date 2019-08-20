val commonsCodecVersion: String by project
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
    api("org.apache.logging.log4j:log4j-api-kotlin:$log4jApiKotlinVersion") {
        // Our version of the Kotlin runtime is provided by the Gradle plugin.
        exclude(module = "kotlin-runtime")
        // The Kotlin standard library we use is now called "kotlin-stdlib-jdk8".
        exclude(module = "kotlin-stdlib")
    }

    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    api("com.squareup.okhttp3:okhttp:$okhttpVersion")
    api("com.vdurmont:semver4j:$semverVersion")

    implementation(project(":spdx-utils"))

    implementation("com.jakewharton:disklrucache:$disklrucacheVersion")
    implementation("commons-codec:commons-codec:$commonsCodecVersion")
    implementation("org.apache.commons:commons-compress:$commonsCompressVersion")
    implementation("org.tukaani:xz:$xzVersion")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable")
    implementation("org.jetbrains.kotlin:kotlin-script-util")
}
