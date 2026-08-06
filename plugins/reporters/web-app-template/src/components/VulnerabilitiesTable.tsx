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
import type { JSX } from "react";
import { Fragment, useCallback, useMemo } from "react";

import { CvssVectorChart, type CvssVectorInput } from "@/components/CvssVectorChart";
import { PackageDetails } from "@/components/PackageDetails";
import { PackagePaths } from "@/components/PackagePaths";
import { MarkdownText, Url } from "@/components/Shared";
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
import {
    indexToRating,
    VulnerabilityRatingBadge,
    type VulnerabilityRatingValue,
} from "@/components/VulnerabilityRatingBadge";
import { normalizeDescription } from "@/lib/markdown";
import type VulnerabilityReference from "@/models/VulnerabilityReference";
import type WebAppVulnerability from "@/models/WebAppVulnerability";
import type { CvssScoreGroup } from "@/models/WebAppVulnerability";

export interface VulnerabilitiesTableProps {
    focusPackageId?: string;
    // When set, expand the vulnerability row matching this advisory id (scoped to focusPackageId) on mount.
    focusVulnerabilityId?: string;
    // Fired with the expanded vulnerability's advisory id and affected package id (or nulls when none is
    // expanded) so the URL can deep-link the exact row.
    onFocusVulnerabilityChange?: (vulnerabilityId: string | null, packageId: string | null) => void;
    vulnerabilities: readonly WebAppVulnerability[];
}

interface VulnerabilityRow {
    description: string;
    id: string;
    isResolved: boolean;
    key: string;
    packageName: string;
    rating: VulnerabilityRatingValue;
    resolutionReasons: string;
    severityIndex: number;
    summary: string;
    vulnerability: WebAppVulnerability;
}

// Gather every CVSS vector carried by the references so the chart can offer a per-version switch.
function collectCvssVectors(references: readonly VulnerabilityReference[]): CvssVectorInput[] {
    const vectors: CvssVectorInput[] = [];
    for (const reference of references) {
        if (reference.vector) {
            vectors.push({ score: reference.score, system: reference.scoringSystem, vector: reference.vector });
        }
    }
    return vectors;
}

function titleCaseRating(rating: string): string {
    return rating.charAt(0).toUpperCase() + rating.slice(1).toLowerCase();
}

// Render the (already de-duplicated) score groups of one CVSS version, e.g. "7.1" or, when a version
// reported more than one rating, "High 7.1, Critical 9.8".
function formatCvssScore(groups: readonly CvssScoreGroup[]): string {
    const scored = groups.filter((group) => group.score !== undefined);
    const multipleRatings = new Set(scored.map((group) => group.rating)).size > 1;
    return scored
        .map((group) =>
            multipleRatings && group.rating ? `${titleCaseRating(group.rating)} ${group.score}` : `${group.score}`,
        )
        .join(", ");
}

const RATING_OPTIONS = [
    { label: "Critical", value: "CRITICAL" },
    { label: "High", value: "HIGH" },
    { label: "Medium", value: "MEDIUM" },
    { label: "Low", value: "LOW" },
    { label: "None", value: "NONE" },
];

function buildRow(vulnerability: WebAppVulnerability, index: number): VulnerabilityRow {
    const reasons = Array.from(vulnerability.resolutionReasons ?? []);
    return {
        description: vulnerability.description ?? "",
        id: vulnerability.id ?? "",
        isResolved: vulnerability.isResolved,
        key: vulnerability.key ?? `vuln-${index}`,
        packageName: vulnerability.packageName,
        rating: indexToRating(vulnerability.severityIndex),
        resolutionReasons: reasons.length > 0 ? `Resolved with ${reasons.join(", ")}` : "",
        severityIndex: vulnerability.severityIndex,
        summary: vulnerability.summary ?? "",
        vulnerability,
    };
}

