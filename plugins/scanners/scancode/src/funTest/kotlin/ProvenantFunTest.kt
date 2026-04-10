/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.scanners.scancode

import io.kotest.core.annotation.Condition
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.Spec

import kotlin.reflect.KClass

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.scanner.AbstractPathScannerWrapperFunTest

@EnabledIf(ProvenantInPath::class)
@Tags("RequiresExternalTool")
class ProvenantFunTest : AbstractPathScannerWrapperFunTest() {
    override val scanner = ProvenantFactory.create()

    override val expectedFileLicenses = listOf(
        LicenseFinding("Apache-2.0", TextLocation("LICENSE", 1, 201), 100.0f)
    )

    override val expectedDirectoryLicenses = listOf(
        LicenseFinding("Apache-2.0", TextLocation("COPYING", 1, 201), 99.43f),
        LicenseFinding("Apache-2.0", TextLocation("LICENCE", 1, 201), 100.0f),
        LicenseFinding("Apache-2.0", TextLocation("LICENSE", 1, 201), 100.0f)
    )
}

internal class ProvenantInPath : Condition {
    override fun evaluate(kclass: KClass<out Spec>): Boolean = ProvenantCommand.isInPath()
}
