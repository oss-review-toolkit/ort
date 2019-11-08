val digraphVersion: String by project
val jacksonVersion: String by project
val kotlinxCoroutinesVersion: String by project
val mavenVersion: String by project
val mavenResolverVersion: String by project
val semverVersion: String by project
val toml4jVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

repositories {
    maven("https://repo.gradle.org/gradle/libs-releases-local/")
}

dependencies {
    api(project(":model"))
    api(project(":clearly-defined"))

    implementation(project(":downloader"))
    implementation(project(":utils"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.moandjiezana.toml:toml4j:$toml4jVersion")
    implementation("com.paypal.digraph:digraph-parser:$digraphVersion")
    implementation("com.vdurmont:semver4j:$semverVersion")

    implementation("org.apache.maven:maven-core:$mavenVersion")
    implementation("org.apache.maven:maven-compat:$mavenVersion")

    // The classes from the maven-resolver dependencies are not used directly but initialized by the Plexus IoC
    // container automatically. They are required on the classpath for Maven dependency resolution to work.
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:$mavenResolverVersion")
    implementation("org.apache.maven.resolver:maven-resolver-transport-file:$mavenResolverVersion")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:$mavenResolverVersion")
    implementation("org.apache.maven.resolver:maven-resolver-transport-wagon:$mavenResolverVersion")

    implementation("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
}
