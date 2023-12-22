/*
 * SPDX-FileCopyrightText: 2023 HH Partners
 *
 * SPDX-License-Identifier: MIT
 */

package org.ossreviewtoolkit.plugins.scanners.dos

import java.time.Duration
import java.time.Instant

import org.ossreviewtoolkit.model.Package

internal fun Collection<Package>.getDosPurls(): List<String> =
    map { pkg ->
        pkg.purl.takeUnless { pkg.vcsProcessed.path.isNotEmpty() }
            // Encode a path within the source code to the PURL.
            ?: "${pkg.purl}#${pkg.vcsProcessed.path}"
    }

/**
 * Elapsed time for a scanjob.
 */
internal fun elapsedTime(startTime: Instant): String {
    val currentTime = Instant.now()
    val duration = Duration.between(startTime, currentTime)
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val seconds = duration.toSecondsPart()

    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
