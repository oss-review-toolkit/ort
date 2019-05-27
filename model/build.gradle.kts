import com.here.ort.gradle.*

import org.ajoberstar.grgit.Grgit

import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

val jacksonVersion: String by project
val semverVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    api(project(":spdx-utils"))

    api("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")

    implementation(project(":utils"))

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    implementation("com.vdurmont:semver4j:$semverVersion")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

val generateVersionResource by tasks.registering {
    group = "Build"
    description = "Generates a plain text resource file containing the current application version."

    val git = Grgit.open(mapOf("dir" to rootDir))
    val version = git.describe(mapOf("longDescr" to true, "tags" to true)) ?: git.head().abbreviatedId
    val versionFile = file("$projectDir/src/main/resources/VERSION")

    inputs.property("version", version)
    outputs.file(versionFile)

    doLast {
        versionFile.writeText(version)
    }
}

tasks.withType(KotlinCompile::class) {
    dependsOn(generateVersionResource)
}

rootProject.idea {
    project {
        settings {
            taskTriggers {
                afterSync(generateVersionResource.get())
                beforeBuild(generateVersionResource.get())
            }
        }
    }
}
