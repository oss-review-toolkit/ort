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

import { computeCvssScores, parseCvssVector } from "@/lib/cvss";

describe("parseCvssVector", () => {
    it("reads the version and metrics of a v3.1 vector", () => {
        const { version, metrics } = parseCvssVector("CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:H");
        expect(version).toBe("3.1");
        expect(metrics.get("AV")).toBe("L");
        expect(metrics.get("A")).toBe("H");
    });

    it("recognises a prefix-less v2 vector by its Authentication metric", () => {
        const { version } = parseCvssVector("AV:N/AC:L/Au:N/C:P/I:P/A:P");
        expect(version).toBe("2.0");
    });
});

describe("computeCvssScores", () => {
    it("scores a CVSS v3.1 vector using the official formula", () => {
        const scores = computeCvssScores("CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:H");
        expect(scores.base).toBe(7.1);
        expect(scores.impact).toBe(5.2);
        expect(scores.exploitability).toBe(1.8);
    });

    it("scores a scope-changed CVSS v3.1 vector", () => {
        const scores = computeCvssScores("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H");
        expect(scores.base).toBe(10);
    });

    it("scores a CVSS v2 vector", () => {
        const scores = computeCvssScores("AV:N/AC:L/Au:N/C:P/I:P/A:P");
        expect(scores.base).toBe(7.5);
    });

    it("falls back to the provided score for a CVSS v4.0 vector", () => {
        const scores = computeCvssScores("CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N", 9.3);
        expect(scores.base).toBe(9.3);
        expect(scores.overall).toBe(9.3);
    });

    it("returns no scores for an unrecognised vector with no fallback", () => {
        expect(computeCvssScores("not-a-vector")).toEqual({});
    });
});
