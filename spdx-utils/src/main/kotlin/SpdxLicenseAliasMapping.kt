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
import com.here.ort.spdx.SpdxLicenseException.*

/**
 * A mapping from varied SPDX license names to valid SPDX expressions. When mapping a name without any indication of a
 * version to an SPDX expression with a version, the most commonly used version at the time of writing is used.
 */
object SpdxLicenseAliasMapping {
    /**
     * The map of custom license names associated with their corresponding SPDX expression.
     */
    internal val customNames = mapOf(
        "afl" to AFL_3_0,
        "afl-2" to AFL_2_0,
        "afl2" to AFL_2_0,
        "afl2.0" to AFL_2_0,
        "afl2.1" to AFL_2_1,
        "AFLv2.1" to AFL_2_1,
        "agpl" to AGPL_3_0_ONLY,
        "ALv2" to APACHE_2_0,
        "alv2" to APACHE_2_0,
        "Apache" to APACHE_2_0,
        "apache" to APACHE_2_0,
        "Apache-2" to APACHE_2_0,
        "apache-license" to APACHE_2_0,
        "Apache2" to APACHE_2_0,
        "APL2" to APACHE_2_0,
        "APLv2.0" to APACHE_2_0,
        "ASL" to APACHE_2_0,
        "asl" to APACHE_2_0,
        "BOOST" to BSL_1_0,
        "boost" to BSL_1_0,
        "Bouncy" to MIT,
        "bouncy" to MIT,
        "bouncy-license" to MIT,
        "BSD" to BSD_3_CLAUSE,
        "bsd" to BSD_3_CLAUSE,
        "BSD-3" to BSD_3_CLAUSE,
        "bsd-license" to BSD_3_CLAUSE,
        "bsd-licensed" to BSD_3_CLAUSE,
        "BSD-like" to BSD_3_CLAUSE,
        "bsd-like" to BSD_3_CLAUSE,
        "BSD-Style" to BSD_3_CLAUSE,
        "BSD-style" to BSD_3_CLAUSE,
        "bsd-style" to BSD_3_CLAUSE,
        "BSD2" to BSD_2_CLAUSE,
        "bsd2" to BSD_2_CLAUSE,
        "BSD3" to BSD_3_CLAUSE,
        "bsd3" to BSD_3_CLAUSE,
        "bsl" to BSL_1_0,
        "bsl1.0" to BSL_1_0,
        "CC0" to CC0_1_0,
        "cc0" to CC0_1_0,
        "CDDL" to CDDL_1_0,
        "cddl" to CDDL_1_0,
        "cddl1.0" to CDDL_1_0,
        "cddl1.1" to CDDL_1_1,
        "CPL" to CPL_1_0,
        "EDL-1.0" to BSD_3_CLAUSE,
        "efl" to EFL_2_0,
        "EPL" to EPL_1_0,
        "epl" to EPL_1_0,
        "epl1.0" to EPL_1_0,
        "epl2.0" to EPL_2_0,
        "eupl" to EUPL_1_0,
        "eupl1.0" to EUPL_1_0,
        "eupl1.1" to EUPL_1_1,
        "eupl1.2" to EUPL_1_2,
        "fdl" to GFDL_1_3_ONLY,
        "FreeBSD" to BSD_2_CLAUSE_FREEBSD,
        "gfdl" to GFDL_1_3_ONLY,
        "GPL" to GPL_2_0_ONLY,
        "gpl" to GPL_2_0_ONLY,
        "GPL-2" to GPL_2_0_ONLY,
        "gpl-license" to GPL_2_0_ONLY,
        "GPL2" to GPL_2_0_ONLY,
        "gpl2" to GPL_2_0_ONLY,
        "gpl3" to GPL_3_0_ONLY,
        "GPLv2" to GPL_2_0_ONLY,
        "gplv2" to GPL_2_0_ONLY,
        "GPLv3" to GPL_3_0_ONLY,
        "gplv3" to GPL_3_0_ONLY,
        "isc-license" to ISC,
        "ISCL" to ISC,
        "iscl" to ISC,
        "LGPL" to LGPL_2_0_OR_LATER,
        "lgpl" to LGPL_2_0_OR_LATER,
        "LGPL-3" to LGPL_3_0_ONLY,
        "lgpl2" to LGPL_2_1_ONLY,
        "LGPL3" to LGPL_3_0_ONLY,
        "lgpl3" to LGPL_3_0_ONLY,
        "lgplv2" to LGPL_2_1_ONLY,
        "LGPLv3" to LGPL_3_0_ONLY,
        "lgplv3" to LGPL_3_0_ONLY,
        "mit-license" to MIT,
        "mit-licensed" to MIT,
        "MIT-like" to MIT,
        "MIT-style" to MIT,
        "MPL" to MPL_2_0,
        "mpl-2" to MPL_2_0,
        "MPL2" to MPL_2_0,
        "mpl2" to MPL_2_0,
        "mpl2.0" to MPL_2_0,
        "MPLv2" to MPL_2_0,
        "MPLv2.0" to MPL_2_0,
        "ODbl" to ODBL_1_0,
        "ODBL" to ODBL_1_0,
        "psf" to PYTHON_2_0,
        "psfl" to PYTHON_2_0,
        "python" to PYTHON_2_0,
        "UNLICENSED" to UNLICENSE,
        "w3cl" to W3C,
        "wtf" to WTFPL,
        "zope" to ZPL_2_1
    ).mapValues { (_, v) -> v.toExpression() }

