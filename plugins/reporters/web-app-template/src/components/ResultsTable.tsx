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

import type { ColumnDef, Row, TableState } from "@tanstack/react-table";
import {
    Boxes,
    Bug,
    FileX,
    FolderGit2,
    Layers,
    type LucideIcon,
    Package,
    Scale,
    ShieldAlert,
    Square,
    SquareCheck,
} from "lucide-react";
import type { JSX, ReactNode } from "react";
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
import { IssuesTable } from "@/components/IssuesTable";
import { PackageConfigurations } from "@/components/PackageConfigurations";
import { PackageCurations } from "@/components/PackageCurations";
import { PackageDetails } from "@/components/PackageDetails";
import { PackageLicenses } from "@/components/PackageLicenses";
import { PackagePaths } from "@/components/PackagePaths";
import { PackageScannerFindingsTable } from "@/components/PackageScannerFindingsTable";
import { PackageScanResultsDetails } from "@/components/PackageScanResultsDetails";
import { RuleViolationsTable } from "@/components/RuleViolationsTable";
import { useSettings } from "@/components/SettingsProvider";
import {
    ExcludeStatusIcon,
    IconHeader,
    LicenseExpression,
    LicenseExpressionList,
    NO_ASSERTION,
    PackageConfigurationIcon,
    PackageCurationIcon,
    Url,
} from "@/components/Shared";
import { Badge } from "@/components/ui/badge";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { VulnerabilitiesTable } from "@/components/VulnerabilitiesTable";
import { cn } from "@/lib/utils";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import type WebAppPackage from "@/models/WebAppPackage";

/** A result-table column the user can choose to show or hide by default (the expand toggle is excluded). */
export interface ResultsTableColumn {
    // Columns that are always shown and cannot be turned off (the package identity column).
    alwaysVisible?: boolean;
    // Whether the column shows when the user has not customised the defaults.
    defaultVisible: boolean;
    id: string;
    label: string;
    // Columns that only exist when the report carries the corresponding data.
    presence?: "excludes" | "curations" | "configurations";
}

/** All toggleable result-table columns, in display order. Shared with the Settings page. */
export const RESULTS_TABLE_COLUMNS: readonly ResultsTableColumn[] = [
    { id: "package", label: "Package", defaultVisible: true, alwaysVisible: true },
    { id: "excludes", label: "Excludes", defaultVisible: true, presence: "excludes" },
    { id: "scopes", label: "Scopes", defaultVisible: true },
    { id: "level", label: "Dependency level", defaultVisible: true },
    { id: "declaredLicenses", label: "Declared Licenses", defaultVisible: false },
    { id: "effectiveLicense", label: "Effective License", defaultVisible: true },
    { id: "concludedLicenses", label: "Concluded Licenses", defaultVisible: false },
    { id: "detectedLicenses", label: "Detected Licenses", defaultVisible: true },
    { id: "issues", label: "Technical Issues", defaultVisible: false },
    { id: "violations", label: "Policy Violations", defaultVisible: false },
    { id: "vulnerabilities", label: "Vulnerabilities", defaultVisible: false },
    { id: "unmappedDeclaredLicenses", label: "Unmapped Declared Licenses", defaultVisible: false },
    { id: "homepage", label: "Homepage", defaultVisible: false },
    { id: "sources", label: "Sources", defaultVisible: false },
    { id: "labels", label: "Labels", defaultVisible: false },
    { id: "curations", label: "Package Curations", defaultVisible: false, presence: "curations" },
    { id: "configurations", label: "Package Configurations", defaultVisible: false, presence: "configurations" },
];

export interface ResultsTableProps {
    // When set, the table filters the Level column on these dependency kinds on mount.
    focusLevel?: string[];
    // When set, the table filters the given license column on the given license on mount.
    focusLicense?: { columnId: string; license: string };
    // When set, the table searches for this package id and expands its row on mount.
    focusPackageId?: string;
    // Deep-linked inner package-detail tab to open for the focused (expanded) package.
    focusPackageTab?: string;
    // Fired with the expanded package's id (or null when none is expanded) so the URL can track it.
    onFocusPackageChange?: (packageId: string | null) => void;
    // Fired when the focused package's inner detail tab changes.
    onFocusPackageTabChange?: (tab: string | null) => void;
    webAppEvaluatedModel: WebAppEvaluatedModel;
}

