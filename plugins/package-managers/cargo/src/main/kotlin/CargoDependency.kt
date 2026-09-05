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

package org.ossreviewtoolkit.plugins.packagemanagers.cargo

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxexpression.SpdxOperator

/**
 * A class that bundles all information about a Cargo dependency that is required to create ORT model classes from it.
 */
internal class CargoDependency(
    /** The ORT package that corresponds to this dependency. */
    val pkg: Package,

    /** Whether this dependency is to be regarded as an ORT project instead of as a package. */
    val isProject: Boolean,

    /** A function to resolve the direct dependencies of this dependency. */
    resolveDependencies: () -> List<CargoDependency>
) {
    /**
     * The direct dependencies of this dependency. These are resolved lazily as the dependencies of a Cargo package may
     * not be known yet at the time this instance is created.
     */
    val dependencies: List<CargoDependency> by lazy(resolveDependencies)
}

/**
 * Return the dependencies of the resolved dependency graph in this metadata, associated by their Cargo package IDs. The
 * [analysisRoot] is used to determine whether a Cargo package is to be regarded as an ORT project, and [hashes] to look
 * up the hashes of source artifacts.
 */
internal fun CargoMetadata.toDependencies(
    analysisRoot: File,
    hashes: Map<String, String>
): Map<String, CargoDependency> {
    val packageById = packages.associateBy { it.id }
    val dependencies = mutableMapOf<String, CargoDependency>()

    resolve.nodes.forEach { node ->
        val cargoPackage = packageById.getValue(node.id)

        dependencies[node.id] = CargoDependency(
            pkg = cargoPackage.toPackage(hashes),
            isProject = cargoPackage.isProject(analysisRoot)
        ) {
            // TODO: Handle renamed dependencies here, see:
            //       https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#renaming-dependencies-in-cargotoml
            node.deps.filter { dep ->
                // Only normal dependencies are transitive.
                dep.depKinds.any { it.kind == null }
            }.map { dep ->
                dependencies.getValue(dep.pkg)
            }
        }
    }

    return dependencies
}

/**
 * Return the local path for this Cargo package if applicable, or null if the Cargo package is not local.
 */
private fun CargoMetadata.Package.getLocalPath(): File? =
    id.substringAfter("path+file://", "").ifEmpty { null }
        ?.removeSuffix(")")?.substringBefore("#")?.let { File(it) }

/**
 * Return whether this Cargo package is supposed to be regarded as an ORT project. The [analysisRoot] is used to check
 * whether this Cargo package lives within the analyzer root.
 */
private fun CargoMetadata.Package.isProject(analysisRoot: File): Boolean {
    val isWithinAnalyzerRoot = getLocalPath()?.startsWith(analysisRoot.absoluteFile) == true

    // If a package cannot be retrieved from anywhere but lies within the analyzer root, treat it as a project.
    return source == null && isWithinAnalyzerRoot
}

private fun CargoMetadata.Package.toPackage(hashes: Map<String, String>): Package {
    val declaredLicenses = parseDeclaredLicenses()

    // While the previously used "/" was not explicit about the intended license operator, the community consensus
    // seems to be that an existing "/" should be interpreted as "OR", see e.g. the discussions at
    // https://github.com/rust-lang/cargo/issues/2039
    // https://github.com/rust-lang/cargo/pull/4920
    val declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses, operator = SpdxOperator.OR)

    val vcs = (source.takeIf { it?.startsWith("git+https://") == true } ?: repository)
        ?.let { VcsHost.parseUrl(it) }.orEmpty()
    val vcsProcessed = getLocalPath()?.let { PackageManager.processProjectVcs(it) } ?: vcs.normalize()

    return Package(
        id = Identifier(
            type = PACKAGE_TYPE,
            // Note that Rust / Cargo do not support package namespaces, see:
            // https://samsieber.tech/posts/2020/09/registry-structure-influence/
            namespace = "",
            name = name,
            version = version
        ),
        authors = authors.flatMap { parseAuthorString(it) }.mapNotNullTo(mutableSetOf()) { it.name },
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = declaredLicensesProcessed,
        description = description.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = parseSourceArtifact(hashes).orEmpty(),
        homepageUrl = homepage.orEmpty(),
        vcs = vcs,
        vcsProcessed = vcsProcessed
    )
}

private fun CargoMetadata.Package.parseDeclaredLicenses(): Set<String> {
    val declaredLicenses = license.orEmpty().split('/')
        .map { it.trim() }
        .filterTo(mutableSetOf()) { it.isNotEmpty() }

    // Cargo allows declaring non-SPDX licenses only by referencing a license file. If a license file is specified, add
    // an unknown declared license to indicate that there is a declared license, but we cannot know which it is at this
    // point.
    // See: https://doc.rust-lang.org/cargo/reference/manifest.html#the-license-and-license-file-fields
    if (licenseFile.orEmpty().isNotBlank()) {
        declaredLicenses += SpdxConstants.NOASSERTION
    }

    return declaredLicenses
}

private fun CargoMetadata.Package.parseSourceArtifact(hashes: Map<String, String>): RemoteArtifact? =
    when (source) {
        "registry+https://github.com/rust-lang/crates.io-index" -> {
            val url = "https://crates.io/api/v1/crates/$name/$version/download"
            val key = "$name $version ($source)"
            val hash = Hash.create(hashes[key].orEmpty())

            RemoteArtifact(url, hash)
        }

        else -> null
    }
