/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.io.File

import org.gradle.api.provider.Provider

object CopyrightableFiles {
    private val excludedPaths = listOf(
        "LICENSE",
        "NOTICE",
        "REUSE.toml",
        "gradlew",
        "gradle/",
        "examples/",
        "integrations/completions/",
        "plugins/reporters/asciidoc/src/main/resources/pdf-theme/pdf-theme.yml",
        "plugins/reporters/asciidoc/src/main/resources/templates/freemarker_implicit.ftl",
        "plugins/reporters/fossid/src/main/resources/templates/freemarker_implicit.ftl",
        "plugins/reporters/freemarker/src/main/resources/templates/freemarker_implicit.ftl",
        "plugins/reporters/static-html/src/main/resources/prismjs/",
        "plugins/reporters/web-app-template/yarn.lock",
        "resources/META-INF/",
        "resources/exceptions/",
        "resources/licenses/",
        "resources/licenserefs/",
        "test/assets/",
        "funTest/assets/",
        "test/resources/",
        "funTest/resources/",
        "website/babel.config.js",
        "website/docusaurus.config.js",
        "website/docs/configuration/_category_.yml",
        "website/docs/getting-started/_category_.yml",
        "website/docs/guides/_category_.yml",
        "website/docs/plugins/_category_.yml",
        "website/docs/plugins/advisors/_category_.yml",
        "website/docs/plugins/license-fact-providers/_category_.yml",
        "website/docs/plugins/package-configuration-providers/_category_.yml",
        "website/docs/plugins/package-curation-providers/_category_.yml",
        "website/docs/plugins/package-managers/_category_.yml",
        "website/docs/plugins/reporters/_category_.yml",
        "website/docs/plugins/scanners/_category_.yml",
        "website/docs/tools/_category_.yml",
        "website/sidebars.js"
    )

    private val excludedExtensions = listOf(
        "css",
        "graphql",
        "ico",
        "json",
        "md",
        "png",
        "svg",
        "ttf",
        "woff2"
    )

    fun filter(filesProvider: Provider<List<File>>): List<File> = filesProvider.get().filter { file ->
        val isHidden = file.toPath().any { it.toString().startsWith(".") }

        !isHidden
                && excludedPaths.none { it in file.invariantSeparatorsPath }
                && file.extension !in excludedExtensions
    }
}

object CopyrightUtils {
    const val EXPECTED_HOLDER =
        "The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)"

    private const val MAX_COPYRIGHT_LINES = 50
    private val copyrightPrefixRegex = Regex("Copyright .*\\d{2,}(-\\d{2,})? ", RegexOption.IGNORE_CASE)

    fun extract(file: File): List<String> {
        val copyrights = mutableListOf<String>()

        var lineCounter = 0

        file.useLines { lines ->
            lines.forEach { line ->
                if (++lineCounter > MAX_COPYRIGHT_LINES) return@forEach
                val copyright = line.replaceBefore(" Copyright ", "", "").trim()
                if (copyright.isNotEmpty() && !copyright.endsWith("\"")) copyrights += copyright
            }
        }

        return copyrights
    }

    fun extractHolders(statements: Collection<String>): List<String> {
        val holders = mutableListOf<String>()

        statements.mapNotNullTo(holders) { statement ->
            val holder = statement.replace(copyrightPrefixRegex, "")
            holder.takeUnless { it == statement }?.trim()
        }

        return holders
    }
}

object LicenseUtils {
    // The header without `lastHeaderLine` as that line is used as a marker.
    val expectedHeader = """
        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

            https://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.

        SPDX-License-Identifier: Apache-2.0
    """.trimIndent()

    private const val LAST_HEADER_LINE = "License-Filename: LICENSE"

    fun extractHeader(file: File): List<String> {
        var headerLines = file.useLines { lines ->
            lines.takeWhile { !it.endsWith(LAST_HEADER_LINE) }.toList()
        }

        while (true) {
            val uniqueColumnChars = headerLines.mapNotNullTo(mutableSetOf()) { it.firstOrNull() }

            // If there are very few different chars in a column, assume that column to consist of comment chars /
            // indentation only.
            if (uniqueColumnChars.size < 3) {
                val trimmedHeaderLines = headerLines.mapTo(mutableListOf()) { it.drop(1) }
                headerLines = trimmedHeaderLines
            } else {
                break
            }
        }

        return headerLines
    }
}
