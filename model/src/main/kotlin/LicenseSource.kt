/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
 * The source where a license originates from.
 */
enum class LicenseSource {
    /**
     * Licenses which are part of the [concluded license][Package.concludedLicense] of a [Package].
     */
    CONCLUDED,

    /**
     * Licenses which are part of the [(processed)][Package.declaredLicensesProcessed]
     * [declared licenses][Package.declaredLicenses] of a [Package].
     */
    DECLARED,

    /**
     * Licenses which were detected by a license scanner.
     */
    DETECTED
}
