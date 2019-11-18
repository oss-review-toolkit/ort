val jacksonVersion: String by project
val retrofitVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api("com.squareup.retrofit2:retrofit:$retrofitVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.squareup.retrofit2:converter-jackson:$retrofitVersion")
}
