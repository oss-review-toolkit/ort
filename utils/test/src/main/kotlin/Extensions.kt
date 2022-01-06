/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.utils.test

import io.kotest.core.TestConfiguration
import io.kotest.matchers.nulls.shouldNotBeNull

import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy

import org.ossreviewtoolkit.model.config.LicenseFilenamePatterns
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.createOrtTempFile
import org.ossreviewtoolkit.utils.core.storage.LocalFileStorage

fun Proxy.toGenericString() =
    (address() as? InetSocketAddress)?.let { address -> "${type()} @ ${address.hostString}:${address.port}" }

infix fun <T : Any> T?.shouldNotBeNull(block: T.() -> Unit) {
    this.shouldNotBeNull()
    this.block()
}

fun FileArchiver.Companion.createDefault(): FileArchiver =
    FileArchiver(
        patterns = LicenseFilenamePatterns.DEFAULT.allLicenseFilenames.map { "**/$it" },
        storage = LocalFileStorage(DEFAULT_ARCHIVE_DIR)
    )

fun TestConfiguration.createSpecTempDir(vararg infixes: String): File {
    val dir = createOrtTempDir(*infixes)

    afterSpec {
        dir.safeDeleteRecursively(force = true)
    }

    return dir
}

fun TestConfiguration.createSpecTempFile(prefix: String? = null, suffix: String? = null): File {
    val file = createOrtTempFile(prefix, suffix)

    afterSpec {
        file.delete()
    }

    return file
}

fun TestConfiguration.createTestTempDir(vararg infixes: String): File {
    val dir = createOrtTempDir(*infixes)

    afterTest {
        dir.safeDeleteRecursively(force = true)
    }

    return dir
}

fun TestConfiguration.createTestTempFile(prefix: String? = null, suffix: String? = null): File {
    val file = createOrtTempFile(prefix, suffix)

    afterTest {
        file.delete()
    }

    return file
}
