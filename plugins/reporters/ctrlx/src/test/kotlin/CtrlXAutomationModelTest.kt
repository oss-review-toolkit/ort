/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.ctrlx

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.StringSpec

class CtrlXAutomationModelTest : StringSpec({
    "The license model should accept SPDX expressions with custom exceptions" {
        shouldNotThrow<IllegalArgumentException> {
            License(
                name = "License",
                spdx = "LGPL-3.0-or-later WITH LicenseRef-scancode-openssl-exception-lgpl-3.0-plus",
                text = ""
            )
        }

        shouldNotThrow<IllegalArgumentException> {
            License(
                name = "License",
                spdx = "GPL-2.0-or-later WITH LicenseRef-scancode-autoconf-simple-exception-2.0",
                text = ""
            )
        }
    }
})