type DependencyKind = "project" | "direct" | "transitive";

const DEPENDENCY_KIND_META: Record<DependencyKind, { Icon: LucideIcon; label: string; className: string }> = {
    direct: { Icon: Package, label: "Direct dependency", className: "text-muted-foreground" },
    project: { Icon: FolderGit2, label: "Project", className: "text-muted-foreground" },
    transitive: { Icon: Boxes, label: "Transitive dependency", className: "text-muted-foreground" },
};

const LEVEL_OPTIONS: { label: string; value: DependencyKind }[] = [
    { label: "Project", value: "project" },
    { label: "Direct dependency", value: "direct" },
    { label: "Transitive dependency", value: "transitive" },
];

const YES_NO_OPTIONS = [
    { label: "Yes", value: "yes" },
    { label: "No", value: "no" },
];

const EXCLUDE_OPTIONS = [
    { label: "Excluded", value: "excluded" },
    { label: "Included", value: "included" },
];

interface ResultRow {
    concludedLicenses: string[];
    concludedSimpleExpressions: string[];
    declaredLicenses: string[];
    declaredSimpleExpressions: string[];
    dependencyKind: DependencyKind;
    detectedLicenses: string[];
    detectedSimpleExpressions: string[];
    effectiveLicense: string;
    effectiveSimpleExpressions: string[];
    excludeReasons: string[];
    hasConfigurations: boolean;
    hasCurations: boolean;
    homepage: string;
    id: string;
    isExcluded: boolean;
    issuesCount: number;
    key: string;
    labels: string[];
    pkg: WebAppPackage;
    scopes: string[];
    source: string;
    unmappedDeclaredLicenses: string[];
    violationsCount: number;
    vulnerabilitiesCount: number;
}

function arrFilter(row: Row<ResultRow>, columnId: string, value: unknown): boolean {
    const filter = value as string[] | undefined;
    if (!filter || filter.length === 0) return true;
    const cell = row.getValue<string[]>(columnId) ?? [];
    return filter.some((v) => cell.includes(v));
}

function kindFilter(row: Row<ResultRow>, columnId: string, value: unknown): boolean {
    const filter = value as string[] | undefined;
    if (!filter || filter.length === 0) return true;
    return filter.includes(row.getValue<string>(columnId));
}

// Classify a package by its shallowest role: a project itself, a direct dependency of a project
// (dependency-tree level 0), or otherwise a transitive dependency (only reached at level 1 or deeper).
function classifyDependency(pkg: WebAppPackage): DependencyKind {
    if (pkg.isProject) return "project";
    if (pkg.hasLevel(0)) return "direct";
    return "transitive";
}

