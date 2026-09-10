/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.npm

import org.ossreviewtoolkit.plugins.api.OrtPluginOption

data class NpmConfig(
    /**
     * If true, ignore any project-specific `.npmrc` files.
     */
    @OrtPluginOption(defaultValue = "false")
    val ignoreProjectNpmrcFiles: Boolean,

    /**
     * If true, the "--legacy-peer-deps" flag is passed to NPM to ignore conflicts in peer dependencies which are
     * reported since NPM 7. This allows to analyze NPM 6 projects with peer dependency conflicts. For more information
     * see the [documentation](https://docs.npmjs.com/cli/v8/commands/npm-install#strict-peer-deps) and the
     * [NPM Blog](https://blog.npmjs.org/post/626173315965468672/npm-v7-series-beta-release-and-semver-major).
     */
    @OrtPluginOption(defaultValue = "false")
    val legacyPeerDeps: Boolean,

    /**
     * Allows configuring the version of Node.js to be used for the analysis. This implicitly also sets the NPM version
     * because NPM is bundled with Node.js. The property is interpreted as follows: If it is unspecified, ORT uses the
     * version of Node.js that is currently installed (or ships with the container image if using the ORT Docker
     * image). If the property has the special value "*", ORT tries to set up the version requested by the project, in
     * a `.node-version` or `.nvmrc` file, or in the `engines` field of the `package.json` file. Any other value of the
     * property is interpreted as a specific version of Node.js to be used for the analysis.
     */
    @OrtPluginOption(defaultValue = "")
    val nodeVersion: String,

    /**
     * Override OS of native modules to install. See also https://nodejs.org/api/process.html#processplatform.
     */
    val os: Platform?,

    /**
     * Override CPU architecture of native modules to install. See also https://nodejs.org/api/process.html#processarch.
     */
    val cpu: ProcessorArchitecture?
) {
    /**
     * See https://nodejs.org/api/process.html#processarch.
     */
    enum class ProcessorArchitecture {
        ARM,
        ARM64,
        IA32,
        LOONG64,
        MIPSEL,
        PPC64,
        RISCV64,
        S390,
        S390X,
        X64
    }

    /**
     * See https://nodejs.org/api/process.html#processplatform.
     */
    enum class Platform {
        AIX,
        DARWIN,
        FREEBSD,
        LINUX,
        OPENBSD,
        SUNOS,
        WIN32
    }
}
