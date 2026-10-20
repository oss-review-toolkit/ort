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

import { render, screen } from "@testing-library/react";
import { beforeAll, describe, expect, it } from "vitest";

import { RuleViolationsTable } from "@/components/RuleViolationsTable";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppRuleViolation from "@/models/WebAppRuleViolation";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("RuleViolationsTable", () => {
    let ruleViolations: readonly WebAppRuleViolation[];

    beforeAll(async () => {
        const result = await buildResult(loadSampleEvaluatedModel());
        ruleViolations = result.ruleViolations;
    });

    it("renders its column headers", () => {
        render(<RuleViolationsTable ruleViolations={ruleViolations} />);
        expect(screen.getByRole("columnheader", { name: /severity/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /rule/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /message/i })).toBeInTheDocument();
    });

    it("shows the empty state when there are no policy violations", () => {
        render(<RuleViolationsTable ruleViolations={[]} />);
        expect(screen.getByText("No policy violations")).toBeInTheDocument();
    });

    it("shows policy violations of excluded packages as excluded", () => {
        // A minimal stand-in model resolving the package indexes to an excluded and an included package.
        const packages = [
            { excludeReasons: new Set(["TEST_OF"]), id: "Maven:acme:excluded:1.0", isExcluded: true },
            { excludeReasons: new Set(), id: "Maven:acme:included:1.0", isExcluded: false },
        ];
        const model = { getPackageByIndex: (index: number) => packages[index] } as unknown as WebAppEvaluatedModel;
        const violations = [
            new WebAppRuleViolation({ pkg: 0, rule: "RULE", severity: "ERROR" }, model),
            new WebAppRuleViolation({ pkg: 1, rule: "RULE", severity: "ERROR" }, model),
        ];

        const { unmount } = render(<RuleViolationsTable ruleViolations={violations.slice(1)} />);
        expect(screen.queryByTitle("Included / excluded")).not.toBeInTheDocument();
        unmount();

        render(<RuleViolationsTable ruleViolations={violations} />);
        expect(screen.getByTitle("Included / excluded")).toBeInTheDocument();
        const excludedName = screen.getByText("Maven:acme:excluded:1.0");
        expect(excludedName).toHaveClass("line-through", "text-muted-foreground");
        expect(excludedName.parentElement).toHaveAttribute("title", "Excluded: TEST_OF");
        expect(screen.getByText("Maven:acme:included:1.0")).not.toHaveClass("line-through");
        expect(excludedName.closest("tr")).not.toHaveClass("text-muted-foreground");
    });
});
