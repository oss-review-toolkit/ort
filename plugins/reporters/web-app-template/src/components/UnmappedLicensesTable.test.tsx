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

import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it, vi } from "vitest";

import { buildUnmappedLicenseRows, UnmappedLicensesTable } from "@/components/UnmappedLicensesTable";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppPackage from "@/models/WebAppPackage";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";
import { render } from "@/test/render";

describe("UnmappedLicensesTable", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    it("lists one row per package that declared something ORT could not map", () => {
        const rows = buildUnmappedLicenseRows(result.packages);
        expect(rows.length).toBeGreaterThan(0);

        const { container } = render(<UnmappedLicensesTable packages={result.packages} />);
        expect(container.querySelectorAll("tbody tr")).toHaveLength(rows.length);
    });

    it("shows the licenses exactly as they were declared", () => {
        const rows = buildUnmappedLicenseRows(result.packages);
        const [first] = rows;
        expect(first).toBeDefined();
        if (!first) return;

        const { container } = render(<UnmappedLicensesTable packages={result.packages} />);
        for (const license of first.licenses) {
            expect(container.textContent).toContain(license);
        }
    });

    it("leaves no package out: every package with an unmapped license has a row", () => {
        const expected = result.packages.filter((pkg) => pkg.declaredLicensesUnmapped.size > 0).length;
        expect(buildUnmappedLicenseRows(result.packages)).toHaveLength(expected);
    });

    it("opens the package in the results table when its id is clicked", async () => {
        const onSelectPackage = vi.fn();
        const user = userEvent.setup();
        const [first] = buildUnmappedLicenseRows(result.packages);
        expect(first).toBeDefined();
        if (!first) return;

        render(<UnmappedLicensesTable onSelectPackage={onSelectPackage} packages={result.packages} />);
        await user.click(screen.getByRole("button", { name: first.packageId }));

        expect(onSelectPackage).toHaveBeenCalledWith(first.packageId);
    });

    it("searches the licenses instead of offering the same one once per package", async () => {
        const user = userEvent.setup();
        render(<UnmappedLicensesTable packages={result.packages} />);

        // A faceted filter listed the shared license once for every package declaring it.
        expect(screen.queryByRole("button", { name: /^unmapped licenses$/i })).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: /search unmapped licenses/i }));
        expect(screen.getByRole("textbox", { name: /search unmapped licenses/i })).toBeInTheDocument();
    });

    it("offers neither a column visibility menu nor a global search, having two searched columns", () => {
        render(<UnmappedLicensesTable packages={result.packages} />);
        expect(screen.queryByRole("button", { name: /customize columns/i })).not.toBeInTheDocument();
        expect(screen.queryByRole("textbox", { name: /^search$/i })).not.toBeInTheDocument();
    });

    it("still offers a way out of a search once one is applied", async () => {
        const user = userEvent.setup();
        render(<UnmappedLicensesTable packages={result.packages} />);

        await user.click(screen.getByRole("button", { name: /search package/i }));
        await user.type(screen.getByRole("textbox", { name: /search package/i }), "nothing-matches-this{Enter}");

        // The toolbar is hidden until a filter is active, so removing the global search must not strand
        // the reader with a filter they cannot clear.
        expect(screen.getByRole("button", { name: /reset/i })).toBeInTheDocument();
    });

    it("marks whether each package is excluded", () => {
        render(<UnmappedLicensesTable packages={result.packages} />);
        expect(screen.getByRole("columnheader", { name: /included \/ excluded/i })).toBeInTheDocument();
    });

    it("strikes an excluded package through and explains why, as the packages table does", () => {
        const excluded = result.packages.find((pkg) => pkg.isExcluded && pkg.declaredLicensesUnmapped.size > 0);
        const included = result.packages.find((pkg) => !pkg.isExcluded && pkg.declaredLicensesUnmapped.size > 0);

        // The sample has only included packages here, so build the excluded case explicitly.
        const rows = buildUnmappedLicenseRows(result.packages);
        expect(rows.every((row) => row.isExcluded === false)).toBe(true);
        expect(included).toBeDefined();
        expect(excluded).toBeUndefined();

        // The model resolves the unmapped license indexes to names, so pass it in; without it the package
        // reports no unmapped licenses and never reaches the table.
        const pkg = new WebAppPackage(
            {
                _id: 0,
                id: "PyPI::excluded:1.0.0",
                declared_licenses_processed: { unmapped_licenses: [0] },
                is_excluded: true,
            },
            result,
        );
        expect(pkg.declaredLicensesUnmapped.size).toBeGreaterThan(0);
        const { container } = render(<UnmappedLicensesTable packages={[pkg]} />);
        expect(container.querySelector(".line-through")).toBeInTheDocument();
    });

    it("shows the empty state for a run where everything mapped", () => {
        render(<UnmappedLicensesTable packages={[]} />);
        expect(screen.getByText("No unmapped declared licenses")).toBeInTheDocument();
    });
});
