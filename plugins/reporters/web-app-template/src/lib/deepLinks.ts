/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

/** What a deep link asks the app to open. */
export interface DeepLinkFocus {
    packageId: string | null;
    packageTab: string | null;
    vulnerabilityId: string | null;
}

/**
 * Read what the URL asks to be opened: a package (and the tab within its panel) for the Packages,
 * Dependency Graph, Technical Issues and Policy Violations views, e.g. ?pkg-id=...&pkg-tab=..., and a
 * vulnerability (its advisory id, scoped by the affected package) for the Vulnerabilities tab, e.g.
 * ?vul-id=...&pkg-id=...
 *
 * Read before the model is built as well as by the views, so the model knows which package is about to
 * be opened; see the `buildFindingsEagerly` argument to WebAppPackage.
 */
export function getDeepLinkFocus(): DeepLinkFocus {
    if (typeof window === "undefined") {
        return { packageId: null, packageTab: null, vulnerabilityId: null };
    }

    const params = new URLSearchParams(window.location.search);
    return {
        packageId: params.get("pkg-id"),
        packageTab: params.get("pkg-tab"),
        vulnerabilityId: params.get("vul-id"),
    };
}
