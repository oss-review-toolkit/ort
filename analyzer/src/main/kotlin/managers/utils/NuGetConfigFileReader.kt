/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer.managers.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.util.SortedMap

import org.apache.logging.log4j.kotlin.Logging

/**
 * A reader for XML-based NuGet configuration files, see
 * https://docs.microsoft.com/en-us/nuget/reference/nuget-config-file
 */
object NuGetConfigFileReader : Logging {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JacksonXmlRootElement(localName = "configuration")
    private data class NuGetConfig(
        val packageSources: List<SortedMap<String, String>>
    )

    fun getRegistrationsBaseUrls(configFile: File): List<String> {
        val nuGetConfig = NuGetSupport.XML_MAPPER.readValue<NuGetConfig>(configFile)

        val (remotes, locals) = nuGetConfig.packageSources
            .mapNotNull { it["value"] }
            .partition { it.startsWith("http") }

        if (locals.isNotEmpty()) {
            // TODO: Handle local package sources.
            logger.warn { "Ignoring local NuGet package sources $locals." }
        }

        return remotes
    }
}
