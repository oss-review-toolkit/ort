/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.utils.asTextOrEmpty
import com.here.ort.utils.normalizeVcsUrl

/**
 * Bundles general Version Control System information.
 */
data class VcsInfo(
        /**
         * The name of the VCS type, for example Git, Hg or SVN.
         */
        val type: String,

        /**
         * The URL to the VCS repository.
         */
        val url: String,

        /**
         * The VCS-specific revision (tag, branch, SHA1) that the version of the package maps to.
         */
        val revision: String,

        /**
         * The path inside the VCS to take into account, if any. The actual meaning depends on the VCS type. For
         * example, for Git only this subdirectory of the repository should be cloned, or for Git Repo it is
         * interpreted as the path to the manifest file.
         */
        val path: String
) {
    companion object {
        /**
         * A constant for a [VcsInfo] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = VcsInfo(
                type = "",
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
        if (this == EMPTY) {
            return other
        }

        var type = this.type
        if (type.isBlank()) {
            if (other.type.isNotBlank()) {
                type = other.type
            }
        } else {
            if (type.equals(other.type, true)) {
                // Prefer the other type only if its spelling matches the VCS class names.
                if (other.type.toLowerCase().capitalize() == other.type) {
                    type = other.type
                }
            }
        }

        var url = this.url
        if (url.isBlank() && other.url.isNotBlank()) {
            url = other.url
        }

        var revision = this.revision
        if (revision.isBlank() && other.revision.isNotBlank()) {
            revision = other.revision
        }

        var path = this.path
        if (path.isBlank() && other.path.isNotBlank()) {
            path = other.path
        }

        return VcsInfo(type, url, revision, path)
    }

    /**
     * Returns this [VcsInfo] in normalized form. Currently, this only applies [normalizeVcsUrl] to the [url].
     */
    fun normalize() = copy(url = normalizeVcsUrl(url))
}

class VcsInfoDeserializer : StdDeserializer<VcsInfo>(VcsInfo::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VcsInfo {
        val node = p.codec.readTree<JsonNode>(p)
        val type = node.get("type").asTextOrEmpty()
        val url = node.get("url").asTextOrEmpty()
        val revision = node.get("revision").asTextOrEmpty()
        val path = node.get("path").asTextOrEmpty()
        return VcsInfo(type, url, revision, path)
    }
}
