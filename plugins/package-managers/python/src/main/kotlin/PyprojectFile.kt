/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.asTomlLiteral
import dev.eav.tomlkt.decodeFromNativeReader

import java.io.File
import java.lang.invoke.MethodHandles

import kotlinx.serialization.Serializable

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.getLicenseFromClassifier
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.getLicenseFromLicenseField
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toml

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private val POETRY_AUTHOR_REGEX = Regex("""^(.*?)\s*<([^>]+)>$""")

/**
 * The project metadata of a `pyproject.toml` file, see
 * https://packaging.python.org/en/latest/specifications/pyproject-toml/.
 */
@Serializable
internal data class PyprojectFile(
    val project: Project? = null,
    val tool: Tool? = null
) {
    @Serializable
    data class Tool(
        val poetry: Poetry? = null
    )

    /** Poetry's own metadata table, which projects created before Poetry 2 use instead of the `[project]` table. */
    @Serializable
    data class Poetry(
        val name: String? = null,
        val version: String? = null,
        val description: String? = null,
        val license: String? = null,
        val authors: List<String> = emptyList(),
        val homepage: String? = null,
        val repository: String? = null,
        val classifiers: List<String> = emptyList()
    ) {
        fun toProject(): Project =
            Project(
                name = name,
                version = version,
                description = description,
                license = license?.let { TomlLiteral(it) },
                authors = authors.map { author ->
                    POETRY_AUTHOR_REGEX.matchEntire(author)?.let { Person(it.groupValues[1], it.groupValues[2]) }
                        ?: Person(name = author)
                },
                classifiers = classifiers,
                urls = buildMap {
                    homepage?.also { put("Homepage", it) }
                    repository?.also { put("Repository", it) }
                }
            )
    }

    /** Return the project metadata from the `[project]` table, taking missing values from Poetry's own table. */
    fun getProjectMetadata(): Project? {
        val poetryProject = tool?.poetry?.toProject() ?: return project
        val project = project ?: return poetryProject

        return project.copy(
            name = project.name ?: poetryProject.name,
            version = project.version ?: poetryProject.version,
            description = project.description ?: poetryProject.description,
            license = project.license ?: poetryProject.license,
            authors = project.authors.ifEmpty { poetryProject.authors },
            classifiers = project.classifiers.ifEmpty { poetryProject.classifiers },
            urls = project.urls.ifEmpty { poetryProject.urls }
        )
    }

    @Serializable
    data class Project(
        val name: String? = null,
        val version: String? = null,
        val description: String? = null,

        /** An SPDX expression as specified by PEP 639, or a legacy table with a `text` or `file` key. */
        val license: TomlElement? = null,

        val authors: List<Person> = emptyList(),
        val classifiers: List<String> = emptyList(),
        val urls: Map<String, String> = emptyMap()
    ) {
        /**
         * Return the declared licenses. A PEP 639 license expression takes precedence over the legacy `license.text`
         * value and the license classifiers.
         */
        fun getDeclaredLicenses(): Set<String> =
            when (license) {
                is TomlLiteral -> setOf(license.content)

                else -> buildSet {
                    val licenseText = (license as? TomlTable)?.get("text")?.asTomlLiteral()?.content
                    getLicenseFromLicenseField(licenseText)?.also { add(it) }
                    classifiers.mapNotNullTo(this) { getLicenseFromClassifier(it) }
                }
            }

        fun getAuthors(): Set<String> = authors.mapNotNullTo(mutableSetOf()) { it.toAuthorString() }

        /** Return the homepage URL by a case-insensitive match on "homepage", as the keys of `urls` are free-form. */
        fun getHomepageUrl(): String =
            urls.entries.find { it.key.equals("homepage", ignoreCase = true) }?.value.orEmpty()
    }

    @Serializable
    data class Person(
        val name: String? = null,
        val email: String? = null
    ) {
        fun toAuthorString(): String? =
            when {
                name != null && email != null -> "$name <$email>"
                else -> name ?: email
            }
    }
}

/** Parse the given [pyprojectFile], or return null if it does not exist or cannot be parsed. */
internal fun parsePyprojectFile(pyprojectFile: File): PyprojectFile? {
    if (!pyprojectFile.isFile) return null

    return runCatching {
        pyprojectFile.reader().use { toml.decodeFromNativeReader<PyprojectFile>(it) }
    }.onFailure {
        logger.warn { "Unable to parse '$pyprojectFile': ${it.message}" }
    }.getOrNull()
}
