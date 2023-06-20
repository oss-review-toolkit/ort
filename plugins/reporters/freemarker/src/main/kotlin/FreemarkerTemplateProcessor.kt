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

package org.ossreviewtoolkit.plugins.reporters.freemarker

import freemarker.cache.ClassTemplateLoader
import freemarker.ext.beans.BeansWrapperBuilder
import freemarker.template.Configuration
import freemarker.template.DefaultObjectWrapper
import freemarker.template.TemplateExceptionHandler

import java.io.File

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorResultFilter
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseFileInfo
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.licenses.filterExcluded
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice

/**
 * A class to process [Apache Freemarker][1] templates, intended to be called by a [Reporter] that uses the generated
 * files in a postprocessing step, e.g., generating a PDF.
 *
 * [1]: https://freemarker.apache.org
 */
class FreemarkerTemplateProcessor(
    templatesResourceDirectory: String,
    private val filePrefix: String = "",
    private val fileExtension: String = ""
) {
    companion object : Logging {
        const val OPTION_TEMPLATE_ID = "template.id"
        const val OPTION_TEMPLATE_PATH = "template.path"
    }

    private val freemarkerConfig: Configuration by lazy {
        Configuration(Configuration.VERSION_2_3_30).apply {
            defaultEncoding = "UTF-8"
            fallbackOnNullLoopVariable = false
            logTemplateExceptions = true
            tagSyntax = Configuration.SQUARE_BRACKET_TAG_SYNTAX
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            templateLoader = ClassTemplateLoader(
                this@FreemarkerTemplateProcessor.javaClass.classLoader,
                "templates/$templatesResourceDirectory"
            )
            wrapUncheckedExceptions = true

            setSharedVariable("statics", (objectWrapper as DefaultObjectWrapper).staticModels)
        }
    }

    /**
     * Process all Freemarker templates referenced in "template.id" and "template.path" options and returns the
     * generated files.
     */
    fun processTemplates(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> =
        processTemplatesInternal(createDataModel(input), outputDir, options)

    /**
     * Process all Freemarker templates referenced in "template.id" and "template.path" options and returns the
     * generated files.
     */
    private fun processTemplatesInternal(
        dataModel: Map<String, Any>,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val templatePaths = options[OPTION_TEMPLATE_PATH]?.split(',').orEmpty()
        val templateIds = options[OPTION_TEMPLATE_ID]?.split(',').orEmpty()

        val templateFiles = templatePaths.map { path ->
            File(path).expandTilde().also {
                require(it.isFile) { "Could not find template file at ${it.absolutePath}." }
            }
        }

        val fileExtensionWithDot = fileExtension.takeIf { it.isEmpty() } ?: ".$fileExtension"
        val outputFiles = mutableListOf<File>()

        templateIds.forEach { id ->
            val outputFile = outputDir.resolve("$filePrefix$id$fileExtensionWithDot")

            logger.info { "Generating output file '$outputFile' using template id '$id'." }

            val template = freemarkerConfig.getTemplate("$id.ftl")
            outputFile.writer().use { template.process(dataModel, it) }

            outputFiles += outputFile
        }

        templateFiles.forEach { file ->
            val outputFile = outputDir.resolve("$filePrefix${file.nameWithoutExtension}$fileExtensionWithDot")

            logger.info { "Generating output file '$outputFile' using template file '${file.absolutePath}'." }

            val template = freemarkerConfig.run {
                setDirectoryForTemplateLoading(file.parentFile)
                getTemplate(file.name)
            }
            outputFile.writer().use { template.process(dataModel, it) }

            outputFiles += outputFile
        }

        return outputFiles
    }

    /**
     * License information for a single package or project.
     */
    class PackageModel(
        val id: Identifier,
        private val input: ReporterInput
    ) {
        /**
         * True if the package is excluded.
         */
        val excluded: Boolean by lazy { input.ortResult.isExcluded(id) }

        /**
         * The resolved license information for the package.
         */
        val license: ResolvedLicenseInfo by lazy {
            val resolved = input.licenseInfoResolver.resolveLicenseInfo(id).filterExcluded()
            resolved.copy(licenses = resolved.licenses.sortedBy { it.license.toString() })
        }

        /**
         * The license choices that apply to this package.
         */
        val licenseChoices: List<SpdxLicenseChoice> by lazy {
            listOf(
                input.ortResult.getPackageLicenseChoices(id),
                input.ortResult.getRepositoryLicenseChoices()
            ).flatten()
        }

        /**
         * The resolved license file information for the package.
         */
        val licenseFiles: ResolvedLicenseFileInfo by lazy { input.licenseInfoResolver.resolveLicenseFiles(id) }

        /**
         * Return all [ResolvedLicense]s for this package excluding those licenses which are contained in any of the
         * license files. This is useful when the raw texts of the license files are included in the generated output
         * file and all licenses not contained in those files shall be listed separately.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        @JvmOverloads
        fun licensesNotInLicenseFiles(
            resolvedLicenses: List<ResolvedLicense> = license.licenses
        ): List<ResolvedLicense> {
            val outputFileLicenses = licenseFiles.files.flatMap { it.licenses }
            return resolvedLicenses.filter { it !in outputFileLicenses }
        }
    }

    /**
     * A collection of helper functions for the Freemarker templates.
     */
    class TemplateHelper(private val input: ReporterInput) {
        /**
         * Return only those [packages] that are a dependency of at least one of the provided [projects][projectIds].
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun filterByProjects(
            packages: Collection<PackageModel>,
            projectIds: Collection<Identifier>
        ): List<PackageModel> {
            val dependencies = projectIds.mapNotNull { input.ortResult.getProject(it) }
                .flatMapTo(mutableSetOf()) { input.ortResult.dependencyNavigator.projectDependencies(it) }

            return packages.filter { pkg -> pkg.id in dependencies }
        }

        /**
         * Return only those [licenses] that are classified under the given [category], or that are not categorized at
         * all.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun filterForCategory(licenses: Collection<ResolvedLicense>, category: String): List<ResolvedLicense> =
            licenses.filter { resolvedLicense ->
                input.licenseClassifications[resolvedLicense.license]?.contains(category) ?: true
            }

        /**
         * Merge the [ResolvedLicense]s of multiple [models] and filter them using [licenseView] and
         * [PackageModel.licenseChoices]. [Omits][omitExcluded] excluded packages, licenses, and copyrights by default.
         * [Undefined][omitNotPresent] licenses can be filtered out optionally. [LicenseChoices][skipLicenseChoices] are
         * applied by default. The returned list is sorted by license identifier.
         */
        @JvmOverloads
        fun mergeLicenses(
            models: Collection<PackageModel>,
            licenseView: LicenseView = LicenseView.ALL,
            omitNotPresent: Boolean = false,
            omitExcluded: Boolean = true,
            skipLicenseChoices: Boolean = false
        ): List<ResolvedLicense> =
            mergeResolvedLicenses(
                models.filter { !omitExcluded || !it.excluded }.flatMap { model ->
                    val chosenResolvedLicenseInfo = if (skipLicenseChoices) {
                        model.license
                    } else {
                        licenseView.filter(model.license, model.licenseChoices)
                    }

                    val licenses = chosenResolvedLicenseInfo.filter(licenseView).licenses
                    val filteredLicenses = if (omitExcluded) licenses.filterExcluded() else licenses

                    if (omitNotPresent) filteredLicenses.filter(::isLicensePresent) else filteredLicenses
                }
            )

        /**
         * Return a list of [ResolvedLicense]s where all duplicate entries for a single license in [licenses] are
         * merged. The returned list is sorted by license identifier.
         */
        @Suppress("MemberVisibilityCanBePrivate") // This function is used in the templates.
        fun mergeResolvedLicenses(licenses: List<ResolvedLicense>): List<ResolvedLicense> =
            licenses.groupBy { it.license }
                .map { (_, licenses) -> licenses.merge() }
                .sortedBy { it.license.toString() }

        /**
         * Return true if and only if the given [license] is not one of the special cases _NONE_ or _NOASSERTION_.
         */
        @Suppress("MemberVisibilityCanBePrivate") // This function is used in the templates.
        fun isLicensePresent(license: ResolvedLicense): Boolean = SpdxConstants.isPresent(license.license.toString())

        /**
         * Return `true` if there are any unresolved and non-excluded [Issue]s whose severity is equal to or greater
         * than the [threshold], or `false` otherwise.
         */
        @JvmOverloads
        @Suppress("UNUSED") // This function is used in the templates.
        fun hasUnresolvedIssues(threshold: Severity = input.ortConfig.severeIssueThreshold) =
            input.ortResult.getIssues().any { (identifier, issues) ->
                issues.any { issue ->
                    val isResolved = input.resolutionProvider.isResolved(issue)
                    val isExcluded = input.ortResult.isExcluded(identifier)

                    issue.severity >= threshold && !isResolved && !isExcluded
                }
            }

        /**
         * Return `true` if there are any unresolved and non-excluded [RuleViolation]s whose severity is equal to or
         * greater than the [threshold], or `false` otherwise.
         */
        @JvmOverloads
        @Suppress("UNUSED") // This function is used in the templates.
        fun hasUnresolvedRuleViolations(threshold: Severity = input.ortConfig.severeRuleViolationThreshold) =
            input.ortResult.evaluator?.violations?.any { violation ->
                val isResolved = input.resolutionProvider.isResolved(violation)
                val isExcluded = violation.pkg?.let { input.ortResult.isExcluded(it) } ?: false

                violation.severity >= threshold && !isResolved && !isExcluded
            } ?: false

        /**
         * Return a list of [RuleViolation]s for which no [RuleViolationResolution] is provided.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun filterForUnresolvedRuleViolations(ruleViolation: List<RuleViolation>): List<RuleViolation> =
            ruleViolation.filterNot { input.resolutionProvider.isResolved(it) }

        /**
         * Return a list of [Vulnerability]s for which no [VulnerabilityResolution] is provided.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun filterForUnresolvedVulnerabilities(vulnerabilities: List<Vulnerability>): List<Vulnerability> =
            vulnerabilities.filterNot { input.resolutionProvider.isResolved(it) }

        /**
         * Return a list of [SnippetFinding]s grouped by the source file being matched by those snippets.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun groupSnippetsByFile(snippetFindings: Collection<SnippetFinding>): Map<String, List<SnippetFinding>> =
            snippetFindings.groupBy { it.sourceLocation.path }

        /**
         * Collect all the licenses present in a collection of [SnippetFinding]s.
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun collectLicenses(snippetFindings: Collection<SnippetFinding>): Set<String> =
            snippetFindings.mapTo(mutableSetOf()) { it.snippet.licenses.toString() }

        /**
         * Return a flag indicating that issues have been encountered during the run of an advisor with the given
         * [capability] with at least the given [severity]. This typically means that the report is incomplete;
         * therefore, it should contain a corresponding warning.
         */
        fun hasAdvisorIssues(capability: AdvisorCapability, severity: Severity): Boolean =
            input.ortResult.advisor?.results?.filterResults(
                AdvisorRecord.resultsWithIssues(
                    capability = capability,
                    minSeverity = severity
                )
            )?.isNotEmpty() ?: false

        /**
         * Return the subset of the available advisor results produced by an advisor with the given [capability] that
         * have an issue with at least the given [severity]. With this function packages can be identified whose
         * results may be incomplete.
         */
        fun advisorResultsWithIssues(
            capability: AdvisorCapability,
            severity: Severity
        ): Map<Identifier, List<AdvisorResult>> =
            input.filteredAdvisorResults(
                AdvisorRecord.resultsWithIssues(
                    capability = capability,
                    minSeverity = severity
                )
            )

        /**
         * Return the subset of the available advisor results that contain vulnerabilities.
         */
        fun advisorResultsWithVulnerabilities(): Map<Identifier, List<AdvisorResult>> =
            input.filteredAdvisorResults(AdvisorRecord.RESULTS_WITH_VULNERABILITIES)

        /**
         * Return the subset of the available advisor results that contain defects.
         */
        fun advisorResultsWithDefects(): Map<Identifier, List<AdvisorResult>> =
            input.filteredAdvisorResults(AdvisorRecord.RESULTS_WITH_DEFECTS)

        /**
         * Return the package from the current [OrtResult] with the given [id] or the empty package if the ID cannot be
         * resolved. This function is useful for templates rendering advisor results, which contain only the
         * identifiers of affected packages, but not the packages themselves.
         */
        fun getPackage(id: Identifier): Package =
            input.ortResult.getPackage(id)?.metadata
                ?: Package.EMPTY.also { logger.warn { "Could not resolve package '${id.toCoordinates()}'." } }
    }
}

/**
 * Return a map with wrapper beans for the enum classes that are relevant for templates.These enums can then be
 * referenced directly by templates.
 * See https://freemarker.apache.org/docs/pgui_misc_beanwrapper.html#jdk_15_enums.
 */
private fun enumModel(): Map<String, Any> {
    val beansWrapper = BeansWrapperBuilder(Configuration.VERSION_2_3_30).build()
    val enumModels = beansWrapper.enumModels

    return listOf(
        AdvisorCapability::class.java,
        Severity::class.java
    ).associate { it.simpleName to enumModels.get(it.name) }
}

private fun createDataModel(input: ReporterInput): Map<String, Any> {
    val projects = input.ortResult.getProjects().sortedBy { it.id }.map { project ->
        FreemarkerTemplateProcessor.PackageModel(project.id, input)
    }

    val packages = input.ortResult.getPackages().sortedBy { it.metadata.id }.map { pkg ->
        FreemarkerTemplateProcessor.PackageModel(pkg.metadata.id, input)
    }

    // Keep this in sync with "resources/templates/freemarker_implicit.ftl".
    return mapOf(
        "projects" to projects,
        "packages" to packages,
        "ortResult" to input.ortResult,
        "licenseTextProvider" to input.licenseTextProvider,
        "LicenseView" to LicenseView,
        "helper" to FreemarkerTemplateProcessor.TemplateHelper(input),
        "statistics" to input.statistics,
        "vulnerabilityReference" to VulnerabilityReference
    ) + enumModel()
}

private fun List<ResolvedLicense>.merge(): ResolvedLicense {
    require(isNotEmpty()) { "Cannot merge an empty list." }

    return ResolvedLicense(
        license = first().license,
        originalDeclaredLicenses = flatMapTo(mutableSetOf()) { it.originalDeclaredLicenses },
        originalExpressions = flatMap { it.originalExpressions }.groupBy { it.source }.flatMapTo(mutableSetOf()) {
            it.value
        },
        locations = flatMapTo(mutableSetOf()) { it.locations }
    )
}

/**
 * Apply the given [filter] to the advisor results stored in this [ReporterInput]. Return an empty map if no advisor
 * results are available.
 */
private fun ReporterInput.filteredAdvisorResults(filter: AdvisorResultFilter): Map<Identifier, List<AdvisorResult>> =
    ortResult.advisor?.results?.filterResults(filter).orEmpty()
