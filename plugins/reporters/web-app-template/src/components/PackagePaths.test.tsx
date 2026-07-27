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

import { PackagePaths } from "@/components/PackagePaths";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import type WebAppPath from "@/models/WebAppPath";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("PackagePaths", () => {
    let result: WebAppEvaluatedModel;
    let paths: readonly WebAppPath[];

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
        paths = result.packages.find((candidate) => (candidate.paths?.length ?? 0) > 0)?.paths ?? [];
    });

    it("has a package with dependency paths in the sample", () => {
        expect(paths.length).toBeGreaterThan(0);
    });

    it("renders the target package and its scope along the path", () => {
        const [first] = paths;
        expect(first).toBeDefined();
        if (!first) return;

        render(<PackagePaths paths={paths} />);
        if (first.packageId) expect(screen.getAllByText(first.packageId).length).toBeGreaterThan(0);
        if (first.scopeName) expect(screen.getByText(first.scopeName)).toBeInTheDocument();
    });

    it("numbers the steps from the project root down to the package", () => {
        render(<PackagePaths paths={paths} />);
        // Every path list starts at step "1".
        expect(screen.getAllByText("1").length).toBeGreaterThan(0);
    });

    it("shows the empty state when there are no paths", () => {
        render(<PackagePaths paths={[]} />);
        expect(screen.getByText("No paths.")).toBeInTheDocument();
    });
});
