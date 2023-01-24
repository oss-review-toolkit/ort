plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

group = "org.ossreviewtoolkit.gradle.composite.example3"
version = "3.0.0"

dependencies {
    implementation("log4j:log4j:1.2.17")
}
