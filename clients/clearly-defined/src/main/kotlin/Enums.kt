/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * See https://github.com/clearlydefined/service/blob/48f2c97/schemas/definition-1.0.json#L32-L48.
 */
enum class ComponentType(val value: String) {
    NPM("npm"),
    CRATE("crate"),
    GIT("git"),
    MAVEN("maven"),
    COMPOSER("composer"),
    NUGET("nuget"),
    GEM("gem"),
    GO("go"),
    POD("pod"),
    PYPI("pypi"),
    SOURCE_ARCHIVE("sourcearchive"),
    DEBIAN("deb"),
    DEBIAN_SOURCES("debsrc");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fromString(value: String) =
            enumValues<ComponentType>().single { value.equals(it.value, ignoreCase = true) }
    }

    @JsonValue
    override fun toString() = value
}

/**
 * See https://github.com/clearlydefined/service/blob/48f2c97/schemas/definition-1.0.json#L49-L65.
 */
enum class Provider(val value: String) {
    NPM_JS("npmjs"),
    COCOAPODS("cocoapods"),
    CRATES_IO("cratesio"),
    GITHUB("github"),
    GITLAB("gitlab"),
    PACKAGIST("packagist"),
    GOLANG("golang"),
    MAVEN_CENTRAL("mavencentral"),
    MAVEN_GOOGLE("mavengoogle"),
    NUGET("nuget"),
    RUBYGEMS("rubygems"),
    PYPI("pypi"),
    DEBIAN("debian");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fromString(value: String) = enumValues<Provider>().single { value.equals(it.value, ignoreCase = true) }
    }

    @JsonValue
    override fun toString() = value
}

/**
 * See https://github.com/clearlydefined/service/blob/4917725/schemas/definition-1.0.json#L128.
 */
enum class Nature {
    LICENSE,
    NOTICE;

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fromString(value: String) = enumValues<Nature>().single { value.equals(it.name, ignoreCase = true) }
    }

    @JsonValue
    override fun toString() = name.lowercase()
}

/**
 * See https://github.com/clearlydefined/website/blob/43ec5e3/src/components/ContributePrompt.js#L78-L82.
 */
enum class ContributionType {
    MISSING,
    INCORRECT,
    INCOMPLETE,
    AMBIGUOUS,
    OTHER;

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fromString(value: String) =
            enumValues<ContributionType>().single { value.equals(it.name, ignoreCase = true) }
    }

    @JsonValue
    override fun toString() = name.titlecase()
}

/**
 * The status of metadata harvesting of the various tools.
 */
enum class HarvestStatus {
    NOT_HARVESTED,
    PARTIALLY_HARVESTED,
    HARVESTED
}
