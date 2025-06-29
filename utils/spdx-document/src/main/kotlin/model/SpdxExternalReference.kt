/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.spdxdocument.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

/**
 *  A reference to an external source of additional information for an [SpdxPackage].
 *  See https://spdx.github.io/spdx-spec/v2.3/package-information/#721-external-reference-field.
 */
data class SpdxExternalReference(
    /**
     * Human-readable information about the purpose and target of the reference.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = "",

    /**
     * The category of this reference which corresponds to the [referenceType].
     */
    val referenceCategory: Category,

    /**
     * The references type as specified by
     * https://github.com/spdx/spdx-spec/blob/v2.3/chapters/external-repository-identifiers.md.
     */
    @JsonDeserialize(using = ReferenceTypeDeserializer::class)
    val referenceType: Type,

    /**
     *  The unique string with no spaces necessary to access the package-specific information, metadata, or content
     *  within the target location. The format of the locator is subject to constraints defined by the [referenceType].
     */
    val referenceLocator: String
) {
    /**
     * See https://spdx.github.io/spdx-spec/v2.3/package-information/#721-external-reference-field for valid category
     * values. Note that early versions of the version 2.2 JSON schema erroneously used underscores instead of dashes.
     * Follow the proposed practice to support both for compatibility.
     */
    enum class Category {
        SECURITY,

        @JsonAlias("PACKAGE_MANAGER")
        @JsonProperty("PACKAGE-MANAGER")
        PACKAGE_MANAGER,

        @JsonAlias("PERSISTENT_ID")
        @JsonProperty("PERSISTENT-ID")
        PERSISTENT_ID,

        OTHER
    }

    sealed class Type(
        @JsonValue
        val name: String,
        val category: Category
    ) {
        data object Cpe22Type : Type("cpe22Type", Category.SECURITY)
        data object Cpe23Type : Type("cpe23Type", Category.SECURITY)

        data object Bower : Type("bower", Category.PACKAGE_MANAGER)
        data object MavenCentral : Type("maven-central", Category.PACKAGE_MANAGER)
        data object Npm : Type("npm", Category.PACKAGE_MANAGER)
        data object NuGet : Type("nuget", Category.PACKAGE_MANAGER)
        data object Purl : Type("purl", Category.PACKAGE_MANAGER)

        data object SoftwareHeritage : Type("swh", Category.PERSISTENT_ID)

        data class Other(private val typeName: String) : Type(typeName, Category.OTHER)
    }

    init {
        validate()
    }

    constructor(referenceType: Type, referenceLocator: String, comment: String = "") : this(
        comment,
        referenceType.category,
        referenceType,
        referenceLocator
    )

    fun validate(): SpdxExternalReference =
        apply {
            require(referenceLocator.isNotBlank()) { "The referenceLocator must not be blank." }

            require(referenceType.category == Category.OTHER || referenceType.category == referenceCategory) {
                "The category for '${referenceType.name}' must be '${referenceType.category}', but was " +
                    "'$referenceCategory'."
            }
        }
}

private class ReferenceTypeDeserializer : StdDeserializer<SpdxExternalReference.Type>(
    SpdxExternalReference.Type::class.java
) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SpdxExternalReference.Type {
        val node = p.codec.readTree<JsonNode>(p)
        val typeName = node.textValue()
        val type = SpdxExternalReference.Type::class.sealedSubclasses.mapNotNull { it.objectInstance }
            .find { it.name == typeName }
        return type ?: SpdxExternalReference.Type.Other(typeName)
    }
}
