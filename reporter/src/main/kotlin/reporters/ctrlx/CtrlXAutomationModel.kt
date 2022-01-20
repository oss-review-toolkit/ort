/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters.ctrlx

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * The root element of "fossinfo.json" files, see https://github.com/boschrexroth/json-schema.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FossInfo(
    /**
     * The reference to the JSON schema in use.
     */
    @JsonProperty("\$schema")
    val schema: String? = "https://github.com/boschrexroth/json-schema/blob/a84eab6/ctrlx-automation/ctrlx-core/apps/" +
            "fossinfo/fossinfo.v1.schema.json",

    /**
     * An inlined OSS component, in case of a single component.
     */
    val component: Component? = null,

    /**
     * A list of OSS components, in case of multiple components.
     */
    val components: List<Component>? = null
) {
    init {
        require(component == null || components == null) {
            "Not both a single 'component' and a list of 'components' may be specified."
        }
    }
}

/**
 * An OSS component.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Component(
    /**
     * The OSS component name.
     */
    val name: String,

    /**
     * The OSS component version. If a version is not available, use the date of the download.
     */
    val version: String? = null,

    /**
     * The OSS component homepage.
     */
    val homepage: String? = null,

    /**
     * The OSS component copyright information.
     */
    val copyright: CopyrightInformation? = null,

    /**
     * The OSS component licenses. At least one license is required.
     */
    val licenses: List<License>,

    /**
     * A list of multiple usage forms. The key in the map indicates whether the entry is a "usage" or an
     * "integrationMechanism".
     */
    val usages: List<Map<String, String>>? = null,

    /**
     * A single OSS component usage.
     */
    val usage: Usage? = null,

    /**
     * A single OSS component integration.
     */
    @JsonProperty("integrationMechanism")
    val integrationMechanism: IntegrationMechanism? = null
) {
    init {
        require(name.trim() == name) {
            "The '$name' value of the 'name' field must not contain any leading or trailing whitespaces."
        }

        require(version?.trim() == version) {
            "The '$version' value of the 'version' field must not contain any leading or trailing whitespaces."
        }

        require(homepage?.trim() == homepage) {
            "The '$homepage' value of the 'homepage' field must not contain any leading or trailing whitespaces."
        }

        require(licenses.isNotEmpty()) {
            "The 'licenses' must contain at least one license."
        }
    }
}

/**
 * An OSS component's copyright information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CopyrightInformation(
    /**
     * The copyright text.
     */
    val text: String,

    /**
     * The copyright notice, e.g. NOTICE file contents of Apache-2.0 licensed components. Use "\n" as line separator.
     */
    val notice: String? = null
)

/**
 * An OSS component's license.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class License(
    /**
     * The license name.
     */
    val name: String,

    /**
     * The official identifier of the license, also see https://spdx.org/licenses.
     */
    val spdx: SpdxExpression? = null,

    /**
     * OSS component license text. Use "\n" as line separator.
     */
    val text: String,

    /**
     * The version or date of the license. Hint: Must not contain leading and trailing whitespaces.
     */
    val version: String? = null
) {
    init {
        require(name.trim() == name) {
            "The '$name' value of the 'name' field must not contain any leading or trailing whitespaces."
        }

        require(spdx?.isValid(SpdxExpression.Strictness.ALLOW_LICENSEREF_EXCEPTIONS) != false) {
            "The '$spdx' value of the 'spdx' field must be a valid SPDX identifier."
        }

        require(version?.trim() == version) {
            "The '$version' value of the 'version' field must not contain any leading or trailing whitespaces."
        }
    }
}

/**
 * The OSS component usage which may affect the required obligations.
 */
enum class Usage {
    /** The OSS component is used without modifications. */
    AsIs,

    /** The OSS component has been modified. */
    Modified
}

/**
 * The OSS component integration which may affect the required obligations.
 */
enum class IntegrationMechanism {
    /** DLL, Assembly. */
    LinkedDynamically,

    /** OSS library integrated in an executable. */
    LinkedStatically,

    /** Parts of the OSS sources in proprietary sources. */
    Snippet,

    /** Use system calls to OSS kernel component. */
    CallOfLinuxKernelServiceViaSystemCall,

    /** Delivery of OSS component. */
    SeparateComponent,

    /** Integrated as npm package. */
    Npm,

    /** Integrated as golang module. */
    Go
}
