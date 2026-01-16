/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.psi.absolutePath

import java.io.File
import java.nio.file.Path

import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective

private const val ORT_PACKAGE_NAMESPACE = "org.ossreviewtoolkit"

class OrtPackageNaming(config: Config) : Rule(
    config,
    "Reports files that do not follow ORT's package naming conventions"
) {
    private val pathPattern = Regex("""[/\\]src[/\\][^/\\]+[/\\]kotlin[/\\]""")
    private val forwardOrBackwardSlashPattern = Regex("""[/\\]""")

    private var projectPath: Path? = null

    override fun preVisit(root: KtFile) {
        super.preVisit(root)

        // TODO: Find a better way to determine the project path. Unfortunately, `KtFile.project.basePath` is null.
        projectPath = generateSequence(root.absolutePath()) { it.parent }.find {
            it.resolve("build.gradle.kts").isRegularFile()
        }
    }

    override fun visitPackageDirective(directive: KtPackageDirective) {
        super.visitPackageDirective(directive)

        // Exclusion of packages starting with "test." is required for OrtLogTestExtension used by LoggerTest, which
        // simulates an ORT extension class in a different package.
        if (directive.qualifiedName.isEmpty() || directive.qualifiedName.startsWith("test.")) return

        val path = projectPath?.let { directive.containingKtFile.absolutePath().relativeTo(it).pathString }
        if (path == null || pathPattern !in path) return

        val (pathPrefix, pathSuffix) = path.split(pathPattern, 2).map { File(it) }
        val projectDir = pathPrefix.name
        val projectGroup = pathPrefix.parent
            ?.let { ".$it" }
            ?.replace(forwardOrBackwardSlashPattern, ".")
            ?.replace("-", "")
            .orEmpty()

        // Maintain a hard-coded mapping of exceptions to the general package naming rules.
        val projectName = when (projectDir) {
            "detekt-rules" -> ".detekt"
            "fossid-webapp" -> ".fossid"
            "github-graphql" -> ".github"
            "cli-helper" -> ".helper"
            else -> ".${projectDir.replace("-", "")}"
        }

        val nestedPath = pathSuffix.parent?.replace(forwardOrBackwardSlashPattern, ".")?.let { ".$it" }.orEmpty()
        val expectedPackageName = ORT_PACKAGE_NAMESPACE + projectGroup + projectName + nestedPath
        val actualPackageName = directive.qualifiedName

        if (actualPackageName != expectedPackageName) {
            val finding = Finding(Entity.from(directive), "'$actualPackageName' should be '$expectedPackageName'")
            report(finding)
        }
    }
}
