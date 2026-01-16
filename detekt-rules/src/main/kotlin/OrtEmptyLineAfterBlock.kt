/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.allChildren

class OrtEmptyLineAfterBlock(config: Config) : Rule(
    config,
    "Reports code blocks that are not followed by an empty line"
) {
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
            val finding = Finding(Entity.from(expression), "Missing empty line after block.")
            report(finding)
        }
    }
}

private fun KtExpression.hasNewLine(count: Int = 1): Boolean =
    allChildren.any { it.isNewLine(count) } || allChildren.any { it is KtExpression && it.hasNewLine(count) }

private fun PsiElement.isNewLine(count: Int = 1): Boolean = this is PsiWhiteSpace && "\n".repeat(count) in text

private val allowedElements = setOf(KtTokens.DOT, KtTokens.RBRACE, KtTokens.RPAR, KtTokens.SAFE_ACCESS)
