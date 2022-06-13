/*
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

package org.ossreviewtoolkit.reporter

fun String.patchAsciiDocTemplateResult() =
    // Asciidoctor renders the line breaks platform dependant.
    replace("\r\n", "\n")
        .replace("""\d{4}-\d{2}-\d{2}""".toRegex(), "1970-01-01")
        .replace("""\d{2}:\d{2}:\d{2}""".toRegex(), "00:00:00")
        // Asciidoctor renders time zones differently depending on the platform.
        // For macOS the time is rendered as `00:00:00 +0000` while for Linux it is `00:00:00 UTC`.
        .replace("""[+-]\d{4}""".toRegex(), "UTC")

fun String.patchCycloneDxResult() =
    replace(
        """urn:uuid:[a-f0-9]{8}(?:-[a-f0-9]{4}){4}[a-f0-9]{8}""".toRegex(),
        "urn:uuid:01234567-0123-0123-0123-01234567"
    )
