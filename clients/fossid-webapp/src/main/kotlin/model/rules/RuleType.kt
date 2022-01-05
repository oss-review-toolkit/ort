/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.fossid.model.rules

/**
 * An enum specifying the type of "ignore rule".
 */
enum class RuleType {
    /**
     * A rule that allows to ignore all directories with a specific name during scan.
     * When defining a directory rule, the rule value can be a name or a regex.
     */
    DIRECTORY,

    /**
     * A rule that allows to ignore all files with a given extension during scan.
     */
    EXTENSION,

    /**
     * A rule that allows to ignore all files with a specific name during scan.
     */
    FILE
}
