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

import java.io.File
import java.lang.invoke.MethodHandles

import kotlinx.serialization.Serializable

import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlLiteral
import net.peanuuutz.tomlkt.decodeFromNativeReader

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.getLicenseFromClassifier
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toml

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/** An author in Poetry's `Name <email>` notation. */
private val POETRY_AUTHOR_REGEX = Regex("""^(.*?)\s*<([^>]+)>$""")

/**
 * The parts of a `pyproject.toml` file that describe the project, see
 * https://packaging.python.org/en/latest/specifications/pyproject-toml/. Tool-specific tables are ignored.
 */
@Serializable
internal data class PyprojectFile(
    val project: Project? = null,
    val tool: Tool? = null
) {
    /**
     * The `[tool]` table. Only Poetry's section is used, as projects created before Poetry 2 keep their metadata there.
     */
    @Serializable
    data class Tool(
        val poetry: Poetry? = null
    )

    /** The `[tool.poetry]` table. Only the metadata that has a counterpart in the `[project]` table is modeled. */
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
        /**
         * Convert this table to the equivalent `[project]` table.
         */
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
                    homepage?.let { put("Homepage", it) }
                    repository?.let { put("Repository", it) }
                }
            )
    }

    /**
     * Return the project metadata from the `[project]` table, taking missing values from the `[tool.poetry]` table.
     * This is how Poetry reads projects that are only partially migrated to the `[project]` table.
     */
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

    /**
     * The `[project]` table, see https://packaging.python.org/en/latest/specifications/pyproject-toml/#project.
     */
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
         * Return the declared licenses. A PEP 639 license expression takes precedence, otherwise the legacy
         * `license.text` value and the license classifiers are used. A license file reference is ignored.
         */
        fun getDeclaredLicenses(): Set<String> =
            buildSet {
                when (license) {
                    is TomlLiteral -> add(license.content)
                    is TomlTable -> license["text"]?.asTomlLiteral()?.content?.let { add(it) }
                    else -> {}
                }

                classifiers.mapNotNullTo(this) { getLicenseFromClassifier(it) }
            }

        /** Return the authors in the `Name <email>` notation used by the other Python package managers, too. */
        fun getAuthors(): Set<String> = authors.mapNotNullTo(mutableSetOf()) { it.toAuthorString() }

        /** Return the homepage URL by a case-insensitive match on "homepage", as the keys of `urls` are free-form. */
        fun getHomepageUrl(): String =
            urls.entries.find { it.key.equals("homepage", ignoreCase = true) }?.value.orEmpty()
    }

    /**
     * A person in the `authors` or `maintainers` arrays.
     */
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

/**
 * Parse the given [pyprojectFile], or return null if it does not exist or cannot be parsed, as a broken
 * `pyproject.toml` must not prevent the lockfile from being analyzed.
 */
internal fun parsePyprojectFile(pyprojectFile: File): PyprojectFile? {
    if (!pyprojectFile.isFile) return null

    return runCatching {
        pyprojectFile.reader().use { toml.decodeFromNativeReader<PyprojectFile>(it) }
    }.onFailure {
        logger.warn { "Unable to parse '$pyprojectFile': ${it.message}" }
    }.getOrNull()
}
