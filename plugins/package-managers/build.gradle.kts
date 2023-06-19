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

plugins {
    // Apply precompiled plugins.
    id("ort-plugins-conventions")
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(project(":plugins:package-managers:bower-package-manager"))
    api(project(":plugins:package-managers:bundler-package-manager"))
    api(project(":plugins:package-managers:cargo-package-manager"))
    api(project(":plugins:package-managers:carthage-package-manager"))
    api(project(":plugins:package-managers:cocoapods-package-manager"))
    api(project(":plugins:package-managers:composer-package-manager"))
    api(project(":plugins:package-managers:conan-package-manager"))
    api(project(":plugins:package-managers:gradle-inspector"))
    api(project(":plugins:package-managers:gradle-model"))
    api(project(":plugins:package-managers:gradle-package-manager"))
    api(project(":plugins:package-managers:node-package-manager"))
    api(project(":plugins:package-managers:nuget-package-manager"))
    api(project(":plugins:package-managers:pub-package-manager"))
    api(project(":plugins:package-managers:python-package-manager"))
    api(project(":plugins:package-managers:spdx-package-manager"))
    api(project(":plugins:package-managers:stack-package-manager"))
    api(project(":plugins:package-managers:unmanaged-package-manager"))
}
