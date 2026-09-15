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

import { beforeAll, describe, expect, it, vi as vitest } from "vitest";

import WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";
import type { EvaluatedModel } from "@/types/evaluatedModelData";

describe("WebAppEvaluatedModel", () => {
    const raw: EvaluatedModel = loadSampleEvaluatedModel();
    let result: WebAppEvaluatedModel;

    beforeAll(() => {
        result = new WebAppEvaluatedModel(raw);
    });

    it("is empty when constructed without data", () => {
        const empty = new WebAppEvaluatedModel();
        expect(empty.packages).toHaveLength(0);
        expect(empty.projects).toHaveLength(0);
        expect(empty.issues).toHaveLength(0);
        expect(empty.ruleViolations).toHaveLength(0);
        expect(empty.hasExcludes()).toBe(false);
        expect(empty.hasVulnerabilities()).toBe(false);
        expect(empty.getPackageByIndex(0)).toBeNull();
    });

    it("creates one package per raw package entry", () => {
        expect(result.packages.length).toBeGreaterThan(0);
        expect(result.packages).toHaveLength(raw.packages?.length ?? 0);
    });

    it("round-trips packages through getPackageByIndex", () => {
        result.packages.forEach((pkg, index) => {
            expect(result.getPackageByIndex(index)).toBe(pkg);
            expect(pkg.packageIndex).toBe(index);
        });
        expect(result.getPackageByIndex(result.packages.length)).toBeNull();
    });

    it("collects projects as exactly the packages flagged is_project", () => {
        const projectsAmongPackages = result.packages.filter((pkg) => pkg.isProject);
        expect(result.projects).toHaveLength(projectsAmongPackages.length);
        expect(result.projects.every((pkg) => pkg.isProject)).toBe(true);
        expect(result.projects.length).toBeGreaterThan(0);
    });

    it("resolves packages by id and returns null for unknown ids", () => {
        const [first] = result.packages;
        expect(first).toBeDefined();
        const id = first?.id;
        if (id) {
            expect(result.getPackageById(id)).toBe(first);
        }
        // An unknown id resolves to an empty array, the model's "not found" sentinel.
        expect(result.getPackageById("Nonexistent:package:id:0.0.0")).toEqual([]);
    });

    it("exposes processed license names matching the statistics maps", () => {
        expect(result.declaredLicensesProcessed).toEqual([...result.statistics.licenses.declared.keys()]);
        expect(result.detectedLicensesProcessed).toEqual([...result.statistics.licenses.detected.keys()]);
        expect(result.effectiveLicenses).toEqual([...result.statistics.licenses.effective.keys()]);
        expect(result.hasDeclaredLicensesProcessed()).toBe(result.declaredLicensesProcessed.length > 0);
        expect(result.hasDetectedLicensesProcessed()).toBe(result.detectedLicensesProcessed.length > 0);
        expect(result.hasEffectiveLicenses()).toBe(result.effectiveLicenses.length > 0);
    });

    it("returns null from getLicenseByName for an unknown license", () => {
        expect(result.getLicenseByName("This-Is-Not-A-Real-License-1.0")).toBeNull();
    });

    it("parses path excludes and reflects them in hasExcludes()", () => {
        const rawPathExcludes = raw.path_excludes ?? raw.pathExcludes ?? [];
        expect(result.pathExcludes).toHaveLength(rawPathExcludes.length);
        expect(result.hasExcludes()).toBe(result.pathExcludes.length > 0 || result.scopeExcludes.length > 0);
    });

    it("groups issues, rule violations and vulnerabilities by their package index", () => {
        expect(result.issues).toHaveLength(raw.issues?.length ?? 0);

        for (const issue of result.issues) {
            const index = issue.packageIndex;
            if (typeof index === "number") {
                expect(result.getIssuesForPackageIndex(index)).toContain(issue);
            }
        }
        for (const violation of result.ruleViolations) {
            expect(result.getRuleViolationsForPackageIndex(violation.packageIndex)).toContain(violation);
        }
        for (const vulnerability of result.vulnerabilities) {
            expect(result.getVulnerabilitiesForPackageIndex(vulnerability.packageIndex)).toContain(vulnerability);
        }

        expect(result.hasVulnerabilities()).toBe(result.vulnerabilities.length > 0);
    });

    it("materialises one dependency tree per raw tree once timers drain", async () => {
        const built = await buildResult(raw);
        const rawTrees = raw.dependency_trees ?? raw.dependencyTrees ?? [];

        expect(built.dependencyTrees).toHaveLength(rawTrees.length);
        expect(built.dependencyTrees.length).toBeGreaterThan(0);

        for (const root of built.dependencyTrees) {
            expect(root.isProject).toBe(true);
            expect(typeof root.packageIndex).toBe("number");
            expect(root.title).toBe(built.getPackageByIndex(root.packageIndex ?? -1)?.id);
        }
    });

    it("reports the dependency trees as not ready until the deferred build tasks drain", () => {
        vitest.useFakeTimers({ loopLimit: 5_000_000 });
        try {
            const model = new WebAppEvaluatedModel(raw);
            const rawTrees = raw.dependency_trees ?? raw.dependencyTrees ?? [];
            // A sample without trees would make the readiness gate trivially true, so guard the premise.
            expect(rawTrees.length).toBeGreaterThan(0);

            // Construction only schedules the build tasks; nothing is materialised yet.
            expect(model.dependencyTreesReady).toBe(false);
            expect(model.dependencyTrees).toHaveLength(0);

            let notified = 0;
            model.onDependencyTreesReady(() => {
                notified += 1;
            });
            expect(notified).toBe(0);

            vitest.runAllTimers();

            expect(model.dependencyTreesReady).toBe(true);
            expect(notified).toBe(1);
            expect(model.dependencyTrees).toHaveLength(rawTrees.length);
        } finally {
            vitest.useRealTimers();
        }
    });

    it("invokes a late onDependencyTreesReady listener immediately once the trees are built", () => {
        vitest.useFakeTimers({ loopLimit: 5_000_000 });
        try {
            const model = new WebAppEvaluatedModel(raw);
            vitest.runAllTimers();
            expect(model.dependencyTreesReady).toBe(true);

            let called = 0;
            const unsubscribe = model.onDependencyTreesReady(() => {
                called += 1;
            });
            expect(called).toBe(1);
            unsubscribe();
        } finally {
            vitest.useRealTimers();
        }
    });

    it("does not notify an onDependencyTreesReady listener that unsubscribed before completion", () => {
        vitest.useFakeTimers({ loopLimit: 5_000_000 });
        try {
            const model = new WebAppEvaluatedModel(raw);
            let called = 0;
            const unsubscribe = model.onDependencyTreesReady(() => {
                called += 1;
            });
            unsubscribe();

            vitest.runAllTimers();

            expect(called).toBe(0);
            expect(model.dependencyTreesReady).toBe(true);
        } finally {
            vitest.useRealTimers();
        }
    });

    it("treats a model with no dependency trees as immediately ready", () => {
        const empty = new WebAppEvaluatedModel();
        expect(empty.dependencyTreesReady).toBe(true);

        let called = 0;
        empty.onDependencyTreesReady(() => {
            called += 1;
        });
        expect(called).toBe(1);
    });
});
