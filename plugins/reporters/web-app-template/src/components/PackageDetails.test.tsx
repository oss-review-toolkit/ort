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

import { PackageDetails } from "@/components/PackageDetails";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppPackage from "@/models/WebAppPackage";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("PackageDetails", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    it("renders the identity of a realistic package", () => {
        const pkg = result.packages[0];
        expect(pkg).toBeDefined();
        if (!pkg) return;

        render(<PackageDetails pkg={pkg} />);
        expect(screen.getByText("Id")).toBeInTheDocument();
        expect(screen.getByText(pkg.id ?? "")).toBeInTheDocument();
    });

    it("lays every rendered field out as a definition-list row", () => {
        const pkg = result.packages[0];
        if (!pkg) return;

        const { container } = render(<PackageDetails pkg={pkg} />);
        // Every field is emitted as a <dt>/<dd> pair; a realistic package has at least the Id term.
        expect(container.querySelectorAll("dt").length).toBeGreaterThan(0);
        expect(container.querySelectorAll("dd").length).toBeGreaterThan(0);
    });

    it("does not fall back to the empty state for a realistic package", () => {
        const pkg = result.packages[0];
        if (!pkg) return;

        render(<PackageDetails pkg={pkg} />);
        expect(screen.queryByText("No package details.")).not.toBeInTheDocument();
    });

    it("shows the empty state when the package carries no details", () => {
        render(<PackageDetails pkg={new WebAppPackage()} />);
        expect(screen.getByText("No package details.")).toBeInTheDocument();
    });
});
