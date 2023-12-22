/*
 * SPDX-FileCopyrightText: 2023 HH Partners
 *
 * SPDX-License-Identifier: MIT
 */

package org.ossreviewtoolkit.plugins.scanners.dos

import org.ossreviewtoolkit.model.Package

internal fun Collection<Package>.getDosPurls(): List<String> =
    map { pkg ->
        pkg.purl.takeUnless { pkg.vcsProcessed.path.isNotEmpty() }
            // Encode a path within the source code to the PURL.
            ?: "${pkg.purl}#${pkg.vcsProcessed.path}"
    }
