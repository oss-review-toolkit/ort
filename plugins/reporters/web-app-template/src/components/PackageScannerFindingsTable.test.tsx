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
import { beforeAll, describe, expect, it } from "vitest";

import { PackageScannerFindingsTable } from "@/components/PackageScannerFindingsTable";
import type WebAppFinding from "@/models/WebAppFinding";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

const RESET = /^reset$/i;

describe("PackageScannerFindingsTable", () => {
    let scannerFindings: readonly WebAppFinding[];

    beforeAll(async () => {
        const result = await buildResult(loadSampleEvaluatedModel());
        const pkg = result.packages.find((candidate) =>
            candidate.findings.some((finding) => finding.type === "LICENSE" && finding.license),
        );
        scannerFindings = pkg?.findings ?? [];
    });

    it("has license findings available in the sample", () => {
        expect(scannerFindings.length).toBeGreaterThan(0);
    });

    it("offers a reset button once a filter is applied and clears it on click", async () => {
        const user = userEvent.setup();
        render(<PackageScannerFindingsTable scannerFindings={scannerFindings} />);

        // Nothing filtered yet, so there is nothing to reset.
        expect(screen.queryByRole("button", { name: RESET })).not.toBeInTheDocument();

        // Apply the License column filter by choosing its first option.
        await user.click(screen.getByRole("button", { name: /filter by license/i }));
        const [firstOption] = await screen.findAllByRole("option");
        expect(firstOption).toBeDefined();
        if (firstOption) {
            await user.click(firstOption);
        }

        // The reset button now appears and removes the filter again when clicked.
        const reset = await screen.findByRole("button", { name: RESET });
        await user.click(reset);
        expect(screen.queryByRole("button", { name: RESET })).not.toBeInTheDocument();
    });
});
