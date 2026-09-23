plugins {
    `java-library`
}

allprojects {
    group = "org.ossreviewtoolkit.gradle.nodemodules.example"
    version = "1.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Depend on a "project" that in reality lives inside "node_modules" and is not analyzed by ORT as a project of
    // its own, as "node_modules" is a hard-coded ignored directory.
    implementation(project(":example-native-module"))
}

