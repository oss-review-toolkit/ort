/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Identifier

class NuGetUtilsTest : WordSpec({
    "getIdentifierWithNamespace()" should {
        "split the namespace from the name" {
            assertSoftly {
                getIdentifierWithNamespace("NuGet", "SharpCompress", "0.23.0") shouldBe
                    Identifier("NuGet::SharpCompress:0.23.0")

                getIdentifierWithNamespace("NuGet", "System.IO", "4.1.0") shouldBe
                    Identifier("NuGet:System:IO:4.1.0")
                getIdentifierWithNamespace("NuGet", "System.IO.Compression", "4.3.0") shouldBe
                    Identifier("NuGet:System.IO:Compression:4.3.0")
                getIdentifierWithNamespace("NuGet", "System.IO.Compression.ZipFile", "4.0.1") shouldBe
                    Identifier("NuGet:System.IO:Compression.ZipFile:4.0.1")

                getIdentifierWithNamespace(
                    "NuGet",
                    "Microsoft.Extensions.Diagnostics.HealthChecks.EntityFrameworkCore",
                    "2.2.1"
                ) shouldBe Identifier(
                    "NuGet:Microsoft.Extensions:Diagnostics.HealthChecks.EntityFrameworkCore:2.2.1"
                )

                getIdentifierWithNamespace(
                    "NuGet",
                    "Microsoft.AspNetCore.Components.WebAssembly.Build.BrotliCompression",
                    "3.1.6"
                ) shouldBe Identifier(
                    "NuGet:Microsoft.AspNetCore:Components.WebAssembly.Build.BrotliCompression:3.1.6"
                )
            }
        }
    }
})
