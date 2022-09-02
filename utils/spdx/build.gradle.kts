/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

import de.undercouch.gradle.tasks.download.Download

import groovy.json.JsonSlurper

import java.io.FileNotFoundException
import java.net.URL
import java.time.Year

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val spdxLicenseListVersion: String by project

plugins {
    antlr
    `java-library`

    // Work around the Kotlin plugin already depending on the Download plugin, see
    // https://youtrack.jetbrains.com/issue/KT-46034.
    id(libs.plugins.download.get().pluginId) apply false
}

tasks.withType<AntlrTask>().configureEach {
    arguments = arguments + listOf("-visitor")
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.withType<AntlrTask>())
}

dependencies {
    antlr(libs.antlr)

    implementation(project(":utils:common-utils"))

    implementation(libs.jacksonDatabind)
    implementation(libs.jacksonDataformatYaml)
    implementation(libs.jacksonDatatypeJsr310)
    implementation(libs.jacksonModuleKotlin)
}

/**
 * For the given [owner], [repo] and [revision], return the [path]'s contents as a list of URLs.
 */
fun listGitHubTree(owner: String, repo: String, revision: String, path: String): List<URL> {
    val apiUrl = "https://api.github.com/repos/$owner/$repo/git/trees/$revision"
    val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/$revision"

    logger.quiet("Fetching directory listing from $apiUrl...")

    val jsonSlurper = JsonSlurper()

    /** Get the tree information for a URL pointing to a tree API endpoint. */
    fun URL.tree(): List<Map<String, Any>> {
        val json = jsonSlurper.parse(this, "UTF-8") as Map<String, Any>
        return json["tree"] as List<Map<String, Any>>
    }

    /** Change into the specified [path] for a URL pointing to a tree API endpoint. */
    fun URL.cd(path: String): URL {
        val match = requireNotNull(tree().find { it["path"] == path && it["type"] == "tree" })
        return URL(match["url"] as String)
    }

    /** List the contents of the specified [path] and return a list of path strings. */
    fun URL.list(path: String): List<String> {
        val url = path.split('/').fold(this) { url, segment -> url.cd(segment) }
        return url.tree().map { "$path/${it["path"] as String}" }
    }

    return URL(apiUrl).list(path).map { URL("$rawUrl/$it") }
}

val importScanCodeLicenseTexts by tasks.registering(Download::class) {
    description = "Imports license texts from the ScanCode repository."
    group = "SPDX"

    src { listGitHubTree("nexB", "scancode-toolkit", "develop", "src/licensedcode/data/licenses") }
    dest("$buildDir/download/licenses/scancode-toolkit")
}

val importSpdxLicenseTexts by tasks.registering(Download::class) {
    description = "Imports license texts from the SPDX repository."
    group = "SPDX"

    src { listGitHubTree("spdx", "license-list-data", "v$spdxLicenseListVersion", "text") }
    dest("$buildDir/download/licenses/spdx")
}

val importLicenseTexts by tasks.registering {
    description = "Imports license texts from all known sources."
    group = "SPDX"

    // TODO: Consider using https://github.com/maxhbr/LDBcollector as the single meta-source for license texts.
    val importTasks = tasks.matching { it.name.matches(Regex("import.+LicenseTexts")) }
    dependsOn(importTasks)
    outputs.files(importTasks.flatMap { it.outputs.files })
}

fun getLicenseHeader(fromYear: Int = 2017, toYear: Int = Year.now().value) =
    """
    |/*
    | * Copyright (C) $fromYear-$toYear HERE Europe B.V.
    | *
    | * Licensed under the Apache License, Version 2.0 (the "License");
    | * you may not use this file except in compliance with the License.
    | * You may obtain a copy of the License at
    | *
    | *     https://www.apache.org/licenses/LICENSE-2.0
    | *
    | * Unless required by applicable law or agreed to in writing, software
    | * distributed under the License is distributed on an "AS IS" BASIS,
    | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    | * See the License for the specific language governing permissions and
    | * limitations under the License.
    | *
    | * SPDX-License-Identifier: Apache-2.0
    | * License-Filename: LICENSE
    | */
    |
    |
    """.trimMargin()

data class LicenseMetaData(
    val name: String,
    val deprecated: Boolean
)

