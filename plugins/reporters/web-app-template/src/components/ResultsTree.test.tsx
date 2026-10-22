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

import { act, fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi as vitest } from "vitest";
import { ResultsTree } from "@/components/ResultsTree";
import WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import { buildResult } from "@/test/fixture";
import { render } from "@/test/render";
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
    scope_excludes: [{ _id: 0, pattern: "devDependencies", reason: "DEV_DEPENDENCY_OF" }],
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

const LARGE_TREE_SIZE = 3000;

// A project with one scope holding many direct dependencies, so the default expansion shows them all.
function largeModel(): EvaluatedModel {
    const libs = Array.from({ length: LARGE_TREE_SIZE }, (_, i) => ({
        _id: i + 1,
        id: `NPM:@ns:lib-${i}:1.0.0`,
        purl: `pkg:npm/%40ns/lib-${i}@1.0.0`,
    }));
    return {
        packages: [{ _id: 0, id: APP, is_project: true, definition_file_path: "pom.xml" }, ...libs],
        scopes: [{ _id: 0, name: "dependencies" }],
        dependency_trees: [
            {
                key: 0,
                pkg: 0,
                children: [
                    {
                        key: 1,
                        scope: 0,
                        children: libs.map((lib) => ({ key: lib._id + 1, pkg: lib._id, children: [] })),
                    },
                ],
            },
        ],
    };
}

function mountedRows(): HTMLElement[] {
    return Array.from(document.querySelectorAll<HTMLElement>("[data-tree-row]"));
}

function searchBox(): HTMLElement {
    return screen.getByRole("textbox", { name: /search the dependency tree/i });
}

// The search waits for a pause in typing and then runs in deferred chunks. The search tests use fake timers to drive
// both, so a slow or busy machine cannot run out the time that a findBy query waits for the result. User events
// wait on real timers, so the query is entered with a plain change event.
function enterQuery(query: string): void {
    fireEvent.change(searchBox(), { target: { value: query } });
}

async function finishSearch(): Promise<void> {
    await act(() => vitest.runAllTimersAsync());
}

describe("ResultsTree", () => {
    let result: WebAppEvaluatedModel;

    beforeEach(async () => {
        result = await buildResult(RAW);
    });

    afterEach(() => {
        vitest.useRealTimers();
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
        vitest.useFakeTimers();
        render(<ResultsTree webAppEvaluatedModel={result} />);

        enterQuery("lib-b");
        await finishSearch();

        expect(screen.getByRole("button", { name: LIB_B })).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: EXCLUDED_LIB })).not.toBeInTheDocument();
        expect(screen.getByText("1/1")).toBeInTheDocument();
    });

    it("shows a match counter and steps through results", async () => {
        vitest.useFakeTimers();
        render(<ResultsTree webAppEvaluatedModel={result} />);

        enterQuery("lib");
        await finishSearch();

        expect(screen.getByText("1/3")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: /next match/i }));
        expect(screen.getByText("2/3")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: /previous match/i }));
        expect(screen.getByText("1/3")).toBeInTheDocument();
    });

    it("reports when nothing matches the query", async () => {
        vitest.useFakeTimers();
        render(<ResultsTree webAppEvaluatedModel={result} />);

        enterQuery("does-not-exist");
        await finishSearch();

        expect(screen.getByText("No matching packages.")).toBeInTheDocument();
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

    it("applies the query only after typing pauses", async () => {
        vitest.useFakeTimers();
        render(<ResultsTree webAppEvaluatedModel={result} />);

        enterQuery("lib-b");

        expect(screen.getByText("Searching…")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: LIB_B })).not.toBeInTheDocument();

        await finishSearch();

        expect(screen.getByRole("button", { name: LIB_B })).toBeInTheDocument();
        expect(screen.getByText("1/1")).toBeInTheDocument();
    });

    describe("with a large tree", () => {
        const ROW_HEIGHT = 28;
        const offsetHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "offsetHeight");

        beforeEach(() => {
            Object.defineProperty(HTMLElement.prototype, "offsetHeight", {
                configurable: true,
                get(this: HTMLElement): number {
                    return this.hasAttribute("data-tree-row") ? ROW_HEIGHT : 0;
                },
            });
        });

        afterEach(() => {
            if (offsetHeight) Object.defineProperty(HTMLElement.prototype, "offsetHeight", offsetHeight);
        });

        it("mounts only the rows near the viewport", async () => {
            render(<ResultsTree webAppEvaluatedModel={await buildResult(largeModel())} />);

            // The test setup gives the tree pane a 600 px viewport; the virtualizer adds 10 rows of overscan.
            const rows = mountedRows();
            expect(rows.length).toBeGreaterThan(0);
            expect(rows.length).toBeLessThanOrEqual(Math.ceil(600 / ROW_HEIGHT) + 2 * 10 + 1);
            expect(screen.getByRole("button", { name: APP })).toBeInTheDocument();
            expect(screen.queryByRole("button", { name: `NPM:@ns:lib-${LARGE_TREE_SIZE - 1}:1.0.0` })).toBeNull();
        });

        it("keeps the focused row mounted when it is scrolled out of view", async () => {
            render(<ResultsTree webAppEvaluatedModel={await buildResult(largeModel())} />);
            const firstLib = screen.getByRole("button", { name: "NPM:@ns:lib-0:1.0.0" });
            const pane = document.querySelector<HTMLElement>("[data-tree-scroll]");
            if (!pane) throw new Error("The tree pane is missing.");

            act(() => {
                firstLib.focus();
            });
            act(() => {
                Object.defineProperty(pane, "scrollTop", { configurable: true, value: 1000 * ROW_HEIGHT });
                pane.dispatchEvent(new Event("scroll"));
            });

            expect(screen.getByRole("button", { name: "NPM:@ns:lib-1000:1.0.0" })).toBeInTheDocument();
            expect(screen.queryByRole("button", { name: "NPM:@ns:lib-1:1.0.0" })).toBeNull();
            expect(firstLib).toBeInTheDocument();
            expect(firstLib).toHaveFocus();
        });

        it("mounts the matching row when searching for a dependency far down the tree", async () => {
            const model = await buildResult(largeModel());
            vitest.useFakeTimers();
            render(<ResultsTree webAppEvaluatedModel={model} />);

            enterQuery(`lib-${LARGE_TREE_SIZE - 1}:`);
            await finishSearch();

            expect(
                screen.getByRole("button", { name: `NPM:@ns:lib-${LARGE_TREE_SIZE - 1}:1.0.0` }),
            ).toBeInTheDocument();
            expect(mountedRows()).toHaveLength(3);
        });
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
