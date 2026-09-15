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

import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi as vitest } from "vitest";

import { ResultsTree } from "@/components/ResultsTree";
import WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { buildResult } from "@/test/fixture";
import type { EvaluatedModel } from "@/types/evaluatedModelData";

const APP = "Maven:com.example:app:1.0.0";
const LIB_A = "NPM:@ns:lib-a:1.0.0";
const LIB_B = "NPM:@ns:lib-b:2.0.0";
const EXCLUDED_LIB = "NPM:@ns:excluded-lib:3.0.0";

// A tiny project with one scope holding a direct dependency (lib-a) that pulls in a transitive one
// (lib-b), plus an excluded devDependencies scope holding excluded-lib.
const RAW: EvaluatedModel = {
    packages: [
        { _id: 0, id: APP, is_project: true, definition_file_path: "pom.xml", purl: "pkg:maven/com.example/app@1.0.0" },
        { _id: 1, id: LIB_A, purl: "pkg:npm/%40ns/lib-a@1.0.0" },
        { _id: 2, id: LIB_B, purl: "pkg:npm/%40ns/lib-b@2.0.0" },
        { _id: 3, id: EXCLUDED_LIB, purl: "pkg:npm/%40ns/excluded-lib@3.0.0" },
    ],
    scopes: [
        { _id: 0, name: "dependencies" },
        { _id: 1, name: "devDependencies" },
    ],
    scope_excludes: [{ _id: 0, name: "devDependencies", reason: "DEV_DEPENDENCY_OF" }],
    dependency_trees: [
        {
            key: 0,
            pkg: 0,
            children: [
                {
                    key: 1,
                    scope: 0,
                    children: [{ key: 2, pkg: 1, children: [{ key: 3, pkg: 2, children: [] }] }],
                },
                {
                    key: 4,
                    scope: 1,
                    scope_excludes: [0],
                    children: [{ key: 5, pkg: 3, children: [] }],
                },
            ],
        },
    ],
};

const PLACEHOLDER = "Select a package in the tree to view its details.";

function searchBox(): HTMLElement {
    return screen.getByRole("textbox", { name: /search the dependency tree/i });
}

describe("ResultsTree", () => {
    let result: WebAppEvaluatedModel;

    beforeEach(async () => {
        result = await buildResult(RAW);
    });

    it("expands projects and scopes by default, showing direct but not transitive dependencies", () => {
        render(<ResultsTree webAppEvaluatedModel={result} />);

        expect(screen.getByRole("button", { name: APP })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "dependencies" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: LIB_A })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: EXCLUDED_LIB })).toBeInTheDocument();
        // lib-b is a transitive dependency of lib-a, which is collapsed by default.
        expect(screen.queryByRole("button", { name: LIB_B })).not.toBeInTheDocument();
    });

    it("filters the tree to the matching path when searching", async () => {
        const user = userEvent.setup();
        render(<ResultsTree webAppEvaluatedModel={result} />);

        await user.type(searchBox(), "lib-b");

        expect(await screen.findByRole("button", { name: LIB_B })).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: EXCLUDED_LIB })).not.toBeInTheDocument();
        expect(screen.getByText("1/1")).toBeInTheDocument();
    });

    it("shows a match counter and steps through results", async () => {
        const user = userEvent.setup();
        render(<ResultsTree webAppEvaluatedModel={result} />);

        await user.type(searchBox(), "lib");

        expect(screen.getByText("1/3")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: /next match/i }));
        expect(screen.getByText("2/3")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: /previous match/i }));
        expect(screen.getByText("1/3")).toBeInTheDocument();
    });

    it("reports when nothing matches the query", async () => {
        const user = userEvent.setup();
        render(<ResultsTree webAppEvaluatedModel={result} />);

        await user.type(searchBox(), "does-not-exist");

        expect(await screen.findByText("No matching packages.")).toBeInTheDocument();
        expect(screen.getByText("0/0")).toBeInTheDocument();
    });

    it("shows package details on the right when a node is selected", async () => {
        const user = userEvent.setup();
        render(<ResultsTree webAppEvaluatedModel={result} />);

        expect(screen.getByText(PLACEHOLDER)).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: LIB_A }));

        expect(screen.queryByText(PLACEHOLDER)).not.toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "Overview" })).toBeInTheDocument();
    });

    it("shows a building placeholder until the deferred tree construction finishes, then the tree", () => {
        vitest.useFakeTimers({ loopLimit: 5_000_000 });
        try {
            // A freshly constructed model has only scheduled its (deferred) tree-build tasks, so it is not
            // ready to render yet — this is the reload-with-#tree state that used to show an empty tree.
            const model = new WebAppEvaluatedModel(RAW);
            expect(model.dependencyTreesReady).toBe(false);

            render(<ResultsTree webAppEvaluatedModel={model} />);

            expect(screen.getByText("Building dependency tree…")).toBeInTheDocument();
            expect(screen.queryByRole("button", { name: APP })).not.toBeInTheDocument();

            // Draining the build tasks flips the model to ready, which re-renders and mounts the real view.
            act(() => {
                vitest.runAllTimers();
            });

            expect(screen.queryByText("Building dependency tree…")).not.toBeInTheDocument();
            expect(screen.getByRole("button", { name: APP })).toBeInTheDocument();
            expect(screen.getByRole("button", { name: LIB_A })).toBeInTheDocument();
        } finally {
            vitest.useRealTimers();
        }
    });
});
