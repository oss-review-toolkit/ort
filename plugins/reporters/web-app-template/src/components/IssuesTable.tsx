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

import type { ColumnDef, Row, TableState } from "@tanstack/react-table";
import { Lightbulb } from "lucide-react";
import type { JSX } from "react";
import { useCallback, useMemo } from "react";

import {
    createExpandColumn,
    DataTable,
    DataTableColumnHeader,
    DataTableColumnSearch,
    DataTableFacetedFilter,
    getActiveExpandedRowId,
    LARGE_TABLE_PAGE_SIZES,
} from "@/components/data-table";
import { PackageDetails } from "@/components/PackageDetails";
import { PackageLicenses } from "@/components/PackageLicenses";
import { PackagePaths } from "@/components/PackagePaths";
import { SeverityTag, type SeverityValue } from "@/components/SeverityTag";
import { MarkdownText, PackageLink } from "@/components/Shared";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type WebAppOrtIssue from "@/models/WebAppOrtIssue";

export interface IssuesTableProps {
    // When set, expand the first technical issue row for this package id on mount.
    focusPackageId?: string;
    // When set, pre-filter the Severity column to these values on mount (e.g. ["ERROR"]).
    focusSeverity?: string[];
    issues: readonly WebAppOrtIssue[];
    // Fired with the expanded issue's package id (or null when none is expanded) so the URL can track it.
    onFocusPackageChange?: (packageId: string | null) => void;
    onPackageClick?: (packageId: string) => void;
}

interface IssueRow {
    hasHowToFix: boolean;
    howToFix: string;
    isResolved: boolean;
    issue: WebAppOrtIssue;
    key: string;
    message: string;
    packageName: string;
    resolutionReasons: string;
    severity: SeverityValue | "";
    severityIndex: number;
    source: string;
}

const SEVERITY_OPTIONS = [
    { label: "Error", value: "ERROR" },
    { label: "Warning", value: "WARNING" },
    { label: "Hint", value: "HINT" },
    { label: "Resolved", value: "RESOLVED" },
];

function buildRow(issue: WebAppOrtIssue, index: number): IssueRow {
    const severity = (issue.severity ?? "") as SeverityValue | "";
    const reasons = Array.from(issue.resolutionReasons ?? []);
    return {
        hasHowToFix: issue.hasHowToFix(),
        howToFix: issue.howToFix ?? "",
        isResolved: issue.isResolved,
        issue,
        key: issue.key ?? `issue-${index}`,
        message: issue.message ?? "",
        packageName: issue.packageName,
        resolutionReasons: reasons.length > 0 ? `Resolved with ${reasons.join(", ")}` : "",
        severity,
        severityIndex: issue.severityIndex,
        source: issue.source ?? "",
    };
}

function renderSubRow(row: Row<IssueRow>): JSX.Element {
    const { hasHowToFix, howToFix, isResolved, issue, message, resolutionReasons } = row.original;
    const pkg = issue.package;
    const paths = pkg?.paths ?? [];
    return (
        <div className="p-4 text-sm">
            <Tabs defaultValue="details">
                <TabsList className="flex h-auto flex-wrap">
                    <TabsTrigger value="details">Technical Issue Details</TabsTrigger>
                    {issue.hasPackage() ? <TabsTrigger value="package">Package Details</TabsTrigger> : null}
                    {issue.hasPackage() && paths.length > 0 ? (
                        <TabsTrigger value="paths">Dependency Paths</TabsTrigger>
                    ) : null}
                </TabsList>
                <TabsContent className="mt-4 space-y-3" value="details">
                    <div className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1">
                        {isResolved && resolutionReasons ? (
                            <>
                                <span className="font-medium text-muted-foreground">Resolution</span>
                                <span>{resolutionReasons}</span>
                            </>
                        ) : null}
                    </div>
                    {message ? (
                        <div>
                            <h4 className="mb-1 font-semibold text-muted-foreground text-xs uppercase">Message</h4>
                            <MarkdownText>{message}</MarkdownText>
                        </div>
                    ) : null}
                    {hasHowToFix ? (
                        <div>
                            <h4 className="mb-1 font-semibold text-muted-foreground text-xs uppercase">How to fix</h4>
                            <MarkdownText>{howToFix}</MarkdownText>
                        </div>
                    ) : null}
                </TabsContent>
                {issue.hasPackage() && pkg ? (
                    <TabsContent className="mt-4" value="package">
                        <div className="grid gap-6 lg:grid-cols-2">
                            <section className="space-y-4">
                                <div>
                                    <h3 className="mb-2 font-semibold text-muted-foreground text-sm">Details</h3>
                                    <PackageDetails pkg={pkg} />
                                </div>
                            </section>
                            {pkg.hasLicenses() ? (
                                <section>
                                    <h3 className="mb-2 font-semibold text-muted-foreground text-sm">Licenses</h3>
                                    <PackageLicenses pkg={pkg} />
                                </section>
                            ) : null}
                        </div>
                    </TabsContent>
                ) : null}
                {issue.hasPackage() && pkg && paths.length > 0 ? (
                    <TabsContent className="mt-4" value="paths">
                        <PackagePaths paths={paths} />
                    </TabsContent>
                ) : null}
            </Tabs>
        </div>
    );
}

