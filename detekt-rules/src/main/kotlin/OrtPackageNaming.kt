/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.github.detekt.psi.toFilePath

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity

import java.io.File

import org.jetbrains.kotlin.psi.KtPackageDirective

private const val ORT_PACKAGE_NAMESPACE = "org.ossreviewtoolkit"

class OrtPackageNaming(config: Config) : Rule(config) {
    private val pathPattern = Regex("""[/\\]src[/\\][^/\\]+[/\\]kotlin[/\\]""")
    private val forwardOrBackwardSlashPattern = Regex("""[/\\]""")

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Reports files that do not follow ORT's package naming conventions",
        Debt.FIVE_MINS
    )

    override fun visitPackageDirective(directive: KtPackageDirective) {
        super.visitPackageDirective(directive)

        // Exclusion of packages starting with "test." is required for OrtLogTestExtension used by LoggerTest, which
        // simulates an ORT extension class in a different package.
        if (directive.qualifiedName.isEmpty() || directive.qualifiedName.startsWith("test.")) return

        val path = directive.containingKtFile.toFilePath().relativePath.toString()
        if (pathPattern !in path) return

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

        if (directive.qualifiedName != expectedPackageName) {
            val message = "'${directive.qualifiedName}' should be '$expectedPackageName'"
            val finding = CodeSmell(
                issue,
                // Use the message as the name to also see it in CLI output and not only in the report files.
                Entity.from(directive).copy(name = message),
                message
            )
            report(finding)
        }
    }
}
