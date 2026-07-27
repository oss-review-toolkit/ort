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
import { describe, expect, it, vi } from "vitest";

import { type LicenseStatRow, LicenseStatsTable } from "@/components/LicenseStatsTable";

const licenseStats: readonly LicenseStatRow[] = [
    { color: "#111111", name: "Apache-2.0", value: 5 },
    { color: "#222222", name: "BSD-3-Clause", value: 1 },
    { color: "#333333", name: "MIT", value: 3 },
];

describe("LicenseStatsTable", () => {
    it("renders a row for every license", () => {
        render(<LicenseStatsTable licenseStats={licenseStats} />);
        expect(screen.getByText("Apache-2.0")).toBeInTheDocument();
        expect(screen.getByText("BSD-3-Clause")).toBeInTheDocument();
        expect(screen.getByText("MIT")).toBeInTheDocument();
    });

    it("calls handleClick with the license when its badge is activated", async () => {
        const handleClick = vi.fn();
        const user = userEvent.setup();
        render(<LicenseStatsTable handleClick={handleClick} licenseStats={licenseStats} />);

        await user.click(screen.getByRole("button", { name: "MIT" }));

        expect(handleClick).toHaveBeenCalledTimes(1);
        expect(handleClick).toHaveBeenCalledWith("MIT");
    });

    it("shows the default empty state when there are no licenses", () => {
        render(<LicenseStatsTable licenseStats={[]} />);
        expect(screen.getByText("No licenses")).toBeInTheDocument();
    });

    it("shows a custom empty state when one is provided", () => {
        render(<LicenseStatsTable emptyText="Nothing to report" licenseStats={[]} />);
        expect(screen.getByText("Nothing to report")).toBeInTheDocument();
    });
});
