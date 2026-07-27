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

import type { Column } from "@tanstack/react-table";
import { ChevronDown, ChevronsUpDown, ChevronUp, type LucideIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface DataTableColumnHeaderProps<TData, TValue> {
    column: Column<TData, TValue>;
    title: string;
    // When set, the header shows this icon instead of the title text, with a tooltip showing the
    // column's display name (the same label used by the column-visibility "View" menu).
    icon?: LucideIcon;
    className?: string;
}

export function DataTableColumnHeader<TData, TValue>({
    column,
    title,
    icon: HeaderIcon,
    className,
}: DataTableColumnHeaderProps<TData, TValue>) {
    const content = HeaderIcon ? (
        <>
            <HeaderIcon aria-hidden="true" className="size-4" />
            <span className="sr-only">{title}</span>
        </>
    ) : (
        <span>{title}</span>
    );

    const sorted = column.getIsSorted();
    const SortIcon = sorted === "asc" ? ChevronUp : sorted === "desc" ? ChevronDown : ChevronsUpDown;
    const handleSort = () => {
        if (sorted === false) {
            column.toggleSorting(false);
        } else if (sorted === "asc") {
            column.toggleSorting(true);
        } else {
            column.clearSorting();
        }
    };

    const header = column.getCanSort() ? (
        <Button
            aria-label={`Sort by ${title}`}
            className={cn(HeaderIcon ? "h-8" : "-ml-3 h-8", "data-[state=open]:bg-accent", className)}
            onClick={handleSort}
            size="sm"
            variant="ghost"
        >
            {content}
            <SortIcon aria-hidden="true" className="ml-2 size-4" />
        </Button>
    ) : (
        <span className={cn("inline-flex items-center font-medium text-sm", className)}>{content}</span>
    );

    if (!HeaderIcon) {
        return header;
    }

    // Show the same name the column-visibility menu uses (meta.label, else the title).
    const tooltipLabel = column.columnDef.meta?.label ?? title;
    return <span title={tooltipLabel}>{header}</span>;
}
