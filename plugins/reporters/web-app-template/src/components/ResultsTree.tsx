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

import { defaultRangeExtractor, useVirtualizer } from "@tanstack/react-virtual";
import { ChevronLeft, ChevronRight, FileX2, Network, Package, Search } from "lucide-react";
import type { JSX, ReactNode } from "react";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { PackageDetailPanel } from "@/components/ResultsTable";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import {
    flattenTree,
    getProjectAndScopeKeys,
    nodeKey,
    searchTree,
    type TreeRoot,
    type TreeRow,
    type TreeSearchResult,
} from "@/lib/dependencyTree";
import { cn } from "@/lib/utils";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import type WebAppTreeNode from "@/models/WebAppTreeNode";
import { randomStringGenerator } from "@/utils";

export interface ResultsTreeProps {
    className?: string;
    // When set, select this package (first occurrence) in the tree on mount and reveal/scroll to it.
    focusPackageId?: string;
    // Deep-linked inner package-detail tab to open for the selected package.
    focusPackageTab?: string;
    // Fired with the selected package's id so the URL can deep-link it.
    onFocusPackageChange?: (packageId: string | null) => void;
    // Fired when the selected package's inner detail tab changes.
    onFocusPackageTabChange?: (tab: string | null) => void;
    webAppEvaluatedModel: WebAppEvaluatedModel;
}

// How long typing has to pause before the query is applied to the tree.
const SEARCH_DEBOUNCE_MS = 300;

const ESTIMATED_ROW_HEIGHT_PX = 28;

const OVERSCAN = 10;

// The vertical padding of the tree pane, applied by the virtualizer so it is part of the scrolled height.
const PANE_PADDING_PX = 8;

interface TreeRowItemProps {
    depth: number;
    hasChildren: boolean;
    index: number;
    isOpen: boolean;
    isSelected: boolean;
    measureRef: (element: HTMLElement | null) => void;
    node: WebAppTreeNode;
    onSelect: (key: string, node: WebAppTreeNode) => void;
    onToggle: (key: string, open: boolean) => void;
    rowKey: string;
    searchValue: string;
    start: number;
}

function highlight(title: string, searchValue: string): ReactNode {
    if (!searchValue) {
        return title;
    }

    const lowerTitle = title.toLowerCase();
    const lowerSearch = searchValue.toLowerCase();
    const index = lowerTitle.indexOf(lowerSearch);
    if (index === -1) {
        return title;
    }

    const before = title.substring(0, index);
    const match = title.substring(index, index + searchValue.length);
    const after = title.substring(index + searchValue.length);

    return (
        <>
            {before}
            <mark className="bg-transparent font-semibold text-foreground">{match}</mark>
            {after}
        </>
    );
}

