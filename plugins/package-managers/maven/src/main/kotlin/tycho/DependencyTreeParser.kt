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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho

import java.io.InputStream

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence

import org.apache.maven.project.MavenProject

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.DefaultDependencyNode
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.RemoteRepository

/** The [Json] instance to use for parsing of dependency trees in JSON format. */
internal val JSON = Json { ignoreUnknownKeys = true }

/** The suffix that classifies an OSGi bundle as a source bundle. */
private const val SOURCE_BUNDLE_SUFFIX = ".source"

/**
 * A data class to represent a node in the JSON output generated by the Maven Dependency Plugin.
 */
@Serializable
internal data class DependencyTreeMojoNode(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val type: String,
    val scope: String,
    val classifier: String,
    val children: List<DependencyTreeMojoNode> = emptyList()
) {
    /** An ID that matches the internal IDs generated for Maven projects. */
    val projectId: String = "$groupId:$artifactId:$type:$version"
}

/**
 * Parse the file with the aggregated output of the Maven Dependency Plugin from the given [inputStream] to a sequence
 * of [DependencyNode]s for all the projects encountered during the build. Use the given [projects] that were
 * encountered during the build to enrich the dependencies with further information. Use the given [featureFun] to
 * filter out dependency nodes that represent Tycho features.
 */
internal fun parseDependencyTree(
    inputStream: InputStream,
    projects: Collection<MavenProject>,
    featureFun: (Artifact) -> Boolean
): Sequence<DependencyNode> {
    val projectsById = projects.associateBy(MavenProject::getId)

    return JSON.decodeToSequence<DependencyTreeMojoNode>(inputStream)
        .mapNotNull { node ->
            projectsById[node.projectId]?.let { project ->
                node.toDependencyNode(project.remoteProjectRepositories.orEmpty(), featureFun)
            }
        }
}

/**
 * Convert this [DependencyTreeMojoNode] and all its children to a [DependencyNode]. Set the given [repositories] for
 * all created [DependencyNode]s. Use the given [featureFun] to filter out dependency nodes that represent Tycho
 * features. Result is *null* if this node should not be included in the dependency tree.
 */
private fun DependencyTreeMojoNode.toDependencyNode(
    repositories: List<RemoteRepository>,
    featureFun: (Artifact) -> Boolean
): DependencyNode? {
    val artifact = DefaultArtifact(groupId, artifactId, classifier, type, version)
    if (featureFun(artifact)) return null

    val dependency = Dependency(artifact, scope)
    val childNodes = children.filterSourceBundles().mapNotNull { it.toDependencyNode(repositories, featureFun) }

    return DefaultDependencyNode(dependency).apply {
        children = childNodes
        setRepositories(repositories)
    }
}

/**
 * Filter out source bundles from this collection of [DependencyTreeMojoNode]s. Tycho reports source code bundles
 * (denoted by the suffix ".source") as regular dependencies. As this does not make sense for ORT, remove them, but
 * only if the corresponding binary bundle is present as well. This is to reduce the likelihood of dropping a
 * relevant dependency by accident.
 */
private fun Collection<DependencyTreeMojoNode>.filterSourceBundles(): Collection<DependencyTreeMojoNode> {
    if (none { isSourceBundle(it) }) return this

    val bundleIds = mapTo(mutableSetOf()) { it.artifactId }
    return filterNot { node ->
        isSourceBundle(node) && node.artifactId.removeSuffix(SOURCE_BUNDLE_SUFFIX) in bundleIds
    }
}

/**
 * Return a flag whether the given [node] represents a source bundle.
 */
private fun isSourceBundle(node: DependencyTreeMojoNode): Boolean = node.artifactId.endsWith(SOURCE_BUNDLE_SUFFIX)
