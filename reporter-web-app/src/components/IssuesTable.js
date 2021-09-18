/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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
import PropTypes from 'prop-types';
import { Collapse, Table, Tooltip } from 'antd';
import {
    ExclamationCircleOutlined,
    FileAddOutlined,
    FileExcelOutlined,
    InfoCircleOutlined,
    IssuesCloseOutlined,
    WarningOutlined
} from '@ant-design/icons';

import Markdown from 'markdown-to-jsx';
import PackageDetails from './PackageDetails';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PackageFindingsTable from './PackageFindingsTable';
import PathExcludesTable from './PathExcludesTable';
import ResolutionTable from './ResolutionTable';
import ScopeExcludesTable from './ScopeExcludesTable';
import { getColumnSearchProps } from './Shared';

const { Panel } = Collapse;

// Generates the HTML to display violations as a Table
class IssuesTable extends React.Component {
    render () {
        const {
            issues,
            onChange,
            showExcludesColumn,
            state: {
                filteredInfo = {},
                sortedInfo =  {}
            }
        } = this.props;

        // If return null to prevent React render error
        if (!issues) {
            return null;
        }

        const columns = [
            {
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
                        ) : (
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
            }
        ];

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
                    const webAppPackage = webAppOrtIssue.package;

                    if (value === 'excluded') {
                        return webAppPackage.isExcluded;
                    }

                    if (value === 'included') {
                        return !webAppPackage.isExcluded;
                    }

                    return false;
                },
                render: (webAppOrtIssue) => {
                    const webAppPackage = webAppOrtIssue.package;

                    return webAppPackage.isExcluded ? (
                        <span className="ort-excludes">
                            <Tooltip
                                placement="right"
                                title={Array.from(webAppPackage.excludeReasons).join(', ')}
                            >
                                <FileExcelOutlined className="ort-excluded" />
                            </Tooltip>
                        </span>
                    ) : (
                        <FileAddOutlined />
                    );
                },
                width: '2em'
            });
        }

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
                ...getColumnSearchProps('message', filteredInfo, this)
            }
        );

        return (
            <Table
                className="ort-table-issues"
                columns={columns}
                dataSource={issues}
                expandedRowRender={
                    (webAppOrtIssue) => {
                        const defaultActiveKey = [1];
                        const webAppPackage = webAppOrtIssue.package;

                        if (webAppOrtIssue.isResolved) {
                            defaultActiveKey.unshift(0);
                        }

                        return (
                            <Collapse
                                className="ort-package-collapse"
                                bordered={false}
                                defaultActiveKey={defaultActiveKey}
                            >
                                {
                                    webAppOrtIssue.hasHowToFix()
                                    && (
                                        <Panel header="How to fix" key="0">
                                            <Markdown
                                                className="ort-how-to-fix"
                                            >
                                                {webAppOrtIssue.howToFix}
                                            </Markdown>
                                        </Panel>
                                    )
                                }
                                {
                                    webAppOrtIssue.isResolved
                                    && (
                                        <Panel header="Resolutions" key="1">
                                            <ResolutionTable
                                                resolutions={webAppOrtIssue.resolutions}
                                            />
                                        </Panel>
                                    )
                                }
                                <Panel header="Details" key="2">
                                    <PackageDetails webAppPackage={webAppPackage} />
                                </Panel>
                                {
                                    webAppPackage.hasLicenses()
                                    && (
                                        <Panel header="Licenses" key="3">
                                            <PackageLicenses webAppPackage={webAppPackage} />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppPackage.hasPaths()
                                    && (
                                        <Panel header="Paths" key="4">
                                            <PackagePaths paths={webAppPackage.paths} />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppPackage.hasFindings()
                                    && (
                                        <Panel header="Scan Results" key="5">
                                            <PackageFindingsTable
                                                webAppPackage={webAppPackage}
                                            />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppPackage.hasPathExcludes()
                                    && (
                                        <Panel header="Path Excludes" key="6">
                                            <PathExcludesTable
                                                excludes={webAppPackage.pathExcludes}
                                            />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppPackage.hasScopeExcludes()
                                    && (
                                        <Panel header="Scope Excludes" key="7">
                                            <ScopeExcludesTable
                                                excludes={webAppPackage.scopeExcludes}
                                            />
                                        </Panel>
                                    )
                                }
                            </Collapse>
                        );
                    }
                }
                locale={{
                    emptyText: 'No issues'
                }}
                onChange={onChange}
                pagination={
                    {
                        defaultPageSize: 25,
                        hideOnSinglePage: true,
                        pageSizeOptions: ['50', '100', '250', '500'],
                        position: 'bottom',
                        showQuickJumper: true,
                        showSizeChanger: true,
                        showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} issues`
                    }
                }
                rowKey="key"
                size="small"
            />
        );
    }
};

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
