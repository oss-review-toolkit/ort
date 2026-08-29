/*
 * Copyright (C) 2023 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.clients.fossid.model.result

/**
 * The category of a license in the FossId Knowledge Base. The list of license categories is visible at:
 * https://<FossID server>/help/en/web-application/license_categories.html.
 */
enum class LicenseCategory {
    COMMERCIAL,
    NON_COMMERCIAL,
    NON_LICENSE,
    PERMISSIVE,
    SOURCE_AVAILABLE,
    SOURCE_AVAILABLE_NC,
    STRONG_COPYLEFT,
    UNCATEGORIZED,
    WEAK_COPYLEFT
}
