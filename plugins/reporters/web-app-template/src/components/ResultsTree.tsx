/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import { ChevronLeft, ChevronRight, FileX2, Network, Package, Search } from "lucide-react";
import type { JSX, ReactNode } from "react";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { PackageDetailPanel } from "@/components/ResultsTable";
import { Button } from "@/components/ui/Button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/Collapsible";
import { Input } from "@/components/ui/Input";
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

interface TreeNodeProps {
    depth: number;
    expandedKeys: ReadonlySet<string>;
    fallbackKey: string;
    node: WebAppTreeNode;
    onSelect: (key: string, node: WebAppTreeNode) => void;
    onToggle: (key: string, open: boolean) => void;
    searchValue: string;
    selectedKeys: ReadonlySet<string>;
    visibleKeys: ReadonlySet<string> | null;
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

const TreeNode = memo(function TreeNode({
    depth,
    expandedKeys,
    fallbackKey,
    node,
    onSelect,
    onToggle,
    searchValue,
    selectedKeys,
    visibleKeys,
}: TreeNodeProps): JSX.Element {
    const key = node.key ?? fallbackKey;
    const hasChildren = node.children.length > 0;
    const isOpen = hasChildren && expandedKeys.has(key);
    const isSelected = selectedKeys.has(key);
    const title = node.title ?? "";
    const isExcluded = node.isExcluded === true;
    const isProject = node.isProject;
    const isScope = node.isScope;
    const rowRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (isSelected) {
            rowRef.current?.scrollIntoView({ block: "nearest" });
        }
    }, [isSelected]);

    const handleSelect = useCallback(() => {
        onSelect(key, node);
    }, [key, node, onSelect]);

