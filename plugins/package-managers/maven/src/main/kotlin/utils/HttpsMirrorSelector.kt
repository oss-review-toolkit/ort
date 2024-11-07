/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.utils

import org.apache.logging.log4j.kotlin.logger

import org.eclipse.aether.repository.MirrorSelector
import org.eclipse.aether.repository.RemoteRepository

/**
 * Several Maven repositories have disabled HTTP access and require HTTPS now. To be able to still analyze old Maven
 * projects that use the HTTP URLs, this [MirrorSelector] implementation automatically creates an HTTPS mirror if a
 * [RemoteRepository] uses a disabled HTTP URL. Without that Maven would abort with an exception as soon as it tries to
 * download an Artifact from any of those repositories.
 *
 * **See also:**
 *
 * [GitHub Security Lab issue](https://github.com/github/security-lab/issues/21)
 * [Medium article](https://medium.com/p/d069d253fe23)
 */
internal class HttpsMirrorSelector(private val originalMirrorSelector: MirrorSelector?) : MirrorSelector {
    companion object {
        private val DISABLED_HTTP_REPOSITORY_URLS = listOf(
            "http://jcenter.bintray.com",
            "http://repo.maven.apache.org",
            "http://repo1.maven.org",
            "http://repo.spring.io"
        )
    }

    override fun getMirror(repository: RemoteRepository?): RemoteRepository? {
        originalMirrorSelector?.getMirror(repository)?.let { return it }

        if (repository == null || DISABLED_HTTP_REPOSITORY_URLS.none { repository.url.startsWith(it) }) return null

        logger.debug {
            "HTTP access to ${repository.id} (${repository.url}) was disabled. Automatically switching to HTTPS."
        }

        return RemoteRepository.Builder(
            "${repository.id}-https-mirror",
            repository.contentType,
            "https://${repository.url.removePrefix("http://")}"
        ).apply {
            setRepositoryManager(false)
            setSnapshotPolicy(repository.getPolicy(true))
            setReleasePolicy(repository.getPolicy(false))
            setMirroredRepositories(listOf(repository))
        }.build()
    }
}
