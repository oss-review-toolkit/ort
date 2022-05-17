/*
 * Copyright (C) 2020-2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.clearlydefined

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * See https://github.com/clearlydefined/service/blob/48f2c97/schemas/definition-1.0.json#L32-L48.
 */
@Serializable
enum class ComponentType {
    @SerialName("npm")
    NPM,
    @SerialName("crate")
    CRATE,
    @SerialName("git")
    GIT,
    @SerialName("maven")
    MAVEN,
    @SerialName("composer")
    COMPOSER,
    @SerialName("nuget")
    NUGET,
    @SerialName("gem")
    GEM,
    @SerialName("go")
    GO,
    @SerialName("pod")
    POD,
    @SerialName("pypi")
    PYPI,
    @SerialName("sourcearchive")
    SOURCE_ARCHIVE,
    @SerialName("deb")
    DEBIAN,
    @SerialName("debsrc")
    DEBIAN_SOURCES;

    companion object {
        @JvmStatic
        fun fromString(value: String) = enumValues<ComponentType>().single { it.toString() == value }
    }

    // Align the string representation with the serial name to make Retrofit's GET request work. Also see:
    // https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues/39
    override fun toString() = ClearlyDefinedService.JSON.encodeToJsonElement(this).jsonPrimitive.content
}

/**
 * See https://github.com/clearlydefined/service/blob/48f2c97/schemas/definition-1.0.json#L49-L65.
 */
@Serializable
enum class Provider {
    @SerialName("npmjs")
    NPM_JS,
    @SerialName("cocoapods")
    COCOAPODS,
    @SerialName("cratesio")
    CRATES_IO,
    @SerialName("github")
    GITHUB,
    @SerialName("gitlab")
    GITLAB,
    @SerialName("packagist")
    PACKAGIST,
    @SerialName("golang")
    GOLANG,
    @SerialName("mavencentral")
    MAVEN_CENTRAL,
    @SerialName("mavengoogle")
    MAVEN_GOOGLE,
    @SerialName("nuget")
    NUGET,
    @SerialName("rubygems")
    RUBYGEMS,
    @SerialName("pypi")
    PYPI,
    @SerialName("debian")
    DEBIAN;

    companion object {
        @JvmStatic
        fun fromString(value: String) = enumValues<Provider>().single { it.toString() == value }
    }

    // Align the string representation with the serial name to make Retrofit's GET request work. Also see:
    // https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues/39
    override fun toString() = ClearlyDefinedService.JSON.encodeToJsonElement(this).jsonPrimitive.content
}

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L128.
 */
@Serializable
enum class Nature {
    @SerialName("license")
    LICENSE,
    @SerialName("notice")
    NOTICE
}

/**
 * See https://github.com/clearlydefined/website/blob/43ec5e3/src/components/ContributePrompt.js#L78-L82.
 */
@Serializable
enum class ContributionType {
    @SerialName("Missing")
    MISSING,
    @SerialName("Incorrect")
    INCORRECT,
    @SerialName("Incomplete")
    INCOMPLETE,
    @SerialName("Ambiguous")
    AMBIGUOUS,
    @SerialName("Other")
    OTHER
}

/**
 * The status of metadata harvesting of the various tools.
 */
enum class HarvestStatus {
    NOT_HARVESTED,
    PARTIALLY_HARVESTED,
    HARVESTED
}
