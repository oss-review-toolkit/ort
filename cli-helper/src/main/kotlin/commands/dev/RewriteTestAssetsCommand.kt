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

package org.ossreviewtoolkit.helper.commands.dev

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import kotlin.reflect.KClass

import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.utils.common.expandTilde

internal class RewriteTestAssetsCommand : OrtHelperCommand(
    help = "Searches all test assets directories in the given ORT sources directory for recognized serialized files " +
        "and tries to de-serialize and serialize the file. The command can be used to update the test assets " +
        "after making changes to the corresponding model classes or serializer configuration, e.g. after " +
        "annotating a property to not be serialized if empty."
) {
    private val ortSourcesDir by option(
        "--ort-sources-dir", "-i",
        help = "The directory containing the source code of ORT."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val candidateFiles = findCandidateFiles(ortSourcesDir)
        println("Found ${candidateFiles.size} candidate file(s).\n")

        val candidateFilesByType = patchAll(candidateFiles)

        candidateFilesByType.forEach { (type, files) ->
            files.forEach { file ->
                println("[$type] ${file.absolutePath}")
            }
        }

        println("\nSummary\n")

        candidateFilesByType.forEach { (type, files) ->
            println("$type: ${files.size}")
        }
    }
}

// Fake values to replace the replace patterns with in order to make de-serialization work. The fake values must be
// unique and no fake value must be a substring of another.
private val REPLACE_PATTERN_REPLACEMENTS = listOf(
    "<REPLACE_JAVA>" to "33445501",
    "<REPLACE_OS>" to "33445502",
    "\"<REPLACE_PROCESSORS>\"" to "33445503",
    "\"<REPLACE_MAX_MEMORY>\"" to "33445504",
    "<REPLACE_DEFINITION_FILE_PATH>" to "33445505",
    "<REPLACE_ABSOLUTE_DEFINITION_FILE_PATH>" to "33445506",
    "<REPLACE_URL>" to "http://replace/url/non/processed",
    "<REPLACE_REVISION>" to "33445507",
    "<REPLACE_PATH>" to "replace/path",
    "<REPLACE_URL_PROCESSED>" to "http://replace/url/processed"
)

private val TARGET_CLASSES = setOf(
    AnalyzerResult::class,
    AnalyzerRun::class,
    EvaluatorRun::class,
    OrtResult::class,
    ProjectAnalyzerResult::class,
    PackageManagerResult::class,
    ScanResult::class,
    ScannerRun::class
)

// Paths to nodes in the tree of JsonNodes, whose subtree shall not be changed. Explicitly ignoring these subtrees is
// needed in order to avoid bringing in default values property values which are not present in the original file.
private val IGNORE_PATHS_FOR_TARGET_CLASSES = mapOf(
    AnalyzerRun::class to listOf(
        "config",
        "environment"
    ),
    EvaluatorRun::class to listOf(
        "config",
        "environment"
    ),
    OrtResult::class to listOf(
        "analyzer/config",
        "analyzer/environment",
        "scanner/config",
        "scanner/environment",
        "evaluator/config",
        "evaluator/environment",
        "advisor/config",
        "advisor/environment"
    ),
    ScannerRun::class to listOf(
        "config",
        "environment"
    )
)

private fun findCandidateFiles(dir: File): List<File> =
    FileFormat.findFilesWithKnownExtensions(dir).filter {
        "/src/funTest/assets/" in it.absolutePath || "/src/test/assets/" in it.absolutePath
    }

private fun patchAll(candidateFiles: Collection<File>): Map<String, List<File>> =
    candidateFiles.groupByTo(sortedMapOf()) { it.patch() }

private fun File.patch(): String = TARGET_CLASSES.firstOrNull { patch(it) }?.simpleName ?: "Unknown"

private fun File.patch(kClass: KClass<out Any>): Boolean =
    runCatching {
        val content = readText().removeReplacePatterns()
        val mapper = FileFormat.forFile(this).mapper
        val value = mapper.readValue(content, kClass.java)
        var rewrittenContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)

        val ignorePaths = IGNORE_PATHS_FOR_TARGET_CLASSES[kClass].orEmpty()

        if (ignorePaths.isNotEmpty()) {
            val originalTree = mapper.readTree(content)
            val rewrittenTree = mapper.readTree(rewrittenContent)

            ignorePaths.forEach { path ->
                rewrittenTree.replacePath(path, originalTree)
                rewrittenTree.replacePath(path, originalTree)
            }

            rewrittenContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rewrittenTree)
        }

        writeText(rewrittenContent.recoverReplacePatterns())
    }.isSuccess

private fun JsonNode.replacePath(path: String, target: JsonNode) {
    val parentPath = path.substringBeforeLast("/")
    val key = path.substringAfterLast("/")

    val parentNode = getNodeAtPath(parentPath) as? ObjectNode
    val targetNode = target.getNodeAtPath(path)

    if (parentNode != null && targetNode != null) {
        parentNode.replace(key, targetNode)
    }
}

private fun JsonNode.getNodeAtPath(path: String): JsonNode? {
    val keys = path.split('/').toMutableList()
    var result: JsonNode? = this

    while (keys.isNotEmpty() && result != null) {
        val key = keys.removeFirst()
        result = result[key]
    }

    return result
}

private fun String.removeReplacePatterns(): String {
    var result = this

    REPLACE_PATTERN_REPLACEMENTS.forEach { (pattern, replacement) ->
        result = result.replace(pattern, replacement)
    }

    return result
}

private fun String.recoverReplacePatterns(): String {
    var result = this

    REPLACE_PATTERN_REPLACEMENTS.forEach { (pattern, replacement) ->
        result = result.replace(replacement, pattern)
    }

    return result
}
