/*
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.licenses

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider

class DefaultLicenseInfoProviderTest : WordSpec({
    val defaultLicenseInfoProvider = DefaultLicenseInfoProvider(ortResult, PackageConfigurationProvider.EMPTY)

    "declaredLicenseInfo" should {
        "contain author information for package" {
            defaultLicenseInfoProvider.get(packageWithAuthors.id).declaredLicenseInfo.authors shouldBe authors
        }

        "contain author information for project" {
            defaultLicenseInfoProvider.get(project.id).declaredLicenseInfo.authors shouldBe projectAuthors
        }
    }
})
