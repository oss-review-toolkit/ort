/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import {
    useMemo,
    useState
} from 'react';

import {
    FileAddOutlined,
    FileExcelOutlined
} from '@ant-design/icons';
import {
    Collapse,
    Table,
    Tag,
    Tooltip
} from 'antd';
import Markdown from 'markdown-to-jsx';

import PackageDetails from './PackageDetails';
import PackageFindingsTable from './PackageFindingsTable';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PathExcludesTable from './PathExcludesTable';
import ResolutionTable from './ResolutionTable';
import ScopeExcludesTable from './ScopeExcludesTable';
import SeverityTag from './SeverityTag';
import { getColumnSearchProps } from './Shared';

// Generates the HTML to display issues as a table
const IssuesTable = ({ webAppOrtIssues = [], showExcludesColumn = true }) => {
    // Convert issues as Antd only accepts vanilla objects as input
    const issues = useMemo(
        () => {
            return webAppOrtIssues
                .map(
                    (webAppOrtIssue) => ({
                        isResolved: webAppOrtIssue.isResolved,
                        key: webAppOrtIssue.key,
                        message: webAppOrtIssue.message,
                        packageId: webAppOrtIssue.package.id,
                        severity: webAppOrtIssue.severity,
                        severityIndex: webAppOrtIssue.severityIndex,
                        source: webAppOrtIssue.source,
                        webAppOrtIssue
                    })
                )
        },
        []
    );

    /* === Table state handling === */

    // State variable for displaying table in various pages
    const [pagination, setPagination] = useState({ current: 1, pageSize: 100 });

    // State variable for filtering the contents of table columns
    const [filteredInfo, setFilteredInfo] = useState({
        excludes: [],
        message: [],
        packageId: [],
        source: [],
        severityIndex: []
    });

    // State variable for sorting table columns
    const [sortedInfo, setSortedInfo] = useState({
        columnKey: 'severityIndex',
        field: 'severityIndex',
        order: 'ascend'
    });

    /* === Table columns === */
    const columns = [];

    if (showExcludesColumn) {
        columns.push({
            align: 'right',
            filters: (() => [
                {
                    text: (
                        <span>
                            <FileExcelOutlined className="ort-excluded" />
                            {' '}
                            Excluded
                        </span>
                    ),
                    value: true
                },
                {
                    text: (
                        <span>
                            <FileAddOutlined />
                            {' '}
                            Included
                        </span>
                    ),
                    value: false
                }
            ])(),
            filteredValue: filteredInfo.excludes || null,
            key: 'excludes',
            onFilter: (value, record) => {
                if (!record.webAppOrtIssue.hasPackage()) return false;

                const { isExcluded } = record.webAppOrtIssue.package;

                return isExcluded === value;
            },
            render: (record) => {
                const webAppPackage = record.webAppOrtIssue.package;

                if (webAppPackage) {
                    return webAppPackage.isExcluded
                        ? (
                        <span className="ort-excludes">
                            <Tooltip
                                placement="right"
                                title={Array.from(webAppPackage.excludeReasons).join(', ')}
                            >
                                <FileExcelOutlined className="ort-excluded" />
                            </Tooltip>
                        </span>
                            )
                        : (
                        <FileAddOutlined />
                            );
                }

                return null;
            },
            responsive: ['md'],
            width: '2em'
        });
    }

    columns.push({
        align: 'center',
        dataIndex: 'severityIndex',
        key: 'severityIndex',
        filters: [
            {
                text: (<SeverityTag severity={'error'} />),
                value: 0
            },
            {
                text: (<SeverityTag severity={'warning'} />),
                value: 1
            },
            {
                text: (<SeverityTag severity={'hint'} />),
                value: 2
            },
            {
                text: (<Tag color="#b0c4de">Resolved</Tag>),
                value: 10
            }
        ],
        filteredValue: filteredInfo.severityIndex || null,
        onFilter: (value, record) => record.severityIndex === Number(value)
            || (record.severityIndex > 10 && Number(value) >= 10),
        render: (text, record) => (
            record.isResolved
                ? (
                    <SeverityTag
                        isResolved={true}
                        severity={record.severity.toLowerCase()}
                        tooltipText={
                            `Resolved with ${Array.from(record.webAppOrtIssue.resolutionReasons).join(', ')
                            } resolution${
                                record.webAppOrtIssue.resolutionReasons.size > 0 ? 's' : ''
                            }`
                        }
                    />
                    )
                : (
                    <span>
                        {
                            record.severity === 'ERROR'
                            && !record.isResolved
                            && (
                                <SeverityTag
                                    severity={'error'}
                                />
                            )
                        }
                        {
                            record.severity === 'WARNING'
                            && !record.isResolved
                            && (
                                <SeverityTag
                                    severity={'warning'}
                                />
                            )
                        }
                        {
                            record.severity === 'HINT'
                            && !record.isResolved
                            && (
                                <SeverityTag
                                    severity={'hint'}
                                />
                            )
                        }
                    </span>
                    )
        ),
        sorter: (a, b) => a.severityIndex - b.severityIndex,
        sortOrder: sortedInfo.field === 'severityIndex' && sortedInfo.order,
        title: 'Severity',
        width: '8em'
    });

    columns.push(
        {
            dataIndex: 'packageId',
            ellipsis: true,
            filteredValue: filteredInfo.packageId || null,
            key: 'packageId',
            responsive: ['md'],
            sorter: (a, b) => a.packageId.localeCompare(b.packageId),
            sortOrder: sortedInfo.field === 'packageId' && sortedInfo.order,
            title: 'Package',
            width: '25%',
            ...getColumnSearchProps(
                'packageId',
                filteredInfo.packageId,
                (value) => setFilteredInfo({ ...filteredInfo, packageId: value })
            )
        },
        {
            dataIndex: 'source',
            filteredValue: filteredInfo.source || null,
            key: 'source',
            responsive: ['md'],
            sorter: (a, b) => a.source.localeCompare(b.source),
            sortOrder: sortedInfo.field === 'source' && sortedInfo.order,
            title: 'Source',
            width: '25%',
            ...getColumnSearchProps(
                'source',
                filteredInfo.source,
                (value) => setFilteredInfo({ ...filteredInfo, source: value })
            )
        },
        {
            dataIndex: 'message',
            filteredValue: filteredInfo.message || null,
            key: 'message',
            textWrap: 'word-break',
            title: 'Message',
            ...getColumnSearchProps(
                'message',
                filteredInfo.message,
                (value) => setFilteredInfo({ ...filteredInfo, message: value })
            )
        }
    );

    // Handle for table pagination changes
    const handlePaginationChange = (page, pageSize) => {
        setPagination({ current: page, pageSize });
    };

    // Handle for any table content changes
    const handleTableChange = (pagination, filters, sorter) => {
        setFilteredInfo(filters);
        setSortedInfo(sorter);
    };

    return (
        <Table
            className="ort-table-issues"
            columns={columns}
            dataSource={issues}
            rowKey="key"
            size="small"
            expandable={{
                expandedRowRender: (record) => {
                    const webAppOrtIssue = record.webAppOrtIssue;
                    const webAppPackage = webAppOrtIssue.package;
                    let defaultActiveKey = record.isResolved
                        ? 'issue-resolutions'
                        : 'issue-package-details';

                    if (webAppOrtIssue.hasHowToFix() && !record.isResolved) {
                        defaultActiveKey = 'issue-how-to-fix';
                    }

                    return (
                        <Collapse
                            className="ort-package-collapse"
                            bordered={false}
                            defaultActiveKey={defaultActiveKey}
                            items={(() => {
                                const collapseItems = [];

                                if (webAppOrtIssue.hasHowToFix()) {
                                    collapseItems.push({
                                        label: 'How to fix',
                                        key: 'issue-how-to-fix',
                                        children: (
                                            <Markdown className="ort-how-to-fix">
                                                {webAppOrtIssue.howToFix}
                                            </Markdown>
                                        )
                                    });
                                }

                                if (webAppOrtIssue.isResolved) {
                                    collapseItems.push({
                                        label: 'Resolutions',
                                        key: 'issue-resolutions',
                                        children: (
                                            <ResolutionTable
                                                resolutions={webAppOrtIssue.resolutions}
                                            />
                                        )
                                    });
                                }

                                if (webAppOrtIssue.hasPackage()) {
                                    collapseItems.push({
                                        label: 'Details',
                                        key: 'issue-package-details',
                                        children: (
                                            <PackageDetails webAppPackage={webAppPackage} />
                                        )
                                    });

                                    if (webAppPackage.hasLicenses()) {
                                        collapseItems.push({
                                            label: 'Licenses',
                                            key: 'issue-package-licenses',
                                            children: (
                                                <PackageLicenses webAppPackage={webAppPackage} />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasPaths()) {
                                        collapseItems.push({
                                            label: 'Paths',
                                            key: 'issue-package-paths',
                                            children: (
                                                <PackagePaths paths={webAppPackage.paths} />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasFindings()) {
                                        collapseItems.push({
                                            label: 'Scan Results',
                                            key: 'issue-package-scan-results',
                                            children: (
                                                <PackageFindingsTable
                                                    webAppPackage={webAppPackage}
                                                />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasPathExcludes()) {
                                        collapseItems.push({
                                            label: 'Path Excludes',
                                            key: 'issue-package-path-excludes',
                                            children: (
                                                <PathExcludesTable
                                                    excludes={webAppPackage.pathExcludes}
                                                />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasScopeExcludes()) {
                                        collapseItems.push({
                                            label: 'Scope Excludes',
                                            key: 'issue-package-scope-excludes',
                                            children: (
                                                <ScopeExcludesTable
                                                    excludes={webAppPackage.scopeExcludes}
                                                />
                                            )
                                        });
                                    }
                                }

                                return collapseItems;
                            })()}
                        />
                    );
                }
            }}
            locale={{
                emptyText: 'No issues'
            }}
            pagination={
                {
                    current: pagination.current,
                    hideOnSinglePage: true,
                    onChange: handlePaginationChange,
                    pageSize: pagination.pageSize,
                    pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                    position: 'bottom',
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} issues`
                }
            }
            onChange={handleTableChange}
        />
    );
}

export default IssuesTable;
