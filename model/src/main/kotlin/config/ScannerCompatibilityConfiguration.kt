/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

/**
 * A data class storing configuration options related to the compatibility of scan results.
 *
 * The options defined in this class are evaluated when scan results are looked up from a scan results storage. It
 * then has to be checked whether the results from the storage has been produced by a compatible scanner version with
 * compatible command line options. Per default, this check is rather strict. By specifying some of these options of
 * this class, it can be configured that scan results are reused even if their parameters do not exactly match the
 * current scanner settings.
 */
data class ScannerCompatibilityConfiguration(
    /**
     * A regular expression pattern to match the name of the scanner. A result loaded from a storage is accepted only
     * if the name of the scanner that produced it is matched by this expression. Defaults to the name of the current
     * scanner if unspecified.
     */
    val namePattern: String? = null,

    /**
     * The minimum scanner version of a scan result to be considered compatible. A result loaded from a storage is
     * accepted only if its scanner version is greater than or equal to this version. Defaults to the current scanner
     * version if unspecified.
     */
    val minVersion: String? = null,

    /**
     * The maximum scanner version of a scan result to be considered compatible. A result loaded from a storage is
     * accepted only if its scanner version is less than to this version. (This bound of the version range is
     * excluding.) Defaults to the next minor version of [minVersion] if unspecified; so patch level updates do not
     * cause the compatibility check to fail.
     */
    val maxVersion: String? = null
)
