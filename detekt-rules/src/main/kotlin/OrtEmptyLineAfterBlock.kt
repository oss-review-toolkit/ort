/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.allChildren

class OrtEmptyLineAfterBlock(config: Config) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Reports code blocks that are not followed by an empty line",
        Debt.FIVE_MINS
    )

    override fun visitBlockExpression(blockExpression: KtBlockExpression) {
        super.visitBlockExpression(blockExpression)
        checkExpression(blockExpression)
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        super.visitLambdaExpression(lambdaExpression)
        checkExpression(lambdaExpression)
    }

    private fun checkExpression(expression: KtExpression) {
        // Only care about blocks that span multiple lines.
        if (!expression.hasNewLine()) return

        // Find the next expression after the block, if any.
        var currentElement: PsiElement = expression
        while (currentElement.nextSibling == null) {
            currentElement = currentElement.parent ?: return
        }

        val firstElementAfterBlock = currentElement.nextSibling ?: return
        if (!firstElementAfterBlock.isNewLine()) return

        val secondElementAfterBlock = firstElementAfterBlock.nextSibling ?: return
        if (secondElementAfterBlock is LeafPsiElement && secondElementAfterBlock.elementType in allowedElements) return

        if (!firstElementAfterBlock.isNewLine(2)) {
            val message = "Missing empty line after block."

            val finding = CodeSmell(
                issue,
                // Use the message as the name to also see it in CLI output and not only in the report files.
                Entity.from(expression).copy(name = message),
                message
            )

            report(finding)
        }
    }
}

private fun KtExpression.hasNewLine(count: Int = 1): Boolean =
    allChildren.any { it.isNewLine(count) } || allChildren.any { it is KtExpression && it.hasNewLine(count) }

private fun PsiElement.isNewLine(count: Int = 1): Boolean = this is PsiWhiteSpace && "\n".repeat(count) in text

private val allowedElements = setOf(KtTokens.DOT, KtTokens.RBRACE, KtTokens.RPAR, KtTokens.SAFE_ACCESS)
