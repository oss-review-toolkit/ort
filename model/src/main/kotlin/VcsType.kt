/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * An enum of supported Version Control System types. Each type has one or more [aliases] associated to it, where the
 * first alias is the definite name.
 */
enum class VcsType(private vararg var aliases: String) {
    /**
     * An unknown VCS type.
     */
    UNKNOWN(""),

    /**
     * [Git](https://git-scm.com/) - the stupid content tracker.
     */
    GIT("Git"),

    /**
     * [Repo](https://source.android.com/setup/develop/repo) complements Git by simplifying work across multiple
     * repositories.
     */
    GIT_REPO("GitRepo", "git-repo", "repo"),

    /**
     * [Mercurial](https://www.mercurial-scm.org/) is a free, distributed source control management tool.
     */
    MERCURIAL("Mercurial", "hg"),

    /**
     * [Subversion](https://subversion.apache.org/) is an open source Version Control System.
     */
    SUBVERSION("Subversion", "svn"),

    /**
     * The [Concurrent Versions System](https://en.wikipedia.org/wiki/Concurrent_Versions_System), not actively
     * developed anymore.
     */
    CVS("CVS");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromString(alias: String) =
            enumValues<VcsType>().find { vcsType ->
                vcsType.aliases.find { vcsAlias ->
                    vcsAlias.equals(alias, true)
                } != null
            } ?: UNKNOWN.also {
                it.aliases = arrayOf(alias)
            }
    }

    @JsonValue
    override fun toString(): String = aliases.first()
}