fun licenseToEnumEntry(id: String, meta: LicenseMetaData): String {
    var enumEntry = id.toUpperCase().replace(Regex("[-.]"), "_").replace("+", "PLUS")
    if (enumEntry[0].isDigit()) {
        enumEntry = "_$enumEntry"
    }

    val fullName = meta.name.replace("\"", "\\\"")
    return if (meta.deprecated) {
        "$enumEntry(\"$id\", \"$fullName\", true)"
    } else {
        "$enumEntry(\"$id\", \"$fullName\")"
    }
}

fun generateEnumClass(
    taskName: String, description: String, jsonUrl: String, className: String, resourcePath: String,
    collectIds: (Map<String, Any>) -> Map<String, LicenseMetaData>
): Map<String, LicenseMetaData> {
    logger.quiet("Fetching $description list...")

    val jsonSlurper = JsonSlurper()
    val json = jsonSlurper.parse(URL(jsonUrl), "UTF-8") as Map<String, Any>

    val licenseListVersion = json["licenseListVersion"] as String
    logger.quiet("Found license list version '$licenseListVersion'.")

    val ids = collectIds(json)
    logger.quiet("Found ${ids.size} SPDX $description identifiers.")

    val enumFile = file("src/main/kotlin/$className.kt")
    logger.quiet("Writing enum entries to file '$enumFile'...")

    enumFile.writeText(getLicenseHeader())
    enumFile.appendText(
        """
        |@file:Suppress("MaxLineLength")
        |
        |package org.ossreviewtoolkit.utils.spdx
        |
        |import com.fasterxml.jackson.annotation.JsonCreator
        |
        """.trimMargin()
    )

    if (description == "license exception") {
        enumFile.appendText(
            """
            |import com.fasterxml.jackson.module.kotlin.readValue
            |
            """.trimMargin()
        )
    }

    enumFile.appendText(
        """
        |
        |/**
        | * An enum containing all SPDX $description IDs. This class is generated by the Gradle task
        | * '$taskName'.
        | */
        |@Suppress("EnumNaming")
        |enum class $className(
        |    /**
        |     * The SPDX id of the $description.
        |     */
        |    val id: String,
        |
        |    /**
        |     * The human-readable name of the $description.
        |     */
        |    val fullName: String,
        |
        |    /**
        |     * Whether the [id] is deprecated or not.
        |     */
        |    val deprecated: Boolean = false
        |) {
        |
        """.trimMargin()
    )

    val enumValues = ids.map { (id, meta) ->
        licenseToEnumEntry(id, meta)
    }.sorted().joinToString(",\n") {
        "    $it"
    } + ";"

    enumFile.appendText(enumValues)
    enumFile.appendText(
        """
        |
        |
        |    companion object {
        """.trimMargin()
    )

    if (description == "license") {
        enumFile.appendText(
            """
        |
        |        /**
        |         * The version of the license list.
        |         */
        |        const val LICENSE_LIST_VERSION = "$licenseListVersion"
        |
            """.trimMargin()
        )
    }

    if (description == "license exception") {
        enumFile.appendText(
            """
        |
        |        /**
        |         * The map which associates SPDX exceptions with their applicable SPDX licenses.
        |         */
        |        val mapping by lazy {
        |            val resource = SpdxLicenseException::class.java.getResource("/exception-mapping.yml")
        |            yamlMapper.readValue<Map<String, List<SpdxLicense>>>(resource)
        |        }
        |
            """.trimMargin()
        )
    }

    enumFile.appendText(
        """
        |
        |        /**
        |         * Return the enum value for the given [id], or null if it is no SPDX $description id.
        |         */
        |        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        |        @JvmStatic
        |        fun forId(id: String) =
        |            values().find { id.equals(it.id, ignoreCase = true) || id.equals(it.fullName, ignoreCase = true) }
        |    }
        |
        |
        """.trimMargin()
    )

    enumFile.appendText(
        """
        |    /**
        |     * The full $description text as a string.
        |     */
        |    val text by lazy { javaClass.getResource("/$resourcePath/${'$'}id").readText() }
        |}
        |
        """.trimMargin()
    )

    logger.quiet("Generated SPDX $description enum file '$enumFile'.")

    return ids
}

