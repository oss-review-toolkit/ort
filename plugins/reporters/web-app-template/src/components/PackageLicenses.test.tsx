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

import { PackageLicenses } from "@/components/PackageLicenses";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppPackage from "@/models/WebAppPackage";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

const COPY_LICENSE = /copy .+ license/i;

describe("PackageLicenses", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    function packageWithLicenses(): WebAppPackage {
        const pkg = result.packages.find((candidate) => candidate.hasLicenses());
        if (!pkg) throw new Error("The sample has no package with licenses.");
        return pkg;
    }

    it("labels the license fields the package provides", () => {
        const pkg = packageWithLicenses();
        render(<PackageLicenses pkg={pkg} />);

        // hasLicenses() means at least one of the declared / detected fields is populated.
        const label = pkg.hasDeclaredLicenses() ? "Declared" : "Detected";
        expect(screen.getByText(label)).toBeInTheDocument();
        expect(screen.queryByText("No licenses.")).not.toBeInTheDocument();
    });

    it("offers a copy button for a copyable license field", () => {
        render(<PackageLicenses pkg={packageWithLicenses()} />);
        expect(screen.getAllByRole("button", { name: COPY_LICENSE }).length).toBeGreaterThan(0);
    });

    it("confirms the copy once its button is clicked", async () => {
        const user = userEvent.setup();
        render(<PackageLicenses pkg={packageWithLicenses()} />);

        const [copyButton] = screen.getAllByRole("button", { name: COPY_LICENSE });
        expect(copyButton).toBeDefined();
        if (!copyButton) return;

        await user.click(copyButton);
        expect(copyButton).toHaveAttribute("title", "Copied");
    });

    it("shows the empty state when the package has no licenses", () => {
        render(<PackageLicenses pkg={new WebAppPackage()} />);
        expect(screen.getByText("No licenses.")).toBeInTheDocument();
    });
});