// The Technical Issues table: one row per ORT issue, expandable to issue / package / dependency-path tabs.
function IssuesTable({
    focusPackageId,
    focusSeverity,
    issues,
    onFocusPackageChange,
    onPackageClick,
}: IssuesTableProps): JSX.Element {
    const data = useMemo<IssueRow[]>(() => issues.map((issue, index) => buildRow(issue, index)), [issues]);

    // Expand the deep-linked package's first technical issue row and/or pre-filter the severity column on mount.
    const initialState = useMemo<Partial<TableState>>(() => {
        const state: Partial<TableState> = { sorting: [{ id: "severity", desc: false }] };
        if (focusPackageId) {
            const target = data.find((row) => row.packageName === focusPackageId);
            if (target) {
                state.expanded = { [target.key]: true };
            }
        }
        if (focusSeverity && focusSeverity.length > 0) {
            state.columnFilters = [{ id: "severity", value: focusSeverity }];
        }
        return state;
    }, [data, focusPackageId, focusSeverity]);

    // Report the most-recently-expanded row's package id (or null) so the URL can deep-link the open issue's package.
    const handleStateChange = useCallback(
        (state: TableState): void => {
            if (!onFocusPackageChange) return;
            const expandedKey = getActiveExpandedRowId(state.expanded);
            const packageName = expandedKey ? data.find((row) => row.key === expandedKey)?.packageName : undefined;
            const packageId = packageName || null;
            if (packageId !== (focusPackageId ?? null)) {
                onFocusPackageChange(packageId);
            }
        },
        [data, focusPackageId, onFocusPackageChange],
    );

    const columns = useMemo<ColumnDef<IssueRow, unknown>[]>(
        () => [
            createExpandColumn<IssueRow>({ enableExpandAll: true }),
            {
                id: "severity",
                meta: { label: "Severity" },
                // Resolved issues filter/count under "Resolved" rather than their underlying severity.
                accessorFn: (row) => (row.isResolved ? "RESOLVED" : row.severity),
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Severity" />
                        <DataTableFacetedFilter column={column} options={SEVERITY_OPTIONS} title="Severity" />
                    </div>
                ),
                cell: ({ row }) => {
                    const { isResolved, resolutionReasons, severity } = row.original;
                    if (!severity) return null;
                    return (
                        <SeverityTag
                            isResolved={isResolved}
                            severity={severity as SeverityValue}
                            tooltipText={resolutionReasons}
                        />
                    );
                },
                sortingFn: (a, b) => a.original.severityIndex - b.original.severityIndex,
                filterFn: "arrIncludesSome",
                enableColumnFilter: true,
            },
            {
                id: "packageName",
                accessorKey: "packageName",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Package" />
                        <DataTableColumnSearch column={column} title="Package" />
                    </div>
                ),
                cell: ({ row }) => (
                    <PackageLink
                        id={row.original.packageName}
                        {...(onPackageClick ? { onClick: onPackageClick } : {})}
                    />
                ),
                filterFn: "includesString",
                enableColumnFilter: true,
                meta: { label: "Package Id" },
            },
            {
                id: "source",
                meta: { label: "Source" },
                accessorKey: "source",
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Source" />
                        <DataTableFacetedFilter column={column} title="Source" />
                    </div>
                ),
                cell: ({ row }) => <span className="font-mono text-xs">{row.original.source}</span>,
                filterFn: "arrIncludesSome",
                enableColumnFilter: true,
            },
            {
                id: "message",
                meta: { label: "Message" },
                accessorKey: "message",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Message" />
                        <DataTableColumnSearch column={column} title="Message" />
                    </div>
                ),
                cell: ({ row }) => (
                    <span className="line-clamp-3 whitespace-pre-wrap text-xs leading-relaxed">
                        {row.original.message}
                    </span>
                ),
                enableSorting: false,
                filterFn: "includesString",
                enableColumnFilter: true,
            },
            {
                id: "howToFix",
                enableHiding: false,
                header: () => null,
                cell: ({ row }) => {
                    if (!row.original.hasHowToFix) return null;
                    return (
                        <Button
                            aria-label="Show how to fix"
                            className="size-6"
                            onClick={(event) => {
                                event.stopPropagation();
                                row.toggleExpanded();
                            }}
                            size="icon"
                            title="Expand to see how to fix"
                            variant="ghost"
                        >
                            <Lightbulb aria-hidden="true" className="size-4 text-amber-500" />
                        </Button>
                    );
                },
                enableSorting: false,
                enableColumnFilter: false,
                meta: { headerClassName: "w-8", cellClassName: "w-8" },
            },
        ],
        [onPackageClick],
    );

    return (
        <DataTable<IssueRow>
            columns={columns}
            data={data}
            emptyText="No technical issues"
            getRowCanExpand={() => true}
            getRowId={(row) => row.key}
            hidePaginationWhenSinglePage
            initialState={initialState}
            onStateChange={handleStateChange}
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
            renderSubComponent={renderSubRow}
        />
    );
}

export { IssuesTable };
export default IssuesTable;
