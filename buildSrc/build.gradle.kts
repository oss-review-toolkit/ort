import java.io.FileInputStream
import java.util.Properties

// Get the root project's properties as extras.
FileInputStream("${rootDir.parentFile}/gradle.properties").use {
    Properties().apply { load(it) }.forEach {
        extra[it.key.toString()] = it.value.toString()
    }
}

plugins {
    `kotlin-dsl`
}

repositories {
    jcenter()
    maven("https://plugins.gradle.org/m2/")
}

val ideaExtPluginVersion = extra["ideaExtPluginVersion"]

dependencies {
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:$ideaExtPluginVersion")
}
