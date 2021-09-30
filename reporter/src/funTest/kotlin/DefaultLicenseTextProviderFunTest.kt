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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty

class DefaultLicenseTextProviderFunTest : StringSpec({
    "Can provide an SPDX license text" {
        val resolver = DefaultLicenseTextProvider()

        resolver.hasLicenseText("Apache-2.0") shouldBe true
        resolver.getLicenseText("Apache-2.0") shouldNot beEmpty()
    }

    "Can provide a LicenseRef-scancode license text" {
        val resolver = DefaultLicenseTextProvider()

        resolver.hasLicenseText("LicenseRef-scancode-mit-modern") shouldBe true
        resolver.getLicenseText("LicenseRef-scancode-mit-modern") shouldNot beEmpty()
    }
})