fun generateLicenseTextResources(description: String, ids: Map<String, LicenseMetaData>, resourcePath: String) {
    logger.quiet("Determining SPDX $description texts...")

    val scanCodeLicensePath = "$buildDir/download/licenses/scancode-toolkit"
    val spdxIdToScanCodeKey = mutableMapOf<String, String>()

    file(scanCodeLicensePath).walk().maxDepth(1).filter { it.isFile && it.extension == "yml" }.forEach { file ->
        file.readLines().forEach { line ->
            val keyAndValue = line.split(Regex("^spdx_license_key:"), 2)
            if (keyAndValue.size == 2) {
                spdxIdToScanCodeKey[keyAndValue.last().trim()] = file.name.removeSuffix(".yml")
            }
        }
    }

    val resourcesDir = file("src/main/resources/$resourcePath").apply {
        if (isDirectory && !deleteRecursively()) {
            throw GradleException("Failed to delete the existing '$this' directory.")
        }

        mkdirs()
    }

    ids.forEach { (id, meta) ->
        val resourceFile = resourcesDir.resolve(id)

        // Prefer the texts from ScanCode as these have better formatting than those from SPDX.
        val candidates = mutableListOf(
            "$scanCodeLicensePath/${spdxIdToScanCodeKey[id]}.LICENSE",
            "$buildDir/download/licenses/spdx/$id.txt"
        )

        if (meta.deprecated) {
            candidates += "$buildDir/download/licenses/spdx/deprecated_$id.txt"
        }

        val i = candidates.iterator()
        while (true) {
            if (i.hasNext()) {
                val candidate = i.next()

                @Suppress("SwallowedException")
                try {
                    val licenseFile = file(candidate)
                    val lines = licenseFile.readLines().map { it.trimEnd() }.asReversed().dropWhile { it.isEmpty() }
                        .asReversed().dropWhile { it.isEmpty() }
                    resourceFile.writeText(lines.joinToString("\n", postfix = "\n"))
                    logger.quiet("Got $description text for id '$id' from:\n\t$licenseFile.")
                } catch (e: FileNotFoundException) {
                    continue
                }

                break
            } else {
                throw GradleException("Failed to determine $description text for '$id' from any of $candidates.")
            }
        }
    }
}

val generateSpdxLicenseEnum by tasks.registering {
    description = "Generates the enum class of SPDX license ids and their associated texts as resources."
    group = "SPDX"

    dependsOn(importLicenseTexts)
    finalizedBy("cleanImportLicenseTexts")

    doLast {
        val description = "license"
        val resourcePath = "licenses"
        val ids = generateEnumClass(
            name,
            description,
            "https://raw.githubusercontent.com/spdx/license-list-data/v$spdxLicenseListVersion/json/licenses.json",
            "SpdxLicense",
            resourcePath
        ) { json ->
            (json["licenses"] as List<Map<String, Any>>).associate {
                val id = it["licenseId"] as String
                id to LicenseMetaData(it["name"] as String, it["isDeprecatedLicenseId"] as Boolean)
            }
        }
        generateLicenseTextResources(description, ids, resourcePath)
    }
}

val generateSpdxLicenseExceptionEnum by tasks.registering {
    description = "Generates the enum class of SPDX license exception ids and their associated texts as resources."
    group = "SPDX"

    dependsOn(importLicenseTexts)
    finalizedBy("cleanImportLicenseTexts")

    doLast {
        val description = "license exception"
        val resourcePath = "exceptions"
        val ids = generateEnumClass(
            name,
            description,
            "https://raw.githubusercontent.com/spdx/license-list-data/v$spdxLicenseListVersion/json/exceptions.json",
            "SpdxLicenseException",
            resourcePath
        ) { json ->
            (json["exceptions"] as List<Map<String, Any>>).associate {
                val id = it["licenseExceptionId"] as String
                id to LicenseMetaData(it["name"] as String, it["isDeprecatedLicenseId"] as Boolean)
            }
        }
        generateLicenseTextResources(description, ids, resourcePath)
    }
}

val generateSpdxEnums by tasks.registering {
    description = "Generates the enums for SPDX license and exception ids and their associated texts."
    group = "SPDX"

    val generateTasks = tasks.matching { it.name.matches(Regex("generateSpdx.+Enum")) }
    dependsOn(generateTasks)
    outputs.files(generateTasks.flatMap { it.outputs.files })
}
