/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.analyzer.integration

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.managers.Composer
import org.ossreviewtoolkit.analyzer.managers.Npm
import org.ossreviewtoolkit.analyzer.managers.Yarn
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class DrupalIntegrationFunTest : AbstractIntegrationSpec() {
    override val pkg = Package(
        id = Identifier(
            type = "Composer",
            namespace = "",
            name = "Drupal",
            version = ""
        ),
        declaredLicenses = sortedSetOf(),
        description = "",
        homepageUrl = "",
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = VcsInfo(
            VcsType.GIT,
            "https://github.com/drupal/drupal.git",
            "4a765491d80d1bcb11e542ffafccf10aef05b853",
            ""
        )
    )

    override val expectedManagedFiles by lazy {
        val downloadDir = downloadResult.downloadDirectory

        mapOf(
            Composer.Factory() as PackageManagerFactory to listOf(
                downloadDir.resolve("core/modules/system/tests/fixtures/HtaccessTest/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Uuid/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Utility/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Transliteration/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Serialization/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Render/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/ProxyBuilder/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Plugin/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/PhpStorage/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/HttpFoundation/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Graph/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Gettext/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/FileSystem/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/FileCache/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/EventDispatcher/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Discovery/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Diff/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/DependencyInjection/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Datetime/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/ClassFinder/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Bridge/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Assertion/composer.json"),
                downloadDir.resolve("core/lib/Drupal/Component/Annotation/composer.json"),
                downloadDir.resolve("core/composer.json"),
                downloadDir.resolve("composer.json")
            ),
            Npm.Factory() as PackageManagerFactory to listOf(
                downloadDir.resolve("core/package.json"),
                downloadDir.resolve("core/assets/vendor/jquery.ui/package.json")
            ),
            Yarn.Factory() as PackageManagerFactory to listOf(
                downloadDir.resolve("core/package.json"),
                downloadDir.resolve("core/assets/vendor/jquery.ui/package.json")
            )
        )
    }

    override val managedFilesForTest by lazy {
        mapOf(
            Composer.Factory() as PackageManagerFactory to
                    // Limit to definition files that come long with a lock file.
                    listOf(downloadResult.downloadDirectory.resolve("composer.json"))
        )
    }
}
