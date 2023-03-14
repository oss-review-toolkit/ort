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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal
import org.gradle.api.logging.Logging
import org.gradle.internal.deprecation.DeprecatableConfiguration
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.util.GradleVersion

class OrtModelBuilder : ToolingModelBuilder {
    private lateinit var repositories: Map<String, String?>

    private val visitedDependencies = mutableSetOf<ModuleComponentIdentifier>()
    private val visitedProjects = mutableSetOf<ModuleVersionIdentifier>()

    // Note: Using "LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE" does not work for the lookup as it is not String-typed.
    private val libraryElementsAttribute = Attribute.of("org.gradle.libraryelements", String::class.java)

    private val logger = Logging.getLogger(OrtModelBuilder::class.java)
    private val errors = mutableListOf<String>()
    private val warnings = mutableListOf<String>()

    override fun canBuild(modelName: String): Boolean =
        modelName == OrtDependencyTreeModel::class.java.name

    override fun buildAll(modelName: String, project: Project): OrtDependencyTreeModel {
        repositories = project.repositories.associate { it.name to (it as? UrlArtifactRepository)?.url?.toString() }

        val resolvableConfigurations = project.configurations.filter { it.isResolvable() }

        val ortConfigurations = resolvableConfigurations.mapNotNull { config ->
            // Get the root of the resolved dependency graph. This is also what Gradle's own "dependencies" task uses to
            // recursively obtain information about resolved dependencies. Resolving dependencies triggers the download
            // of metadata (like Maven POMs) only, not of binary artifacts, also see [1]. However, Gradle stores the
            // metadata e.g. for Maven parent POMs in its own binary "descriptor.bin" format only, as long as the parent
            // POM artifact is no explicitly requested.
            //
            // [1]: https://docs.gradle.org/current/userguide/dependency_management.html#obtaining_module_metadata
            val root = config.incoming.resolutionResult.root

            // Omit configurations without dependencies.
            root.dependencies.takeUnless { it.isEmpty() }?.let { dep ->
                // Reset visited dependencies and projects per configuration.
                visitedDependencies.clear()
                visitedProjects.clear()

                OrtConfigurationImpl(name = config.name, dependencies = dep.toOrtDependencies())
            }
        }

        return OrtDependencyTreeModelImpl(
            group = project.group.toString(),
            name = project.name,
            version = project.version.toString(),
            configurations = ortConfigurations,
            repositories = repositories.values.filterNotNull(),
            errors = errors,
            warnings = warnings
        )
    }

    private fun Configuration.isResolvable(): Boolean {
        val canBeResolved = GradleVersion.current() < GradleVersion.version("3.3") || isCanBeResolved

        val isDeprecatedConfiguration = GradleVersion.current() >= GradleVersion.version("6.0")
                && this is DeprecatableConfiguration && resolutionAlternatives != null

        return canBeResolved && !isDeprecatedConfiguration
    }

    private fun Collection<DependencyResult>.toOrtDependencies(): List<OrtDependency> =
        if (GradleVersion.current() < GradleVersion.version("5.1")) {
            this
        } else {
            filterNot { it.isConstraint }
        }.mapNotNull { dep ->
            when (dep) {
                is ResolvedDependencyResult -> {
                    val selectedComponent = dep.selected
                    val attributes = dep.resolvedVariant.attributes

                    when (val id = selectedComponent.id) {
                        is ModuleComponentIdentifier -> {
                            // Cut the graph on cyclic dependencies.
                            if (id in visitedDependencies) return@mapNotNull null
                            visitedDependencies += id

                            val extension = attributes.getAttribute(libraryElementsAttribute) ?: "jar"

                            val repositoryName = (selectedComponent as? ResolvedComponentResultInternal)?.repositoryName
                            val pomFile = repositories[repositoryName]?.let { repositoryUrl ->
                                // Note: Only Maven-style layout is supported for now.
                                buildString {
                                    append(repositoryUrl.removeSuffix("/"))
                                    append('/')
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

                            OrtDependencyImpl(
                                groupId = id.group,
                                artifactId = id.module,
                                version = id.version,
                                classifier = "",
                                extension = extension,
                                dependencies = selectedComponent.dependencies.toOrtDependencies(),
                                error = null,
                                warning = null,
                                pomFile = pomFile,
                                localPath = null
                            )
                        }

                        is ProjectComponentIdentifier -> {
                            val moduleId = selectedComponent.moduleVersion ?: return@mapNotNull null

                            // Cut the graph on cyclic dependencies.
                            if (moduleId in visitedProjects) return@mapNotNull null
                            visitedProjects += moduleId

                            OrtDependencyImpl(
                                groupId = moduleId.group,
                                artifactId = moduleId.name,
                                version = moduleId.version,
                                classifier = "",
                                extension = "",
                                dependencies = selectedComponent.dependencies.toOrtDependencies(),
                                error = null,
                                warning = null,
                                pomFile = null,
                                localPath = id.projectPath
                            )
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
                        // is uncritical in terms of resovling dependencies, as the for the project itself dependencies
                        // will still get resolved.
                        return@mapNotNull null
                    }

                    val message = buildString {
                        append(dep.failure.message?.removeSuffix("."))
                        append(" from ")
                        append(dep.from)
                        append(".")

                        val causes = (dep.failure as? ModuleVersionResolveException)?.causes?.takeIf { it.isNotEmpty() }
                        if (causes != null) {
                            appendLine(" Causes are:")
                            append(causes.joinToString("\n") { it.toString() })
                        }
                    }

                    logger.warn(message)
                    warnings += message

                    null
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
