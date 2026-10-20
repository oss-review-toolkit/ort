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

import type WebAppTreeNode from "@/models/WebAppTreeNode";

// A root of the dependency tree with the key its descendants derive their fallback keys from.
export interface TreeRoot {
    fallbackKey: string;
    node: WebAppTreeNode;
}

// One displayed line of the dependency tree: a node occurrence at its depth below the roots.
export interface TreeRow {
    depth: number;
    hasChildren: boolean;
    isOpen: boolean;
    key: string;
    node: WebAppTreeNode;
}

export interface TreeMatch {
    key: string;
    node: WebAppTreeNode;
}

export interface TreeSearchResult {
    // The nodes on a path to a match, which are opened while searching.
    expanded: ReadonlySet<string>;
    // The matching node occurrences in display (pre-order) order.
    matches: readonly TreeMatch[];
    query: string;
    // The keys of the matches and of all nodes on a path to one.
    visible: ReadonlySet<string>;
}

export interface TreeSearchOptions {
    // The longest a single chunk of the search may block the main thread.
    chunkTimeMs?: number;
    now?: () => number;
    schedule?: (task: () => void) => () => void;
}

const CHUNK_TIME_MS = 8;

// How many nodes are visited between two looks at the clock.
const CLOCK_CHECK_INTERVAL = 512;

function scheduleWithTimeout(task: () => void): () => void {
    const handle = setTimeout(task, 0);
    return () => clearTimeout(handle);
}

// Nodes without a key of their own are keyed by their position below the root, as in "<root key>-0-2".
export function nodeKey(node: WebAppTreeNode, fallbackKey: string): string {
    return node.key ?? fallbackKey;
}

// The project and scope nodes, which are expanded by default so the direct dependencies are visible. Package
// nodes are not descended into, so the cost does not grow with the number of dependencies.
export function getProjectAndScopeKeys(roots: readonly TreeRoot[]): Set<string> {
    const keys = new Set<string>();
    const stack = [...roots];

    for (let entry = stack.pop(); entry; entry = stack.pop()) {
        const { fallbackKey, node } = entry;
        if (!node.isProject && !node.isScope) continue;

        keys.add(nodeKey(node, fallbackKey));
        node.children.forEach((child, index) => {
            stack.push({ fallbackKey: `${fallbackKey}-${index}`, node: child });
        });
    }

    return keys;
}

// Flatten the open part of the tree into rows in display order. Only open nodes are descended into, and while
// searching only the visible ones are kept, so the cost is bounded by the number of displayed rows.
export function flattenTree(
    roots: readonly TreeRoot[],
    expanded: ReadonlySet<string>,
    visible: ReadonlySet<string> | null,
): TreeRow[] {
    const rows: TreeRow[] = [];
    const stack: { depth: number; fallbackKey: string; node: WebAppTreeNode }[] = [];

    for (let i = roots.length - 1; i >= 0; i--) {
        const root = roots[i];
        if (root) stack.push({ depth: 0, fallbackKey: root.fallbackKey, node: root.node });
    }

    for (let entry = stack.pop(); entry; entry = stack.pop()) {
        const { depth, fallbackKey, node } = entry;
        const key = nodeKey(node, fallbackKey);
        if (visible && !visible.has(key)) continue;

        const hasChildren = node.children.length > 0;
        const isOpen = hasChildren && expanded.has(key);
        rows.push({ depth, hasChildren, isOpen, key, node });

        if (!isOpen) continue;

        for (let i = node.children.length - 1; i >= 0; i--) {
            const child = node.children[i];
            if (child) stack.push({ depth: depth + 1, fallbackKey: `${fallbackKey}-${i}`, node: child });
        }
    }

    return rows;
}

interface SearchFrame {
    childIndex: number;
    childMatch: boolean;
    fallbackKey: string;
    key: string;
    node: WebAppTreeNode;
    selfMatch: boolean;
}

// Search all node occurrences for titles containing the query, ignoring case. The tree is walked in chunks that
// each block the main thread for at most a few milliseconds, so typing and scrolling stay responsive on trees
// with millions of occurrences. Returns a function that cancels the search; onComplete is not called afterwards.
export function searchTree(
    roots: readonly TreeRoot[],
    query: string,
    onComplete: (result: TreeSearchResult) => void,
    {
        chunkTimeMs = CHUNK_TIME_MS,
        now = () => performance.now(),
        schedule = scheduleWithTimeout,
    }: TreeSearchOptions = {},
): () => void {
    const needle = query.toLowerCase();
    const expanded = new Set<string>();
    const matches: TreeMatch[] = [];
    const visible = new Set<string>();

    // The same package occurs many times in a tree, so match each distinct title only once.
    const titleMatches = new Map<string, boolean>();
    const matchesTitle = (title: string): boolean => {
        let result = titleMatches.get(title);
        if (result === undefined) {
            result = title.toLowerCase().includes(needle);
            titleMatches.set(title, result);
        }
        return result;
    };

    const stack: SearchFrame[] = [];
    let rootIndex = 0;
    let cancelled = false;
    let cancelTask: (() => void) | undefined;

    const enter = (node: WebAppTreeNode, fallbackKey: string): void => {
        const key = nodeKey(node, fallbackKey);
        const selfMatch = matchesTitle(node.title ?? "");
        if (selfMatch) matches.push({ key, node });
        stack.push({ childIndex: 0, childMatch: false, fallbackKey, key, node, selfMatch });
    };

    const runChunk = (): void => {
        cancelTask = undefined;
        const deadline = now() + chunkTimeMs;
        let visited = 0;

        while (stack.length > 0 || rootIndex < roots.length) {
            if (++visited % CLOCK_CHECK_INTERVAL === 0 && now() > deadline) {
                cancelTask = schedule(runChunk);
                return;
            }

            const frame = stack[stack.length - 1];
            if (!frame) {
                const root = roots[rootIndex++];
                if (root) enter(root.node, root.fallbackKey);
                continue;
            }

            const child = frame.node.children[frame.childIndex];
            if (child) {
                enter(child, `${frame.fallbackKey}-${frame.childIndex}`);
                frame.childIndex++;
                continue;
            }

            stack.pop();
            if (frame.selfMatch || frame.childMatch) visible.add(frame.key);
            if (frame.childMatch) expanded.add(frame.key);

            const parent = stack[stack.length - 1];
            if (parent && (frame.selfMatch || frame.childMatch)) parent.childMatch = true;
        }

        if (!cancelled) onComplete({ expanded, matches, query, visible });
    };

    runChunk();

    return () => {
        cancelled = true;
        cancelTask?.();
        cancelTask = undefined;
    };
}
