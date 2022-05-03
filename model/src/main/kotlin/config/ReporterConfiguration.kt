/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Type alias for a map with configuration options for a single _Reporter_. In addition to the concrete configuration
 * classes for the reporters shipped with ORT, [ReporterConfiguration] holds a map with generic options that can be used
 * to configure external plugins via the ORT configuration.
 */
typealias ReporterOptions = Map<String, String>

/**
 * The base configuration model of the reporter.
 */
data class ReporterConfiguration(
    /**
     * Reporter specific configuration options. The key needs to match the name of the reporter class, e.g. "FossId"
     * for the FossId reporter. See the documentation of the reporter for available options.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val options: Map<String, ReporterOptions>? = null
)
