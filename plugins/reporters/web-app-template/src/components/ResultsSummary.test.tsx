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
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it, vi } from "vitest";

import { ResultsSummary } from "@/components/ResultsSummary";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

// An array of the given length; ResultsSummary only reads `.length` for issues, violations, projects,
// scopes and the license lists, so the elements themselves do not matter.
const list = (length: number): unknown[] => Array.from({ length });

interface SeverityCounts {
    errors: number;
    hints: number;
    total: number;
    warnings: number;
}

interface FakeOptions {
    issuesTotal?: number;
    openIssues?: SeverityCounts;
    openVulnerabilities?: number;
    resolvedConfiguration?: {
        licenseChoices: number;
        packageConfigurations: number;
        packageCurations: number;
        resolutions: number;
    };
    severeOpenIssuesCount?: number;
    vulnerabilities?: { isResolved: boolean; severityIndex: number }[];
}

const NO_SEVERITY: SeverityCounts = { errors: 0, hints: 0, total: 0, warnings: 0 };
const NO_CONFIG = { licenseChoices: 0, packageConfigurations: 0, packageCurations: 0, resolutions: 0 };

// A minimal stand-in for WebAppEvaluatedModel exposing only the fields ResultsSummary reads, so the
// verdict, severity-badge and configuration branches can be exercised deterministically. The type is
// satisfied by casting; at runtime the component only touches the properties provided here.
function fakeModel(options: FakeOptions = {}): WebAppEvaluatedModel {
    const openIssues = options.openIssues ?? NO_SEVERITY;
    const model = {
        declaredLicensesProcessed: [],
        detectedLicensesProcessed: [],
        effectiveLicenses: [],
        hasExcludes: () => false,
        hasRepositoryConfiguration: () => false,
        issues: list(options.issuesTotal ?? openIssues.total),
        packages: [],
        projects: [],
        repository: { vcsProcessed: { revision: "abcdef1", type: "Git", url: "https://example.com/acme/repo.git" } },
        ruleViolations: [],
        scopes: [],
        severeOpenIssuesCount: options.severeOpenIssuesCount ?? 0,
        severeOpenRuleViolationsCount: 0,
        statistics: {
            openIssues,
            openRuleViolations: NO_SEVERITY,
            openVulnerabilities: options.openVulnerabilities ?? 0,
            resolvedConfiguration: options.resolvedConfiguration ?? NO_CONFIG,
        },
        vulnerabilities: options.vulnerabilities ?? [],
    };
    return model as unknown as WebAppEvaluatedModel;
}

