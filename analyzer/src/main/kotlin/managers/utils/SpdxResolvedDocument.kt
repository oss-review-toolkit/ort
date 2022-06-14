/*
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.analyzer.managers.utils

import java.io.File
import java.net.URI

import org.ossreviewtoolkit.analyzer.managers.SpdxDocumentFile
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.addBasicAuthorization
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.downloadFile
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.model.SpdxExternalDocumentReference
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdx.model.SpdxRelationship

/**
 * A data class storing information about a root SPDX document and all the documents referenced by it.
 *
 * This class is used by [SpdxDocumentFile] to get a combined view on all packages and their relations defined in a set
 * of SPDX files.
 */
internal data class SpdxResolvedDocument(
    /**
     * The root document. This is the starting point, from which all external references have been traversed.
     */
    val rootDocument: ResolvedSpdxDocument,

    /**
     * The name of the package manager that uses this document. This is mainly used to fill the source in generated
     * [OrtIssue]s.
     */
    val managerName: String,

    /**
     * Holds a map with all [ResolvedSpdxDocument]s that are referenced directly or indirectly from the root document,
     * using the external reference objects as keys.
     */
    val referencedDocuments: Map<SpdxExternalDocumentReference, ResolvedSpdxDocument>,

    /**
     * Holds a list with the accumulated [SpdxRelationship]s from all documents referenced directly or indirectly from
     * the root document.
     */
    val relationships: List<SpdxRelationship>,

    /**
     * A map allowing direct access to the packages declared in one of the contained documents. For the packages of the
     * root document, the key is the package's identifiers. For other packages, the identifier needs to be prefixed
     * with the document identifier of the reference.
     */
    private val packagesById: Map<String, SpdxPackage>,

    /**
     * A map storing issues that were encountered when resolving external document references. These issues are also
     * assigned to [SpdxPackage]s defined in the corresponding external documents.
     */
    private val issuesByReferenceId: Map<String, OrtIssue>
) {
    companion object {
        fun load(cache: SpdxDocumentCache, rootDocumentFile: File, managerName: String): SpdxResolvedDocument {
            val rootDocument = cache.load(rootDocumentFile).getOrThrow()

            val references = mutableMapOf<SpdxExternalDocumentReference, ResolvedSpdxDocument>()
            val issues = mutableMapOf<String, OrtIssue>()
            resolveAllReferences(
                cache,
                managerName,
                rootDocument,
                rootDocumentFile.toURI(),
                references,
                issues,
                mutableSetOf()
            )

            val resolvedRootDocument = ResolvedSpdxDocument(rootDocument, rootDocumentFile.toURI())

            // Note: The identifiers from packages defined in external documents are qualified with the relation name,
            // while package identifiers from the root document are not qualified. Thus, there can be no clash.
            val packages = collectPackages(references) + rootDocument.getPackages()
            val relations = collectAndQualifyRelations(references) + rootDocument.relationships

            return SpdxResolvedDocument(resolvedRootDocument, managerName, references, relations, packages, issues)
        }
    }

    /**
     * Get the [SpdxPackage] for the given [identifier] by resolving against packages or external document references
     * contained in this document. If the package cannot be resolved, add an issue to [issues].
     */
    fun getSpdxPackageForId(identifier: String, issues: MutableList<OrtIssue>): SpdxPackage? {
        val pkg = packagesById[identifier]
        val issue = issuesByReferenceId[identifier.substringBefore(':', "")]

        if (pkg != null) {
            issue?.also { issues += it }
        } else {
            issues += issue ?: createAndLogIssue(
                source = managerName,
                message = "'$identifier' could neither be resolved to a 'package' nor to an 'externalDocumentRef'."
            )
        }

        return pkg
    }

    /**
     * Return the local definition file in which the package with the given [identifier] is declared. If the package
     * cannot be resolved or if it has not been declared in a local file, return *null*.
     */
    fun getDefinitionFile(identifier: String): File? {
        if (identifier !in packagesById) return null

        val reference = identifier.substringBefore(':', "")
            .takeUnless { it.isEmpty() }
            ?.let { refId -> referencedDocuments.entries.find { it.key.externalDocumentId == refId } }

        return reference?.value?.definitionFile() ?: rootDocument.definitionFile()
    }
}

/**
 * A data class storing information about an SPDX document and the URL from which it was loaded. The latter is
 * required to generate the VCS information in the analyzer result.
 */
internal data class ResolvedSpdxDocument(
    /** The actual SPDX document. */
    val document: SpdxDocument,

    /** The URL from which this document was loaded. */
    val url: URI
) {
    /**
     * Return the local definition file from which this document was loaded if there is one. Return *null* if this
     * document was loaded from the internet.
     */
    fun definitionFile(): File? = url.toDefinitionFile()
}

