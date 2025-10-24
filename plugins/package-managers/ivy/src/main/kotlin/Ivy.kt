/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.ivy

import java.io.File

import nl.adaptivity.xmlutil.serialization.XML

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor

/**
 * Configuration options for the Ivy package manager.
 */
data class IvyConfig(
    /**
     * Enable transitive dependency resolution using Ivy CLI.
     * If true, requires Apache Ivy to be installed and available in PATH.
     * If false, only direct dependencies from ivy.xml are parsed.
     * Default is false (parse ivy.xml only) for compatibility and performance.
     */
    @OrtPluginOption(defaultValue = "false")
    val resolveTransitive: Boolean
)

/**
 * The [Apache Ivy](https://ant.apache.org/ivy/) package manager for Java.
 *
 * This package manager supports projects that use Apache Ivy for dependency management.
 * Ivy uses `ivy.xml` files to declare dependencies and configurations.
 *
 * ## Features
 * - Transitive dependency resolution via Ivy CLI (enabled by default, requires Ivy to be installed)
 * - Fallback to parsing ivy.xml for direct dependencies only
 * - Support for multiple configurations (compile, runtime, test, etc.)
 * - Dynamic version resolution when using Ivy CLI
 *
 * ## Configuration
 * Transitive resolution is enabled by default. To disable it and only parse direct dependencies:
 * ```yaml
 * analyzer:
 *   package_managers:
 *     Ivy:
 *       options:
 *         resolveTransitive: "false"
 * ```
 */