function renderSubRow(row: Row<VulnerabilityRow>): JSX.Element {
    const { description, id, isResolved, packageName, resolutionReasons, summary, vulnerability } = row.original;
    const cvssVectors = collectCvssVectors(vulnerability.references);
    const normalizedDescription = description ? normalizeDescription(description) : undefined;
    const pkg = vulnerability.package;
    const paths = pkg?.paths ?? [];
    // "Highest CVSS" is redundant when only one distinct score was reported (that score is the highest by
    // definition and is already shown per version), so only surface it when several scores differ.
    const distinctCvssScores = new Set(
        vulnerability.cvssSummaries.map((cvss) => formatCvssScore(cvss.groups)).filter(Boolean),
    );
    const showHighestCvss = vulnerability.highestCvss !== undefined && distinctCvssScores.size > 1;
    return (
        <div className="p-4 text-sm">
            <Tabs defaultValue="advisory">
                <TabsList className="flex h-auto flex-wrap">
                    <TabsTrigger value="advisory">Advisory</TabsTrigger>
                    {pkg ? <TabsTrigger value="package">Package Details</TabsTrigger> : null}
                    {pkg && paths.length > 0 ? <TabsTrigger value="paths">Dependency Paths</TabsTrigger> : null}
                </TabsList>
                <TabsContent className="mt-4 space-y-4" value="advisory">
                    {/* Two-column split like the Overview tab; the expanded cell wraps its text (see DataTable),
                        so the two columns share the available width. */}
                    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                        {/* Left column: Id, Summary, Description, Resolution. */}
                        <div className="min-w-0 space-y-3">
                            <div className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1">
                                {id ? (
                                    <>
                                        <span className="font-medium text-muted-foreground">Id</span>
                                        <span className="break-all font-mono">{id}</span>
                                    </>
                                ) : null}
                                {packageName ? (
                                    <>
                                        <span className="font-medium text-muted-foreground">Package</span>
                                        <span className="break-all font-mono">{packageName}</span>
                                    </>
                                ) : null}
                                {vulnerability.ratings.length > 0 ? (
                                    <>
                                        <span className="font-medium text-muted-foreground">Rating</span>
                                        <span>{vulnerability.ratings.map(titleCaseRating).join(", ")}</span>
                                    </>
                                ) : null}
                                {showHighestCvss && vulnerability.highestCvss ? (
                                    <>
                                        <span className="font-medium text-muted-foreground">Highest CVSS</span>
                                        <span>{`${vulnerability.highestCvss.score} (CVSS ${vulnerability.highestCvss.label})`}</span>
                                    </>
                                ) : null}
                                {vulnerability.epss ? (
                                    <>
                                        <span className="font-medium text-muted-foreground">EPSS</span>
                                        <span>
                                            {`${(vulnerability.epss.score * 100).toFixed(2)}% (percentile ${vulnerability.epss.percentile})`}
                                        </span>
                                    </>
                                ) : null}
                                {vulnerability.cvssSummaries.map((cvss) => {
                                    const score = formatCvssScore(cvss.groups);
                                    return (
                                        <Fragment key={cvss.label}>
                                            {score ? (
                                                <>
                                                    <span className="font-medium text-muted-foreground">{`CVSS ${cvss.label} Score`}</span>
                                                    <span>{score}</span>
                                                </>
                                            ) : null}
                                            {cvss.vectors.length > 0 ? (
                                                <>
                                                    <span className="font-medium text-muted-foreground">{`CVSS ${cvss.label} Vector`}</span>
                                                    <span className="break-all font-mono">
                                                        {cvss.vectors.join(", ")}
                                                    </span>
                                                </>
                                            ) : null}
                                        </Fragment>
                                    );
                                })}
                            </div>
                            {summary ? (
                                <div>
                                    <h4 className="mb-1 font-semibold text-muted-foreground text-xs uppercase">
                                        Summary
                                    </h4>
                                    <p className="leading-relaxed">{summary}</p>
                                </div>
                            ) : null}
                            {normalizedDescription?.content ? (
                                <div>
                                    <h4 className="mb-1 font-semibold text-muted-foreground text-xs uppercase">
                                        Description
                                    </h4>
                                    {normalizedDescription.isMarkdown ? (
                                        <MarkdownText>{normalizedDescription.content}</MarkdownText>
                                    ) : (
                                        <p className="whitespace-pre-wrap leading-relaxed">
                                            {normalizedDescription.content}
                                        </p>
                                    )}
                                </div>
                            ) : null}
                            {isResolved && resolutionReasons ? (
                                <div>
                                    <h4 className="mb-1 font-semibold text-muted-foreground text-xs uppercase">
                                        Resolution
                                    </h4>
                                    <p className="leading-relaxed">{resolutionReasons}</p>
                                </div>
                            ) : null}
                        </div>
                        {/* Right column: only the CVSS chart (it renders its own "CVSS Score N" title). */}
                        {cvssVectors.length > 0 ? (
                            <div className="min-w-0">
                                <CvssVectorChart className="mx-auto max-w-xs" vectors={cvssVectors} />
                            </div>
                        ) : null}
                    </div>
                    <div className="min-w-0">
                        <h4 className="mb-1 font-semibold text-muted-foreground text-xs uppercase">References</h4>
                        {vulnerability.referenceUrls.length > 0 ? (
                            <ul className="space-y-1">
                                {vulnerability.referenceUrls.map((url) => (
                                    <li className="min-w-0" key={url}>
                                        <Url className="break-all" href={url} />
                                    </li>
                                ))}
                            </ul>
                        ) : (
                            <p className="text-muted-foreground">No references.</p>
                        )}
                    </div>
                </TabsContent>
                {pkg ? (
                    <TabsContent className="mt-4" value="package">
                        <PackageDetails pkg={pkg} />
                    </TabsContent>
                ) : null}
                {pkg && paths.length > 0 ? (
                    <TabsContent className="mt-4" value="paths">
                        <PackagePaths paths={paths} />
                    </TabsContent>
                ) : null}
            </Tabs>
        </div>
    );
}

