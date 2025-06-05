/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import java.io.File
import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression

import org.semver4j.RangesListFactory
import org.semver4j.Semver

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private const val GENERIC_BSD_LICENSE = "BSD License"
private const val SHORT_STRING_MAX_CHARS = 200

internal const val OPTION_PYTHON_VERSION_DEFAULT = "3.11"
internal val PYTHON_VERSIONS = listOf("2.7", "3.6", "3.7", "3.8", "3.9", "3.10", OPTION_PYTHON_VERSION_DEFAULT)

internal const val PYPROJECT_FILENAME = "pyproject.toml"

internal fun getLicenseFromClassifier(classifier: String): String? {
    // Example license classifier (also see https://pypi.org/classifiers/):
    // "License :: OSI Approved :: GNU Library or Lesser General Public License (LGPL)"
    val classifiers = classifier.split(" :: ").map { it.trim() }
    val licenseClassifiers = listOf("License", "OSI Approved")
    val license = classifiers.takeIf { it.first() in licenseClassifiers }?.last()
    return license?.takeUnless { it in licenseClassifiers }
}

internal fun getLicenseFromLicenseField(value: String?): String? {
    if (value.isNullOrBlank() || value == "UNKNOWN") return null

    // See https://docs.python.org/3/distutils/setupscript.html#additional-meta-data for what a "short string" is.
    val isShortString = value.length <= SHORT_STRING_MAX_CHARS && value.lines().size == 1
    if (!isShortString) return null

    // Apply a work-around for projects that declare licenses in classifier-syntax in the license field.
    return getLicenseFromClassifier(value) ?: value
}

internal fun processDeclaredLicenses(id: Identifier, declaredLicenses: Set<String>): ProcessedDeclaredLicense {
    var declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses)

    // Python's classifiers only support a coarse license declaration of "BSD License". So if there is another
    // more specific declaration of a BSD license, align on that one.
    if (GENERIC_BSD_LICENSE in declaredLicensesProcessed.unmapped) {
        declaredLicensesProcessed.spdxExpression?.decompose()?.singleOrNull {
            it is SpdxLicenseIdExpression && it.isValid() && it.toString().startsWith("BSD-")
        }?.let { license ->
            logger.debug { "Mapping '$GENERIC_BSD_LICENSE' to '$license' for '${id.toCoordinates()}'." }

            declaredLicensesProcessed = declaredLicensesProcessed.copy(
                mapped = declaredLicensesProcessed.mapped + (GENERIC_BSD_LICENSE to license),
                unmapped = declaredLicensesProcessed.unmapped - GENERIC_BSD_LICENSE
            )
        }
    }

    return declaredLicensesProcessed
}

internal fun getTomlSectionContent(tomlFile: File, sectionName: String): String? {
    val lines = tomlFile.takeIf { it.isFile }?.readLines() ?: return null

    val sectionHeaderIndex = lines.indexOfFirst { it.trim() == "[$sectionName]" }
    if (sectionHeaderIndex == -1) return null

    val sectionLines = lines.subList(sectionHeaderIndex + 1, lines.size).takeWhile { !it.trim().startsWith('[') }
    return sectionLines.joinToString("\n")
}

internal fun getPythonVersion(constraint: String): String? {
    val rangeLists = constraint.split(',')
        .map { RangesListFactory.create(it) }
        .takeIf { it.isNotEmpty() } ?: return null

    return PYTHON_VERSIONS.lastOrNull { version ->
        rangeLists.all { rangeList ->
            val semver = Semver.coerce(version)
            semver != null && rangeList.isSatisfiedBy(semver)
        }
    }
}

internal fun getPythonVersionConstraint(pyprojectTomlFile: File, sectionName: String): String? {
    val dependenciesSection = getTomlSectionContent(pyprojectTomlFile, sectionName)
        ?: return null

    return dependenciesSection.split('\n').firstNotNullOfOrNull {
        it.trim().withoutPrefix("python = ")
    }?.removeSurrounding("\"")
}