describe("ResultsSummary", () => {
    describe("with the sample report", () => {
        let model: WebAppEvaluatedModel;

        beforeAll(async () => {
            model = await buildResult(loadSampleEvaluatedModel());
        });

        it("renders a row for every finding detector and the licenses", () => {
            render(<ResultsSummary webAppEvaluatedModel={model} />);
            expect(screen.getByRole("button", { name: /^Technical issues/ })).toBeInTheDocument();
            expect(screen.getByRole("button", { name: /^Policy violations/ })).toBeInTheDocument();
            expect(screen.getByRole("button", { name: /^Vulnerabilities/ })).toBeInTheDocument();
            expect(screen.getByRole("button", { name: /^Licenses/ })).toBeInTheDocument();
        });

        it("renders the repository and composition sidebar sections", () => {
            render(<ResultsSummary webAppEvaluatedModel={model} />);
            expect(screen.getByRole("heading", { name: "Repository" })).toBeInTheDocument();
            expect(screen.getByRole("heading", { name: "Composition" })).toBeInTheDocument();
        });

        it.each([
            ["Technical issues", /^Technical issues/, "tech-issues"],
            ["Policy violations", /^Policy violations/, "policy-violations"],
            ["Vulnerabilities", /^Vulnerabilities/, "vulnerabilities"],
            ["Licenses", /^Licenses/, "licenses"],
        ])("opens the %s tab when its row is clicked", async (_label, name, tab) => {
            const onSelectTab = vi.fn();
            const user = userEvent.setup();
            render(<ResultsSummary onSelectTab={onSelectTab} webAppEvaluatedModel={model} />);

            await user.click(screen.getByRole("button", { name }));

            expect(onSelectTab).toHaveBeenCalledWith(tab);
        });

        it.each([
            // The button's accessible name glues the label to its value (e.g. "Projects4"), so match on the
            // label prefix only.
            ["Projects", /^Projects?/, ["project"]],
            ["Packages", /^Packages?/, null],
            ["Direct / transitive", /^Direct/, ["direct", "transitive"]],
        ])("filters the table on %s when its composition stat is clicked", async (_label, name, expected) => {
            const onFilterByLevel = vi.fn();
            const user = userEvent.setup();
            render(<ResultsSummary onFilterByLevel={onFilterByLevel} webAppEvaluatedModel={model} />);

            await user.click(screen.getByRole("button", { name }));

            expect(onFilterByLevel).toHaveBeenCalledWith(expected);
        });
    });

    describe("run verdict", () => {
        it("reports success when no findings are above the severe threshold", () => {
            render(<ResultsSummary webAppEvaluatedModel={fakeModel()} />);
            expect(screen.getByText("Run passed")).toBeInTheDocument();
            expect(screen.queryByText("Attention required")).not.toBeInTheDocument();
        });

        it("requires attention and links to the unresolved findings when the run fails", async () => {
            const onSelectTab = vi.fn();
            const user = userEvent.setup();
            render(
                <ResultsSummary
                    onSelectTab={onSelectTab}
                    webAppEvaluatedModel={fakeModel({
                        issuesTotal: 5,
                        openIssues: { errors: 3, hints: 0, total: 3, warnings: 0 },
                        severeOpenIssuesCount: 3,
                    })}
                />,
            );

            expect(screen.getByText("Attention required")).toBeInTheDocument();
            const cta = screen.getByRole("button", { name: /3 unresolved technical issues/i });
            await user.click(cta);
            expect(onSelectTab).toHaveBeenCalledWith("tech-issues");
        });
    });

    describe("severity badges", () => {
        it("shows one badge per open severity and hides those with a zero count", () => {
            render(
                <ResultsSummary
                    webAppEvaluatedModel={fakeModel({
                        issuesTotal: 5,
                        openIssues: { errors: 3, hints: 0, total: 5, warnings: 2 },
                        severeOpenIssuesCount: 3,
                        vulnerabilities: [
                            { isResolved: false, severityIndex: 0 },
                            { isResolved: false, severityIndex: 1 },
                        ],
                        openVulnerabilities: 2,
                    })}
                />,
            );

            // The count and severity word render together (e.g. "3 Errors"), with the full explanation as
            // the badge's title.
            expect(screen.getByTitle("3 open technical issues with severity error")).toHaveTextContent("3 Errors");
            expect(screen.getByTitle("2 open technical issues with severity warning")).toHaveTextContent("2 Warnings");
            expect(screen.getByTitle("1 open critical vulnerability")).toHaveTextContent("1 Critical");
            // Hints are zero, so their badge is omitted entirely.
            expect(screen.queryByTitle(/with severity hint/)).not.toBeInTheDocument();
        });

        it("shows no severity badges for a clean detector", () => {
            render(<ResultsSummary webAppEvaluatedModel={fakeModel()} />);
            expect(screen.queryByTitle(/with severity/)).not.toBeInTheDocument();
            expect(screen.queryByTitle(/vulnerabilit/)).not.toBeInTheDocument();
        });
    });

    describe("applied configuration", () => {
        it("is omitted when nothing was applied to the run", () => {
            render(<ResultsSummary webAppEvaluatedModel={fakeModel()} />);
            expect(screen.queryByRole("heading", { name: "Applied configuration" })).not.toBeInTheDocument();
        });

        it("lists the applied configuration and opens its Run Details tab on click", async () => {
            const onSelectRunDetailsTab = vi.fn();
            const user = userEvent.setup();
            render(
                <ResultsSummary
                    onSelectRunDetailsTab={onSelectRunDetailsTab}
                    webAppEvaluatedModel={fakeModel({
                        resolvedConfiguration: {
                            licenseChoices: 0,
                            packageConfigurations: 0,
                            packageCurations: 5,
                            resolutions: 0,
                        },
                    })}
                />,
            );

            expect(screen.getByRole("heading", { name: "Applied configuration" })).toBeInTheDocument();
            await user.click(screen.getByRole("button", { name: /package curations/ }));
            expect(onSelectRunDetailsTab).toHaveBeenCalledWith("package-curations");
        });
    });
});