// The Vulnerabilities table: one row per advisory, expandable to advisory/CVSS details and package tabs.
function VulnerabilitiesTable({
    focusPackageId,
    focusVulnerabilityId,
    onFocusVulnerabilityChange,
    vulnerabilities,
}: VulnerabilitiesTableProps): JSX.Element {
    const data = useMemo<VulnerabilityRow[]>(
        () => vulnerabilities.map((vulnerability, index) => buildRow(vulnerability, index)),
        [vulnerabilities],
    );

    // Expand the deep-linked vulnerability row on mount (matched by advisory id, scoped by package when set).
    const initialState = useMemo<Partial<TableState>>(() => {
        const state: Partial<TableState> = { sorting: [{ id: "rating", desc: false }] };
        if (focusVulnerabilityId) {
            const target = data.find(
                (row) => row.id === focusVulnerabilityId && (!focusPackageId || row.packageName === focusPackageId),
            );
            if (target) {
                state.expanded = { [target.key]: true };
            }
        }
        return state;
    }, [data, focusVulnerabilityId, focusPackageId]);

    // Report the most-recently-expanded row's advisory id and affected package id (or nulls) so the URL can track it.
    const handleStateChange = useCallback(
        (state: TableState): void => {
            if (!onFocusVulnerabilityChange) return;
            const expandedKey = getActiveExpandedRowId(state.expanded);
            const row = expandedKey ? data.find((r) => r.key === expandedKey) : undefined;
            const vulnerabilityId = row?.id || null;
            const packageId = row?.packageName || null;
            if (vulnerabilityId !== (focusVulnerabilityId ?? null) || packageId !== (focusPackageId ?? null)) {
                onFocusVulnerabilityChange(vulnerabilityId, packageId);
            }
        },
        [data, focusVulnerabilityId, focusPackageId, onFocusVulnerabilityChange],
    );

    const columns = useMemo<ColumnDef<VulnerabilityRow, unknown>[]>(
        () => [
            createExpandColumn<VulnerabilityRow>({ enableExpandAll: true }),
            {
                id: "rating",
                meta: { label: "Rating" },
                accessorFn: (row) => row.rating,
                header: ({ column }) => (
                    <div className="flex items-center gap-2">
                        <DataTableColumnHeader column={column} title="Rating" />
                        <DataTableFacetedFilter column={column} options={RATING_OPTIONS} title="Rating" />
                    </div>
                ),
                cell: ({ row }) => (
                    <VulnerabilityRatingBadge
                        isResolved={row.original.isResolved}
                        rating={row.original.rating}
                        tooltipText={row.original.resolutionReasons}
                    />
                ),
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
                cell: ({ row }) => <span className="break-all font-mono text-xs">{row.original.packageName}</span>,
                filterFn: "includesString",
                enableColumnFilter: true,
                meta: { label: "Package Id" },
            },
            {
                id: "id",
                accessorKey: "id",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Id" />
                        <DataTableColumnSearch column={column} title="Id" />
                    </div>
                ),
                cell: ({ row }) => <span className="font-mono text-xs">{row.original.id}</span>,
                filterFn: "includesString",
                enableColumnFilter: true,
                meta: { label: "Vulnerability Id" },
            },
            {
                id: "highestCvss",
                accessorFn: (row) => row.vulnerability.highestCvss?.score ?? -1,
                header: ({ column }) => <DataTableColumnHeader column={column} title="Highest CVSS" />,
                cell: ({ row }) => {
                    const highest = row.original.vulnerability.highestCvss;
                    return highest ? (
                        <span className="whitespace-nowrap tabular-nums">
                            {highest.score}
                            <span className="ml-1 text-muted-foreground text-xs">{`(CVSS ${highest.label})`}</span>
                        </span>
                    ) : (
                        <span className="text-muted-foreground">—</span>
                    );
                },
                sortingFn: (a, b) =>
                    (a.original.vulnerability.highestCvss?.score ?? -1) -
                    (b.original.vulnerability.highestCvss?.score ?? -1),
                enableColumnFilter: false,
                meta: { label: "Highest CVSS" },
            },
            {
                id: "summary",
                meta: { label: "Summary" },
                accessorKey: "summary",
                header: ({ column }) => (
                    <div className="flex items-center gap-0.5">
                        <DataTableColumnHeader column={column} title="Summary" />
                        <DataTableColumnSearch column={column} title="Summary" />
                    </div>
                ),
                cell: ({ row }) => <span className="line-clamp-2 text-xs leading-relaxed">{row.original.summary}</span>,
                enableSorting: false,
                filterFn: "includesString",
                enableColumnFilter: true,
            },
        ],
        [],
    );

    return (
        <DataTable<VulnerabilityRow>
            columns={columns}
            data={data}
            emptyText="No vulnerabilities"
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

export { VulnerabilitiesTable };
export default VulnerabilitiesTable;
