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

import { getActiveExpandedRowId } from "@/components/ui/data-table/expandedRow";

describe("getActiveExpandedRowId", () => {
    it("returns undefined when nothing is expanded", () => {
        expect(getActiveExpandedRowId(undefined)).toBeUndefined();
        expect(getActiveExpandedRowId({})).toBeUndefined();
    });

    it("returns the id of a single expanded row", () => {
        expect(getActiveExpandedRowId({ a: true })).toBe("a");
    });

    it("returns the most-recently-expanded (last inserted) row when several stay open", () => {
        // TanStack appends newly expanded keys, so expanding a while b, c stay open puts the newest last.
        expect(getActiveExpandedRowId({ b: true, c: true, a: true })).toBe("a");
    });

    it("ignores keys explicitly set to false", () => {
        expect(getActiveExpandedRowId({ a: true, b: false })).toBe("a");
        expect(getActiveExpandedRowId({ a: false })).toBeUndefined();
    });

    it("returns undefined when every row is expanded (boolean true state)", () => {
        expect(getActiveExpandedRowId(true)).toBeUndefined();
    });
});
