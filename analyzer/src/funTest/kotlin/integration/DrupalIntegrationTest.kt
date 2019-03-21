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

package com.here.ort.analyzer.integration

import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.analyzer.managers.NPM
import com.here.ort.analyzer.managers.PhpComposer
import com.here.ort.analyzer.managers.Yarn
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo

import java.io.File

class DrupalIntegrationTest : AbstractIntegrationSpec() {
    override val pkg: Package = Package(
        id = Identifier(
            type = "PhpComposer",
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
            "Git",
            "https://github.com/drupal/drupal.git",
            "4a765491d80d1bcb11e542ffafccf10aef05b853",
            ""
        )
    )

    override val expectedManagedFiles by lazy {
        val downloadDir = downloadResult.downloadDirectory

        mapOf(
            PhpComposer.Factory() as PackageManagerFactory to listOf(
                File(downloadDir, "core/modules/system/tests/fixtures/HtaccessTest/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Uuid/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Utility/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Transliteration/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Serialization/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Render/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/ProxyBuilder/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Plugin/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/PhpStorage/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/HttpFoundation/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Graph/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Gettext/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/FileSystem/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/FileCache/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/EventDispatcher/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Discovery/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Diff/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/DependencyInjection/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Datetime/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/ClassFinder/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Bridge/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Assertion/composer.json"),
                File(downloadDir, "core/lib/Drupal/Component/Annotation/composer.json"),
                File(downloadDir, "core/composer.json"),
                File(downloadDir, "composer.json")
            ),
            NPM.Factory() as PackageManagerFactory to listOf(
                File(downloadDir, "core/package.json"),
                File(downloadDir, "core/assets/vendor/jquery.ui/package.json")
            ),
            Yarn.Factory() as PackageManagerFactory to listOf(
                File(downloadDir, "core/package.json"),
                File(downloadDir, "core/assets/vendor/jquery.ui/package.json")
            )
        )
    }

    override val managedFilesForTest by lazy {
        mapOf(
            PhpComposer.Factory() as PackageManagerFactory to
                    // Limit to definition files that come long with a lock file.
                    listOf(File(downloadResult.downloadDirectory, "composer.json"))
        )
    }
}
