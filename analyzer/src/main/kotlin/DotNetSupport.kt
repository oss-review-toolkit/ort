/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import com.here.ort.model.Hash
import com.here.ort.model.Identifier
import com.here.ort.model.OrtIssue
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.xmlMapper
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.textValueOrEmpty

import okhttp3.Request

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection

class DotNetSupport(
    packageReferencesMap: Map<String, String>,
    val workingDir: File
) {
    companion object {
        private const val SCOPE_NAME = "dependencies"
        private const val PROVIDER_NAME = "nuget"

        private fun extractRepositoryType(node: JsonNode) =
            node["repository"]?.get("type").textValueOrEmpty()

        private fun extractRepositoryUrl(node: JsonNode) =
            node["repository"]?.get("url")?.textValue()
                ?: node["projectURL"].textValueOrEmpty()

        private fun extractRepositoryRevision(node: JsonNode): String =
            node["repository"]?.get("commit").textValueOrEmpty()

        private fun extractRepositoryPath(node: JsonNode): String =
            node["repository"]?.get("branch").textValueOrEmpty()

        private fun extractVcsInfo(node: JsonNode) =
            VcsInfo(
                type = extractRepositoryType(node),
                url = extractRepositoryUrl(node),
                revision = extractRepositoryRevision(node),
                path = extractRepositoryPath(node)
            )

        private fun extractPackageId(node: JsonNode) =
            Identifier(
                type = PROVIDER_NAME,
                namespace = "",
                name = node["id"].textValueOrEmpty(),
                version = node["version"].textValueOrEmpty()
            )

        private fun extractDeclaredLicenses(node: JsonNode) =
            sortedSetOf<String>().apply {
                val license = node["license"]?.textValue() ?: node["licenseUrl"].textValueOrEmpty()
                // Most nuget packages only provide a "licenseUrl" which counts as a declared license.
                if (license.isNotEmpty()) {
                    add(license)
                }
            }

        private fun extractRemoteArtifact(node: JsonNode, nupkgUrl: String): RemoteArtifact {
            val encodedHash = node["packageHash"].textValueOrEmpty()
            val hashAlgorithm = node["packageHashAlgorithm"].textValueOrEmpty()
            val sri = hashAlgorithm.replace("-", "") + "-" + encodedHash
            val hash = Hash.fromValue(sri)

            return RemoteArtifact(nupkgUrl, hash.value, hash.algorithm)
        }

        private fun getCatalogURL(registrationNode: JsonNode): String =
            registrationNode["catalogEntry"].textValueOrEmpty()

        private fun getNuspecURL(registrationNode: JsonNode): String =
            registrationNode["packageContent"].textValueOrEmpty()

        private fun extractVersion(range: String): String {
            if (range.isEmpty()) return ""
            val rangeReplaces = range.replace("[", "")
                .replace(" ", "")
                .replace(")", "")
            return rangeReplaces.split(",").elementAt(0)
        }
    }

    val packages = mutableListOf<Package>()
    val errors = mutableListOf<OrtIssue>()
    val scope = Scope(SCOPE_NAME, sortedSetOf())

    // Maps an (id, version) pair to a (nupkg URL, catalog entry) pair.
    private val packageReferencesAlreadyFound = mutableMapOf<Pair<String, String>, Pair<String, JsonNode>>()

    init {
        packageReferencesMap.forEach { (name, version) ->
            val scopeDependency = getPackageReferenceFromRestAPI(name, version)
            if (scopeDependency != null) scope.dependencies += scopeDependency
        }

        scope.collectDependencies().forEach { packageReference ->
            val pkg = packageReferenceToPackage(packageReference)

            if (pkg != Package.EMPTY) {
                packages += pkg
            }
        }
    }

    private fun packageReferenceToPackage(packageReference: PackageReference) =
        jsonNodeToPackage(getPackageReferenceJsonContent(packageReference))

    private fun getPackageReferenceJsonContent(packageReference: PackageReference): Pair<String, JsonNode> {
        if (packageReferencesAlreadyFound.containsKey(
                Pair(packageReference.id.name, packageReference.id.version)
            )
        ) {
            return packageReferencesAlreadyFound[Pair(
                packageReference.id.name,
                packageReference.id.version
            )]!!
        }
        return try {
            val informationUrl = getInformationURL(
                packageReference.id.name,
                packageReference.id.version
            ) ?: throw Exception()
            Pair(informationUrl.first, jacksonObjectMapper().readTree(informationUrl.second.requestFromNugetAPI()))
        } catch (e: Exception) {
            Pair("", xmlMapper.readTree(""))
        }
    }

    private fun jsonNodeToPackage(packageContent: Pair<String, JsonNode>): Package {
        val jsonCatalogNode = packageContent.second
        val jsonNuspecNode = try {
            xmlMapper.readTree(
                packageContent.first.replace(
                    "${jsonCatalogNode["version"].textValueOrEmpty()}.nupkg",
                    "nuspec"
                ).requestFromNugetAPI()
            )
        } catch (e: Exception) {
            xmlMapper.readTree("")
        }
        if (jsonCatalogNode["id"]?.textValue() == null) return Package.EMPTY

        val vcsInfo = extractVcsInfo(jsonNuspecNode["metadata"] ?: xmlMapper.readTree(""))

        return Package(
            id = extractPackageId(jsonCatalogNode),
            declaredLicenses = extractDeclaredLicenses(jsonCatalogNode),
            description = jsonCatalogNode["description"].textValueOrEmpty(),
            homepageUrl = jsonCatalogNode["projectUrl"].textValueOrEmpty(),
            binaryArtifact = extractRemoteArtifact(jsonCatalogNode, packageContent.first),
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = vcsInfo,
            vcsProcessed = vcsInfo.normalize()
        )
    }

    private fun getPackageReferenceFromRestAPI(packageID: String, version: String): PackageReference? {
        val packageJsonNode = preparePackageReference(packageID, version)

        if (packageJsonNode == null) {
            errors.add(
                OrtIssue(
                    source = "nuget-API does not provide package",
                    message = "$packageID:$version can not be found on Nugets RestAPI "
                )
            )
            return null
        }

        val packageReference = PackageReference(
            Identifier(
                type = PROVIDER_NAME,
                namespace = "",
                name = if (packageID == packageJsonNode["id"]?.textValue()) {
                    packageID
                } else {
                    packageJsonNode["id"].textValueOrEmpty()
                },
                version = packageJsonNode["version"].textValueOrEmpty()
            )
        )

        val dependenciesIterator = packageJsonNode["dependencyGroups"]?.elements()

        dependenciesIterator?.forEach {
            val dependencyIterator = it["dependencies"]?.elements()

            if (dependencyIterator != null) while (dependencyIterator.hasNext()) {
                val node = dependencyIterator.next()
                val nodeAsPair = Pair(
                    node["id"].textValueOrEmpty(),
                    extractVersion(node["range"].textValueOrEmpty())
                )
                if (!hasPackageReferenceAlready(nodeAsPair)) {
                    val subPackageRef = getPackageReferenceFromRestAPI(
                        nodeAsPair.first,
                        nodeAsPair.second
                    )
                    if (subPackageRef != null) packageReference.dependencies += subPackageRef
                }
            }
        }

        return packageReference
    }

    private fun preparePackageReference(packageID: String, version: String): JsonNode? {
        val (first, second) = getInformationURL(packageID, version) ?: return null
        val packageJsonNode = try {
            jacksonObjectMapper().readTree(
                second.requestFromNugetAPI()
            )
                ?: throw Exception("No configuration URL could be fetched")
        } catch (e: Exception) {
            xmlMapper.readTree("")
        }

        packageReferencesAlreadyFound[Pair(first = packageID, second = version)] =
            Pair(first, packageJsonNode)

        return packageJsonNode
    }

    private fun hasPackageReferenceAlready(nodeAsPair: Pair<String, String>) =
        packageReferencesAlreadyFound.containsKey(nodeAsPair)

    private fun getInformationURL(packageID: String, version: String): Pair<String, String>? {
        val registrationInfo: String? = try {
            "https://api.nuget.org/v3/registration3/$packageID/$version.json".requestFromNugetAPI()
        } catch (e: IOException) {
            try {
                getIdUrl(packageID, version).requestFromNugetAPI()
            } catch (e: IOException) {
                ""
            }
        }

        val node = jacksonObjectMapper().readTree(
            registrationInfo.orEmpty()
        )

        return if (node != null) {
            Pair(getNuspecURL(node), getCatalogURL(node))
        } else {
            null
        }
    }

    private fun getIdUrl(packageID: String, version: String) =
        getCreateSearchRestAPIURL(packageID).let { node ->
            getRightVersionUrl(node["data"]?.elements(), packageID, version)
                ?: getFirstMatchingIdUrl(node["data"]?.elements(), packageID).orEmpty()
        }

    private fun getRightVersionUrl(
        dataIterator: Iterator<JsonNode>?,
        packageID: String, version: String
    ): String? {
        while (dataIterator != null && dataIterator.hasNext()) {
            val packageNode = dataIterator.next()
            if (packageNode["id"].textValueOrEmpty() == packageID) {
                packageNode["versions"].elements().forEach {
                    if (it["version"].textValueOrEmpty() == version) {
                        return it["@id"].textValueOrEmpty()
                    } else if (!dataIterator.hasNext() && version == "latest") {
                        return it["@id"].textValueOrEmpty()
                    }
                }
            }
        }

        return null
    }

    private fun getFirstMatchingIdUrl(dataIterator: Iterator<JsonNode>?, packageID: String): String? {
        while (dataIterator != null && dataIterator.hasNext()) {
            val packageNode = dataIterator.next()
            if (packageNode["id"].textValueOrEmpty() == packageID) {
                return packageNode["versions"]?.last()?.get("@id").textValueOrEmpty()
            }
        }

        return null
    }

    private fun getCreateSearchRestAPIURL(packageID: String) =
        jacksonObjectMapper().readTree(
            "https://api-v2v3search-0.nuget.org/query?q=\"$packageID\"&prerelease=false".requestFromNugetAPI()
        )

    private fun String.requestFromNugetAPI(): String {
        if (isNullOrEmpty()) {
            throw IOException("GET with URL $this could not be resolved")
        }

        val pkgRequest = Request.Builder()
            .get()
            .url(this)
            .build()

        OkHttpClientHelper.execute(HTTP_CACHE_PATH, pkgRequest).use { response ->
            val body = response.body()?.string()?.trim()

            if (response.code() != HttpURLConnection.HTTP_OK || body.isNullOrEmpty()) {
                throw IOException("GET with URL $this could not be resolved")
            }

            return body
        }
    }
}
