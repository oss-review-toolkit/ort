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

import React from 'react';

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
import PropTypes from 'prop-types';

import PackageDetails from './PackageDetails';
import PackageFindingsTable from './PackageFindingsTable';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PathExcludesTable from './PathExcludesTable';
import ResolutionTable from './ResolutionTable';
import ScopeExcludesTable from './ScopeExcludesTable';
import { getColumnSearchProps } from './Shared';

// Generates the HTML to display issues as a Table
class IssuesTable extends React.Component {
    render() {
        const {
            issues,
            onChange,
            showExcludesColumn,
            state: {
                filteredInfo = {},
                sortedInfo = {}
            }
        } = this.props;

        // If return null to prevent React render error
        if (!issues) {
            return null;
        }

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
                onFilter: (value, webAppOrtIssue) => {
                    if (value === 'excluded') {
                        return webAppOrtIssue.isExcluded;
                    }

                    if (value === 'included') {
                        return !webAppOrtIssue.isExcluded;
                    }

                    return false;
                },
                render: (webAppOrtIssue) => {
                    const webAppPackage = webAppOrtIssue.package;

                    return webAppOrtIssue.isExcluded
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
                },
                width: '2em'
            });
        }

        columns.push({
            align: 'center',
            dataIndex: 'severityIndex',
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
            onFilter: (value, webAppOrtIssue) => webAppOrtIssue.severityIndex === Number(value),
            render: (text, webAppOrtIssue) => (
                webAppOrtIssue.isResolved
                    ? (
                        <Tooltip
                            placement="right"
                            title={Array.from(webAppOrtIssue.resolutionReasons).join(', ')}
                        >
                            <IssuesCloseOutlined
                                className="ort-ok"
                            />
                        </Tooltip>
                        )
                    : (
                        <span>
                            {
                                webAppOrtIssue.severity === 'ERROR'
                                && (
                                    <ExclamationCircleOutlined
                                        className="ort-error"
                                    />
                                )
                            }
                            {
                                webAppOrtIssue.severity === 'WARNING'
                                && (
                                    <WarningOutlined
                                        className="ort-warning"
                                    />
                                )
                            }
                            {
                                webAppOrtIssue.severity === 'HINT'
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
                ellipsis: true,
                dataIndex: 'packageName',
                key: 'packageName',
                sorter: (a, b) => a.packageName.localeCompare(b.packageName),
                sortOrder: sortedInfo.field === 'packageName' && sortedInfo.order,
                title: 'Package',
                width: '25%',
                ...getColumnSearchProps('packageName', filteredInfo, this)
            },
            {
                dataIndex: 'message',
                key: 'message',
                textWrap: 'word-break',
                title: 'Message',
                render: (text) => <pre>{text}</pre>,
                ...getColumnSearchProps('message', filteredInfo, this)
            }
        );

        return (
            <Table
                className="ort-table-issues"
                columns={columns}
                dataSource={issues}
                rowKey="key"
                size="small"
                expandable={{
                    expandedRowRender: (webAppOrtIssue) => {
                        const defaultActiveKey = webAppOrtIssue.isResolved
                            ? 'issue-how-to-fix'
                            : 'issue-package-details';
                        const webAppPackage = webAppOrtIssue.package;

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
                        defaultPageSize: 25,
                        hideOnSinglePage: true,
                        pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                        position: 'bottom',
                        showQuickJumper: true,
                        showSizeChanger: true,
                        showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} issues`
                    }
                }
                onChange={onChange}
            />
        );
    }
}

IssuesTable.propTypes = {
    issues: PropTypes.array.isRequired,
    onChange: PropTypes.func.isRequired,
    showExcludesColumn: PropTypes.bool,
    state: PropTypes.object.isRequired
};

IssuesTable.defaultProps = {
    showExcludesColumn: false
};

export default IssuesTable;
