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

import { computeCvssScores, cvssVersionLabel, cvssVersionOf, cvssVersionRank } from "@/lib/cvss";

// The scoring itself belongs to ae-cvss-calculator and is covered by that project's own tests
// against the official validation vectors; what is checked here is that a vector reaches it and
// that its result is adapted to what the report needs.
describe("cvssVersionOf", () => {
    it.each([
        ["CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N", "4.0"],
        ["CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:H", "3.1"],
        ["CVSS:3.0/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:H", "3.0"],
        ["AV:N/AC:L/Au:N/C:P/I:P/A:P", "2.0"],
    ])("recognises %s as CVSS %s", (vector, version) => {
        expect(cvssVersionOf(vector)).toBe(version);
    });

    it.each(["", "not-a-cvss-vector", "0.84061"])("does not recognise %s as a CVSS vector", (vector) => {
        // ORT reports an EPSS percentile in the same field as a CVSS vector, hence the bare number.
        expect(cvssVersionOf(vector)).toBe("unknown");
    });
});

describe("cvssVersionLabel and cvssVersionRank", () => {
    it("labels a known version and leaves an unknown one blank", () => {
        expect(cvssVersionLabel("3.1")).toBe("v3.1");
        expect(cvssVersionLabel("unknown")).toBe("");
    });

    it("ranks the versions oldest to newest", () => {
        const ranks = (["4.0", "2.0", "3.1", "3.0"] as const).map(cvssVersionRank);
        expect([...ranks].sort((a, b) => a - b)).toEqual([0, 1, 2, 3]);
        expect(cvssVersionRank("2.0")).toBeLessThan(cvssVersionRank("4.0"));
    });
});

describe("computeCvssScores", () => {
    it("splits a CVSS v3.1 vector into its base, impact and exploitability sub-scores", () => {
        const scores = computeCvssScores("CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:H");
        expect(scores.base).toBe(7.1);
        // Normalized to the 0-10 scale the radar axes use, rather than the raw formula values.
        expect(scores.impact).toBeGreaterThan(0);
        expect(scores.impact).toBeLessThanOrEqual(10);
        expect(scores.exploitability).toBeGreaterThan(0);
        expect(scores.exploitability).toBeLessThanOrEqual(10);
    });

    it("scores a CVSS v2 vector", () => {
        expect(computeCvssScores("AV:N/AC:L/Au:N/C:P/I:P/A:P").base).toBe(7.5);
    });

    it("scores a CVSS v4.0 vector without being given a score", () => {
        // Previously nothing was computed for v4.0, which left the radar empty for a vector whose
        // reference carried no score of its own.
        const scores = computeCvssScores("CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N");
        expect(scores.overall).toBe(9.3);
    });

    it("applies the threat metrics of a CVSS v4.0 vector rather than the score it was given", () => {
        // An unreported exploit lowers the score well below the base score an advisor reports.
        const vector =
            "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:N/VA:H/SC:N/SI:N/SA:N/E:U/S:N/AU:Y/R:U/V:D/RE:M/U:Amber";
        const scores = computeCvssScores(vector, 8.7);
        expect(scores.overall).toBe(6.6);
        expect(scores.threat).toBe(6.6);
    });

    it("falls back to the given score for a vector it cannot score", () => {
        const scores = computeCvssScores("not-a-cvss-vector", 4.2);
        expect(scores.base).toBe(4.2);
        expect(scores.overall).toBe(4.2);
    });

    it("returns no scores for an unrecognised vector with no fallback", () => {
        expect(computeCvssScores("not-a-cvss-vector")).toEqual({
            base: undefined,
            environmental: undefined,
            exploitability: undefined,
            impact: undefined,
            modifiedImpact: undefined,
            overall: undefined,
            temporal: undefined,
            threat: undefined,
        });
    });
});
