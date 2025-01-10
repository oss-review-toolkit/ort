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
    ExclamationCircleOutlined,
    FileAddOutlined,
    FileExcelOutlined,
    InfoCircleOutlined,
    IssuesCloseOutlined,
    WarningOutlined
} from '@ant-design/icons';
import { Collapse, Table, Tooltip } from 'antd';
import Markdown from 'markdown-to-jsx';

import PackageDetails from './PackageDetails';
import PackageFindingsTable from './PackageFindingsTable';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PathExcludesTable from './PathExcludesTable';
import ResolutionTable from './ResolutionTable';
import ScopeExcludesTable from './ScopeExcludesTable';
import { getColumnSearchProps } from './Shared';

// Generates the HTML to display violations as a table
const RuleViolationsTable = ({ webAppRuleViolations = [], showExcludesColumn = true }) => {
    // Convert rule violations as Antd only accepts vanilla objects as input
    const violations = useMemo(
        () => {
            return webAppRuleViolations
                .map(
                    (webAppRuleViolation) => ({
                        isResolved: webAppRuleViolation.isResolved,
                        key: webAppRuleViolation.key,
                        message: webAppRuleViolation.message,
                        packageName: webAppRuleViolation.package.id,
                        rule: webAppRuleViolation.rule,
                        severity: webAppRuleViolation.severity,
                        severityIndex: webAppRuleViolation.severityIndex,
                        webAppRuleViolation
                    })
                )
        },
        []
    );

    /* === Table state handling === */

    // State variable for displaying table in various pages
    const [pagination, setPagination] = useState({ current: 1, pageSize: 100 });

    // State variable for filtering the contents of table columns
    const filteredInfoDefault = {
        excludes: [],
        message: [],
        packageName: [],
        rule: [],
        severityIndex: []
    };
    const [filteredInfo, setFilteredInfo] = useState(filteredInfoDefault);

    // State variable for sorting table columns
    const [sortedInfo, setSortedInfo] = useState({});

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
                    value: 'excluded'
                },
                {
                    text: (
                        <span>
                            <FileAddOutlined />
                            {' '}
                            Included
                        </span>
                    ),
                    value: 'included'
                }
            ])(),
            filteredValue: filteredInfo.excludes || null,
            key: 'excludes',
            onFilter: (value, record) => {
                if (!record.webAppRuleViolation.hasPackage()) return true;

                const { isExcluded } = record.webAppRuleViolation.package;

                return (isExcluded && value === 'excluded') || (!isExcluded && value === 'included');
            },
            render: (record) => {
                const webAppPackage = record.webAppRuleViolation.package;

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
                text: 'Errors',
                value: 0
            },
            {
                text: 'Warnings',
                value: 1
            },
            {
                text: 'Hint',
                value: 2
            },
            {
                text: 'Resolved',
                value: 3
            }
        ],
        filteredValue: filteredInfo.severityIndex || null,
        onFilter: (value, record) => record.severityIndex === Number(value),
        render: (text, record) => (
            record.isResolved
                ? (
                    <Tooltip
                        placement="right"
                        title={Array.from(record.webAppRuleViolation.resolutionReasons).join(', ')}
                    >
                        <IssuesCloseOutlined
                            className="ort-ok"
                        />
                    </Tooltip>
                    )
                : (
                    <span>
                        {
                            record.severity === 'ERROR'
                            && (
                                <ExclamationCircleOutlined
                                    className="ort-error"
                                />
                            )
                        }
                        {
                            record.severity === 'WARNING'
                            && (
                                <WarningOutlined
                                    className="ort-warning"
                                />
                            )
                        }
                        {
                            record.severity === 'HINT'
                            && (
                                <InfoCircleOutlined
                                    className="ort-hint"
                                />
                            )
                        }
                    </span>
                    )
        ),
        sorter: (a, b) => a.severityIndex - b.severityIndex,
        sortOrder: sortedInfo.field === 'severityIndex' && sortedInfo.order,
        width: '5em'
    });

    columns.push(
        {
            dataIndex: 'packageName',
            ellipsis: true,
            key: 'packageName',
            responsive: ['md'],
            sorter: (a, b) => a.packageName.localeCompare(b.packageName),
            sortOrder: sortedInfo.field === 'packageName' && sortedInfo.order,
            title: 'Package',
            width: '25%',
            ...getColumnSearchProps(
                'packageName',
                filteredInfo.packageName,
                (value) => setFilteredInfo({ ...filteredInfo, packageName: value })
            )
        },
        {
            dataIndex: 'rule',
            key: 'rule',
            responsive: ['md'],
            sorter: (a, b) => a.rule.localeCompare(b.rule),
            sortOrder: sortedInfo.field === 'rule' && sortedInfo.order,
            title: 'Rule',
            width: '25%',
            ...getColumnSearchProps(
                'rule',
                filteredInfo.rule,
                (value) => setFilteredInfo({ ...filteredInfo, rule: value })
            )
        },
        {
            dataIndex: 'message',
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
            className="ort-table-rule-violations"
            columns={columns}
            dataSource={violations}
            rowKey="key"
            size="small"
            expandable={{
                expandedRowRender: (record) => {
                    let defaultActiveKey = [0];
                    const webAppRuleViolation = record.webAppRuleViolation;
                    const webAppPackage = webAppRuleViolation.package;

                    if (webAppRuleViolation.isResolved) {
                        defaultActiveKey = [1];
                    }

                    return (
                        <Collapse
                            className="ort-package-collapse"
                            bordered={false}
                            defaultActiveKey={defaultActiveKey}
                            items={(() => {
                                const collapseItems = [];

                                if (webAppRuleViolation.hasHowToFix()) {
                                    collapseItems.push({
                                        label: 'How to fix',
                                        key: 'rule-violation-how-to-fix',
                                        children: (
                                            <Markdown className="ort-how-to-fix">
                                                {webAppRuleViolation.howToFix}
                                            </Markdown>
                                        )
                                    });
                                }

                                if (webAppRuleViolation.isResolved) {
                                    collapseItems.push({
                                        label: 'Resolutions',
                                        key: 'rule-violation-resolutions',
                                        children: (
                                            <ResolutionTable
                                                resolutions={webAppRuleViolation.resolutions}
                                            />
                                        )
                                    });
                                }

                                if (webAppRuleViolation.hasPackage()) {
                                    collapseItems.push({
                                        label: 'Details',
                                        key: 'rule-violation-package-details',
                                        children: (
                                            <PackageDetails webAppPackage={webAppPackage} />
                                        )
                                    });

                                    if (webAppPackage.hasLicenses()) {
                                        collapseItems.push({
                                            label: 'Licenses',
                                            key: 'rule-violation-package-licenses',
                                            children: (
                                                <PackageLicenses webAppPackage={webAppPackage} />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasPaths()) {
                                        collapseItems.push({
                                            label: 'Paths',
                                            key: 'rule-violation-package-paths',
                                            children: (
                                                <PackagePaths paths={webAppPackage.paths} />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasFindings()) {
                                        collapseItems.push({
                                            label: 'Scan Results',
                                            key: 'rule-violation-package-scan-results',
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
                                            key: 'rule-violation-package-path-excludes',
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
                                            key: 'rule-violation-package-scope-excludes',
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
                emptyText: 'No violations'
            }}
            pagination={
                {
                    current: pagination.current,
                    hideOnSinglePage: true,
                    onChange: handlePaginationChange,
                    pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                    position: 'bottom',
                    showQuickJumper: true,
                    showSizeChanger: true,
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} violations`
                }
            }
            onChange={handleTableChange}
        />
    );
}

export default RuleViolationsTable;
