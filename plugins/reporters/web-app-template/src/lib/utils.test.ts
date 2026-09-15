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

import { describe, expect, it } from "vitest";

import { cn } from "@/lib/utils";

describe("cn", () => {
    it("joins truthy class names", () => {
        expect(cn("a", "b")).toBe("a b");
    });

    it("ignores falsy values", () => {
        expect(cn("a", false, null, undefined, "", "b")).toBe("a b");
    });

    it("merges conflicting tailwind utilities keeping the last one", () => {
        expect(cn("p-2", "p-4")).toBe("p-4");
        expect(cn("text-foreground", "text-muted-foreground")).toBe("text-muted-foreground");
    });

    it("supports arrays and conditional object syntax", () => {
        expect(cn(["a", "b"], { active: true, disabled: false })).toBe("a b active");
    });
});
