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

package org.ossreviewtoolkit.analyzer.managers.utils

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import java.util.*

private val json = Json { ignoreUnknownKeys = true }

internal object NugetInspector : CommandLineTool, Logging {
    override fun command(workingDir: File?) = "nuget-inspector"

    fun run(
        workingDir: File,
        definitionFile: File,
    ): Result {
        val outputFile = createOrtTempFile(prefix = "nuget-inspector", suffix = ".json")

        val commandLineOptions = buildList {
            add("--json")
            add(outputFile.absolutePath)

            add("--project-file")
            add(definitionFile.absolutePath)

            add("--verbose")
        }

        return try {
            run(workingDir, *commandLineOptions.toTypedArray())
            outputFile.inputStream().use { json.decodeFromStream(it) }
        } finally {
            outputFile.delete()
        }
    }

    @Serializable
    internal data class Result(
        val packages: List<Package>
    )

    @Serializable
    internal data class Package(
        @SerialName("project_name") val projectName: String,
        @SerialName("project_datafile_type") val projectDatafileType: String,
        @SerialName("datasource_id") val datasourceId: String,
        @SerialName("project_file") val projectFile: String,
        val outputs: List<String>,
        val packages: List<ParentDep>
    )

    @Serializable
    internal data class ParentDep(
        @SerialName("package") val parent: PackageData,
        val dependencies: List<PackageData>
    )

    @Serializable
    internal data class PackageData(
        val type: String,
        val name: String,
        val version: String,
        val purl: String,
        @SerialName("download_url") val downloadUrl: String,
    )
}
private fun parseAuthors(spec: PackageSpec?): SortedSet<String> =
    spec?.metadata?.authors?.split(',', ';').orEmpty()
        .map(String::trim)
        .filterNot(String::isEmpty)
        .toSortedSet()

private fun parseLicenses(spec: PackageSpec?): SortedSet<String> {
    val data = spec?.metadata ?: return sortedSetOf()

    // Prefer "license" over "licenseUrl" as the latter has been deprecated, see
    // https://docs.microsoft.com/en-us/nuget/reference/nuspec#licenseurl
    val license = data.license?.value?.takeUnless { data.license.type == "file" }
    if (license != null) return sortedSetOf(license)

    val licenseUrl = data.licenseUrl?.takeUnless { it == "https://aka.ms/deprecateLicenseUrl" } ?: return sortedSetOf()
    return sortedSetOf(licenseUrl)
}

private fun resolveLocalSpec(definitionFile: File): File? =
    definitionFile.parentFile?.resolve(".nuspec")?.takeIf { it.isFile }

private fun List<NugetInspector.ParentDep>.toPackageReferences(): SortedSet<PackageReference> =
    mapTo(sortedSetOf()) { it.toPackageReference() }



private fun NugetInspector.ParentDep.toPackageReference() =
    PackageReference(
        id = Identifier(type = "nuget", namespace = "", name = parent.name, version = parent.version),
        dependencies = dependencies.mapTo(sortedSetOf()){
            PackageReference(
                id = Identifier(type = "nuget", namespace = "", name = it.name, version = it.version),
            )
        }
    )

internal fun NugetInspector.Result.toOrtProject(
    managerName: String,
    analysisRoot: File,
    definitionFile: File,
):Project{
    val spec = resolveLocalSpec(definitionFile)?.let { NuGetSupport.XML_MAPPER.readValue<PackageSpec>(it) }
    val id = Identifier(
        type = managerName,
        namespace = "",
        name = spec?.metadata?.id ?: definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath,
        version = spec?.metadata?.version.orEmpty()
    )
    val workingDir = definitionFile.parentFile

    val nestedPackages: MutableList<NugetInspector.ParentDep> = mutableListOf()

    packages.forEach(){
        it.packages.map(){ nestedPackages.add(it) }
    }

    val packageReferences = nestedPackages.mapTo(sortedSetOf()){
        it.toPackageReference()
    }

    val scopes = sortedSetOf(Scope("install", packageReferences))
    return Project(
        id = id,
        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
        vcs = VcsInfo.EMPTY,
        authors = parseAuthors(spec),
        vcsProcessed = PackageManager.processProjectVcs(workingDir),
        declaredLicenses = parseLicenses(spec),
        homepageUrl = "",
    )
}

internal fun Set<NugetInspector.PackageData>.toOrtPackages(): SortedSet<Package> =
    groupBy { "${it.name}:${it.version}" }.mapTo(sortedSetOf()) { (_, packages) ->
        // The python inspector currently often contains two entries for a package where the only difference is the
        // download URL. In this case, one package contains the URL of the binary artifact, the other for the source
        // artifact. So take all metadata from the first package except for the artifacts.
        val pkg = packages.first()

        fun PythonInspector.Package.getHash(): Hash = Hash.create(sha512 ?: sha256 ?: sha1 ?: md5 ?: "")

        val id = Identifier(type = "nuget", namespace = "", name = pkg.name, version = pkg.version)

        Package(
            // The package has a namespace property which is currently always empty. Deliberately set the namespace to
            // an empty string here to be consistent with the resolved packages which do not have a namespace property.
            id = id,
            purl = pkg.purl,
            // Only use the first line of the description because the descriptions provided by python-inspector are
            // currently far too long, see: https://github.com/nexB/python-inspector/issues/74
            description = "",
            declaredLicenses = sortedSetOf(),
            homepageUrl = "",
            vcs= VcsInfo.EMPTY,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
        )
    }
