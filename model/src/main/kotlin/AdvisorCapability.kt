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

package org.ossreviewtoolkit.model

/**
 * An enum class that defines the capabilities of a specific advisor implementation.
 *
 * There are multiple types of findings that can be retrieved by an advisor, such as security vulnerabilities or
 * defects. An [AdvisorResult] has different fields for the different findings types. This enum corresponds to these
 * fields. It allows an advisor implementation to declare, which of these fields it can populate. This information is
 * of interest, for instance, when generating reports for specific findings to determine, which advisor may have
 * contributed.
 */
enum class AdvisorCapability {
    /** Indicates that an advisor can retrieve information about defects. */
    DEFECTS,

    /** Indicates that an advisor can retrieve information about security vulnerabilities. */
    VULNERABILITIES
}
