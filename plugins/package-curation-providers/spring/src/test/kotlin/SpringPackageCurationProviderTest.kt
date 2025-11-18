/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.spring

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData

class SpringPackageCurationProviderTest : StringSpec({
    val provider = SpringPackageCurationProvider()

    "Recognize metadata only projects" {
        val id = Identifier("Maven:org.springframework.boot:spring-boot-starter-parent:2.7.4")
        val pkg = Package.EMPTY.copy(id = id)

        provider.getCurationsFor(setOf(pkg)) shouldContainExactly setOf(
            PackageCuration(
                id = id,
                data = PackageCurationData(isMetadataOnly = true)
            )
        )
    }

    "Get the correct paths for Spring Boot projects" {
        val id = Identifier("Maven:org.springframework.boot:spring-boot-antlib:3.5.4")
        val pkg = Package.EMPTY.copy(
            id = id,
            vcsProcessed = VcsInfo.EMPTY.copy(url = "https://github.com/spring-projects/spring-boot.git")
        )

        provider.getCurationsFor(setOf(pkg)) shouldContainExactly setOf(
            PackageCuration(
                id = id,
                data = PackageCurationData(
                    vcs = VcsInfoCurationData(path = "spring-boot-project/spring-boot-tools/spring-boot-antlib")
                )
            )
        )
    }
})
