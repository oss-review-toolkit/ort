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

import { describe, expect, it, vi } from "vitest";

import {
    flattenTree,
    getProjectAndScopeKeys,
    searchTree,
    type TreeRoot,
    type TreeSearchResult,
} from "@/lib/dependencyTree";
import type WebAppTreeNode from "@/models/WebAppTreeNode";

interface FakeNode {
    children: FakeNode[];
    isProject: boolean;
    isScope: boolean;
    key: string | undefined;
    title: string;
}

function fakeNode(title: string, children: FakeNode[] = [], options: Partial<FakeNode> = {}): FakeNode {
    return { children, isProject: false, isScope: false, key: title, title, ...options };
}

// app > dependencies > lib-a > lib-b, and app > test > lib-b, with lib-b occurring twice.
function sampleRoots(): TreeRoot[] {
    const app = fakeNode(
        "app",
        [
            fakeNode("dependencies", [fakeNode("lib-a", [fakeNode("lib-b", [], { key: "lib-b-1" })])], {
                isScope: true,
            }),
            fakeNode("test", [fakeNode("lib-b", [], { key: "lib-b-2" })], { isScope: true }),
        ],
        { isProject: true },
    );
    return [{ fallbackKey: "root", node: app as unknown as WebAppTreeNode }];
}

function search(roots: readonly TreeRoot[], query: string): TreeSearchResult {
    let result: TreeSearchResult | undefined;
    searchTree(roots, query, (r) => {
        result = r;
    });
    if (!result) throw new Error("The search did not complete synchronously.");
    return result;
}

describe("getProjectAndScopeKeys", () => {
    it("returns the project and scope keys without descending into packages", () => {
        const roots = sampleRoots();
        const libA = roots[0]?.node.children[0]?.children[0] as unknown as FakeNode;
        const children = vi.fn(() => []);
        Object.defineProperty(libA, "children", { get: children });

        expect(getProjectAndScopeKeys(roots)).toEqual(new Set(["app", "dependencies", "test"]));
        expect(children).not.toHaveBeenCalled();
    });
});

describe("flattenTree", () => {
    it("lists the open part of the tree in display order with depths", () => {
        const rows = flattenTree(sampleRoots(), new Set(["app", "dependencies", "test"]), null);

        expect(rows.map((row) => [row.key, row.depth, row.isOpen])).toEqual([
            ["app", 0, true],
            ["dependencies", 1, true],
            ["lib-a", 2, false],
            ["test", 1, true],
            ["lib-b-2", 2, false],
        ]);
    });

    it("keeps only the visible nodes while searching", () => {
        const rows = flattenTree(sampleRoots(), new Set(["app", "test"]), new Set(["app", "test", "lib-b-2"]));

        expect(rows.map((row) => row.key)).toEqual(["app", "test", "lib-b-2"]);
    });

    it("keys nodes without a key by their position below the root", () => {
        const roots: TreeRoot[] = [
            {
                fallbackKey: "root",
                node: fakeNode("app", [fakeNode("lib", [], { key: undefined })], {
                    key: undefined,
                }) as unknown as WebAppTreeNode,
            },
        ];

        expect(flattenTree(roots, new Set(["root"]), null).map((row) => row.key)).toEqual(["root", "root-0"]);
    });
});

describe("searchTree", () => {
    it("finds every occurrence, ignoring case, and opens the paths to them", () => {
        const result = search(sampleRoots(), "LIB-B");

        expect(result.matches.map((match) => match.key)).toEqual(["lib-b-1", "lib-b-2"]);
        expect(result.visible).toEqual(new Set(["app", "dependencies", "lib-a", "lib-b-1", "test", "lib-b-2"]));
        expect(result.expanded).toEqual(new Set(["app", "dependencies", "lib-a", "test"]));
    });

    it("finds nothing for a query no title contains", () => {
        const result = search(sampleRoots(), "does-not-exist");

        expect(result.matches).toEqual([]);
        expect(result.visible.size).toBe(0);
    });

    it("yields to the browser between chunks on a large tree", () => {
        const libs = Array.from({ length: 5000 }, (_, i) => fakeNode(`lib-${i}`));
        const roots: TreeRoot[] = [
            {
                fallbackKey: "root",
                node: fakeNode("app", [fakeNode("dependencies", libs, { isScope: true })], {
                    isProject: true,
                }) as unknown as WebAppTreeNode,
            },
        ];
        // Every look at the clock is past the deadline, so each chunk stops at its first clock check.
        let time = 0;
        const now = (): number => (time += 100);
        const tasks: (() => void)[] = [];
        const onComplete = vi.fn();

        searchTree(roots, "lib-4999", onComplete, {
            now,
            schedule: (task) => {
                tasks.push(task);
                return () => {};
            },
        });

        expect(onComplete).not.toHaveBeenCalled();
        let chunks = 1;
        for (let task = tasks.shift(); task; task = tasks.shift()) {
            task();
            chunks++;
        }

        expect(chunks).toBeGreaterThan(1);
        expect(onComplete).toHaveBeenCalledTimes(1);
        expect(onComplete.mock.calls[0]?.[0].matches.map((match: { key: string }) => match.key)).toEqual(["lib-4999"]);
    });

    it("does not report the result of a cancelled search", () => {
        const libs = Array.from({ length: 5000 }, (_, i) => fakeNode(`lib-${i}`));
        const roots: TreeRoot[] = [{ fallbackKey: "root", node: fakeNode("app", libs) as unknown as WebAppTreeNode }];
        let time = 0;
        const tasks: (() => void)[] = [];
        const onComplete = vi.fn();

        const cancel = searchTree(roots, "lib", onComplete, {
            now: () => (time += 100),
            schedule: (task) => {
                tasks.push(task);
                return () => tasks.splice(tasks.indexOf(task), 1);
            },
        });
        cancel();

        expect(tasks).toEqual([]);
        expect(onComplete).not.toHaveBeenCalled();
    });
});
