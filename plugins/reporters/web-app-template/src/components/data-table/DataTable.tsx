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

import type {
    ColumnDef,
    ColumnFiltersState,
    ExpandedState,
    PaginationState,
    Row,
    SortingState,
    TableOptions,
    TableState,
    VisibilityState,
} from "@tanstack/react-table";
import {
    flexRender,
    getCoreRowModel,
    getExpandedRowModel,
    getFacetedMinMaxValues,
    getFacetedRowModel,
    getFacetedUniqueValues,
    getFilteredRowModel,
    getPaginationRowModel,
    getSortedRowModel,
    useReactTable,
} from "@tanstack/react-table";
import { ChevronLeft, ChevronRight, FilterX } from "lucide-react";
import { Fragment, type MouseEvent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { DataTablePagination } from "@/components/data-table/DataTablePagination";
import { DataTableViewOptions } from "@/components/data-table/DataTableViewOptions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

export interface DataTableProps<TData> {
    columns: ColumnDef<TData, unknown>[];
    data: TData[];
    getRowId?: (row: TData, index: number) => string;
    getRowCanExpand?: (row: Row<TData>) => boolean;
    // Returns an optional class applied to a data row's <tr>, e.g. to mute an excluded row.
    getRowClassName?: (row: Row<TData>) => string | undefined;
    renderSubComponent?: (row: Row<TData>) => ReactNode;
    expandOnRowClick?: boolean;
    initialState?: Partial<TableState>;
    state?: Partial<TableState>;
    onStateChange?: (state: TableState) => void;
    enableSorting?: boolean;
    enableFilters?: boolean;
    enableGlobalFilter?: boolean;
    // When false, the "Customize Columns" visibility toggle is hidden from the toolbar.
    enableColumnVisibility?: boolean;
    enablePagination?: boolean;
    // When true, the pagination controls are hidden if all rows fit on a single page.
    hidePaginationWhenSinglePage?: boolean;
    pageSizeOptions?: number[];
    emptyText?: ReactNode;
    className?: string;
}

const DEFAULT_PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

// The page-size choices shared by the large report tables (packages, issues, violations, vulnerabilities,
// findings and the smaller config tables), which routinely hold far more rows than the default table.
export const LARGE_TABLE_PAGE_SIZES = [50, 100, 250, 500, 1000];

function alignClassName(align?: "left" | "center" | "right"): string | undefined {
    if (align === "left") return "text-left";
    if (align === "center") return "text-center";
    if (align === "right") return "text-right";
    return undefined;
}

function DataTable<TData>({
    columns,
    data,
    getRowId,
    getRowCanExpand,
    getRowClassName,
    renderSubComponent,
    expandOnRowClick = false,
    initialState,
    state,
    onStateChange,
    enableSorting = true,
    enableFilters = true,
    enableGlobalFilter = true,
    enableColumnVisibility = true,
    enablePagination = true,
    hidePaginationWhenSinglePage = false,
    pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
    emptyText = "No results.",
    className,
}: DataTableProps<TData>) {
    const defaultPageSize = pageSizeOptions[0] ?? 10;

    const [sorting, setSorting] = useState<SortingState>(initialState?.sorting ?? []);
    const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>(initialState?.columnFilters ?? []);
    const [globalFilter, setGlobalFilter] = useState<string>(initialState?.globalFilter ?? "");
    const [columnVisibility, setColumnVisibility] = useState<VisibilityState>(initialState?.columnVisibility ?? {});
    const [expanded, setExpanded] = useState<ExpandedState>(initialState?.expanded ?? {});
    const [pagination, setPagination] = useState<PaginationState>(
        initialState?.pagination ?? { pageIndex: 0, pageSize: defaultPageSize },
    );

    const scrollRef = useRef<HTMLDivElement | null>(null);
    const [scroll, setScroll] = useState<{ overflow: boolean; canLeft: boolean; canRight: boolean }>({
        overflow: false,
        canLeft: false,
        canRight: false,
    });

    const mergedState = useMemo<Partial<TableState>>(
        () => ({
            sorting,
            columnFilters,
            globalFilter,
            columnVisibility,
            expanded,
            pagination,
            ...state,
        }),
        [sorting, columnFilters, globalFilter, columnVisibility, expanded, pagination, state],
    );

    const tableOptions: TableOptions<TData> = {
        data,
        columns,
        state: mergedState,
        onSortingChange: setSorting,
        onColumnFiltersChange: setColumnFilters,
        onGlobalFilterChange: setGlobalFilter,
        onColumnVisibilityChange: setColumnVisibility,
        onExpandedChange: setExpanded,
        onPaginationChange: setPagination,
        enableSorting,
        enableFilters,
        enableGlobalFilter,
        autoResetPageIndex: true,
        getCoreRowModel: getCoreRowModel(),
        getSortedRowModel: getSortedRowModel(),
        getFilteredRowModel: getFilteredRowModel(),
        getExpandedRowModel: getExpandedRowModel(),
        getFacetedRowModel: getFacetedRowModel(),
        getFacetedUniqueValues: getFacetedUniqueValues(),
        getFacetedMinMaxValues: getFacetedMinMaxValues(),
        meta: { pageSizeOptions },
    };

    if (enablePagination) {
        tableOptions.getPaginationRowModel = getPaginationRowModel();
    }
    if (getRowId) {
        tableOptions.getRowId = getRowId;
    }
    if (getRowCanExpand) {
        tableOptions.getRowCanExpand = getRowCanExpand;
    }

    const table = useReactTable<TData>(tableOptions);

    // biome-ignore lint/correctness/useExhaustiveDependencies: emit a TableState snapshot whenever any underlying slice changes.
    useEffect(() => {
        onStateChange?.(table.getState());
    }, [mergedState, onStateChange, table]);

    // Track horizontal overflow so the top scroll arrows only appear when the columns exceed the visible width.
    const updateScroll = useCallback(() => {
        const el = scrollRef.current;
        if (!el) return;
        const maxScrollLeft = el.scrollWidth - el.clientWidth;
        setScroll({
            overflow: maxScrollLeft > 1,
            canLeft: el.scrollLeft > 1,
            canRight: el.scrollLeft < maxScrollLeft - 1,
        });
    }, []);

    useEffect(() => {
        const el = scrollRef.current;
        if (!el) return;
        updateScroll();
        el.addEventListener("scroll", updateScroll, { passive: true });
        const observer = new ResizeObserver(updateScroll);
        observer.observe(el);
        // Observing the inner table catches width changes when columns are toggled on/off.
        const inner = el.querySelector("table");
        if (inner) observer.observe(inner);
        return () => {
            el.removeEventListener("scroll", updateScroll);
            observer.disconnect();
        };
    }, [updateScroll]);

    const scrollColumns = (direction: -1 | 1): void => {
        const el = scrollRef.current;
        if (!el) return;
        const amount = Math.max(160, Math.round(el.clientWidth * 0.8));
        el.scrollBy({ left: direction * amount, behavior: "smooth" });
    };

    const visibleColumnCount = table.getVisibleLeafColumns().length;
    const rows = table.getRowModel().rows;

    const isFiltered = table.getState().columnFilters.length > 0 || globalFilter.length > 0;
    const showToolbar = enableGlobalFilter || scroll.overflow || isFiltered;

    const handleRowClick = (row: Row<TData>) => (event: MouseEvent<HTMLTableRowElement>) => {
        if (!row.getCanExpand()) return;
        const target = event.target as HTMLElement;
        if (target.closest("button, a, [role='checkbox']")) return;
        row.toggleExpanded();
    };

    return (
        <div className={cn("space-y-3", className)}>
            {showToolbar && (
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    {enableGlobalFilter ? (
                        <Input
                            aria-label="Search"
                            className="h-8 w-full sm:w-[280px]"
                            onChange={(event) => setGlobalFilter(event.target.value)}
                            placeholder="Search..."
                            value={globalFilter}
                        />
                    ) : (
                        <div />
                    )}
                    <div className="flex items-center gap-2">
                        {scroll.overflow && (
                            <div className="flex items-center gap-1">
                                <Button
                                    aria-label="Scroll columns left"
                                    disabled={!scroll.canLeft}
                                    onClick={() => scrollColumns(-1)}
                                    size="icon-sm"
                                    title="Scroll columns left"
                                    type="button"
                                    variant="outline"
                                >
                                    <ChevronLeft aria-hidden="true" className="size-4" />
                                </Button>
                                <Button
                                    aria-label="Scroll columns right"
                                    disabled={!scroll.canRight}
                                    onClick={() => scrollColumns(1)}
                                    size="icon-sm"
                                    title="Scroll columns right"
                                    type="button"
                                    variant="outline"
                                >
                                    <ChevronRight aria-hidden="true" className="size-4" />
                                </Button>
                            </div>
                        )}
                        {enableColumnVisibility ? <DataTableViewOptions table={table} /> : null}
                        {isFiltered ? (
                            <Button
                                className="h-8"
                                onClick={() => {
                                    table.resetColumnFilters();
                                    setGlobalFilter("");
                                }}
                                size="sm"
                                type="button"
                                variant="outline"
                            >
                                <FilterX aria-hidden="true" className="mr-2 size-4" />
                                Reset
                            </Button>
                        ) : null}
                    </div>
                </div>
            )}
            <div className="rounded-md border">
                <Table containerRef={scrollRef}>
                    <TableHeader>
                        {table.getHeaderGroups().map((headerGroup) => (
                            <TableRow key={headerGroup.id}>
                                {headerGroup.headers.map((header) => {
                                    const meta = header.column.columnDef.meta;
                                    return (
                                        <TableHead
                                            className={cn(alignClassName(meta?.align), meta?.headerClassName)}
                                            colSpan={header.colSpan}
                                            key={header.id}
                                        >
                                            {header.isPlaceholder
                                                ? null
                                                : flexRender(header.column.columnDef.header, header.getContext())}
                                        </TableHead>
                                    );
                                })}
                            </TableRow>
                        ))}
                    </TableHeader>
                    <TableBody>
                        {rows.length === 0 ? (
                            <TableRow>
                                <TableCell className="h-24 text-center" colSpan={visibleColumnCount}>
                                    {emptyText}
                                </TableCell>
                            </TableRow>
                        ) : (
                            rows.map((row) => {
                                const canExpand = row.getCanExpand();
                                const clickable = expandOnRowClick && canExpand;
                                return (
                                    <Fragment key={row.id}>
                                        <TableRow
                                            aria-expanded={canExpand ? row.getIsExpanded() : undefined}
                                            className={cn(clickable && "cursor-pointer", getRowClassName?.(row))}
                                            data-state={row.getIsSelected() ? "selected" : undefined}
                                            onClick={clickable ? handleRowClick(row) : undefined}
                                        >
                                            {row.getVisibleCells().map((cell) => {
                                                const meta = cell.column.columnDef.meta;
                                                return (
                                                    <TableCell
                                                        className={cn(alignClassName(meta?.align), meta?.cellClassName)}
                                                        key={cell.id}
                                                    >
                                                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                                    </TableCell>
                                                );
                                            })}
                                        </TableRow>
                                        {row.getIsExpanded() && renderSubComponent ? (
                                            <TableRow data-slot="data-table-sub-row">
                                                {/* Expanded rows hold rich, multi-line content, so override the cell's
                                                    default whitespace-nowrap/align-middle - otherwise the content cannot
                                                    wrap and blows the table wider than its container. */}
                                                <TableCell
                                                    className="whitespace-normal bg-muted/30 align-top"
                                                    colSpan={visibleColumnCount}
                                                >
                                                    {renderSubComponent(row)}
                                                </TableCell>
                                            </TableRow>
                                        ) : null}
                                    </Fragment>
                                );
                            })
                        )}
                    </TableBody>
                </Table>
            </div>
            {enablePagination && (!hidePaginationWhenSinglePage || table.getPageCount() > 1) && (
                <DataTablePagination pageSizeOptions={pageSizeOptions} table={table} />
            )}
        </div>
    );
}

export { DataTable };
export default DataTable;
