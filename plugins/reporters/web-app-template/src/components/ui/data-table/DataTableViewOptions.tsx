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
import { Settings2 } from "lucide-react";

import { Button } from "@/components/ui/Button";
import {
    DropdownMenu,
    DropdownMenuCheckboxItem,
    DropdownMenuContent,
    DropdownMenuTrigger,
} from "@/components/ui/DropdownMenu";

interface DataTableViewOptionsProps<TData> {
    table: Table<TData>;
}

function resolveColumnLabel<TData>(column: ReturnType<Table<TData>["getAllLeafColumns"]>[number]): string {
    const label = column.columnDef.meta?.label;
    if (label) {
        return label;
    }
    const header = column.columnDef.header;
    if (typeof header === "string" && header.length > 0) {
        return header;
    }
    // Most columns use a JSX header, so fall back to the id: split camelCase into words (e.g.
    // "detectedLicenses" -> "detected Licenses") and let the `capitalize` class title-case them.
    return column.id.replace(/([a-z0-9])([A-Z])/g, "$1 $2");
}

export function DataTableViewOptions<TData>({ table }: DataTableViewOptionsProps<TData>) {
    const hideableColumns = table
        .getAllLeafColumns()
        .filter((column) => column.getCanHide())
        .sort((a, b) => resolveColumnLabel(a).localeCompare(resolveColumnLabel(b)));

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button className="ml-auto h-8" size="sm" variant="outline">
                    <Settings2 aria-hidden="true" className="mr-2 size-4" />
                    Customize Columns
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-[180px]">
                {hideableColumns.length === 0 ? (
                    <div className="px-2 py-1.5 text-muted-foreground text-sm">No toggleable columns.</div>
                ) : (
                    hideableColumns.map((column) => (
                        <DropdownMenuCheckboxItem
                            checked={column.getIsVisible()}
                            className="capitalize"
                            key={column.id}
                            onCheckedChange={(value) => column.toggleVisibility(Boolean(value))}
                        >
                            {resolveColumnLabel(column)}
                        </DropdownMenuCheckboxItem>
                    ))
                )}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
