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

import type { Table } from "@tanstack/react-table";
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

interface DataTablePaginationProps<TData> {
    table: Table<TData>;
    pageSizeOptions?: number[];
}

const DEFAULT_PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

export function DataTablePagination<TData>({ table, pageSizeOptions }: DataTablePaginationProps<TData>) {
    const meta = table.options.meta as { pageSizeOptions?: number[] } | undefined;
    const options = pageSizeOptions ?? meta?.pageSizeOptions ?? DEFAULT_PAGE_SIZE_OPTIONS;

    const pageSize = table.getState().pagination.pageSize;
    const pageIndex = table.getState().pagination.pageIndex;
    const pageCount = Math.max(table.getPageCount(), 1);
    const filteredRowCount = table.getFilteredRowModel().rows.length;
    const selectedRowCount = table.getFilteredSelectedRowModel().rows.length;

    return (
        <div className="flex flex-col gap-3 px-2 sm:flex-row sm:items-center sm:justify-between">
            <div className="text-muted-foreground text-sm">
                {selectedRowCount > 0
                    ? `${selectedRowCount} of ${filteredRowCount} row(s) selected`
                    : `${filteredRowCount} row(s) shown`}
            </div>
            <div className="flex flex-wrap items-center gap-4 sm:gap-6 lg:gap-8">
                <div className="flex items-center gap-2">
                    <p className="font-medium text-sm">Rows per page</p>
                    <Select
                        onValueChange={(value) => {
                            table.setPageSize(Number(value));
                        }}
                        value={`${pageSize}`}
                    >
                        <SelectTrigger className="h-8 w-[72px]" size="sm">
                            <SelectValue placeholder={`${pageSize}`} />
                        </SelectTrigger>
                        <SelectContent side="top">
                            {options.map((size) => (
                                <SelectItem key={size} value={`${size}`}>
                                    {size}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
                <div className="flex w-[110px] items-center justify-center font-medium text-sm">
                    Page {pageIndex + 1} of {pageCount}
                </div>
                <div className="flex items-center gap-2">
                    <Button
                        aria-label="Go to first page"
                        className="hidden lg:flex"
                        disabled={!table.getCanPreviousPage()}
                        onClick={() => table.setPageIndex(0)}
                        size="icon-sm"
                        variant="outline"
                    >
                        <ChevronsLeft aria-hidden="true" className="size-4" />
                    </Button>
                    <Button
                        aria-label="Go to previous page"
                        disabled={!table.getCanPreviousPage()}
                        onClick={() => table.previousPage()}
                        size="icon-sm"
                        variant="outline"
                    >
                        <ChevronLeft aria-hidden="true" className="size-4" />
                    </Button>
                    <Button
                        aria-label="Go to next page"
                        disabled={!table.getCanNextPage()}
                        onClick={() => table.nextPage()}
                        size="icon-sm"
                        variant="outline"
                    >
                        <ChevronRight aria-hidden="true" className="size-4" />
                    </Button>
                    <Button
                        aria-label="Go to last page"
                        className="hidden lg:flex"
                        disabled={!table.getCanNextPage()}
                        onClick={() => table.setPageIndex(pageCount - 1)}
                        size="icon-sm"
                        variant="outline"
                    >
                        <ChevronsRight aria-hidden="true" className="size-4" />
                    </Button>
                </div>
            </div>
        </div>
    );
}
