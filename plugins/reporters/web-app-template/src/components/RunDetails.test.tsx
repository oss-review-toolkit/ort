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

import { RunDetails } from "@/components/RunDetails";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { buildResult, loadSampleEvaluatedModel } from "@/test/fixture";

describe("RunDetails", () => {
    let result: WebAppEvaluatedModel;

    beforeAll(async () => {
        result = await buildResult(loadSampleEvaluatedModel());
    });

    it("renders a tab per available run-configuration section", () => {
        render(<RunDetails webAppEvaluatedModel={result} />);
        // The sample report carries an .ort.yml and always exposes the Tools metadata.
        expect(screen.getByRole("tab", { name: ".ort.yml" })).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: /tools/i })).toBeInTheDocument();
    });

    it("opens the focused tab first when focusTab is set", () => {
        render(<RunDetails focusTab="tools" webAppEvaluatedModel={result} />);

        expect(screen.getByRole("tab", { name: /tools/i })).toHaveAttribute("aria-selected", "true");
        expect(screen.getByRole("tab", { name: ".ort.yml" })).toHaveAttribute("aria-selected", "false");
    });
});
