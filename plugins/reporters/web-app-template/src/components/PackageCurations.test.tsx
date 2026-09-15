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

import { render } from "@testing-library/react";
import { beforeAll, describe, expect, it } from "vitest";

import { PackageCurations } from "@/components/PackageCurations";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppPackage from "@/models/WebAppPackage";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("PackageCurations", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    function packageWithCurations(): WebAppPackage {
        const pkg = result.packages.find((candidate) => candidate.hasCurations());
        if (!pkg) throw new Error("The sample has no package with curations.");
        return pkg;
    }

    it("renders the applied curations as a syntax-highlighted block", () => {
        const { container } = render(<PackageCurations pkg={packageWithCurations()} />);
        expect(container.querySelector("pre")).not.toBeNull();
    });

    it("shows the curation content", () => {
        const { container } = render(<PackageCurations pkg={packageWithCurations()} />);
        // Every curation entry nests its corrections under a "curations" key.
        expect(container.textContent ?? "").toMatch(/curations/);
    });

    it("renders nothing when the package has no curations", () => {
        const { container } = render(<PackageCurations pkg={new WebAppPackage()} />);
        expect(container.firstChild).toBeNull();
    });
});