@OrtPlugin(
    id = "Ivy",
    displayName = "Apache Ivy",
    description = "The Apache Ivy package manager for Java.",
    factory = PackageManagerFactory::class
)
class Ivy(
    override val descriptor: PluginDescriptor = IvyFactory.descriptor,
    private val config: IvyConfig
) : PackageManager("Ivy") {
    companion object {
        private val xmlFormat = XML {
            // Ignore unknown elements and attributes to be lenient with ivy.xml variations
            autoPolymorphic = true
        }
    }

    override val globsForDefinitionFiles = listOf("ivy.xml")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        logger.info { "Parsing Ivy module descriptor from '$definitionFile'..." }

        val ivyModule = runCatching {
            xmlFormat.decodeFromString(IvyModule.serializer(), definitionFile.readText())
        }.getOrElse { e ->
            logger.error { "Failed to parse ivy.xml: ${e.message}" }
            return emptyList()
        }

        val info = ivyModule.info
        if (info == null) {
            logger.warn { "No info section found in ivy.xml" }
            return emptyList()
        }

        val organisation = info.organisation.orEmpty()
        val moduleName = info.module.orEmpty()
        val revision = info.revision ?: "unspecified"

        val projectId = Identifier(
            type = projectType,
            namespace = organisation,
            name = moduleName,
            version = revision
        )

        logger.info { "Resolved project: $projectId" }

        // Choose resolution strategy based on configuration
        val (scopes, packages, issues) = if (config.resolveTransitive && IvyCommand.isInPath()) {
            logger.info { "Resolving transitive dependencies using Ivy CLI..." }
            resolveTransitiveDependencies(workingDir, definitionFile, ivyModule, excludes)
        } else {
            if (config.resolveTransitive) {
                logger.warn {
                    "Transitive resolution requested but Ivy CLI not found in PATH. " +
                        "Falling back to parsing ivy.xml only."
                }
            } else {
                logger.info { "Parsing direct dependencies from ivy.xml only (transitive resolution disabled)..." }
            }

            resolveDirectDependencies(ivyModule, excludes)
        }

        // Create project metadata
        val project = Project(
            id = projectId,
            definitionFilePath = definitionFile.relativeTo(analysisRoot).path,
            authors = emptySet(),
            declaredLicenses = info.license?.name?.let { setOf(it) }.orEmpty(),
            declaredLicensesProcessed = DeclaredLicenseProcessor.process(
                info.license?.name?.let { setOf(it) }.orEmpty()
            ),
            vcs = processProjectVcs(workingDir),
            vcsProcessed = processProjectVcs(workingDir),
            homepageUrl = info.homepage.orEmpty(),
            scopeDependencies = scopes,
            scopeNames = null
        )

        return listOf(ProjectAnalyzerResult(project, packages, issues))
    }

    /**
     * Create a package with empty metadata.
     * Used for Ivy dependencies or artifacts without available metadata.
     */
    private fun createEmptyPackage(id: Identifier) =
        Package(
            id = id,
            authors = emptySet(),
            declaredLicenses = emptySet(),
            declaredLicensesProcessed = DeclaredLicenseProcessor.process(emptySet()),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY,
            isMetadataOnly = false,
            isModified = false
        )

    /**
     * Resolve only direct dependencies by parsing ivy.xml.
     */
    private fun resolveDirectDependencies(
        ivyModule: IvyModule,
        excludes: Excludes
    ): Triple<Set<Scope>, Set<Package>, List<Issue>> {
        val scopes = mutableSetOf<Scope>()
        val packages = mutableSetOf<Package>()
        val issues = mutableListOf<Issue>()

        ivyModule.dependencies?.dependency?.forEach { dep ->
            val depOrg = dep.org.orEmpty()
            val depName = dep.name.orEmpty()
            val depRev = dep.rev ?: "latest.integration"
            val depConf = dep.conf ?: "default"

            // Extract the scope name (configuration on the left side of ->)
            val scopeName = depConf.substringBefore("->").trim()

            // Skip if scope is excluded
            if (excludes.isScopeExcluded(scopeName)) {
                logger.debug { "Skipping dependency $depOrg:$depName in excluded scope '$scopeName'" }
                return@forEach
            }

            val packageId = Identifier(
                type = "Maven", // Most Ivy dependencies are Maven artifacts
                namespace = depOrg,
                name = depName,
                version = depRev
            )

            logger.debug { "Processing direct dependency: $packageId in configuration '$scopeName'" }

            // Add warning for dynamic versions
            if (depRev.contains("latest") || depRev.contains("+")) {
                issues += createAndLogIssue(
                    "Dynamic version '$depRev' found for $depOrg:$depName. " +
                        "Enable transitive resolution to resolve concrete versions.",
                    Severity.WARNING
                )
            }

            // Create package reference
            val packageRef = PackageReference(
                id = packageId,
                linkage = PackageLinkage.DYNAMIC,
                dependencies = emptySet()
            )

            // Add to appropriate scope
            var scope = scopes.find { it.name == scopeName }
            if (scope == null) {
                scope = Scope(
                    name = scopeName,
                    dependencies = mutableSetOf()
                )
                scopes.add(scope)
            }

            (scope.dependencies as MutableSet).add(packageRef)

            // Create package metadata
            packages.add(createEmptyPackage(packageId))
        }

        return Triple(scopes, packages, issues)
    }

    /**
     * Resolve transitive dependencies using Ivy CLI.
     */
    private fun resolveTransitiveDependencies(
        workingDir: File,
        definitionFile: File,
        ivyModule: IvyModule,
        excludes: Excludes
    ): Triple<Set<Scope>, Set<Package>, List<Issue>> {
        val scopes = mutableSetOf<Scope>()
        val packages = mutableSetOf<Package>()
        val issues = mutableListOf<Issue>()

        // Get list of configurations to resolve
        val configurations = ivyModule.configurations?.conf?.map { it.name.orEmpty() }?.filter { it.isNotEmpty() }
            ?: listOf("default")

        configurations.forEach { conf ->
            if (excludes.isScopeExcluded(conf)) {
                logger.debug { "Skipping excluded configuration: $conf" }
                return@forEach
            }

            logger.info { "Resolving configuration: $conf" }

            runCatching {
                // Run ivy resolve
                val result = IvyCommand.run(
                    workingDir,
                    "-ivy", definitionFile.name,
                    "-confs", conf
                )

                if (!result.isSuccess) {
                    issues += createAndLogIssue(
                        "Ivy resolve failed for configuration '$conf': ${result.stderr}",
                        Severity.ERROR
                    )
                    return@forEach
                }

                // Parse the output to extract resolved dependencies
                val (scopeRefs, scopePackages) = parseDependenciesFromOutput(result.stdout)

                val scope = Scope(
                    name = conf,
                    dependencies = scopeRefs
                )
                scopes.add(scope)
                packages.addAll(scopePackages)
            }.onFailure { e ->
                issues += createAndLogIssue(
                    "Failed to resolve configuration '$conf': ${e.collectMessages()}",
                    Severity.ERROR
                )
            }
        }

        return Triple(scopes, packages, issues)
    }

    /**
     * Parse Ivy output to extract resolved dependencies.
     * Output format: "found org.apache.commons#commons-lang3;3.14.0 in public"
     */
    private fun parseDependenciesFromOutput(output: String): Pair<Set<PackageReference>, Set<Package>> {
        val packageRefs = mutableSetOf<PackageReference>()
        val packages = mutableSetOf<Package>()

        // Pattern: "found <org>#<module>;<version> in <resolver>"
        val pattern = Regex("""^\s*found\s+([^#]+)#([^;]+);([^\s]+)\s+in\s+""")

        output.lines().forEach { line ->
            pattern.find(line)?.let { match ->
                val org = match.groupValues[1].trim()
                val module = match.groupValues[2].trim()
                val version = match.groupValues[3].trim()

                val packageId = Identifier(
                    type = "Maven",
                    namespace = org,
                    name = module,
                    version = version
                )

                logger.debug { "Found dependency: $packageId" }

                val packageRef = PackageReference(
                    id = packageId,
                    linkage = PackageLinkage.DYNAMIC,
                    dependencies = emptySet()
                )
                packageRefs.add(packageRef)

                packages.add(createEmptyPackage(packageId))
            }
        }

        logger.info { "Parsed ${packages.size} dependencies from Ivy output" }

        return Pair(packageRefs, packages)
    }
}