/**
 * Resolve all external references to SPDX documents contained in [document], and recursively in all referenced
 * documents. Use [cache] to load documents. Resolve relative URLs against [baseUri]. Store all encountered references
 * and the documents they point to in [references]. Store issues encountered when resolving references in [issues] with
 * [managerName] as source of the issues. Use [knownUris] to detect cycles.
 */
private fun resolveAllReferences(
    cache: SpdxDocumentCache,
    managerName: String,
    document: SpdxDocument,
    baseUri: URI,
    references: MutableMap<SpdxExternalDocumentReference, ResolvedSpdxDocument>,
    issues: MutableMap<String, OrtIssue>,
    knownUris: MutableSet<URI>
) {
    document.resolveReferences(cache, baseUri, managerName).forEach { (ref, resolvedDoc) ->
        resolvedDoc.document?.let { document ->
            references += ref to ResolvedSpdxDocument(document, resolvedDoc.uri)
            if (knownUris.add(resolvedDoc.uri)) {
                resolveAllReferences(
                    cache,
                    managerName,
                    document,
                    resolvedDoc.uri,
                    references,
                    issues,
                    knownUris
                )
            }
        }

        resolvedDoc.issue?.let { issues += ref.externalDocumentId to it }
    }
}

/**
 * Return a map with all [SpdxPackage]s found in one of the given [references] using qualified identifiers as keys.
 */
private fun collectPackages(
    references: MutableMap<SpdxExternalDocumentReference, ResolvedSpdxDocument>
): Map<String, SpdxPackage> {
    val allPackages = mutableMapOf<String, SpdxPackage>()

    references.forEach { (reference, resolvedDocument) ->
        allPackages += resolvedDocument.document.getPackages("${reference.externalDocumentId}:")
    }

    return allPackages
}

/**
 * Return a list with all [SpdxRelationship]s found in the given [references]. Qualify the identifiers used in these
 * relationships, so that they are compatible with the keys used to access the aggregated packages.
 */
private fun collectAndQualifyRelations(
    references: MutableMap<SpdxExternalDocumentReference, ResolvedSpdxDocument>
): List<SpdxRelationship> =
    references.flatMap { (reference, resolvedSpdxDocument) ->
        resolvedSpdxDocument.document.relationships.map { it.qualify(reference) }
    }

/**
 * A data class to hold the result of an operation to resolve an [SpdxDocument] from an external reference. Resolving
 * of the document may fail, then the document is *null*, and a corresponding [OrtIssue] is present.
 */
internal data class ResolutionResult(
    /**
     * The document the reference points to, if it could be resolved successfully.
     */
    val document: SpdxDocument?,

    /** The URI pointing to the document. */
    val uri: URI,

    /**
     * An issue that occurred while resolving the document. If the document could not be resolved, this gives details
     * about the underlying error. It could also be a warning.
     */
    val issue: OrtIssue?
)

/**
 * Return a flag whether this URI points to a local definition file.
 */
private fun URI.isLocalDefinitionFile(): Boolean = scheme.equals("file", ignoreCase = true) || !isAbsolute

/**
 * Convert this URI to a local definition file if possible. Otherwise, return *null*.
 */
private fun URI.toDefinitionFile(): File? =
    takeIf { isLocalDefinitionFile() }?.let { File(it.path).absoluteFile.normalize() }?.takeIf { it.isFile }

/**
 * Return the [SpdxDocument] this [SpdxExternalDocumentReference]'s [SpdxDocument] refers to. Use [cache] to parse
 * the document, and [baseUri] to resolve relative references.
 */
internal fun SpdxExternalDocumentReference.resolve(
    cache: SpdxDocumentCache,
    baseUri: URI,
    managerName: String
): ResolutionResult {
    val uri = runCatching {
        val resolvedUri = baseUri.resolve(spdxDocument)
        resolvedUri.takeUnless { baseUri.query != null } ?: URI("$resolvedUri?${baseUri.query}")
    }.getOrElse {
        return ResolutionResult(
            document = null,
            uri = baseUri,
            issue = createAndLogIssue(
                source = managerName,
                message = "The SPDX document at '$spdxDocument' cannot be resolved as a URI (referred from $baseUri " +
                        "as part of '$externalDocumentId')."
            )
        )
    }

    return if (uri.isLocalDefinitionFile()) {
        resolveFromFile(uri, cache, baseUri, managerName)
    } else {
        resolveFromDownload(uri, cache, baseUri, managerName)
    }
}

/**
 * Resolve this [SpdxExternalDocumentReference] from [uri] if it points to a file on the local file system. Use
 * [cache] to load the file. In case of a failure, create an [OrtIssue] whose message includes [baseUri], and
 * [managerName].
 */
