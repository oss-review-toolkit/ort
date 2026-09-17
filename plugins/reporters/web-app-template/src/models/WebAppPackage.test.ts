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

import { afterEach, describe, expect, it, vi } from "vitest";

import WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { loadSampleEvaluatedModel } from "@/test/fixture";

const DEEP_LINKED = "Crate::httpdate:1.0.3";

/** The model as it is the moment it is constructed, with none of its deferred work run yet. */
function buildUndrainedResult(eagerFindingsForPackageId?: string): WebAppEvaluatedModel {
    vi.useFakeTimers({ loopLimit: 5_000_000 });
    return new WebAppEvaluatedModel(loadSampleEvaluatedModel(), eagerFindingsForPackageId);
}

describe("WebAppPackage findings", () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it("builds them up front for the package a deep link opens", () => {
        // A deep link such as ?pkg-id=...&pkg-tab=findings expands the package in the same tick the
        // model was constructed, so its panel read an array that was still empty and reported
        // "No scanner findings". Nothing then told it the array had been filled.
        const result = buildUndrainedResult(DEEP_LINKED);
        const pkg = result.packages.find((candidate) => candidate.id === DEEP_LINKED);

        expect(pkg).toBeDefined();
        expect(pkg?.findings.length).toBeGreaterThan(0);
    });

    it("leaves every other package to the deferred build, keeping that work off the main thread", () => {
        const result = buildUndrainedResult(DEEP_LINKED);
        const others = result.packages.filter((candidate) => candidate.id !== DEEP_LINKED);

        expect(others.length).toBeGreaterThan(0);
        expect(others.every((candidate) => candidate.findings.length === 0)).toBe(true);

        vi.runAllTimers();
        expect(others.some((candidate) => candidate.findings.length > 0)).toBe(true);
    });

    it("defers every package when no deep link names one", () => {
        const result = buildUndrainedResult();
        const pkg = result.packages.find((candidate) => candidate.id === DEEP_LINKED);

        expect(pkg?.findings).toHaveLength(0);

        vi.runAllTimers();
        expect(pkg?.findings.length).toBeGreaterThan(0);
    });

    it("resolves the scanner that reported each finding, which needs the scan results too", () => {
        const result = buildUndrainedResult(DEEP_LINKED);
        const finding = result.packages.find((candidate) => candidate.id === DEEP_LINKED)?.findings[0];

        expect(finding).toBeDefined();
        // Building the findings alone is not enough: each resolves the scanner that reported it from
        // the model's scan results, which are deferred as well, so the panel would have listed them
        // with an empty scanner column.
        expect(finding?.scanResult?.scanner?.name).toBe("ScanCode");
    });

    it("defers the scan results when no deep link names a package", () => {
        const result = buildUndrainedResult();

        expect(result.getScanResultByIndex(0)).toBeNull();

        vi.runAllTimers();
        expect(result.getScanResultByIndex(0)).not.toBeNull();
    });

    it("builds the deep-linked package's findings exactly once", () => {
        const result = buildUndrainedResult(DEEP_LINKED);
        const pkg = result.packages.find((candidate) => candidate.id === DEEP_LINKED);
        const eager = pkg?.findings.length;

        vi.runAllTimers();

        // Building again on the deferred task would double them, and the excluded indexes with them.
        expect(pkg?.findings.length).toBe(eager);
    });
});
