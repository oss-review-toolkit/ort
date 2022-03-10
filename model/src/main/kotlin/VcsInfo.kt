/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException

import kotlin.reflect.full.memberProperties

import org.ossreviewtoolkit.utils.common.fieldNamesOrEmpty
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl

/**
 * Bundles general Version Control System information.
 */
@JsonDeserialize(using = VcsInfoDeserializer::class)
data class VcsInfo(
    /**
     * The type of the VCS, for example Git, GitRepo, Mercurial, etc.
     */
    val type: VcsType,

    /**
     * The URL to the VCS repository.
     */
    val url: String,

    /**
     * The VCS-specific revision (tag, branch, SHA1) that the version of the package maps to.
     */
    val revision: String,

    /**
     * The path inside the VCS to take into account. If the VCS supports checking out only a subdirectory, only this
     * path is checked out.
     */
    val path: String = ""
) {
    companion object {
        /**
         * A constant for a [VcsInfo] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = VcsInfo(
            type = VcsType.UNKNOWN,
            url = "",
            revision = "",
            path = ""
        )
    }

    /**
     * Merge two sources of VCS information by mixing and matching fields to get as complete information as possible.
     * If in question, information in this instance has precedence over information in the other instance.
     */
    fun merge(other: VcsInfo): VcsInfo {
        if (this == EMPTY) return other

        return VcsInfo(
            type.takeUnless { it == EMPTY.type } ?: other.type,
            url.takeUnless { it == EMPTY.url } ?: other.url,
            revision.takeUnless { it == EMPTY.revision } ?: other.revision,
            path.takeUnless { it == EMPTY.path } ?: other.path
        )
    }

    /**
     * Return this [VcsInfo] in normalized form by applying [normalizeVcsUrl] to the [url].
     */
    fun normalize() = copy(url = normalizeVcsUrl(url))

    /**
     * Return a [VcsInfoCurationData] with the properties from this [VcsInfo].
     */
    fun toCuration() = VcsInfoCurationData(type, url, revision, path)
}

/**
 * Return this [VcsInfo] if not null or else [VcsInfo.EMPTY].
 */
fun VcsInfo?.orEmpty(): VcsInfo = this ?: VcsInfo.EMPTY

private class VcsInfoDeserializer : StdDeserializer<VcsInfo>(VcsInfo::class.java) {
    companion object {
        val KNOWN_FIELDS by lazy {
            VcsInfo::class.memberProperties.map { PROPERTY_NAMING_STRATEGY.translate(it.name) } +
                    PROPERTY_NAMING_STRATEGY.translate("resolvedRevision")
        }
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VcsInfo {
        val node = p.codec.readTree<JsonNode>(p)

        val fields = node.fieldNamesOrEmpty().asSequence().toList()
        (fields - KNOWN_FIELDS).let { unknownFields ->
            if (unknownFields.isNotEmpty()) {
                throw UnrecognizedPropertyException.from(p, VcsInfo::class.java, unknownFields.first(), KNOWN_FIELDS)
            }
        }

        return VcsInfo(
            VcsType(node["type"].textValueOrEmpty()),
            node["url"].textValueOrEmpty(),
            // For backward compatibility, if resolved_revision is set, prefer it over revision because it is more
            // specific.
            node["resolved_revision"]?.textValue() ?: node["revision"].textValueOrEmpty(),
            node["path"].textValueOrEmpty()
        )
    }
}