const TreeRowItem = memo(function TreeRowItem({
    depth,
    hasChildren,
    index,
    isOpen,
    isSelected,
    measureRef,
    node,
    onSelect,
    onToggle,
    rowKey,
    searchValue,
    start,
}: TreeRowItemProps): JSX.Element {
    const title = node.title ?? "";
    const isExcluded = node.isExcluded === true;
    const isProject = node.isProject;
    const isScope = node.isScope;

    const Icon = isScope ? Network : isExcluded ? FileX2 : Package;

    // Excluded packages carry a tooltip explaining why, mirroring the Excludes-column icon in the table.
    const isExcludedPackage = isExcluded && !isScope && !isProject;
    const excludeReason = isExcludedPackage
        ? Array.from(node.package?.excludeReasons ?? [])
              .sort()
              .join(", ")
        : "";

    const titleButton = (
        <button
            className={cn(
                "flex-1 truncate text-left",
                isProject && "font-semibold",
                (isScope || isSelected) && "italic",
                isExcludedPackage && "line-through",
                "rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
            )}
            onClick={() => onSelect(rowKey, node)}
            type="button"
            {...(isExcludedPackage ? {} : { title })}
        >
            {highlight(title, searchValue)}
        </button>
    );

    return (
        <div
            className={cn(
                "group absolute top-0 left-0 flex w-full items-center gap-1 rounded-md py-1 pr-2 text-sm",
                "transition-colors hover:bg-accent/60",
                isSelected && "font-semibold text-foreground",
                isExcluded && "text-muted-foreground",
            )}
            data-index={index}
            data-tree-row=""
            ref={measureRef}
            style={{ paddingLeft: `${depth * 16 + 4}px`, transform: `translateY(${start}px)` }}
        >
            {hasChildren ? (
                <button
                    aria-expanded={isOpen}
                    aria-label={isOpen ? "Collapse node" : "Expand node"}
                    className="flex size-5 items-center justify-center rounded text-muted-foreground hover:bg-accent"
                    onClick={() => onToggle(rowKey, !isOpen)}
                    type="button"
                >
                    <ChevronRight
                        aria-hidden="true"
                        className={cn("size-4 transition-transform", isOpen && "rotate-90")}
                    />
                </button>
            ) : (
                <span aria-hidden="true" className="inline-block size-5" />
            )}
            <Icon aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
            {isExcludedPackage ? (
                <span title={excludeReason ? `Excluded: ${excludeReason}` : "Excluded"}>{titleButton}</span>
            ) : (
                titleButton
            )}
        </div>
    );
});

interface ScrollRequest {
    key: string;
}

interface VirtualTreeProps {
    onSelect: (key: string, node: WebAppTreeNode) => void;
    onToggle: (key: string, open: boolean) => void;
    rows: readonly TreeRow[];
    // A request to bring a row into view, such as the current search match. Each request is applied once, to
    // the rows it was made for, so later expanding or collapsing nodes does not scroll back to it.
    scrollRequest: ScrollRequest | null;
    searchValue: string;
    selectedKey: string | null;
}

// Renders only the rows in and near the viewport, so a tree with tens of thousands of displayed rows keeps a
// small, constant number of mounted elements.
function VirtualTree({
    onSelect,
    onToggle,
    rows,
    scrollRequest,
    searchValue,
    selectedKey,
}: VirtualTreeProps): JSX.Element {
    const scrollRef = useRef<HTMLDivElement>(null);
    const appliedScrollRequestRef = useRef<ScrollRequest | null>(null);
    const [focusedKey, setFocusedKey] = useState<string | null>(null);

    // Keep the row with the keyboard focus mounted while it is scrolled out of view, so the focus is not lost.
    const focusedIndex = focusedKey === null ? -1 : rows.findIndex((row) => row.key === focusedKey);

    const virtualizer = useVirtualizer({
        count: rows.length,
        estimateSize: () => ESTIMATED_ROW_HEIGHT_PX,
        getItemKey: (index) => rows[index]?.key ?? index,
        getScrollElement: () => scrollRef.current,
        overscan: OVERSCAN,
        paddingEnd: PANE_PADDING_PX,
        paddingStart: PANE_PADDING_PX,
        rangeExtractor: (range) => {
            const indexes = defaultRangeExtractor(range);
            if (focusedIndex >= 0 && !indexes.includes(focusedIndex)) {
                indexes.push(focusedIndex);
                indexes.sort((a, b) => a - b);
            }
            return indexes;
        },
    });

    useEffect(() => {
        const pane = scrollRef.current;
        if (!pane) return;

        const handleFocusIn = (event: FocusEvent): void => {
            const rowElement = event.target instanceof Element ? event.target.closest("[data-index]") : null;
            const index = Number(rowElement?.getAttribute("data-index"));
            setFocusedKey(Number.isInteger(index) ? (rows[index]?.key ?? null) : null);
        };
        const handleFocusOut = (event: FocusEvent): void => {
            if (!(event.relatedTarget instanceof Node && pane.contains(event.relatedTarget))) setFocusedKey(null);
        };

        pane.addEventListener("focusin", handleFocusIn);
        pane.addEventListener("focusout", handleFocusOut);
        return () => {
            pane.removeEventListener("focusin", handleFocusIn);
            pane.removeEventListener("focusout", handleFocusOut);
        };
    }, [rows]);

    useEffect(() => {
        if (!scrollRequest || appliedScrollRequestRef.current === scrollRequest) return;
        appliedScrollRequestRef.current = scrollRequest;
        const index = rows.findIndex((row) => row.key === scrollRequest.key);
        if (index >= 0) virtualizer.scrollToIndex(index, { align: "auto" });
    }, [rows, scrollRequest, virtualizer]);

    return (
        <div
            className="min-h-0 flex-1 overflow-auto rounded-md border bg-card px-2 text-sm"
            data-tree-scroll=""
            ref={scrollRef}
        >
            <div className="relative w-full" style={{ height: virtualizer.getTotalSize() }}>
                {virtualizer.getVirtualItems().map((item) => {
                    const row = rows[item.index];
                    if (!row) return null;
                    return (
                        <TreeRowItem
                            depth={row.depth}
                            hasChildren={row.hasChildren}
                            index={item.index}
                            isOpen={row.isOpen}
                            isSelected={row.key === selectedKey}
                            key={item.key}
                            measureRef={virtualizer.measureElement}
                            node={row.node}
                            onSelect={onSelect}
                            onToggle={onToggle}
                            rowKey={row.key}
                            searchValue={searchValue}
                            start={item.start}
                        />
                    );
                })}
            </div>
        </div>
    );
}

