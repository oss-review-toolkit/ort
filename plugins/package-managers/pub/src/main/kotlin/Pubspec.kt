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

package org.ossreviewtoolkit.plugins.packagemanagers.pub

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar

import java.io.File

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.serializer

import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.Dependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.GitDependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.HostedDependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.PathDependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.SdkDependency

private val YAML = Yaml(configuration = YamlConfiguration(strictMode = false))

internal fun parsePubspec(pubspecFile: File): Pubspec = parsePubspec(pubspecFile.readText())

internal fun parsePubspec(pubspecYaml: String): Pubspec = YAML.decodeFromString(pubspecYaml)

/**
 * See https://dart.dev/tools/pub/pubspec.
 */
@Serializable
internal data class Pubspec(
    val name: String,
    val version: String? = null,
    val description: String? = null,
    val homepage: String? = null,
    val author: String? = null,
    val authors: Set<String> = emptySet(),
    val repository: String? = null,
    val sdk: String? = null,
    @Serializable(DependencyMapSerializer::class)
    val dependencies: Map<String, Dependency>? = null,
    @Serializable(DependencyMapSerializer::class)
    @SerialName("dev_dependencies")
    val devDependencies: Map<String, Dependency>? = null
) {
    @Serializable
    sealed interface Dependency

    /** See https://dart.dev/tools/pub/dependencies#hosted-packages. */
    @Serializable
    data class HostedDependency(
        val version: String,
        val hosted: String? = null
    ) : Dependency

    /** See https://dart.dev/tools/pub/dependencies#git-packages. */
    @Serializable
    data class GitDependency(
        val url: String,
        val path: String? = null,
        val ref: String? = null
    ) : Dependency

    /** See https://dart.dev/tools/pub/dependencies#path-packages. */
    @Serializable
    data class PathDependency(
        val path: String
    ) : Dependency

    /** See https://dart.dev/tools/pub/dependencies#sdk. */
    @Serializable
    data class SdkDependency(
        val sdk: String
    ) : Dependency
}

/**
 * If transformations like for JSON were available in kaml, this serializer could be simplified, see also
 * https://github.com/charleskorn/kaml/issues/29.
 */
private object DependencyMapSerializer : KSerializer<Map<String, Dependency>> by serializer<Map<String, Dependency>>() {
    override fun deserialize(decoder: Decoder): Map<String, Dependency> {
        val input = decoder.beginStructure(descriptor) as YamlInput

        val result = when (val node = input.node) {
            is YamlScalar -> emptyMap()
            is YamlMap -> node.entries.asSequence().associateBy({ it.key.content }, { it.value.decodeDependency() })
            else -> throw SerializationException("Unexpected YAML node type: ${node.javaClass.simpleName}.")
        }

        input.endStructure(descriptor)

        return result
    }

    private fun YamlNode.decodeDependency(): Dependency {
        if (this is YamlScalar) return HostedDependency(yamlScalar.content)

        yamlMap.get<YamlScalar>("sdk")?.let { sdk ->
            return SdkDependency(sdk = sdk.content)
        }

        yamlMap.get<YamlScalar>("path")?.let { path ->
            return PathDependency(path = path.content)
        }

        yamlMap.get<YamlNode>("hosted")?.let { hosted ->
            val version = checkNotNull(yamlMap.get<YamlScalar>("version")).content
            val url = if (hosted is YamlMap) {
                checkNotNull(hosted.get<YamlScalar>("url")).content
            } else {
                hosted.yamlScalar.content
            }

            return HostedDependency(version, url)
        }

        yamlMap.get<YamlNode>("git")?.let { git ->
            return if (git is YamlMap) {
                GitDependency(
                    url = checkNotNull(git.get<YamlScalar>("url")).content,
                    ref = git.get<YamlScalar>("ref")?.content,
                    path = git.get<YamlScalar>("path")?.content
                )
            } else {
                GitDependency(url = git.yamlScalar.content)
            }
        }

        throw SerializationException("Unexpected dependency node format.")
    }
}
