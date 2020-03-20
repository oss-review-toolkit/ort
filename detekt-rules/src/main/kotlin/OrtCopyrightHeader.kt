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

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity

import org.jetbrains.kotlin.psi.KtFile

private const val COPYRIGHT_LINE_NUMBER = 2
private const val COPYRIGHT_STATEMENT = "Copyright (C) "

class OrtCopyrightHeader : Rule() {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Reports files that do not contain ORT's Copyright header",
        Debt.FIVE_MINS
    )

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)

        val lines = file.text.lineSequence()

        val copyrightLine = lines.elementAtOrNull(COPYRIGHT_LINE_NUMBER - 1)
        if (copyrightLine?.contains(COPYRIGHT_STATEMENT) != true) {
            val message = "Statement '$COPYRIGHT_STATEMENT' not found in line $COPYRIGHT_LINE_NUMBER"
            val finding = CodeSmell(
                issue,
                // Use the message as the name to also see it in CLI output and not only in the report files.
                Entity.from(file).copy(name = message),
                message
            )
            report(finding)
        }
    }
}