    /**
     * The map of deprecated SPDX license names associated with their current SPDX expression.
     */
    private val deprecatedLicenses = mapOf(
        "AGPL-1.0" to AGPL_1_0_ONLY,
        "AGPL-1.0+" to AGPL_1_0_OR_LATER,
        "AGPL-3.0" to AGPL_3_0_ONLY,
        "AGPL-3.0+" to AGPL_3_0_OR_LATER,
        "GFDL-1.1" to GFDL_1_1_ONLY,
        "GFDL-1.1+" to GFDL_1_1_OR_LATER,
        "GFDL-1.2" to GFDL_1_2_ONLY,
        "GFDL-1.2+" to GFDL_1_2_OR_LATER,
        "GFDL-1.3" to GFDL_1_3_ONLY,
        "GFDL-1.3+" to GFDL_1_3_OR_LATER,
        "GPL-1.0" to GPL_1_0_ONLY,
        "GPL-1.0+" to GPL_1_0_OR_LATER,
        "GPL-2.0" to GPL_2_0_ONLY,
        "GPL-2.0+" to GPL_2_0_OR_LATER,
        "GPL-3.0" to GPL_3_0_ONLY,
        "GPL-3.0+" to GPL_3_0_OR_LATER,
        "LGPL-2.0" to LGPL_2_0_ONLY,
        "LGPL-2.0+" to LGPL_2_0_OR_LATER,
        "LGPL-2.1" to LGPL_2_1_ONLY,
        "LGPL-2.1+" to LGPL_2_1_OR_LATER,
        "LGPL-3.0" to LGPL_3_0_ONLY,
        "LGPL-3.0+" to LGPL_3_0_OR_LATER
    ).mapValues { (_, v) -> v.toExpression() }

    /**
     * The map of deprecated SPDX license exception names associated with their current compound SPDX expression.
     */
    private val deprecatedExceptions = mapOf(
        "GPL-2.0-with-autoconf-exception" to (GPL_2_0_ONLY with AUTOCONF_EXCEPTION_2_0),
        "GPL-2.0-with-bison-exception" to (GPL_2_0_ONLY with BISON_EXCEPTION_2_2),
        "GPL-2.0-with-classpath-exception" to (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0),
        "GPL-2.0-with-font-exception" to (GPL_2_0_ONLY with FONT_EXCEPTION_2_0),
        "GPL-2.0-with-GCC-exception" to (GPL_2_0_ONLY with GCC_EXCEPTION_2_0),
        "GPL-3.0-with-autoconf-exception" to (GPL_3_0_ONLY with AUTOCONF_EXCEPTION_3_0),
        "GPL-3.0-with-GCC-exception" to (GPL_3_0_ONLY with GCC_EXCEPTION_3_1)
    )

    /**
     * The map of varied SPDX license names associated with their corresponding SPDX expression.
     */
    val mapping = customNames + deprecatedLicenses + deprecatedExceptions

    /**
     * Return the [SpdxExpression] the [license] name maps to, or null if there is no corresponding expression. If
     * [mapDeprecated] is true, license names marked as deprecated in the SPDX standard are mapped to their
     * corresponding current expression, otherwise they are mapped to their corresponding deprecated expression.
     */
    fun map(license: String, mapDeprecated: Boolean = true) =
        (if (mapDeprecated) mapping else customNames)[license] ?: SpdxLicense.forId(license)?.toExpression()
}