function buildRow(pkg: WebAppPackage, ortResult: WebAppEvaluatedModel, index: number): ResultRow {
    // Display columns show the original SPDX license expressions; the filter uses the decomposed simple
    // expressions so a compound expression is filterable by each individual license.
    const declaredLicenses = Array.from(pkg.declaredLicensesMapped).sort();
    const detectedLicenses = Array.from(pkg.detectedLicensesProcessed).sort();
    const declaredSimpleExpressions = Array.from(pkg.declaredSpdxSimpleExpressions).sort();
    const detectedSimpleExpressions = Array.from(pkg.detectedSpdxSimpleExpressions).sort();
    const concludedSimpleExpressions = Array.from(pkg.concludedSpdxSimpleExpressions).sort();
    const effectiveSimpleExpressions = Array.from(pkg.effectiveSpdxSimpleExpressions).sort();
    const labels = Array.from(pkg.labels.entries())
        .map(([labelKey, value]) => `${labelKey}: ${value}`)
        .sort();
    return {
        concludedLicenses: pkg.concludedLicense ? [pkg.concludedLicense] : [NO_ASSERTION],
        concludedSimpleExpressions: concludedSimpleExpressions.length > 0 ? concludedSimpleExpressions : [NO_ASSERTION],
        declaredLicenses: declaredLicenses.length > 0 ? declaredLicenses : [NO_ASSERTION],
        declaredSimpleExpressions: declaredSimpleExpressions.length > 0 ? declaredSimpleExpressions : [NO_ASSERTION],
        dependencyKind: classifyDependency(pkg),
        detectedLicenses: detectedLicenses.length > 0 ? detectedLicenses : [NO_ASSERTION],
        detectedSimpleExpressions: detectedSimpleExpressions.length > 0 ? detectedSimpleExpressions : [NO_ASSERTION],
        effectiveLicense: pkg.effectiveLicense || NO_ASSERTION,
        effectiveSimpleExpressions: effectiveSimpleExpressions.length > 0 ? effectiveSimpleExpressions : [NO_ASSERTION],
        excludeReasons: Array.from(pkg.excludeReasons).sort(),
        hasConfigurations: pkg.hasPackageConfigurations(),
        hasCurations: pkg.hasCurations(),
        homepage: pkg.homepageUrl ?? "",
        id: pkg.id ?? "",
        isExcluded: pkg.isExcluded,
        // The Technical Issues and Policy Violations columns report the open findings at or above the run's
        // severe threshold — matching the expanded-row tab counts — rather than the raw totals.
        issuesCount: pkg.severeOpenIssuesCount,
        key: pkg.key ?? `pkg-${index}`,
        labels,
        pkg,
        scopes: Array.from(pkg.scopeNames ?? []).sort(),
        source: pkg.vcsProcessed.url || pkg.vcs.url || pkg.sourceArtifact?.url || "",
        unmappedDeclaredLicenses: Array.from(pkg.declaredLicensesUnmapped).sort(),
        violationsCount: pkg.severeOpenRuleViolationsCount,
        vulnerabilitiesCount: ortResult.getVulnerabilitiesForPackageIndex(pkg.packageIndex ?? -1).length,
    };
}

function renderCountBadge(count: number): ReactNode {
    return <Badge variant="secondary">{count}</Badge>;
}

function StringBadgeList({ values }: { values: readonly string[] }): JSX.Element | null {
    if (values.length === 0) return null;
    return (
        <div className="flex flex-wrap gap-1">
            {values.map((value) => (
                <Badge className="max-w-[18rem] truncate text-xs" key={value} variant="outline">
                    {value}
                </Badge>
            ))}
        </div>
    );
}

function DependencyKindIcon({ kind }: { kind: DependencyKind }): JSX.Element {
    const { className, Icon, label } = DEPENDENCY_KIND_META[kind];
    return (
        <span className="inline-flex items-center justify-center" title={label}>
            <Icon aria-hidden="true" className={cn("size-4", className)} />
            <span className="sr-only">{label}</span>
        </span>
    );
}

function LinkCell({ url }: { url: string }): JSX.Element | null {
    if (!url) return null;
    // Reuse the shared Url truncate variant so the value shows the external-link icon and ellipsis, with
    // the full URL on hover - matching the package detail panel.
    return <Url className="max-w-[24rem] text-xs" href={url} truncate />;
}

function BooleanIcon({
    falseText,
    trueText,
    value,
}: {
    value: boolean;
    trueText: string;
    falseText: string;
}): JSX.Element {
    const Icon = value ? SquareCheck : Square;
    const text = value ? trueText : falseText;
    return (
        <span className="inline-flex items-center justify-center" title={text}>
            <Icon aria-hidden="true" className="size-4 text-muted-foreground" />
            <span className="sr-only">{text}</span>
        </span>
    );
}

