/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.Issue

/**
 * Possible reasons for resolving an [Issue] using a [IssueResolution].
 */
enum class IssueResolutionReason {
    /**
     * The issue originates from the build tool used by the project.
     */
    BUILD_TOOL_ISSUE,

    /**
     * The issue can not be fixed, e.g. because it requires a change to be made by a third party that is not responsive.
     */
    CANT_FIX_ISSUE,

    /**
     * The issue is due to an irrelevant scanner issue, such as time out on a large file that is not distributed.
     */
    SCANNER_ISSUE
}
