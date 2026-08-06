/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import type { JSX, ReactNode } from "react";
import { useCallback, useMemo } from "react";
import { PackageConfigurations } from "@/components/PackageConfigurations";
import { PackageCurations } from "@/components/PackageCurations";
import { PackageDetails } from "@/components/PackageDetails";
import { PackageLicenses } from "@/components/PackageLicenses";
import { PackagePaths } from "@/components/PackagePaths";
import { PackageScannerFindingsTable } from "@/components/PackageScannerFindingsTable";
import { PackageScanResultsDetails } from "@/components/PackageScanResultsDetails";
import { SeverityTag, type SeverityValue } from "@/components/SeverityTag";
import { LicenseBadge, MarkdownText, PackageLink } from "@/components/Shared";
import { Button } from "@/components/ui/Button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/Collapsible";
import {
    createExpandColumn,
    DataTable,
    DataTableColumnHeader,
    DataTableColumnSearch,
    DataTableFacetedFilter,
    getActiveExpandedRowId,
    LARGE_TABLE_PAGE_SIZES,
} from "@/components/ui/data-table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import type WebAppRuleViolation from "@/models/WebAppRuleViolation";

export interface RuleViolationsTableProps {
    // When set, expand the first policy violation row for this package id on mount.
    focusPackageId?: string;
    // When set, pre-filter the Severity column to these values on mount (e.g. ["ERROR"]).
    focusSeverity?: string[];
    // Fired with the expanded violation's package id (or null when none is expanded) so the URL can track it.
    onFocusPackageChange?: (packageId: string | null) => void;
    onPackageClick?: (packageId: string) => void;
    ruleViolations: readonly WebAppRuleViolation[];
}

interface RuleViolationRow {
    hasHowToFix: boolean;
    howToFix: string;
    isResolved: boolean;
    key: string;
    license: string;
    licenseSource: string;
    message: string;
    packageName: string;
    resolutionReasons: string;
    ruleName: string;
    severity: SeverityValue | "";
    severityIndex: number;
    violation: WebAppRuleViolation;
}

const SEVERITY_OPTIONS = [
    { label: "Error", value: "ERROR" },
    { label: "Warning", value: "WARNING" },
    { label: "Hint", value: "HINT" },
    { label: "Resolved", value: "RESOLVED" },
];

function buildRow(violation: WebAppRuleViolation, index: number): RuleViolationRow {
    const severity = (violation.severity ?? "") as SeverityValue | "";
    const reasons = Array.from(violation.resolutionReasons ?? []);
    return {
        hasHowToFix: violation.hasHowToFix(),
        howToFix: violation.howToFix ?? "",
        isResolved: violation.isResolved,
        key: violation.key ?? `violation-${index}`,
        license: violation.licenseName ?? "",
        licenseSource: violation.licenseSource ?? "",
        message: violation.message ?? "",
        packageName: violation.packageName,
        resolutionReasons: reasons.length > 0 ? `Resolved with ${reasons.join(", ")}` : "",
        ruleName: violation.rule ?? "",
        severity,
        severityIndex: violation.severityIndex,
        violation,
    };
}

