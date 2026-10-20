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

import { IssuesTable } from "@/components/IssuesTable";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppOrtIssue from "@/models/WebAppOrtIssue";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("IssuesTable", () => {
    let issues: readonly WebAppOrtIssue[];

    beforeAll(async () => {
        const result = await buildResult(loadSampleEvaluatedModel());
        issues = result.issues;
    });

    it("renders its column headers", () => {
        render(<IssuesTable issues={issues} />);
        expect(screen.getByRole("columnheader", { name: /severity/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /package/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /message/i })).toBeInTheDocument();
    });

    it("shows the empty state when there are no issues", () => {
        render(<IssuesTable issues={[]} />);
        expect(screen.getByText("No technical issues")).toBeInTheDocument();
    });

    it("shows the excludes column only when an issue is excluded", () => {
        const included = new WebAppOrtIssue({ _id: 0, message: "Included issue", severity: "ERROR" });
        const excluded = new WebAppOrtIssue({
            _id: 1,
            is_excluded: true,
            message: "Excluded issue",
            severity: "ERROR",
        });

        const { unmount } = render(<IssuesTable issues={[included]} />);
        expect(screen.queryByTitle("Included / excluded")).not.toBeInTheDocument();
        unmount();

        render(<IssuesTable issues={[included, excluded]} />);
        expect(screen.getByTitle("Included / excluded")).toBeInTheDocument();
        expect(screen.getByTitle("Excluded")).toBeInTheDocument();
        expect(screen.getByTitle("Included")).toBeInTheDocument();
    });

    it("strikes through the package only when the package itself is excluded", () => {
        // A minimal stand-in model resolving the package indexes to an excluded and an included package.
        const packages = [
            { excludeReasons: new Set(["TEST_OF"]), id: "Maven:acme:excluded:1.0", isExcluded: true },
            { excludeReasons: new Set(), id: "Maven:acme:included:1.0", isExcluded: false },
        ];
        const model = { getPackageByIndex: (index: number) => packages[index] } as unknown as WebAppEvaluatedModel;
        const issues = [
            new WebAppOrtIssue({ _id: 0, is_excluded: true, pkg: 0, severity: "ERROR" }, model),
            // Excluded by its affected path while its package is included.
            new WebAppOrtIssue({ _id: 1, is_excluded: true, pkg: 1, severity: "ERROR" }, model),
        ];

        render(<IssuesTable issues={issues} />);
        const excludedName = screen.getByText("Maven:acme:excluded:1.0");
        expect(excludedName).toHaveClass("line-through", "text-muted-foreground");
        expect(excludedName).not.toHaveClass("text-primary");
        expect(excludedName.parentElement).toHaveAttribute("title", "Excluded: TEST_OF");
        expect(screen.getByText("Maven:acme:included:1.0")).not.toHaveClass("line-through");
    });
});