private fun SpdxExternalDocumentReference.resolveFromFile(
    uri: URI,
    cache: SpdxDocumentCache,
    baseUri: URI,
    managerName: String
): ResolutionResult {
    val file = uri.toDefinitionFile() ?: return ResolutionResult(
        document = null,
        uri = baseUri,
        issue = createAndLogIssue(
            source = managerName,
            message = "The file pointed to by '$uri' in reference '$externalDocumentId' does not exist."
        )
    )

    val document = cache.load(file).getOrElse {
        return ResolutionResult(
            document = null,
            uri = uri,
            issue = createAndLogIssue(
                source = managerName,
                message = "Failed to parse the SPDX document pointed to by '$uri' in reference " +
                        "'$externalDocumentId': ${it.message}"
            )
        )
    }

    return ResolutionResult(document, uri, verifyChecksum(file, baseUri, managerName))
}

/**
 * Resolve this [SpdxExternalDocumentReference] from [uri] if it requires a download from a server. Use [cache] to
 * parse the document after it has been downloaded. In case of a failure, create an [OrtIssue] whose message includes
 * [baseUri], and [managerName].
 */
private fun SpdxExternalDocumentReference.resolveFromDownload(
    uri: URI,
    cache: SpdxDocumentCache,
    baseUri: URI,
    managerName: String
): ResolutionResult {
    log.info { "Downloading SPDX document from $uri (referred from $baseUri as part of '$externalDocumentId')." }

    val tempDir = createOrtTempDir()
    return try {
        val client = OkHttpClientHelper.buildClient {
            // Use the authenticator also to request preemptive authentication.
            val auth = requestPasswordAuthentication(uri)

            if (auth != null) {
                addBasicAuthorization(auth.userName, String(auth.password))
            }
        }

        val file = client.downloadFile(uri.toString(), tempDir).getOrNull() ?: run {
            return ResolutionResult(
                document = null,
                uri = uri,
                issue = createAndLogIssue(
                    source = managerName,
                    message = "Failed to download SPDX document from $uri (referred from $baseUri as part of " +
                            "'$externalDocumentId')."
                )
            )
        }

        val document = cache.load(file).getOrElse {
            return ResolutionResult(
                document = null,
                uri = uri,
                issue = createAndLogIssue(
                    source = managerName,
                    message = "Failed to parse SPDX document from $uri (referred from $baseUri as part of " +
                            "'$externalDocumentId'): ${it.message}"
                )
            )
        }

        ResolutionResult(document, uri, verifyChecksum(file, baseUri, managerName))
    } finally {
        tempDir.safeDeleteRecursively(force = true)
    }
}

/**
 * Verify that the resolved or downloaded [file] this [SpdxExternalDocumentReference] refers to matches the expected
 * checksum. If not, return an [OrtIssue] based on the document [uri] and [managerName].
 */
private fun SpdxExternalDocumentReference.verifyChecksum(file: File, uri: URI, managerName: String): OrtIssue? {
    val hash = Hash.create(checksum.checksumValue, checksum.algorithm.name)
    if (hash.verify(file)) return null

    return createAndLogIssue(
        source = managerName,
        severity = Severity.WARNING,
        message = "The SPDX document at '$spdxDocument' does not match the expected $hash (referred from $uri as " +
                "part of '$externalDocumentId')."
    )
}

/**
 * Load all documents referenced by external references in this [SpdxDocument] using [cache]. Resolve relative paths
 * based on [documentUri]. If issues occur, use [managerName] as source.
 */
private fun SpdxDocument.resolveReferences(cache: SpdxDocumentCache, documentUri: URI, managerName: String):
        Map<SpdxExternalDocumentReference, ResolutionResult> =
    externalDocumentRefs.associateWith { it.resolve(cache, documentUri, managerName) }

/**
 * Return a map with all the SPDX packages contained in this document. Keys are the identifiers of the packages,
 * optionally with the given [idPrefix]. The prefix is used to assign packages to external references.
 */
private fun SpdxDocument.getPackages(idPrefix: String? = null): Map<String, SpdxPackage> =
    packages.associateBy { pkg -> idPrefix?.let { "$it${pkg.spdxId}" } ?: pkg.spdxId }

/**
 * Transform the identifiers of the packages referenced by this relation to qualified identifiers if necessary. When
 * combining the relationships from multiple SPDX documents, packages must always be referenced with qualified
 * identifiers (including the ID of the [reference] that points to the document), so that they can be resolved
 * correctly.
 */
private fun SpdxRelationship.qualify(reference: SpdxExternalDocumentReference): SpdxRelationship {
    val qualifiedElementId = ensureQualified(spdxElementId, reference)
    val qualifiedRelatedId = ensureQualified(relatedSpdxElement, reference)

    return takeIf { spdxElementId == qualifiedElementId && relatedSpdxElement == qualifiedRelatedId }
        ?: copy(spdxElementId = qualifiedElementId, relatedSpdxElement = qualifiedRelatedId)
}

/**
 * Transform the given [spdxId] to a qualified identifier based on [reference] unless it is already qualified.
 */
private fun ensureQualified(spdxId: String, reference: SpdxExternalDocumentReference): String =
    spdxId.takeIf { ':' in it } ?: "${reference.externalDocumentId}:$spdxId"