    const handleOpenChange = useCallback(
        (open: boolean) => {
            onToggle(key, open);
        },
        [key, onToggle],
    );

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
            onClick={handleSelect}
            type="button"
            {...(isExcludedPackage ? {} : { title })}
        >
            {highlight(title, searchValue)}
        </button>
    );

    const row = (
        <div
            className={cn(
                "group flex items-center gap-1 rounded-md py-1 pr-2 text-sm transition-colors",
                "hover:bg-accent/60",
                isSelected && "font-semibold text-foreground",
                isExcluded && "text-muted-foreground",
            )}
            ref={rowRef}
            style={{ paddingLeft: `${depth * 16 + 4}px` }}
        >
            {hasChildren ? (
                <CollapsibleTrigger
                    aria-label={isOpen ? "Collapse node" : "Expand node"}
                    className="flex size-5 items-center justify-center rounded text-muted-foreground hover:bg-accent"
                    type="button"
                >
                    <ChevronRight
                        aria-hidden="true"
                        className={cn("size-4 transition-transform", isOpen && "rotate-90")}
                    />
                </CollapsibleTrigger>
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

    if (!hasChildren) {
        return <div>{row}</div>;
    }

    const children = node.children
        .map((child, index) => ({ child, index, childKey: child.key ?? `${fallbackKey}-${index}` }))
        .filter(({ childKey }) => !visibleKeys || visibleKeys.has(childKey));

    return (
        <Collapsible onOpenChange={handleOpenChange} open={isOpen}>
            <div>{row}</div>
            <CollapsibleContent>
                <div>
                    {children.map(({ child, childKey, index }) => (
                        <TreeNode
                            depth={depth + 1}
                            expandedKeys={expandedKeys}
                            fallbackKey={`${fallbackKey}-${index}`}
                            key={childKey}
                            node={child}
                            onSelect={onSelect}
                            onToggle={onToggle}
                            searchValue={searchValue}
                            selectedKeys={selectedKeys}
                            visibleKeys={visibleKeys}
                        />
                    ))}
                </div>
            </CollapsibleContent>
        </Collapsible>
    );
});

// The dependency tree view: an expandable tree of scopes and their (transitive) packages.
function ResultsTreeView({
    className,
    focusPackageId,
    focusPackageTab,
    onFocusPackageChange,
    onFocusPackageTabChange,
    webAppEvaluatedModel,
}: ResultsTreeProps): JSX.Element {
    const roots = useMemo(() => webAppEvaluatedModel.dependencyTrees, [webAppEvaluatedModel]);
    const fallbackKeys = useMemo(() => roots.map(() => randomStringGenerator(8)), [roots]);

    // Expand projects and scopes by default so the direct dependencies are always visible.
    const defaultExpanded = useMemo(() => {
        const keys = new Set<string>();
        const walk = (node: WebAppTreeNode, fk: string): void => {
            if (node.isProject || node.isScope) {
                keys.add(node.key ?? fk);
            }
            node.children.forEach((child, index) => {
                walk(child, `${fk}-${index}`);
            });
        };
        roots.forEach((root, index) => {
            walk(root, fallbackKeys[index] ?? `root-${index}`);
        });
        return keys;
    }, [roots, fallbackKeys]);

    const [search, setSearch] = useState("");
    const [expanded, setExpanded] = useState<ReadonlySet<string>>(defaultExpanded);
    const [selected, setSelected] = useState<{ key: string; node: WebAppTreeNode } | null>(null);
    const [matchIndex, setMatchIndex] = useState(0);

    // While searching, only keep nodes on a path to a match, force those paths open, and
    // collect the matches in display (pre-order) order so they can be stepped through.
    const { matches, searchExpanded, visibleKeys } = useMemo<{
        visibleKeys: ReadonlySet<string> | null;
        searchExpanded: ReadonlySet<string> | null;
        matches: { key: string; node: WebAppTreeNode }[];
    }>(() => {
        const trimmed = search.trim().toLowerCase();
        if (!trimmed) {
            return { visibleKeys: null, searchExpanded: null, matches: [] };
        }
        const visible = new Set<string>();
        const expand = new Set<string>();
        const found: { key: string; node: WebAppTreeNode }[] = [];
        const walk = (node: WebAppTreeNode, fk: string): boolean => {
            const key = node.key ?? fk;
            const selfMatch = (node.title ?? "").toLowerCase().includes(trimmed);
            if (selfMatch) {
                found.push({ key, node });
            }
            let childMatch = false;
            node.children.forEach((child, index) => {
                if (walk(child, `${fk}-${index}`)) {
                    childMatch = true;
                }
            });
            if (selfMatch || childMatch) {
                visible.add(key);
            }
            if (childMatch) {
                expand.add(key);
            }
            return selfMatch || childMatch;
        };
        roots.forEach((root, index) => {
            walk(root, fallbackKeys[index] ?? `root-${index}`);
        });
        return { matches: found, searchExpanded: expand, visibleKeys: visible };
    }, [search, roots, fallbackKeys]);

    const clampedIndex = matches.length > 0 ? Math.min(matchIndex, matches.length - 1) : 0;

    // Select the current search match so its package details show on the right as you type or step.
    useEffect(() => {
        const match = matches[clampedIndex];
        if (!match) {
            return;
        }
        setSelected((prev) => (prev?.key === match.key ? prev : { key: match.key, node: match.node }));
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

    const effectiveExpanded = searchExpanded ?? expanded;
    const selectedSet = useMemo(() => new Set(selected ? [selected.key] : []), [selected]);

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
            const key = node.key ?? fk;
            const index = node.packageIndex;
            if (index !== undefined && webAppEvaluatedModel.getPackageByIndex(index)?.id === focusPackageId) {
                result = { ancestors, key, node };
                return true;
            }
            return node.children.some((child, i) => walk(child, `${fk}-${i}`, [...ancestors, key]));
        };
        roots.some((root, index) => walk(root, fallbackKeys[index] ?? `root-${index}`, []));
        return result;
    }, [focusPackageId, roots, fallbackKeys, webAppEvaluatedModel]);

    // Apply the deep-linked selection once on mount: select the node and expand its ancestors so the
    // selected TreeNode renders and scrolls itself into view.
    const appliedFocusRef = useRef(false);
    useEffect(() => {
        if (appliedFocusRef.current || !focusTarget) {
            return;
        }
        appliedFocusRef.current = true;
        setSelected({ key: focusTarget.key, node: focusTarget.node });
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

    const visibleRoots = roots
        .map((root, index) => ({ root, index, rootKey: root.key ?? fallbackKeys[index] ?? `root-${index}` }))
        .filter(({ rootKey }) => !visibleKeys || visibleKeys.has(rootKey));

    return (
        <div
            className={cn(
                "grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] lg:[grid-template-rows:minmax(0,1fr)]",
                className,
            )}
        >
            <div className="flex min-h-0 flex-col gap-2 lg:h-full">
                <div className="flex items-center gap-2">
                    <div className="relative flex-1">
                        <Search
                            aria-hidden="true"
                            className="absolute top-1/2 left-2 size-4 -translate-y-1/2 text-muted-foreground"
                        />
                        <Input
                            aria-label="Search the dependency tree"
                            className="h-8 pl-8"
                            onChange={(event) => {
                                // Restart the result cursor at the first match on every query change.
                                setSearch(event.target.value);
                                setMatchIndex(0);
                            }}
                            placeholder="Search the tree for package ids..."
                            value={search}
                        />
                    </div>
                    {search ? (
                        <div className="flex shrink-0 items-center gap-1 text-muted-foreground text-xs">
                            <span className="tabular-nums">
                                {matches.length === 0 ? 0 : clampedIndex + 1}/{matches.length}
                            </span>
                            <Button
                                aria-label="Previous match"
                                className="size-7"
                                disabled={matches.length === 0}
                                onClick={() => stepMatch(-1)}
                                size="icon"
                                variant="ghost"
                            >
                                <ChevronLeft aria-hidden="true" className="size-4" />
                            </Button>
                            <Button
                                aria-label="Next match"
                                className="size-7"
                                disabled={matches.length === 0}
                                onClick={() => stepMatch(1)}
                                size="icon"
                                variant="ghost"
                            >
                                <ChevronRight aria-hidden="true" className="size-4" />
                            </Button>
                        </div>
                    ) : null}
                </div>
                <div className="max-h-[75vh] min-h-0 flex-1 overflow-auto rounded-md border bg-card p-2 text-sm lg:max-h-none">
                    {roots.length === 0 ? (
                        <p className="px-2 py-4 text-muted-foreground">No dependency tree available.</p>
                    ) : visibleRoots.length === 0 ? (
                        <p className="px-2 py-4 text-muted-foreground">No matching packages.</p>
                    ) : (
                        visibleRoots.map(({ index, root, rootKey }) => (
                            <TreeNode
                                depth={0}
                                expandedKeys={effectiveExpanded}
                                fallbackKey={fallbackKeys[index] ?? `root-${index}`}
                                key={rootKey}
                                node={root}
                                onSelect={handleSelect}
                                onToggle={handleToggle}
                                searchValue={search}
                                selectedKeys={selectedSet}
                                visibleKeys={visibleKeys}
                            />
                        ))
                    )}
                </div>
            </div>
            <div className="max-h-[75vh] min-h-0 overflow-auto rounded-md border bg-card lg:h-full lg:max-h-none">
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
 * Gates the dependency-tree view on the model's asynchronous, chunked tree construction. Large reports build
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
