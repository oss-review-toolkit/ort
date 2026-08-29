/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.conan

import java.io.File

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal val JSON = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

internal fun parsePackageInfosV1(file: File): List<PackageInfoV1> =
    JSON.decodeFromString<List<PackageInfoV1>>(file.readText())

internal fun parsePackageInfosV2(file: File): List<PackageInfoV2> =
    JSON.decodeFromString<PackageInfoV2Raw>(file.readText()).toPackageInfoV2()

/**
 * A class containing the properties common to all [PackageInfo], regardless of their Conan version. Used for
 * abstracting some functions and keeping them in [Conan].
 */
@Serializable
sealed interface PackageInfo {
    val author: String?
    val revision: String?
    val url: String?
}

@Serializable
internal data class PackageInfoV1(
    override val author: String? = null,
    override val revision: String? = null,
    override val url: String? = null,
    val reference: String? = null,
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val displayName: String,
    val requires: List<String> = emptyList(),
    val buildRequires: List<String> = emptyList()
) : PackageInfo

@Serializable
internal data class PackageInfoV2(
    override val author: String?,
    override val revision: String?,
    override val url: String?,
    val ref: String? = null,
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val label: String,
    val requires: List<String> = emptyList(),
    val buildRequires: List<String> = emptyList(),
    val testRequires: List<String> = emptyList(),
    val recipeFolder: String? = null,
    val description: String? = null,
    // The next two properties can be null for the first package, i.e. the conanfile itself. To simplify the processing
    // by the package manager, they are exposed as non-nullable properties.
    val name: String,
    val version: String,
    val binaryRemote: String? = null,
    val packageType: PackageType
) : PackageInfo

@Serializable
private data class PackageInfoV2Raw(
    val graph: Graph
) {
    fun toPackageInfoV2() =
        graph.nodes.values.map {
            val directDependencies = it.dependencies.values.filter { dep -> dep.direct }
            PackageInfoV2(
                author = it.author,
                revision = it.rrev,
                url = it.url,
                ref = it.ref,
                license = it.license,
                homepage = it.homepage,
                label = it.label,
                // See https://docs.conan.io/2/reference/conanfile/methods/requirements.html#default-traits-for-each-kind-of-requires.
                requires = directDependencies.mapNotNull { dep -> dep.ref.takeUnless { dep.build || dep.test } },
                buildRequires = directDependencies.mapNotNull { dep -> dep.ref.takeIf { dep.build } },
                testRequires = directDependencies.mapNotNull { dep -> dep.ref.takeIf { dep.test } },
                recipeFolder = it.recipeFolder,
                description = it.description,
                name = it.name.orEmpty(),
                version = it.version.orEmpty(),
                binaryRemote = it.binaryRemote,
                packageType = it.packageType
            )
        }
}

@Serializable
internal data class Graph(
    val nodes: Map<String, PackageV2>
)

@Serializable
internal data class PackageV2(
    val ref: String,
    val author: String? = null,
    @Serializable(StringListSerializer::class)
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val url: String? = null,
    val rrev: String? = null,
    val label: String,
    // The next four properties can be null for the first package, i.e. the conanfile itself.
    val recipeFolder: String? = null,
    val description: String?,
    val name: String?,
    val version: String?,
    val dependencies: Map<String, DependencyReference>,
    val binaryRemote: String? = null,
    val packageType: PackageType
)

/**
 * See https://docs.conan.io/2/reference/conanfile/attributes.html#package-type.
 */
@Serializable
internal enum class PackageType {
    @SerialName("application") APPLICATION,
    @SerialName("library") LIBRARY,
    @SerialName("shared-library") SHARED_LIBRARY,
    @SerialName("static-library") STATIC_LIBRARY,
    @SerialName("header-library") HEADER_LIBRARY,
    @SerialName("build-scripts") BUILD_SCRIPTS,
    @SerialName("python-require") PYTHON_REQUIRE,
    @SerialName("unknown") UNKNOWN
}

/**
 * See https://docs.conan.io/2/reference/conanfile/methods/requirements.html#requirement-traits.
 */
@Serializable
internal data class DependencyReference(
    val ref: String,

    /**
     * This dependency is a build tool, an application or executable, like cmake, that is used exclusively at build
     * time. It is not linked/embedded into binaries, and will be in the build context.
     */
    val build: Boolean = false,

    /**
     * If the dependency is a direct one, that is, it has explicitly been declared by the current recipe, or if it is a
     * transitive one.
     */
    val direct: Boolean = false,

    /**
     * This requires will force its version in the dependency graph upstream, overriding other existing versions even of
     * transitive dependencies, and also solving potential existing conflicts. The downstream consumer's force traits
     * always have higher priority.
     */
    val force: Boolean = false,

    /**
     * Indicates that there are headers that are going to be included from this package at compile time. The dependency
     * will be in the host context.
     */
    val headers: Boolean = false,

    /**
     * The dependency contains some library or artifact that will be used at link time of the consumer. This trait will
     * typically be true for direct shared and static libraries, but could be false for indirect static libraries that
     * are consumed via a shared library. The dependency will be in the host context.
     */
    val libs: Boolean = false,

    /**
     * The same as the force trait, but not adding a direct dependency. If there is no transitive dependency to
     * override, this "requires" will be discarded. This trait only exists at the time of defining a "requires", but it
     * will not exist as an actual "requires" once the graph is fully evaluated.
     */
    val override: Boolean = false,

    /**
     * This dependency contains some executables, either apps or shared libraries that need to be available to execute
     * (typically in the path, or other system env-vars). This trait can be true if [build] is false. In that case, the
     * package will contain some executables that can run in the host system when installing it, typically like an
     * end-user application. This trait can be true if [build] is true, then the package will contain executables that
     * will run in the build context, typically while being used to build other packages.
     */
    val run: Boolean = false,

    /**
     * This requirement is a test library or framework, like Catch2 or gtest. It is mostly a library that needs to be
     * included and linked, but that will not be propagated downstream.
     */
    val test: Boolean = false,

    /**
     * This require will be propagated downstream, even if it does not propagate headers, libs or run traits.
     * Requirements that propagate downstream can cause version conflicts. This is typically true, because in most
     * cases, having two different versions of the same library in the same dependency graph is at least complicated, if
     * not directly violating ODR or causing linking errors. It can be set to false in advanced scenarios, when we want
     * to use different versions of the same package during the build.
     */
    val visible: Boolean = true
)

/**
 * A (de)serialized for the list of licenses: the JSON can contain either null, string or an array of string for this
 * property.
 */
private object StringListSerializer : KSerializer<List<String>> {
    override val descriptor = listSerialDescriptor<String>()

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(ListSerializer(String.serializer()), value)
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element.jsonArray.map { it.jsonPrimitive.content }
            is JsonPrimitive -> listOfNotNull(element.contentOrNull)
            else -> emptyList()
        }
    }
}
