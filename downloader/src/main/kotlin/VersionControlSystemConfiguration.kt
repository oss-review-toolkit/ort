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

package org.ossreviewtoolkit.downloader

import org.ossreviewtoolkit.utils.common.Options

/**
 * The configuration for a version control system (VCS).
 * Contains common configuration data for all VCS implementations,
 * and implementation-specific configuration [options].
 */
data class VersionControlSystemConfiguration(

    /**
     * [revision] to check-out from the VCS.
     */
    val revision: String,

    /**
     * Optional: The check-out can be limited to the given [path].
     */
    val path: String = "",

    /**
     * Optional: Enable check-out of any nested working trees (recursively) if [recursive] is set to true.
     */
    val recursive: Boolean = false,

    /**
     * Custom implementation-specific configuration options for the VCS.
     * See the documentation of the respective class for available options.
     */
    val options: Options? = null
)
