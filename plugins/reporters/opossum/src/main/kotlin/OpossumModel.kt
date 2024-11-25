/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.opossum

import io.ks3.java.typealiases.LocalDateTimeAsString
import io.ks3.java.typealiases.UuidAsString

import java.io.File
import java.time.LocalDateTime

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

internal val JSON = Json {
    explicitNulls = false
    encodeDefaults = true
}

internal fun File.writeReport(opossumInput: OpossumInput): File =
    apply { outputStream().use { JSON.encodeToStream(opossumInput, it) } }

@Serializable
internal data class OpossumInput(
    val metadata: OpossumInputMetadata = OpossumInputMetadata(),
    val resources: OpossumResources,
    val externalAttributions: Map<UuidAsString, OpossumSignalFlat>,
    val resourcesToAttributions: Map<String, Set<UuidAsString>>,
    val attributionBreakpoints: Set<String>,
    val filesWithChildren: Set<String>,
    val frequentLicenses: Set<OpossumFrequentLicense>,
    val baseUrlsForSources: Map<String, String>,
    val externalAttributionSources: Map<String, OpossumExternalAttributionSource>
) {
    fun getSignalsForFile(file: String): List<OpossumSignalFlat> =
        resourcesToAttributions[file].orEmpty().mapNotNull { uuid -> externalAttributions[uuid] }
}

@Serializable
internal data class OpossumInputMetadata(
    val projectId: String = "0",
    val fileCreationDate: LocalDateTimeAsString = LocalDateTime.now()
)

@Serializable(OpossumResourcesSerializer::class)
internal data class OpossumResources(
    val tree: MutableMap<String, OpossumResources> = mutableMapOf()
) {
    fun addResource(pathPieces: List<String>) {
        if (pathPieces.isEmpty()) {
            return
        }

        val head = pathPieces.first()
        val tail = pathPieces.drop(1)

        if (head !in tree) {
            tree[head] = OpossumResources()
        }

        tree.getValue(head).addResource(tail)
    }

    fun addResource(path: String) {
        val pathPieces = path.split("/").filter { it.isNotEmpty() }

        addResource(pathPieces)
    }

    fun isFile() = tree.isEmpty()

    fun isPathAFile(path: String): Boolean {
        val pathPieces = path.split("/").filter { it.isNotEmpty() }

        return isPathAFile(pathPieces)
    }

    fun isPathAFile(pathPieces: List<String>): Boolean {
        if (pathPieces.isEmpty()) {
            return isFile()
        }

        val head = pathPieces.first()
        val tail = pathPieces.drop(1)

        return head !in tree || tree.getValue(head).isPathAFile(tail)
    }

    fun toFileList(): Set<String> =
        tree.flatMapTo(mutableSetOf()) { (key, value) ->
            value.toFileList().map { resolvePath(key, it, isDirectory = false) }
        }.plus("/")
}

private object OpossumResourcesSerializer : KSerializer<OpossumResources> {
    override val descriptor = buildClassSerialDescriptor("Resource")

    override fun serialize(encoder: Encoder, value: OpossumResources) {
        if (value.isFile()) {
            encoder.encodeInt(1)
        } else {
            encoder.encodeSerializableValue(MapSerializer(String.serializer(), this), value.tree)
        }
    }

    override fun deserialize(decoder: Decoder): OpossumResources {
        throw NotImplementedError("Deserialization of OpossumResources is not supported.")
    }
}

@Serializable
internal data class OpossumSignalFlat(
    val source: OpossumSignalSource,
    val attributionConfidence: Int = 80,
    val packageType: String?,
    val packageNamespace: String?,
    val packageName: String?,
    val packageVersion: String?,
    val copyright: String?,
    val licenseName: String?,
    val url: String?,
    val preSelected: Boolean,
    val followUp: OpossumFollowUp?,
    val excludeFromNotice: Boolean,
    val comment: String?
) {
    companion object {
        fun create(signal: OpossumSignal): OpossumSignalFlat =
            OpossumSignalFlat(
                source = signal.base.source,
                attributionConfidence = signal.attributionConfidence,
                packageType = signal.base.packageType,
                packageNamespace = signal.base.packageNamespace,
                packageName = signal.base.packageName,
                packageVersion = signal.base.packageVersion,
                copyright = signal.base.copyright,
                licenseName = signal.base.licenseName,
                url = signal.base.url,
                preSelected = signal.base.preSelected,
                followUp = signal.followUp,
                excludeFromNotice = signal.excludeFromNotice,
                comment = signal.base.comment
            )
    }

    data class OpossumSignal(
        val base: OpossumSignalBase,
        val attributionConfidence: Int = 80,
        val followUp: OpossumFollowUp?,
        val excludeFromNotice: Boolean
    ) {
        companion object {
            @Suppress("LongParameterList")
            fun create(
                source: String,
                id: Identifier? = null,
                url: String? = null,
                license: SpdxExpression? = null,
                copyright: String? = null,
                comment: String? = null,
                preSelected: Boolean = false,
                followUp: Boolean = false,
                excludeFromNotice: Boolean = false
            ): OpossumSignal =
                OpossumSignal(
                    base = OpossumSignalBase(
                        source = OpossumSignalSource(name = source),
                        packageType = id?.getPurlType().toString(),
                        packageNamespace = id?.namespace,
                        packageName = id?.name,
                        packageVersion = id?.version,
                        copyright = copyright,
                        licenseName = license?.toString(),
                        url = url,
                        preSelected = preSelected,
                        comment = comment
                    ),
                    followUp = OpossumFollowUp.FOLLOW_UP.takeIf { followUp },
                    excludeFromNotice = excludeFromNotice
                )
        }

        data class OpossumSignalBase(
            val source: OpossumSignalSource,
            val packageType: String?,
            val packageNamespace: String?,
            val packageName: String?,
            val packageVersion: String?,
            val copyright: String?,
            val licenseName: String?,
            val url: String?,
            val preSelected: Boolean,
            val comment: String?
        )
    }
}

@Serializable
internal data class OpossumSignalSource(
    val name: String,
    val documentConfidence: Int = 80
)

internal enum class OpossumFollowUp {
    FOLLOW_UP
}

@Serializable
internal data class OpossumFrequentLicense(
    val shortName: String,
    val fullName: String?,
    val defaultText: String?
) : Comparable<OpossumFrequentLicense> {
    override fun compareTo(other: OpossumFrequentLicense) =
        compareValuesBy(
            this,
            other,
            { it.shortName },
            { it.fullName },
            { it.defaultText }
        )
}

@Serializable
internal data class OpossumExternalAttributionSource(
    val name: String,
    val priority: Int
)