// The dependency graph view: an expandable tree of scopes and their (transitive) packages.
function ResultsTreeView({
    className,
    focusPackageId,
    focusPackageTab,
    onFocusPackageChange,
    onFocusPackageTabChange,
    webAppEvaluatedModel,
}: ResultsTreeProps): JSX.Element {
    const roots = useMemo<TreeRoot[]>(
        () => webAppEvaluatedModel.dependencyTrees.map((node) => ({ fallbackKey: randomStringGenerator(8), node })),
        [webAppEvaluatedModel],
    );

    // Expand projects and scopes by default so the direct dependencies are always visible.
    const defaultExpanded = useMemo(() => getProjectAndScopeKeys(roots), [roots]);

    // The input updates on every keystroke, while the query applied to the tree follows after a pause in typing.
    const [searchInput, setSearchInput] = useState("");
    const [query, setQuery] = useState("");
    const [searchResult, setSearchResult] = useState<TreeSearchResult | null>(null);
    const [expanded, setExpanded] = useState<ReadonlySet<string>>(defaultExpanded);
    const [selected, setSelected] = useState<{ key: string; node: WebAppTreeNode } | null>(null);
    const [scrollRequest, setScrollRequest] = useState<ScrollRequest | null>(null);
    const [matchIndex, setMatchIndex] = useState(0);

    useEffect(() => {
        const trimmed = searchInput.trim();
        if (!trimmed) {
            setQuery("");
            return;
        }
        const handle = setTimeout(() => setQuery(trimmed), SEARCH_DEBOUNCE_MS);
        return () => clearTimeout(handle);
    }, [searchInput]);

    // Search on deferred chunks, and cancel a search that a newer query has made obsolete. Clearing the query
    // shows the whole tree again, scrolled to the selected package.
    const selectedKeyRef = useRef<string | null>(null);
    useEffect(() => {
        setMatchIndex(0);
        if (!query) {
            setSearchResult(null);
            if (selectedKeyRef.current) setScrollRequest({ key: selectedKeyRef.current });
            return;
        }
        return searchTree(roots, query, setSearchResult);
    }, [query, roots]);

    const isSearching = searchInput.trim() !== query || (query !== "" && searchResult?.query !== query);
    const matches = useMemo(() => searchResult?.matches ?? [], [searchResult]);
    const clampedIndex = matches.length > 0 ? Math.min(matchIndex, matches.length - 1) : 0;

    // Select the current search match so its package details show on the right as you type or step.
    useEffect(() => {
        const match = matches[clampedIndex];
        if (!match) {
            return;
        }
        setSelected((prev) => (prev?.key === match.key ? prev : { key: match.key, node: match.node }));
        setScrollRequest({ key: match.key });
    }, [matches, clampedIndex]);

    const stepMatch = useCallback(
        (delta: number) => {
            setMatchIndex((i) => {
                const n = matches.length;
                if (n === 0) {
                    return 0;
                }
                return (Math.min(i, n - 1) + delta + n) % n;
            });
        },
        [matches.length],
    );

    const rows = useMemo(
        () => searchResult?.rows ?? flattenTree(roots, expanded, null),
        [roots, searchResult, expanded],
    );

    selectedKeyRef.current = selected?.key ?? null;

    const selectedPackage = useMemo(() => {
        const index = selected?.node.packageIndex;
        if (index === undefined) {
            return null;
        }
        return webAppEvaluatedModel.getPackageByIndex(index);
    }, [selected, webAppEvaluatedModel]);

    const handleToggle = useCallback((key: string, open: boolean) => {
        setExpanded((prev) => {
            const next = new Set(prev);
            if (open) {
                next.add(key);
            } else {
                next.delete(key);
            }
            return next;
        });
    }, []);

    // Locate the deep-linked package in the tree (first occurrence) plus its ancestor keys, so it can be
    // selected, revealed and scrolled to on mount.
    const focusTarget = useMemo<{ key: string; node: WebAppTreeNode; ancestors: string[] } | null>(() => {
        if (!focusPackageId) {
            return null;
        }
        let result: { key: string; node: WebAppTreeNode; ancestors: string[] } | null = null;
        const walk = (node: WebAppTreeNode, fk: string, ancestors: string[]): boolean => {
            const key = nodeKey(node, fk);
            const index = node.packageIndex;
            if (index !== undefined && webAppEvaluatedModel.getPackageByIndex(index)?.id === focusPackageId) {
                result = { ancestors, key, node };
                return true;
            }
            return node.children.some((child, i) => walk(child, `${fk}-${i}`, [...ancestors, key]));
        };
        roots.some((root) => walk(root.node, root.fallbackKey, []));
        return result;
    }, [focusPackageId, roots, webAppEvaluatedModel]);

    // Apply the deep-linked selection once on mount: select the node, expand its ancestors and scroll to it.
    const appliedFocusRef = useRef(false);
    useEffect(() => {
        if (appliedFocusRef.current || !focusTarget) {
            return;
        }
        appliedFocusRef.current = true;
        setSelected({ key: focusTarget.key, node: focusTarget.node });
        setScrollRequest({ key: focusTarget.key });
        if (focusTarget.ancestors.length > 0) {
            setExpanded((prev) => new Set([...prev, ...focusTarget.ancestors]));
        }
    }, [focusTarget]);

    const handleSelect = useCallback(
        (key: string, node: WebAppTreeNode) => {
            setSelected({ key, node });
            const index = node.packageIndex;
            const packageId = index !== undefined ? (webAppEvaluatedModel.getPackageByIndex(index)?.id ?? null) : null;
            onFocusPackageChange?.(packageId);
        },
        [webAppEvaluatedModel, onFocusPackageChange],
    );

    return (
        <div
            className={cn(
                "grid min-h-0 gap-4 [grid-template-rows:minmax(0,1fr)_minmax(0,1fr)]",
                "lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] lg:[grid-template-rows:minmax(0,1fr)]",
                className,
            )}
        >
            <div className="flex h-full min-h-0 flex-col gap-2">
                <div className="flex items-center gap-2">
                    <div className="relative flex-1">
                        <Search
                            aria-hidden="true"
                            className="absolute top-1/2 left-2 size-4 -translate-y-1/2 text-muted-foreground"
                        />
                        <Input
                            aria-label="Search the dependency tree"
                            className="h-8 pl-8"
                            onChange={(event) => setSearchInput(event.target.value)}
                            placeholder="Search the tree for package ids..."
                            value={searchInput}
                        />
                    </div>
                    {searchInput.trim() ? (
                        <div className="flex shrink-0 items-center gap-1 text-muted-foreground text-xs">
                            <span aria-live="polite" className="tabular-nums">
                                {isSearching
                                    ? "Searching…"
                                    : `${matches.length === 0 ? 0 : clampedIndex + 1}/${matches.length}`}
                            </span>
                            <Button
                                aria-label="Previous match"
                                className="size-7"
                                disabled={isSearching || matches.length === 0}
                                onClick={() => stepMatch(-1)}
                                size="icon"
                                variant="ghost"
                            >
                                <ChevronLeft aria-hidden="true" className="size-4" />
                            </Button>
                            <Button
                                aria-label="Next match"
                                className="size-7"
                                disabled={isSearching || matches.length === 0}
                                onClick={() => stepMatch(1)}
                                size="icon"
                                variant="ghost"
                            >
                                <ChevronRight aria-hidden="true" className="size-4" />
                            </Button>
                        </div>
                    ) : null}
                </div>
                {roots.length === 0 || rows.length === 0 ? (
                    <div className="min-h-0 flex-1 rounded-md border bg-card p-2 text-sm">
                        <p className="px-2 py-4 text-muted-foreground">
                            {roots.length === 0 ? "No dependency tree available." : "No matching packages."}
                        </p>
                    </div>
                ) : (
                    <VirtualTree
                        onSelect={handleSelect}
                        onToggle={handleToggle}
                        rows={rows}
                        scrollRequest={scrollRequest}
                        searchValue={searchResult?.query ?? ""}
                        selectedKey={selected?.key ?? null}
                    />
                )}
            </div>
            <div className="h-full min-h-0 overflow-auto rounded-md border bg-card">
                {selectedPackage ? (
                    <PackageDetailPanel
                        key={selectedPackage.id ?? ""}
                        ortResult={webAppEvaluatedModel}
                        pkg={selectedPackage}
                        {...(selectedPackage.id === focusPackageId && focusPackageTab
                            ? { defaultTab: focusPackageTab }
                            : {})}
                        {...(onFocusPackageTabChange ? { onTabChange: onFocusPackageTabChange } : {})}
                    />
                ) : (
                    <p className="p-4 text-muted-foreground text-sm">
                        Select a package in the tree to view its details.
                    </p>
                )}
            </div>
        </div>
    );
}

