/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import io.github.detekt.psi.absolutePath

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity

import org.jetbrains.kotlin.psi.KtImportList

class OrtImportOrder : Rule() {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Reports files that do not follow ORT's order for imports",
        Debt.FIVE_MINS
    )

    @ExperimentalStdlibApi
    override fun visitImportList(importList: KtImportList) {
        super.visitImportList(importList)

        val importPaths = importList.imports.mapTo(mutableListOf()) { it.importPath.toString() }
        val sortedImportPaths = importPaths.sorted().toMutableList()

        // TODO: Also check for blank lines between imports from different top-level domains.
        if (importPaths != sortedImportPaths) {
            val message = "Invalid import order in file '${importList.containingKtFile.absolutePath()}'"
            val finding = CodeSmell(
                issue,
                // Use the message as the name to also see it in CLI output and not only in the report files.
                Entity.from(importList).copy(name = message),
                message
            )
            report(finding)
        }
    }
}
