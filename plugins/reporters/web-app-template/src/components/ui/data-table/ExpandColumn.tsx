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

import type { ColumnDef } from "@tanstack/react-table";
import { ChevronsDownUp, ChevronsUpDown } from "lucide-react";

import { ExpandRowIcon } from "@/components/Shared";
import { Button } from "@/components/ui/Button";

interface ExpandColumnOptions {
    // When true, the column header shows a toggle that expands or collapses every row at once.
    enableExpandAll?: boolean;
}

// The leading expand/collapse toggle column shared by every expandable data table: a ghost chevron
// button that toggles the row's expanded state. It never hides, sorts, or filters and stays width-capped.
// With `enableExpandAll`, the header gains a matching toggle that expands or collapses all rows at once.
export function createExpandColumn<TData>(options?: ExpandColumnOptions): ColumnDef<TData, unknown> {
    return {
        id: "expand",
        enableHiding: false,
        header: options?.enableExpandAll
            ? ({ table }) => {
                  const allExpanded = table.getIsAllRowsExpanded();
                  return (
                      <Button
                          aria-label={allExpanded ? "Collapse all rows" : "Expand all rows"}
                          className="size-6"
                          onClick={table.getToggleAllRowsExpandedHandler()}
                          size="icon"
                          title={allExpanded ? "Collapse all rows" : "Expand all rows"}
                          type="button"
                          variant="ghost"
                      >
                          {allExpanded ? (
                              <ChevronsDownUp aria-hidden="true" className="size-4" />
                          ) : (
                              <ChevronsUpDown aria-hidden="true" className="size-4" />
                          )}
                      </Button>
                  );
              }
            : () => null,
        cell: ({ row }) => (
            <Button
                aria-label={row.getIsExpanded() ? "Collapse row" : "Expand row"}
                className="size-6"
                onClick={(event) => {
                    event.stopPropagation();
                    row.toggleExpanded();
                }}
                size="icon"
                variant="ghost"
            >
                <ExpandRowIcon expanded={row.getIsExpanded()} />
            </Button>
        ),
        enableSorting: false,
        enableColumnFilter: false,
        meta: { headerClassName: "w-8", cellClassName: "w-8" },
    };
}
