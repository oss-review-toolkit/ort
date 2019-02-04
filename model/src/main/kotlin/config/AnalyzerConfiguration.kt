/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.model.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

data class AnalyzerConfiguration(
        /**
         * If set to true, ignore the versions of used command line tools. Note that this might lead to erroneous
         * results if the tools have changed in usage or behavior. If set to false, check the versions to match the
         * expected versions and fail the analysis on a mismatch.
         */
        val ignoreToolVersions: Boolean,

        /**
         * Enable the analysis of projects that use version ranges to declare their dependencies. If set to true,
         * dependencies of exactly the same project might change with another scan done at a later time if any of the
         * (transitive) dependencies are declared using version ranges and a new version of such a dependency was
         * published in the meantime. If set to false, analysis of projects that use version ranges will fail.
         */
        val allowDynamicVersions: Boolean
)

class AnalyzerConfigurationDeserializer : StdDeserializer<AnalyzerConfiguration>(AnalyzerConfiguration::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AnalyzerConfiguration {
        val node = p.codec.readTree<JsonNode>(p)
        return if (node.isBoolean) {
            // For backward-compatibility if only "allowDynamicVersions" / "allow_dynamic_versions" is specified.
            AnalyzerConfiguration(false, node.booleanValue())
        } else {
            AnalyzerConfiguration(
                    node.get("ignore_tool_versions").booleanValue(),
                    node.get("allow_dynamic_versions").booleanValue()
            )
        }
    }
}
