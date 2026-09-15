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

import { PackageScanResultsDetails } from "@/components/PackageScanResultsDetails";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppPackage from "@/models/WebAppPackage";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("PackageScanResultsDetails", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    function packageWithScanResults(): WebAppPackage {
        const pkg = result.packages.find((candidate) =>
            candidate.scanResults.some((scanResult) => Boolean(scanResult?.scanner?.name)),
        );
        if (!pkg) throw new Error("The sample has no package with a named scanner in its scan results.");
        return pkg;
    }

    it("labels the scanner that produced the results", () => {
        render(<PackageScanResultsDetails pkg={packageWithScanResults()} />);
        expect(screen.getByText("Scanner")).toBeInTheDocument();
    });

    it("renders the name of a scanner from the package's scan results", () => {
        const pkg = packageWithScanResults();
        const scannerName = pkg.scanResults.find((scanResult) => scanResult?.scanner?.name)?.scanner?.name;
        expect(scannerName).toBeTruthy();

        render(<PackageScanResultsDetails pkg={pkg} />);
        if (scannerName) expect(screen.getAllByText(scannerName).length).toBeGreaterThan(0);
    });

    it("shows the empty state when the package has no scan results", () => {
        render(<PackageScanResultsDetails pkg={new WebAppPackage()} />);
        expect(screen.getByText("No scan results.")).toBeInTheDocument();
    });
});
