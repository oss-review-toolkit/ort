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

package org.ossreviewtoolkit.reporter

/**
 * A provider for license texts.
 */
interface LicenseTextProvider {
    /**
     * Return the license text for the license identified by [licenseId] or null if the license text is not available.
     */
    fun getLicenseText(licenseId: String): String?

    /**
     * Return a lambda that can read the license text for the license identified by [licenseId] or null if no license
     * text is available. This is useful if the license text shall not immediately be read from disk.
     */
    fun getLicenseTextReader(licenseId: String): (() -> String)?

    /**
     * Return true if a license text for the license identified by [licenseId] is available.
     */
    fun hasLicenseText(licenseId: String): Boolean
}
