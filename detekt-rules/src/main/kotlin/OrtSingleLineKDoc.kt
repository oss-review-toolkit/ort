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

package org.ossreviewtoolkit.detekt

import com.intellij.psi.impl.source.tree.LeafPsiElement

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.modifiedText
import dev.detekt.psi.AnnotationExcluder
import dev.detekt.psi.FunctionMatcher
import dev.detekt.psi.fullyQualifiedNameGlobToRegex

import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/** The default of detekt's `MaxLineLength` rule, used if its configuration cannot be found. */
private const val DEFAULT_MAX_LINE_LENGTH = 120

private val SUPPRESSION_ANNOTATIONS = setOf("Suppress", "SuppressWarnings")
private val DETEKT_SUPPRESSION_PREFIX_REGEX = "detekt[.:]".toRegex(RegexOption.IGNORE_CASE)

private val UNSUPPORTED_TOKENS = setOf(KDocTokens.TAG_NAME, KDocTokens.CODE_BLOCK_TEXT)

/**
 * Reports KDoc comments that span multiple lines although their content is a single line that would fit into a
 * single-line KDoc without exceeding the maximum line length. The maximum line length is taken from the configuration
 * of detekt's `style > MaxLineLength` rule.
 */
class OrtSingleLineKDoc(config: Config) : Rule(
    config,
    "Reports multi-line KDoc comments that fit on a single line"
) {
    private val maxLineLength: Int by lazy {
        // Navigate from this rule's configuration via the rule set's configuration to the root configuration.
        config.parent?.parent?.subConfig("style")?.subConfig("MaxLineLength")?.valueOrNull<Int>("maxLineLength")
            ?: DEFAULT_MAX_LINE_LENGTH
    }

    /** The IDs that suppress this rule, including the configured aliases, as accepted by detekt. */
    private val acceptedSuppressionIds: Set<String> by lazy {
        val ruleSetId = OrtRuleSet.ID.value
        val ruleId = ruleName.value
        val aliases = config.valueOrDefault(Config.ALIASES_KEY, emptyList<String>())

        setOf(ruleId, "ALL", "all", "All", ruleSetId, "$ruleSetId.$ruleId", "$ruleSetId:$ruleId") + aliases
    }

    /** Matchers for the annotations configured via `ignoreAnnotated`, as used by detekt. */
    private val ignoredAnnotations: List<Regex> by lazy {
        config.valueOrDefault("ignoreAnnotated", emptyList<String>()).map { it.fullyQualifiedNameGlobToRegex() }
    }

    /** Matchers for the functions configured via `ignoreFunction`, as used by detekt. */
    private val ignoredFunctions: List<FunctionMatcher> by lazy {
        config.valueOrDefault("ignoreFunction", emptyList<String>()).map { FunctionMatcher.fromFunctionSignature(it) }
    }

    override fun visit(root: KtFile) {
        val replacements = root.findSingleLineKDocReplacements()
        if (replacements.isEmpty()) return

        replacements.keys.forEach { kdoc ->
            report(Finding(Entity.from(kdoc), "KDoc fits on a single line and should be written as `/** ... */`."))
        }

        if (autoCorrect) {
            // Other rules might already have modified the file, so work on the latest text.
            val file = root.modifiedText?.let { KtPsiFactory(root.project).createFile(root.name, it) } ?: root
            root.modifiedText = file.replaceKDocs(file.findSingleLineKDocReplacements())
        }
    }

    private fun KtFile.findSingleLineKDocReplacements(): Map<KDoc, String> =
        collectDescendantsOfType<KDoc>()
            // Suppressed findings are only filtered by detekt after visiting the file, so explicitly skip suppressed
            // KDocs to not auto-correct them.
            .filterNot { Entity.from(it).ktElement.isSuppressed() }
            .mapNotNull { kdoc -> kdoc.toSingleLine()?.let { kdoc to it } }
            .toMap()

    /**
     * Return whether this rule is suppressed for this element or any of its enclosing elements. This mirrors the
     * suppression logic of detekt, which is applied to findings only after visiting a file and is not part of its
     * public API: Suppression via `@Suppress` or `@SuppressWarnings` annotations, and via the `ignoreAnnotated` and
     * `ignoreFunction` configuration options.
     */
    private fun KtElement.isSuppressed(): Boolean {
        val annotated = generateSequence(this as? KtAnnotated ?: getStrictParentOfType<KtAnnotated>()) {
            it.getStrictParentOfType<KtAnnotated>()
        }

        val isSuppressedByAnnotation = annotated.flatMap { it.annotationEntries }
            .filter { it.typeReference?.text in SUPPRESSION_ANNOTATIONS }
            .flatMap { it.valueArguments }
            .mapNotNull { it.getArgumentExpression()?.text }
            .map { it.replace(DETEKT_SUPPRESSION_PREFIX_REGEX, "").replace("\"", "") }
            .any { it in acceptedSuppressionIds }
        if (isSuppressedByAnnotation) return true

        if (ignoredAnnotations.isNotEmpty()) {
            val excluder = AnnotationExcluder(containingKtFile, ignoredAnnotations, fullAnalysis = false)
            if (annotated.any { excluder.shouldExclude(it.annotationEntries) }) return true
        }

        if (ignoredFunctions.isNotEmpty()) {
            val functions = generateSequence(this as? KtNamedFunction ?: getStrictParentOfType<KtNamedFunction>()) {
                it.getStrictParentOfType<KtNamedFunction>()
            }

            if (functions.any { function -> ignoredFunctions.any { it.match(function, fullAnalysis = false) } }) {
                return true
            }
        }

        return false
    }

    /**
     * Return the single-line representation of this KDoc, or null if it already is a single line, has more than one
     * line of content, contains block tags or code blocks, or would exceed the maximum line length.
     */
    private fun KDoc.toSingleLine(): String? {
        if ('\n' !in text) return null

        // Rely on the KDoc lexer to detect block tags and code blocks, as code blocks would lose their formatting on a
        // single line.
        if (anyDescendantOfType<LeafPsiElement> { it.elementType in UNSUPPORTED_TOKENS }) return null

        // Rely on the KDoc parser to remove the decorative asterisks without changing the Markdown content.
        val content = getDefaultSection().getContent().lines().singleOrNull { it.isNotBlank() }?.trim() ?: return null

        val singleLine = "/** $content */"

        val fileText = containingFile.text
        val lineStart = fileText.lastIndexOf('\n', textRange.startOffset - 1) + 1
        val lineEnd = fileText.indexOf('\n', textRange.endOffset).takeUnless { it < 0 } ?: fileText.length
        val lineLength = textRange.startOffset - lineStart + singleLine.length + lineEnd - textRange.endOffset

        return singleLine.takeIf { lineLength <= maxLineLength }
    }
}

private fun KtFile.replaceKDocs(replacements: Map<KDoc, String>): String =
    StringBuilder(text).apply {
        replacements.entries.sortedByDescending { it.key.textRange.startOffset }.forEach { (kdoc, replacement) ->
            replace(kdoc.textRange.startOffset, kdoc.textRange.endOffset, replacement)
        }
    }.toString()
