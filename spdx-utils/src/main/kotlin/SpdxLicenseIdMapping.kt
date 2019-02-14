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

package com.here.ort.spdx

import com.here.ort.spdx.SpdxLicense.*

/**
 * A mapping from misspelled SPDX license IDs to valid SPDX IDs.
 */
object SpdxLicenseIdMapping {
    val mapping = mapOf(
            "AFLv2.1" to AFL_2_1,
            "ALv2" to APACHE_2_0,
            "APL2" to APACHE_2_0,
            "APLv2.0" to APACHE_2_0,
            "ASL" to APACHE_2_0,
            "Apache" to APACHE_2_0,
            "Apache-2" to APACHE_2_0,
            "Apache2" to APACHE_2_0,
            "BOOST" to BSL_1_0,
            "BSD" to BSD_2_CLAUSE,
            "BSD-3" to BSD_3_CLAUSE,
            "BSD-Style" to BSD_3_CLAUSE,
            "BSD-like" to BSD_3_CLAUSE,
            "BSD-style" to BSD_3_CLAUSE,
            "BSD2" to BSD_2_CLAUSE,
            "BSD3" to BSD_3_CLAUSE,
            "CC0" to CC0_1_0,
            "CDDL" to CDDL_1_0,
            "CPL" to CPL_1_0,
            "Cecill-C" to CECILL_C,
            "EDL-1.0" to BSD_3_CLAUSE,
            "EPL" to EPL_1_0,
            "FreeBSD" to BSD_2_CLAUSE_FREEBSD,
            "GPL" to GPL_2_0_ONLY,
            "GPL-2" to GPL_2_0_ONLY,
            "GPL2" to GPL_2_0_ONLY,
            "GPLv2" to GPL_2_0_ONLY,
            "GPLv3" to GPL_3_0_ONLY,
            "LGPL" to LGPL_2_1_ONLY,
            "LGPL-3" to LGPL_3_0_ONLY,
            "LGPL3" to LGPL_3_0_ONLY,
            "LGPLv3" to LGPL_3_0_ONLY,
            "MIT-like" to MIT,
            "MIT-style" to MIT,
            "MPL" to MPL_2_0,
            "MPLv2.0" to MPL_2_0,
            "Ruby's" to RUBY,
            "UNLICENSE" to UNLICENSE,
            "UNLICENSED" to UNLICENSE,
            "apache-2.0" to APACHE_2_0,
            "boost" to BSL_1_0,
            "lgpl" to LGPL_2_1_ONLY,
            "ruby" to RUBY,
            "wtfpl" to WTFPL,
            "zlib" to ZLIB
    )

    fun map(license: String) = mapping[license] ?: SpdxLicense.forId(license)
}