/**
 * Gates the dependency graph view on the model's asynchronous, chunked tree construction. Large reports build
 * their trees across many deferred tasks to keep the main thread responsive; until those drain we show a
 * "Building…" placeholder and then mount the real view fresh, so its initial expansion is derived from the
 * finished tree rather than a half-built one. Re-rendering in place would not work: the live dependencyTrees
 * array keeps the same identity as it fills, so the view's useMemo/useState would never recompute.
 */
function ResultsTree(props: ResultsTreeProps): JSX.Element {
    const { className, webAppEvaluatedModel } = props;
    const [ready, setReady] = useState(() => webAppEvaluatedModel.dependencyTreesReady);

    useEffect(() => {
        if (webAppEvaluatedModel.dependencyTreesReady) {
            setReady(true);
            return;
        }

        setReady(false);
        return webAppEvaluatedModel.onDependencyTreesReady(() => {
            setReady(true);
        });
    }, [webAppEvaluatedModel]);

    if (!ready) {
        return (
            <div className={cn("rounded-md border bg-card p-2 text-sm", className)}>
                <p className="px-2 py-4 text-muted-foreground">Building dependency tree…</p>
            </div>
        );
    }

    return <ResultsTreeView {...props} />;
}

export { ResultsTree };
export default ResultsTree;
