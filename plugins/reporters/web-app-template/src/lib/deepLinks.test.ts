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

import { afterEach, describe, expect, it } from "vitest";

import { getDeepLinkFocus } from "@/lib/deepLinks";

function setSearch(search: string): void {
    window.history.replaceState({}, "", search === "" ? "/" : `/${search}`);
}

describe("getDeepLinkFocus", () => {
    afterEach(() => {
        setSearch("");
    });

    it("reads nothing from a plain URL", () => {
        setSearch("");
        expect(getDeepLinkFocus()).toEqual({ packageId: null, packageTab: null, vulnerabilityId: null });
    });

    it("decodes a package id, which carries the colons of a package coordinate", () => {
        setSearch("?pkg-id=Crate%3A%3Ahttpdate%3A1.0.3&pkg-tab=findings");

        // The model reads this before it is built, to decide whose findings to build up front.
        expect(getDeepLinkFocus().packageId).toBe("Crate::httpdate:1.0.3");
        expect(getDeepLinkFocus().packageTab).toBe("findings");
    });

    it("reads a vulnerability scoped by its package", () => {
        setSearch("?vul-id=CVE-2020-0000&pkg-id=Crate%3A%3Ahttpdate%3A1.0.3");

        expect(getDeepLinkFocus().vulnerabilityId).toBe("CVE-2020-0000");
        expect(getDeepLinkFocus().packageId).toBe("Crate::httpdate:1.0.3");
    });
});
