/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxexpression.SpdxOperator

internal class CargoDependencyHandler(
    private val analysisRoot: File,
    private val packageById: Map<String, CargoMetadata.Package>,
    private val nodeById: Map<String, CargoMetadata.Node>,
    private val hashes: Map<String, String>
) : DependencyHandler<CargoMetadata.Node> {
    override fun identifierFor(dependency: CargoMetadata.Node): Identifier =
        packageById.getValue(dependency.id).toIdentifier()

    override fun dependenciesFor(dependency: CargoMetadata.Node): List<CargoMetadata.Node> =
        // TODO: Handle renamed dependencies here, see:
        //       https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#renaming-dependencies-in-cargotoml
        dependency.deps.mapNotNull { dep ->
            // Only normal dependencies are transitive.
            dep.takeIf {
                it.depKinds.any { depKind -> depKind.kind == null }
            }?.let {
                nodeById.getValue(it.pkg)
            }
        }

    override fun linkageFor(dependency: CargoMetadata.Node): PackageLinkage =
        with(packageById.getValue(dependency.id)) {
            if (isProject(analysisRoot)) PackageLinkage.PROJECT_STATIC else PackageLinkage.STATIC
        }

    override fun createPackage(dependency: CargoMetadata.Node, issues: MutableCollection<Issue>): Package? =
        with(packageById.getValue(dependency.id)) {
            if (isProject(analysisRoot)) null else toPackage(hashes)
        }
}

/**
 * Return the local path for this Cargo package if applicable, or null if the Cargo package is not local.
 */
private fun CargoMetadata.Package.getLocalPath(): File? =
    // Support both Cargo < 1.77.0 IDs like "lib 0.1.0 (path+file:///home/ort)" and Cargo >= 1.77.0 IDs like
    // "path+file:///home/ort#lib@0.1.0".
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

/**
 * Map this Cargo package to an ORT [Identifier].
 */
private fun CargoMetadata.Package.toIdentifier() =
    Identifier(
        type = PACKAGE_TYPE,
        // Note that Rust / Cargo do not support package namespaces, see:
        // https://samsieber.tech/posts/2020/09/registry-structure-influence/
        namespace = "",
        name = name,
        version = version
    )

/**
 * Map this Cargo package to an ORT [Package]. [hashes] are used to look up the Crate's SHA-256 digest.
 */
internal fun CargoMetadata.Package.toPackage(hashes: Map<String, String>): Package {
    val declaredLicenses = parseDeclaredLicenses()

    // Historically, Cargo's metadata used "/" to separate licenses in a string. As the semantics of "/" are unclear in
    // terms of the intended license operator, the community deprecated "/" in favor of an explicit "OR", see
    // https://github.com/rust-lang/cargo/pull/4920 and also the related https://github.com/rust-lang/cargo/issues/2039.
    val declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses, operator = SpdxOperator.OR)

    val vcs = (source.takeIf { it?.startsWith("git+https://") == true } ?: repository)
        ?.let { VcsHost.parseUrl(it) }.orEmpty()
    val vcsProcessed = getLocalPath()?.let { PackageManager.processProjectVcs(it) } ?: vcs.normalize()

    return Package(
        id = toIdentifier(),
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

/**
 * Return the set of licenses declared for this Cargo package.
 */
private fun CargoMetadata.Package.parseDeclaredLicenses(): Set<String> {
    val declaredLicenses = license.orEmpty().split('/')
        .mapNotNullTo(mutableSetOf()) { license ->
            license.trim().takeUnless { it.isEmpty() }
        }

    // Cargo allows declaring non-SPDX licenses only by referencing a license file. If a license file is specified, add
    // an unknown declared license to indicate that there is a declared license, but we cannot know which it is at this
    // point.
    // See: https://doc.rust-lang.org/cargo/reference/manifest.html#the-license-and-license-file-fields
    if (licenseFile.orEmpty().isNotBlank()) {
        declaredLicenses += SpdxConstants.NOASSERTION
    }

    return declaredLicenses
}

/**
 * Return [RemoteArtifact] metadata of the source artifact (Crate) for this Cargo package if available on crates.io, or
 * return null if creates.io does not have any metadata for this package. [hashes] are used to look up the Crate's
 * SHA-256 digest.
 */
private fun CargoMetadata.Package.parseSourceArtifact(hashes: Map<String, String>): RemoteArtifact? =
    when (source) {
        "registry+https://github.com/rust-lang/crates.io-index" -> {
            val url = "https://crates.io/api/v1/crates/$name/$version/download"
            val key = "$name $version ($source)"
            val hash = Hash.create(hashes[key].orEmpty())
            return RemoteArtifact(url, hash)
        }

        else -> null
    }