function renderSubRow(row: Row<RuleViolationRow>): JSX.Element {
    const { hasHowToFix, howToFix, isResolved, licenseSource, message, resolutionReasons, violation } = row.original;
    const pkg = violation.package;

    // The policy-violation details always show first; the remaining tabs mirror the Table view's expanded
    // row so the affected package can be inspected without leaving the Policy Violations list.
    const tabs: { value: string; label: string; content: ReactNode }[] = [
        {
            value: "details",
            label: "Policy Violation Details",
            content: (
                <div className="space-y-3">
                    <div className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1">
                        {licenseSource ? (
                            <>
                                <span className="font-medium text-muted-foreground">License Source</span>
                                <span className="font-mono">{licenseSource}</span>
                            </>
                        ) : null}
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
                </div>
            ),
        },
    ];

    if (pkg) {
        tabs.push({
            value: "package",
            label: "Package Details",
            content: (
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
            ),
        });

        tabs.push({
            value: "findings",
            label: "Scanner Findings",
            content: (
                <div className="space-y-4">
                    <PackageScannerFindingsTable
                        effectiveLicenseIds={pkg.effectiveSpdxSimpleExpressions}
                        scannerFindings={pkg.findings}
                    />
                    {pkg.scanResults.length > 0 ? (
                        <Collapsible>
                            <CollapsibleTrigger className="group font-medium text-muted-foreground text-sm underline-offset-4 hover:text-foreground hover:underline">
                                <span className="group-data-[state=open]:hidden">Show scanner details</span>
                                <span className="group-data-[state=closed]:hidden">Hide scanner details</span>
                            </CollapsibleTrigger>
                            <CollapsibleContent className="mt-2">
                                <PackageScanResultsDetails pkg={pkg} />
                            </CollapsibleContent>
                        </Collapsible>
                    ) : null}
                </div>
            ),
        });

        const paths = pkg.paths ?? [];
        if (paths.length > 0) {
            tabs.push({ value: "paths", label: "Dependency Paths", content: <PackagePaths paths={paths} /> });
        }

        if (pkg.hasPackageConfigurations()) {
            tabs.push({
                content: <PackageConfigurations pkg={pkg} />,
                label: "Package Configurations",
                value: "configurations",
            });
        }

        if (pkg.hasCurations()) {
            tabs.push({ value: "curations", label: "Package Curations", content: <PackageCurations pkg={pkg} /> });
        }
    }

    return (
        <div className="p-4 text-sm">
            <Tabs defaultValue="details">
                <TabsList className="flex h-auto flex-wrap">
                    {tabs.map((tab) => (
                        <TabsTrigger key={tab.value} value={tab.value}>
                            {tab.label}
                        </TabsTrigger>
                    ))}
                </TabsList>
                {tabs.map((tab) => (
                    <TabsContent className="mt-4" key={tab.value} value={tab.value}>
                        {tab.content}
                    </TabsContent>
                ))}
            </Tabs>
        </div>
    );
}

// The Policy Violations table: one row per rule violation, expandable to violation details and package tabs.
function RuleViolationsTable({
    focusPackageId,
    focusSeverity,
    onFocusPackageChange,
    onPackageClick,
    ruleViolations,
}: RuleViolationsTableProps): JSX.Element {
    const data = useMemo<RuleViolationRow[]>(
        () => ruleViolations.map((violation, index) => buildRow(violation, index)),
        [ruleViolations],
    );

    // Expand the deep-linked package's first policy violation row and/or pre-filter the severity column on mount.
    const initialState = useMemo<Partial<TableState>>(() => {
        const state: Partial<TableState> = {
            columnVisibility: { licenseSource: false },
            sorting: [{ id: "severity", desc: false }],
        };
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

    // Report the most-recently-expanded row's package id (or null) so the URL can deep-link the open violation's package.
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

    const columns = useMemo<ColumnDef<RuleViolationRow, unknown>[]>(
        () => [
            createExpandColumn<RuleViolationRow>({ enableExpandAll: true }),
            {
                id: "severity",
                meta: { label: "Severity" },
                // Resolved violations filter/count under "Resolved" rather than their underlying severity.
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
                id: "ruleName",
                meta: { label: "Rule" },
                accessorKey: "ruleName",
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Rule" />
                        <DataTableFacetedFilter column={column} title="Rule" />
                    </div>
                ),
                cell: ({ row }) => <span className="font-mono text-xs">{row.original.ruleName}</span>,
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
                id: "license",
                meta: { label: "License" },
                accessorKey: "license",
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="License" />
                        <DataTableFacetedFilter column={column} title="License" />
                    </div>
                ),
                cell: ({ row }) => {
                    const value = row.original.license;
                    if (!value) return null;
                    return <LicenseBadge name={value} />;
                },
                filterFn: "arrIncludesSome",
                enableColumnFilter: true,
            },
            {
                id: "licenseSource",
                meta: { label: "License Source" },
                accessorKey: "licenseSource",
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="License Source" />
                        <DataTableFacetedFilter column={column} title="License Source" />
                    </div>
                ),
                cell: ({ row }) => {
                    const value = row.original.licenseSource;
                    if (!value) return null;
                    return <span className="font-mono text-xs">{value}</span>;
                },
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
        <DataTable<RuleViolationRow>
            columns={columns}
            data={data}
            emptyText="No policy violations"
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

export { RuleViolationsTable };
export default RuleViolationsTable;