// The expanded package detail is a set of tabs — only the active tab is mounted — so a package with
// many sections stays compact and quick to scan.
function PackageDetailPanel({
    defaultTab,
    onTabChange,
    ortResult,
    pkg,
}: {
    pkg: WebAppPackage;
    ortResult: WebAppEvaluatedModel;
    // Deep-linked inner tab to open on mount, and a callback fired when the inner tab changes.
    defaultTab?: string;
    onTabChange?: (value: string) => void;
}): JSX.Element {
    const issues = pkg.issues;
    const violations = pkg.ruleViolations;
    const vulnerabilities = ortResult.getVulnerabilitiesForPackageIndex(pkg.packageIndex ?? -1);
    const paths = pkg.paths ?? [];

    // The Overview tab combines the package metadata and its licenses; dependency paths have their own tab.
    const overview = (
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
    );

    // Tabs are ordered by how often they are used, per product feedback.
    const tabs: { value: string; label: string; content: ReactNode }[] = [
        { value: "overview", label: "Overview", content: overview },
    ];
    // Always show the Scanner Findings tab (even for scanned-but-clean packages with no findings), but
    // only offer the scanner details when the package actually has scan results.
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
    // Each tab is gated by its open findings, and the content passes the full list so resolved findings
    // still show (marked as resolved) - matching the report-wide tables. The Technical Issues and Policy
    // Violations titles count only the open findings at or above the run's severe threshold.
    if (pkg.openIssuesCount > 0) {
        tabs.push({
            content: <IssuesTable issues={issues} />,
            label: `Technical Issues (${pkg.severeOpenIssuesCount})`,
            value: "issues",
        });
    }
    if (pkg.openRuleViolationsCount > 0) {
        tabs.push({
            content: <RuleViolationsTable ruleViolations={violations} />,
            label: `Policy Violations (${pkg.severeOpenRuleViolationsCount})`,
            value: "violations",
        });
    }
    if (pkg.openVulnerabilitiesCount > 0) {
        tabs.push({
            content: <VulnerabilitiesTable vulnerabilities={vulnerabilities} />,
            label: `Vulnerabilities (${pkg.openVulnerabilitiesCount})`,
            value: "vulnerabilities",
        });
    }

    const firstTab = tabs[0]?.value ?? "overview";
    // Honour a deep-linked tab only when this package actually has it, otherwise fall back to the first.
    const initialTab = defaultTab && tabs.some((tab) => tab.value === defaultTab) ? defaultTab : firstTab;

    return (
        <div className="p-4">
            <Tabs defaultValue={initialTab} {...(onTabChange ? { onValueChange: onTabChange } : {})}>
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

// The main package results table: one row per project/package with license/issue/violation/vulnerability columns and an expandable detail panel.
function ResultsTable({
    focusLevel,
    focusLicense,
    focusPackageId,
    focusPackageTab,
    onFocusPackageChange,
    onFocusPackageTabChange,
    webAppEvaluatedModel,
}: ResultsTableProps): JSX.Element {
    const { settings } = useSettings();
    const data = useMemo<ResultRow[]>(
        () => webAppEvaluatedModel.packages.map((pkg, index) => buildRow(pkg, webAppEvaluatedModel, index)),
        [webAppEvaluatedModel],
    );

    // Report the most-recently-expanded row's package id (or null) so the URL can deep-link the open package.
    const handleTableStateChange = useCallback(
        (state: TableState): void => {
            if (!onFocusPackageChange) return;
            const expandedKey = getActiveExpandedRowId(state.expanded);
            const packageId = expandedKey ? (data.find((row) => row.key === expandedKey)?.id ?? null) : null;
            if (packageId !== (focusPackageId ?? null)) {
                onFocusPackageChange(packageId);
            }
        },
        [data, focusPackageId, onFocusPackageChange],
    );

    const facetOptions = useMemo(() => {
        const declared = new Set<string>();
        const detected = new Set<string>();
        const concluded = new Set<string>();
        const effective = new Set<string>();
        const unmapped = new Set<string>();
        for (const pkg of webAppEvaluatedModel.packages) {
            // License options are the decomposed SPDX simple expressions, so a composite expression is
            // filterable by each identifier. A package missing a license for a field is shown (and
            // therefore filterable) as NOASSERTION.
            const addLicenses = (target: Set<string>, ids: ReadonlySet<string>) => {
                if (ids.size === 0) target.add(NO_ASSERTION);
                for (const id of ids) target.add(id);
            };
            addLicenses(declared, pkg.declaredSpdxSimpleExpressions);
            addLicenses(detected, pkg.detectedSpdxSimpleExpressions);
            addLicenses(concluded, pkg.concludedSpdxSimpleExpressions);
            addLicenses(effective, pkg.effectiveSpdxSimpleExpressions);
            for (const license of pkg.declaredLicensesUnmapped) unmapped.add(license);
        }
        const scopes = Array.from(webAppEvaluatedModel.scopes).map((scope) => scope.name ?? "");
        const mkOption = (value: string) => ({ label: value, value });
        const sortLocale = (a: { label: string }, b: { label: string }) => a.label.localeCompare(b.label);
        return {
            scopes: Array.from(new Set(scopes.filter(Boolean)))
                .map((value) => ({
                    label: value,
                    muted: webAppEvaluatedModel.getScopeByName(value)?.isExcluded ?? false,
                    value,
                }))
                .sort(sortLocale),
            declared: Array.from(declared).map(mkOption).sort(sortLocale),
            detected: Array.from(detected).map(mkOption).sort(sortLocale),
            concluded: Array.from(concluded).map(mkOption).sort(sortLocale),
            effective: Array.from(effective).map(mkOption).sort(sortLocale),
            unmapped: Array.from(unmapped).map(mkOption).sort(sortLocale),
            // The effective simple license ids across all packages, used to mute detected licenses that
            // are not part of any effective license (i.e. no longer applicable).
            effectiveIds: effective as ReadonlySet<string>,
        };
    }, [webAppEvaluatedModel]);

    const columns = useMemo<ColumnDef<ResultRow, unknown>[]>(() => {
        const cols: ColumnDef<ResultRow, unknown>[] = [
            createExpandColumn<ResultRow>({ enableExpandAll: true }),
            {
                id: "package",
                accessorKey: "id",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Package" />
                        <DataTableColumnSearch column={column} title="Package" />
                    </div>
                ),
                cell: ({ row }) => {
                    const { excludeReasons, id, isExcluded } = row.original;
                    const label = (
                        <span
                            className={cn(
                                "break-all font-mono text-xs",
                                isExcluded && "text-muted-foreground line-through decoration-muted-foreground",
                            )}
                        >
                            {id}
                        </span>
                    );
                    if (!isExcluded) return label;
                    // Excluded packages get the same "why is this excluded" tooltip as the Excludes-column icon.
                    const reason = excludeReasons.join(", ");
                    return <span title={reason ? `Excluded: ${reason}` : "Excluded"}>{label}</span>;
                },
                filterFn: "includesString",
                enableColumnFilter: true,
                meta: { label: "Package Id" },
            },
            {
                id: "scopes",
                accessorKey: "scopes",
                // Facet on each array element so the filter dropdown counts every value correctly.
                getUniqueValues: (row) => row.scopes,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Scopes" />
                        <DataTableFacetedFilter column={column} options={facetOptions.scopes} title="Scopes" />
                    </div>
                ),
                cell: ({ row }) => {
                    const scopes = row.original.scopes;
                    if (scopes.length === 0) return null;
                    return (
                        <div className="flex flex-col gap-0.5 break-all font-mono text-xs">
                            {scopes.map((scope) => (
                                <span
                                    className={cn(
                                        (row.original.isExcluded ||
                                            webAppEvaluatedModel.getScopeByName(scope)?.isExcluded) &&
                                            "text-muted-foreground",
                                    )}
                                    key={scope}
                                >
                                    {scope}
                                </span>
                            ))}
                        </div>
                    );
                },
                filterFn: arrFilter,
                enableColumnFilter: true,
                enableSorting: false,
            },
            {
                id: "level",
                accessorKey: "dependencyKind",
                header: ({ column }) => (
                    <div className="flex items-center justify-center gap-1">
                        <IconHeader Icon={Layers} label="Dependency level" />
                        <DataTableFacetedFilter column={column} options={LEVEL_OPTIONS} title="Level" />
                    </div>
                ),
                cell: ({ row }) => <DependencyKindIcon kind={row.original.dependencyKind} />,
                filterFn: kindFilter,
                enableColumnFilter: true,
                enableSorting: false,
                meta: { align: "center", headerClassName: "w-16", cellClassName: "w-16" },
            },
            {
                id: "declaredLicenses",
                accessorFn: (row) => row.declaredSimpleExpressions,
                getUniqueValues: (row) => row.declaredSimpleExpressions,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Declared Licenses" />
                        <DataTableFacetedFilter
                            column={column}
                            options={facetOptions.declared}
                            title="Declared Licenses"
                        />
                    </div>
                ),
                cell: ({ row }) => <LicenseExpressionList expressions={row.original.declaredLicenses} />,
                filterFn: arrFilter,
                enableColumnFilter: true,
                enableSorting: false,
            },
            {
                id: "effectiveLicense",
                accessorFn: (row) => row.effectiveSimpleExpressions,
                getUniqueValues: (row) => row.effectiveSimpleExpressions,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Effective License" />
                        <DataTableFacetedFilter
                            column={column}
                            options={facetOptions.effective}
                            title="Effective License"
                        />
                    </div>
                ),
                cell: ({ row }) => <LicenseExpression expression={row.original.effectiveLicense} />,
                filterFn: arrFilter,
                enableColumnFilter: true,
                enableSorting: false,
            },
            {
                id: "concludedLicenses",
                accessorFn: (row) => row.concludedSimpleExpressions,
                getUniqueValues: (row) => row.concludedSimpleExpressions,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Concluded Licenses" />
                        <DataTableFacetedFilter
                            column={column}
                            options={facetOptions.concluded}
                            title="Concluded Licenses"
                        />
                    </div>
                ),
                cell: ({ row }) => <LicenseExpressionList expressions={row.original.concludedLicenses} />,
                filterFn: arrFilter,
                enableColumnFilter: true,
                enableSorting: false,
            },
            {
                id: "detectedLicenses",
                accessorFn: (row) => row.detectedSimpleExpressions,
                getUniqueValues: (row) => row.detectedSimpleExpressions,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Detected Licenses" />
                        <DataTableFacetedFilter
                            activeValues={facetOptions.effectiveIds}
                            column={column}
                            options={facetOptions.detected}
                            title="Detected Licenses"
                        />
                    </div>
                ),
                cell: ({ row }) => <LicenseExpressionList expressions={row.original.detectedLicenses} />,
                filterFn: arrFilter,
                enableColumnFilter: true,
                enableSorting: false,
            },
            {
                id: "issues",
                accessorKey: "issuesCount",
                header: ({ column }) => <DataTableColumnHeader column={column} icon={Bug} title="Technical Issues" />,
                cell: ({ row }) => renderCountBadge(row.original.issuesCount),
                sortingFn: "basic",
                enableColumnFilter: false,
                meta: { align: "center", label: "Technical Issues" },
            },
            {
                id: "violations",
                accessorKey: "violationsCount",
                header: ({ column }) => (
                    <DataTableColumnHeader column={column} icon={Scale} title="Policy Violations" />
                ),
                cell: ({ row }) => renderCountBadge(row.original.violationsCount),
                sortingFn: "basic",
                enableColumnFilter: false,
                meta: { align: "center", label: "Policy Violations" },
            },
            {
                id: "vulnerabilities",
                accessorKey: "vulnerabilitiesCount",
                header: ({ column }) => (
                    <DataTableColumnHeader column={column} icon={ShieldAlert} title="Vulnerabilities" />
                ),
                cell: ({ row }) => renderCountBadge(row.original.vulnerabilitiesCount),
                sortingFn: "basic",
                enableColumnFilter: false,
                meta: { align: "center" },
            },
            {
                id: "unmappedDeclaredLicenses",
                accessorKey: "unmappedDeclaredLicenses",
                getUniqueValues: (row) => row.unmappedDeclaredLicenses,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Unmapped Declared Licenses" />
                        <DataTableFacetedFilter
                            column={column}
                            options={facetOptions.unmapped}
                            title="Unmapped Declared Licenses"
                        />
                    </div>
                ),
                cell: ({ row }) => {
                    // Unmapped declared licenses are free-form strings (not SPDX ids), so show them as
                    // plain text styled like the Package id rather than as license badges.
                    const values = row.original.unmappedDeclaredLicenses;
                    if (values.length === 0) return null;
                    return (
                        <div className="flex flex-col gap-0.5 break-all font-mono text-xs">
                            {values.map((value) => (
                                <span key={value}>{value}</span>
                            ))}
                        </div>
                    );
                },
                filterFn: arrFilter,
                enableColumnFilter: true,
                enableSorting: false,
            },
            {
                id: "homepage",
                accessorKey: "homepage",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Homepage" />
                        <DataTableColumnSearch column={column} title="Homepage" />
                    </div>
                ),
                cell: ({ row }) => <LinkCell url={row.original.homepage} />,
                filterFn: "includesString",
                enableColumnFilter: true,
            },
            {
                id: "sources",
                accessorKey: "source",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Sources" />
                        <DataTableColumnSearch column={column} title="Sources" />
                    </div>
                ),
                cell: ({ row }) => <LinkCell url={row.original.source} />,
                filterFn: "includesString",
                enableColumnFilter: true,
            },
            {
                id: "labels",
                accessorFn: (row) => row.labels.join(", "),
                header: ({ column }) => <DataTableColumnHeader column={column} title="Labels" />,
                cell: ({ row }) => <StringBadgeList values={row.original.labels} />,
                enableColumnFilter: false,
                enableSorting: false,
            },
        ];

        if (webAppEvaluatedModel.hasExcludes()) {
            // Insert the include/exclude status as the first data column, right after the expand toggle.
            cols.splice(1, 0, {
                id: "excludes",
                accessorFn: (row) => (row.isExcluded ? "excluded" : "included"),
                header: ({ column }) => (
                    <div className="flex items-center justify-center gap-1">
                        <IconHeader Icon={FileX} label="Included / excluded" />
                        <DataTableFacetedFilter column={column} options={EXCLUDE_OPTIONS} title="Excludes" />
                    </div>
                ),
                cell: ({ row }) => (
                    <ExcludeStatusIcon
                        excluded={row.original.isExcluded}
                        reason={row.original.excludeReasons.join(", ")}
                    />
                ),
                filterFn: kindFilter,
                enableColumnFilter: true,
                enableGlobalFilter: false,
                enableSorting: false,
                meta: {
                    align: "center",
                    headerClassName: "w-16",
                    cellClassName: "w-16",
                    label: "Excludes",
                },
            });
        }

        if (webAppEvaluatedModel.hasPackageCurations()) {
            cols.push({
                id: "curations",
                accessorFn: (row) => (row.hasCurations ? "yes" : "no"),
                header: ({ column }) => (
                    <div className="flex items-center justify-center gap-1">
                        <IconHeader Icon={PackageCurationIcon} label="Package Curations" />
                        <DataTableFacetedFilter column={column} options={YES_NO_OPTIONS} title="Curations" />
                    </div>
                ),
                cell: ({ row }) => (
                    <BooleanIcon
                        falseText="No package curations applied"
                        trueText="Package curations have been applied to this package"
                        value={row.original.hasCurations}
                    />
                ),
                filterFn: kindFilter,
                enableColumnFilter: true,
                enableGlobalFilter: false,
                enableSorting: false,
                meta: {
                    align: "center",
                    headerClassName: "w-16",
                    cellClassName: "w-16",
                    label: "Package Curations",
                },
            });
        }

        if (webAppEvaluatedModel.hasPackageConfigurations()) {
            cols.push({
                id: "configurations",
                accessorFn: (row) => (row.hasConfigurations ? "yes" : "no"),
                header: ({ column }) => (
                    <div className="flex items-center justify-center gap-1">
                        <IconHeader Icon={PackageConfigurationIcon} label="Package Configurations" />
                        <DataTableFacetedFilter column={column} options={YES_NO_OPTIONS} title="Configurations" />
                    </div>
                ),
                cell: ({ row }) => (
                    <BooleanIcon
                        falseText="No package configurations applied"
                        trueText="Package configurations have been applied to this package"
                        value={row.original.hasConfigurations}
                    />
                ),
                filterFn: kindFilter,
                enableColumnFilter: true,
                enableGlobalFilter: false,
                enableSorting: false,
                meta: {
                    align: "center",
                    headerClassName: "w-16",
                    cellClassName: "w-16",
                    label: "Package Configurations",
                },
            });
        }

        // Give every column an explicit Customize-Columns label from RESULTS_TABLE_COLUMNS instead of
        // letting the visibility dropdown fall back to a formatted column id; a column's own label wins.
        for (const column of cols) {
            const label = RESULTS_TABLE_COLUMNS.find((entry) => entry.id === column.id)?.label;
            if (label && !column.meta?.label) {
                column.meta = { ...column.meta, label };
            }
        }

        return cols;
    }, [facetOptions, webAppEvaluatedModel]);

    const initialState = useMemo<Partial<TableState>>(() => {
        // Default column visibility comes from Settings when the user has customised it, otherwise from the
        // built-in per-column defaults. Always-visible columns (the package id) are left at their default.
        const visibleIds = new Set(
            settings.defaultVisibleColumns ??
                RESULTS_TABLE_COLUMNS.filter((column) => column.defaultVisible).map((column) => column.id),
        );
        const columnVisibility: NonNullable<TableState["columnVisibility"]> = {};
        for (const column of RESULTS_TABLE_COLUMNS) {
            if (!column.alwaysVisible) {
                columnVisibility[column.id] = visibleIds.has(column.id);
            }
        }
        const state: Partial<TableState> = {
            columnVisibility,
            sorting: [{ id: "package", desc: false }],
        };
        // When navigated here from a finding, search for and expand that package on mount.
        if (focusPackageId) {
            // Ignore an unresolvable pkg-id: only filter/expand when the package actually exists.
            const target = data.find((row) => row.id === focusPackageId);
            if (target) {
                state.globalFilter = focusPackageId;
                state.expanded = { [target.key]: true };
            }
        }
        const columnFilters: TableState["columnFilters"] = [];
        // When navigated here from the Summary licenses, filter (and reveal) the given license column.
        if (focusLicense) {
            columnFilters.push({ id: focusLicense.columnId, value: [focusLicense.license] });
            state.columnVisibility = {
                ...(state.columnVisibility ?? {}),
                [focusLicense.columnId]: true,
            };
        }
        // When navigated here from the Summary projects/dependencies cards, filter the Level column.
        if (focusLevel && focusLevel.length > 0) {
            columnFilters.push({ id: "level", value: focusLevel });
        }
        if (columnFilters.length > 0) {
            state.columnFilters = columnFilters;
        }
        return state;
    }, [data, focusPackageId, focusLicense, focusLevel, settings.defaultVisibleColumns]);

    return (
        <DataTable<ResultRow>
            columns={columns}
            data={data}
            emptyText="No packages"
            expandOnRowClick
            getRowCanExpand={() => true}
            getRowId={(row) => row.key}
            hidePaginationWhenSinglePage
            initialState={initialState}
            onStateChange={handleTableStateChange}
            pageSizeOptions={LARGE_TABLE_PAGE_SIZES}
            renderSubComponent={(row) => {
                // Only the deep-linked (focused) package's panel drives the URL's inner-tab param.
                const isFocused = row.original.id === focusPackageId;
                return (
                    <PackageDetailPanel
                        ortResult={webAppEvaluatedModel}
                        pkg={row.original.pkg}
                        {...(isFocused && focusPackageTab ? { defaultTab: focusPackageTab } : {})}
                        {...(isFocused && onFocusPackageTabChange ? { onTabChange: onFocusPackageTabChange } : {})}
                    />
                );
            }}
        />
    );
}

export { PackageDetailPanel, ResultsTable };
export default ResultsTable;
