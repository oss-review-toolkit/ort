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

package org.ossreviewtoolkit.plugins.packagemanagers.gradleplugin

import OrtDependency
import OrtDependencyTreeModel

import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelBuildingResult

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal
import org.gradle.api.logging.Logging
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.ivy.IvyDescriptorArtifact
import org.gradle.ivy.IvyModule
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.util.GradleVersion

internal class OrtModelBuilder : ToolingModelBuilder {
    private val repositories = mutableMapOf<String, UrlArtifactRepository>()

    private val platformCategories = setOf("platform", "enforced-platform")

    private val logger = Logging.getLogger(OrtModelBuilder::class.java)
    private val errors = mutableListOf<String>()
    private val warnings = mutableListOf<String>()
    private val globalDependencySubtrees = mutableMapOf<String, List<OrtDependency>>()

    // Only create one "OrtDependency" for each "ResolvedComponentResult".
    private val ortDependencyCache = mutableMapOf<ResolvedComponentResult, OrtDependency>()

    override fun canBuild(modelName: String): Boolean = modelName == OrtDependencyTreeModel::class.java.name

    override fun buildAll(modelName: String, project: Project): OrtDependencyTreeModel {
        if (GradleVersion.current() >= GradleVersion.version("6.8")) {
            // There currently is no way to access Gradle settings without using internal API, see
            // https://github.com/gradle/gradle/issues/18616.
            val settings = (project.gradle as GradleInternal).settings

            settings.dependencyResolutionManagement.repositories.associateNamesWithUrlsTo(repositories)
        }

        project.repositories.associateNamesWithUrlsTo(repositories)

        val relevantConfigurations = project.configurations.filter { it.isRelevant() }

        val ortConfigurations = relevantConfigurations.mapNotNull { config ->
            // Explicitly resolve all POM files and their parents, as the latter otherwise may get resolved in Gradle's
            // own binary "descriptor.bin" format only.
            val poms = project.resolvePoms(config)

            // Get the root of the resolved dependency graph. This is also what Gradle's own "dependencies" task uses to
            // recursively obtain information about resolved dependencies. Resolving dependencies triggers the download
            // of metadata (like Maven POMs) only, not of binary artifacts, also see [1].
            //
            // [1]: https://docs.gradle.org/current/userguide/dependency_management.html#obtaining_module_metadata
            val root = config.incoming.resolutionResult.root

            // Omit configurations without dependencies.
            root.dependencies.takeUnless { it.isEmpty() }?.let { dep ->
                OrtConfigurationImpl(name = config.name, dependencies = dep.toOrtDependencies(poms, emptySet()))
            }
        }

        return OrtDependencyTreeModelImpl(
            group = project.group.toString(),
            name = project.name,
            version = project.version.toString().takeUnless { it == "unspecified" }.orEmpty(),
            configurations = ortConfigurations,
            repositories = repositories.values.map { it.toOrtRepository() },
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Resolve the POM files for all dependences in the given [Gradle configuration][config] incl. their parent POMs.
     */
    private fun Project.resolvePoms(config: Configuration): Map<String, ModelBuildingResult> {
        val allComponentIds = config.incoming.resolutionResult.allDependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .map { it.selected.id }
            .distinct()

        // Get the POM files for all resolved dependencies.
        val pomFiles = resolvePoms(allComponentIds)

        val fileModelBuilder = FileModelBuilder { groupId, artifactId, version ->
            val moduleId = DefaultModuleIdentifier.newId(groupId, artifactId)
            val componentId = DefaultModuleComponentIdentifier.newId(moduleId, version)

            val pomFile = resolvePoms(listOf(componentId)).single().file

            FileModelSource(pomFile)
        }

        return pomFiles.mapNotNull { artifact ->
            // Skip Ivy descriptors - they cannot be built as Maven models
            val isIvyDescriptor = artifact.file.name.startsWith("ivy-") && artifact.file.name.endsWith(".xml")
            if (isIvyDescriptor) {
                logger.info("Skipping Maven model building for Ivy descriptor: ${artifact.file.name}")
                null
            } else {
                runCatching {
                    // Trigger resolution of parent POMs by building the POM model.
                    artifact.id.componentIdentifier.toString() to fileModelBuilder.buildModel(artifact.file)
                }.getOrElse { e ->
                    logger.warn("Failed to build Maven model for ${artifact.id.componentIdentifier}: ${e.message}")
                    null
                }
            }
        }.toMap()
    }

    /**
     * Resolve the POM files for the given [componentIds] and return them.
     * If a POM is not found, try to resolve the Ivy descriptor instead.
     */
    private fun Project.resolvePoms(componentIds: List<ComponentIdentifier>): List<ResolvedArtifactResult> {
        // Try to resolve Maven POMs first
        val mavenResolutionResult = dependencies.createArtifactResolutionQuery()
            .forComponents(componentIds)
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .execute()

        val resolvedPoms = mavenResolutionResult.resolvedComponents.flatMap {
            it.getArtifacts(MavenPomArtifact::class.java)
        }.filterIsInstance<ResolvedArtifactResult>()

        // Find component IDs that didn't resolve to POMs
        val resolvedComponentIds = resolvedPoms.map { it.id.componentIdentifier }.toSet()
        val unresolvedComponentIds = componentIds.filterNot { it in resolvedComponentIds }

        // Try to resolve Ivy descriptors for unresolved components
        val ivyDescriptors = if (unresolvedComponentIds.isNotEmpty()) {
            val ivyResolutionResult = dependencies.createArtifactResolutionQuery()
                .forComponents(unresolvedComponentIds)
                .withArtifacts(IvyModule::class.java, IvyDescriptorArtifact::class.java)
                .execute()

            ivyResolutionResult.resolvedComponents.flatMap {
                it.getArtifacts(IvyDescriptorArtifact::class.java)
            }.filterIsInstance<ResolvedArtifactResult>()
        } else {
            emptyList()
        }

        return resolvedPoms + ivyDescriptors
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun Collection<DependencyResult>.toOrtDependencies(
        poms: Map<String, ModelBuildingResult>,
        visited: Set<ComponentIdentifier>
    ): List<OrtDependency> =
        if (GradleVersion.current() < GradleVersion.version("5.1")) {
            this
        } else {
            filterNot { it.isConstraint }
        }.mapNotNull { dep ->
            when (dep) {
                is ResolvedDependencyResult -> {
                    val attributes = dep.resolvedVariant.attributes

                    // Ignore BOMs as they do not define dependencies but version constraints, see
                    // https://docs.gradle.org/current/userguide/platforms.html#sub:bom_import.
                    val isBom = attributes.getValueByName("org.gradle.category") in platformCategories
                    if (isBom) return@mapNotNull null

                    val selectedComponent = dep.selected
                    val id = selectedComponent.id

                    // Cut the graph on cyclic dependencies.
                    if (id in visited) return@mapNotNull null

                    if (selectedComponent in ortDependencyCache) {
                        return@mapNotNull ortDependencyCache[selectedComponent]
                    }

                    when (id) {
                        is ModuleComponentIdentifier -> {
                            val pomFile = if (selectedComponent is ResolvedComponentResultInternal) {
                                val repositoryId = runCatching {
                                    selectedComponent.repositoryId
                                }.recoverCatching {
                                    @Suppress("DEPRECATION")
                                    selectedComponent.repositoryName
                                }.map {
                                    // Work around https://github.com/gradle/gradle/issues/25674.
                                    if (it == "26c913274550a0b2221f47a0fe2d2358") "MavenRepo" else it
                                }.getOrNull()

                                repositories[repositoryId]?.let { repository ->
                                    // Check if this is an Ivy repository
                                    val isIvyRepository = repository is IvyArtifactRepository

                                    buildString {
                                        append(repository.url.toString().removeSuffix("/"))
                                        append('/')

                                        if (isIvyRepository) {
                                            // Ivy layout: [organisation]/[module]/[revision]/ivy-[revision].xml
                                            // Note: organization uses '/' as separator, like Maven
                                            append(id.group.replace('.', '/'))
                                            append('/')
                                            append(id.module)
                                            append('/')
                                            append(id.version)
                                            append('/')
                                            append("ivy-")
                                            append(id.version)
                                            append(".xml")
                                        } else {
                                            // Maven layout: [group]/[artifact]/[version]/[artifact]-[version].pom
                                            append(id.group.replace('.', '/'))
                                            append('/')
                                            append(id.module)
                                            append('/')
                                            append(id.version)
                                            append('/')
                                            append(id.module)
                                            append('-')
                                            append(id.version)
                                            append(".pom")
                                        }
                                    }
                                }
                            } else {
                                null
                            }

                            val modelBuildingResult = poms[id.toString()]
                            val isIvyDescriptor = pomFile?.endsWith(".xml") == true && pomFile.contains("/ivy-")

                            if (modelBuildingResult == null && !isIvyDescriptor) {
                                val message = "No Maven POM or Ivy descriptor found for component '$id'."
                                logger.warn(message)
                                warnings += message
                            }

                            // Check if we have scanned the dependencies of this subtree before, and if so, reuse them.
                            val dependencies = globalDependencySubtrees.getOrPut(id.displayName) {
                                selectedComponent.dependencies.toOrtDependencies(poms, visited + id)
                            }

                            OrtDependencyImpl(
                                groupId = id.group,
                                artifactId = id.module,
                                version = id.version,
                                classifier = "",
                                extension = modelBuildingResult?.effectiveModel?.packaging.orEmpty(),
                                variants = selectedComponent.variants.mapTo(mutableSetOf()) { it.displayName },
                                dependencies = dependencies,
                                error = null,
                                warning = null,
                                pomFile = pomFile,
                                mavenModel = modelBuildingResult?.run {
                                    OrtMavenModelImpl(
                                        licenses = effectiveModel.collectLicenses(),
                                        authors = effectiveModel.collectAuthors(),
                                        description = effectiveModel.description.orEmpty(),
                                        homepageUrl = effectiveModel.url.orEmpty(),
                                        vcs = getVcsModel()
                                    )
                                },
                                localPath = null
                            ).also {
                                ortDependencyCache[selectedComponent] = it
                            }
                        }

                        is ProjectComponentIdentifier -> {
                            val moduleId = selectedComponent.moduleVersion ?: return@mapNotNull null
                            val dependencies = selectedComponent.dependencies.toOrtDependencies(poms, visited + id)

                            OrtDependencyImpl(
                                groupId = moduleId.group,
                                artifactId = moduleId.name,
                                version = moduleId.version.takeUnless { it == "unspecified" }.orEmpty(),
                                classifier = "",
                                extension = "",
                                variants = selectedComponent.variants.mapTo(mutableSetOf()) { it.displayName },
                                dependencies = dependencies,
                                error = null,
                                warning = null,
                                pomFile = null,
                                mavenModel = null,
                                localPath = id.projectPath
                            ).also {
                                ortDependencyCache[selectedComponent] = it
                            }
                        }

                        else -> {
                            val message = "Unhandled component identifier type $id."

                            logger.error(message)
                            errors += message

                            null
                        }
                    }
                }

                is UnresolvedDependencyResult -> {
                    if (dep.attempted is ProjectComponentSelector) {
                        // Ignore unresolved project dependencies. For example for complex Android projects, Gradle's
                        // own "dependencies" task runs into "AmbiguousConfigurationSelectionException", but the project
                        // still builds fine, probably due to some Android plugin magic. Omitting a project dependency
                        // is uncritical in terms of resolving dependencies, as the for the project itself dependencies
                        // will still get resolved.
                        return@mapNotNull null
                    }

                    val message = buildString {
                        append(dep.failure.message?.removeSuffix("."))
                        append(" from ")
                        append(dep.from)
                        append(".")

                        appendCauses(dep.failure)
                    }

                    // Check if this is a targetConfiguration error (legacy Ivy feature)
                    val isTargetConfigurationError =
                        message.contains("no variant with that configuration name exists") ||
                            message.contains("Cannot select a variant by configuration name")

                    if (
                        isTargetConfigurationError &&
                        dep.attempted is org.gradle.api.artifacts.component.ModuleComponentSelector
                    ) {
                        // This is likely an Ivy dependency with legacy targetConfiguration
                        // Try to create a basic package from the available information
                        val selector = dep.attempted as org.gradle.api.artifacts.component.ModuleComponentSelector

                        logger.warn("$message [Creating basic package without full metadata]")
                        warnings += message

                        OrtDependencyImpl(
                            groupId = selector.group,
                            artifactId = selector.module,
                            version = selector.version,
                            classifier = "",
                            extension = "",
                            variants = emptySet(),
                            dependencies = emptyList(),
                            error = null,
                            warning = "Dependency resolved with limited metadata due to legacy targetConfiguration",
                            pomFile = null,
                            mavenModel = null,
                            localPath = null
                        )
                    } else {
                        logger.error(message)
                        errors += message
                        null
                    }
                }

                else -> {
                    val message = "Unhandled dependency result type '$dep' in '${dep.from}'."

                    logger.error(message)
                    errors += message

                    null
                }
            }
        }
}

/**
 * Add a string with information about the causes of the given [exception] to this [StringBuilder]. This is used to
 * log the reason why a dependency could not be resolved. To get meaningful information, all causes need to be obtained
 * recursively. This is because the top-level [ModuleVersionResolveException] typically has only other
 * [ModuleVersionResolveException]s as causes with generic messages. The actual information about what went wrong is
 * hidden somewhere down the cause chain.
 */
private fun StringBuilder.appendCauses(exception: Throwable) {
    val causes = (exception as? ModuleVersionResolveException)?.causes?.takeIf { it.isNotEmpty() }
    if (causes != null) {
        appendLine(" Causes are:")
        val allCauses = mutableSetOf<String>()

        fun getAllCauses(exception: Throwable) {
            exception.message?.also(allCauses::add)
            exception.cause?.also { getAllCauses(it) }
        }

        causes.forEach { getAllCauses(it) }

        append(allCauses.joinToString("\n"))
    }
}
