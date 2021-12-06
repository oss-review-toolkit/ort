/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.spdx.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

/**
 *  References an external source of additional information, metadata, enumerations, asset identifiers, or downloadable
 *  content believed to be relevant to the Package.
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
     * https://github.com/spdx/spdx-spec/blob/master/chapters/appendix-VI-external-repository-identifiers.md.
     */
    @JsonDeserialize(using = ReferenceTypeDeserializer::class)
    val referenceType: Type,

    /**
     *  The unique string with no spaces necessary to access the package-specific information, metadata, or content
     *  within the target location. The format of the locator is subject to constraints defined by the [referenceType].
     */
    val referenceLocator: String
) {
    enum class Category {
        SECURITY,
        PACKAGE_MANAGER,
        PERSISTENT_ID,
        OTHER
    }

    sealed class Type(
        @JsonValue
        val name: String,
        val category: Category
    ) {
        object Cpe22Type : Type("cpe22Type", Category.SECURITY)
        object Cpe23Type : Type("cpe23Type", Category.SECURITY)

        object Bower : Type("bower", Category.PACKAGE_MANAGER)
        object MavenCentral : Type("maven-central", Category.PACKAGE_MANAGER)
        object Npm : Type("npm", Category.PACKAGE_MANAGER)
        object NuGet : Type("nuget", Category.PACKAGE_MANAGER)
        object Purl : Type("purl", Category.PACKAGE_MANAGER)

        object SoftwareHeritage : Type("swh", Category.PERSISTENT_ID)

        data class Other(private val typeName: String) : Type(typeName, Category.OTHER)
    }

    init {
        require(referenceLocator.isNotBlank()) { "The referenceLocator must not be blank." }

        require(referenceType.category == Category.OTHER || referenceType.category == referenceCategory) {
            "The category for '${referenceType.name}' must be '${referenceType.category}', but was " +
                    "'$referenceCategory'."
        }
    }

    constructor(referenceType: Type, referenceLocator: String, comment: String = "") : this(
        comment,
        referenceType.category,
        referenceType,
        referenceLocator
    )
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
