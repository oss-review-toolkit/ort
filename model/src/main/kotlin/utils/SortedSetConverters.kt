/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

@file:Suppress("Filename", "MatchingDeclarationName")

package org.ossreviewtoolkit.model.utils

import com.fasterxml.jackson.databind.util.StdConverter

import java.util.SortedSet

import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project

class CuratedPackageSortedSetConverter : StdConverter<Set<CuratedPackage>, SortedSet<CuratedPackage>>() {
    override fun convert(value: Set<CuratedPackage>) = value.toSortedSet(compareBy { it.metadata.id })
}

class PackageSortedSetConverter : StdConverter<Set<Package>, SortedSet<Package>>() {
    override fun convert(value: Set<Package>) = value.toSortedSet(compareBy { it.id })
}

class ProjectSortedSetConverter : StdConverter<Set<Project>, SortedSet<Project>>() {
    override fun convert(value: Set<Project>) = value.toSortedSet(compareBy { it.id })
}

// TODO: Add more converters to get rid of Comparable implementations that just serve sorted output.
