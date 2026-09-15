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

import { ALL_PACKAGES, LicenseChoicesTable } from "@/components/LicenseChoicesTable";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppLicenseChoices from "@/models/WebAppLicenseChoices";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";
import { render } from "@/test/render";

const PACKAGE_ID = "PyPI::six:1.17.0";

// One choice of each kind: repository-wide, and one targeting a single package.
const CHOICES = new WebAppLicenseChoices({
    repository_license_choices: [{ given: "GPL-2.0-only OR MIT", choice: "MIT" }],
    package_license_choices: [
        {
            package_id: PACKAGE_ID,
            license_choices: [
                { given: "MPL-2.0 OR EPL-1.0", choice: "MPL-2.0" },
                { given: "Apache-2.0 OR BSD-3-Clause", choice: "Apache-2.0" },
            ],
        },
    ],
});

describe("LicenseChoicesTable", () => {
    it("renders its column headers", () => {
        render(<LicenseChoicesTable licenseChoices={CHOICES} />);
        expect(screen.getByRole("columnheader", { name: /applies to/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /given/i })).toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: /choice/i })).toBeInTheDocument();
    });

    it("renders one row per choice, not one per package", () => {
        const { container } = render(<LicenseChoicesTable licenseChoices={CHOICES} />);
        // A package contributing two choices gets two rows; ORT's own statistic would count it once.
        expect(container.querySelectorAll("tbody tr")).toHaveLength(3);
    });

    it("marks repository-wide choices as applying to every package", () => {
        render(<LicenseChoicesTable licenseChoices={CHOICES} />);
        expect(screen.getByText(ALL_PACKAGES)).toBeInTheDocument();
    });

    it("shows the chosen license and the expression it was chosen from", () => {
        const { container } = render(<LicenseChoicesTable licenseChoices={CHOICES} />);
        expect(container.textContent).toContain("GPL-2.0-only");
        expect(container.textContent).toContain("MPL-2.0");
        expect(container.textContent).toContain("EPL-1.0");
    });

    it("links a package-specific choice to its package", async () => {
        const onSelectPackage = vi.fn();
        const user = userEvent.setup();
        render(<LicenseChoicesTable licenseChoices={CHOICES} onSelectPackage={onSelectPackage} />);

        await user.click(screen.getAllByRole("button", { name: PACKAGE_ID })[0] as HTMLElement);

        expect(onSelectPackage).toHaveBeenCalledWith(PACKAGE_ID);
    });

    it("leaves the package id unlinked when there is nowhere to navigate", () => {
        render(<LicenseChoicesTable licenseChoices={CHOICES} />);
        expect(screen.queryByRole("button", { name: PACKAGE_ID })).not.toBeInTheDocument();
        // The package owns two choices, so its id appears once per row - as plain text, not a link.
        expect(screen.getAllByText(PACKAGE_ID)).toHaveLength(2);
    });

    it("hides the pagination controls while every choice fits on one page", () => {
        render(<LicenseChoicesTable licenseChoices={CHOICES} />);
        expect(screen.queryByText("Rows per page")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /go to next page/i })).not.toBeInTheDocument();
    });

    it("still paginates once the choices outgrow a single page", () => {
        // The first LARGE_TABLE_PAGE_SIZES option is 50, so 51 choices force a second page.
        const many = new WebAppLicenseChoices({
            repository_license_choices: Array.from({ length: 51 }, (_, index) => ({
                given: `LicenseRef-choice-${index} OR MIT`,
                choice: "MIT",
            })),
        });

        render(<LicenseChoicesTable licenseChoices={many} />);
        expect(screen.getByText("Rows per page")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /go to next page/i })).toBeInTheDocument();
    });

    it("shows the empty state when the run applied no license choices", () => {
        render(<LicenseChoicesTable licenseChoices={new WebAppLicenseChoices()} />);
        expect(screen.getByText("No license choices")).toBeInTheDocument();
    });

    describe("against the sample model", () => {
        let result: WebAppEvaluatedModel;

        beforeAll(async () => {
            result = await buildResult(loadSampleEvaluatedModel());
        });

        it("renders a row for every applicable choice the sample carries", () => {
            expect(result.hasLicenseChoices()).toBe(true);

            const { container } = render(<LicenseChoicesTable licenseChoices={result.licenseChoices} />);
            // The Summary pill shows this same count, so the two cannot drift apart.
            expect(container.querySelectorAll("tbody tr")).toHaveLength(result.licenseChoicesCount);
        });
    });
});
