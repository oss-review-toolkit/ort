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
class RuleViolationsTable extends React.Component {
    render () {
        const {
            onChange,
            ruleViolations,
            showExcludesColumn,
            state
        } = this.props;
        const {
            filteredInfo = {},
            sortedInfo = {}
        } = state;

        // If return null to prevent React render error
        if (!ruleViolations) {
            return null;
        }

        const columns = [
            {
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
                onFilter: (value, webAppRuleViolation) => webAppRuleViolation.severityIndex === Number(value),
                render: (text, webAppRuleViolation) => (
                    webAppRuleViolation.isResolved
                        ? (
                            <Tooltip
                                placement="right"
                                title={Array.from(webAppRuleViolation.resolutionReasons).join(', ')}
                            >
                                <IssuesCloseOutlined
                                    className="ort-ok"
                                />
                            </Tooltip>
                        ) : (
                            <span>
                                {
                                    webAppRuleViolation.severity === 'ERROR'
                                    && (
                                        <ExclamationCircleOutlined
                                            className="ort-error"
                                        />
                                    )
                                }
                                {
                                    webAppRuleViolation.severity === 'WARNING'
                                    && (
                                        <WarningOutlined
                                            className="ort-warning"
                                        />
                                    )
                                }
                                {
                                    webAppRuleViolation.severity === 'HINT'
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
                onFilter: (value, webAppRuleViolation) => {
                    if (!webAppRuleViolation.hasPackage()) return true;

                    const { isExcluded } = webAppRuleViolation.package;

                    return (isExcluded && value === 'excluded') || (!isExcluded && value === 'included');
                },
                render: (webAppRuleViolation) => {
                    const webAppPackage = webAppRuleViolation.package;

                    if (webAppPackage) {
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
                    }

                    return null;
                },
                responsive: ['md'],
                width: '2em'
            });
        }

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
                ...getColumnSearchProps('packageName', filteredInfo, this)
            },
            {
                dataIndex: 'rule',
                key: 'rule',
                responsive: ['md'],
                sorter: (a, b) => a.rule.localeCompare(b.rule),
                sortOrder: sortedInfo.field === 'rule' && sortedInfo.order,
                title: 'Rule',
                width: '25%',
                ...getColumnSearchProps('rule', filteredInfo, this)
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
                className="ort-table-rule-violations"
                columns={columns}
                dataSource={ruleViolations}
                expandedRowRender={
                    (webAppRuleViolation) => {
                        let defaultActiveKey = [0];
                        const webAppPackage = webAppRuleViolation.package;

                        if (webAppRuleViolation.isResolved) {
                            defaultActiveKey = [1];
                        }

                        return (
                            <Collapse
                                className="ort-package-collapse"
                                bordered={false}
                                defaultActiveKey={defaultActiveKey}
                            >
                                {
                                    webAppRuleViolation.hasHowToFix()
                                    && (
                                        <Panel header="How to fix" key="0">
                                            <Markdown
                                                className="ort-how-to-fix"
                                            >
                                                {webAppRuleViolation.howToFix}
                                            </Markdown>
                                        </Panel>
                                    )
                                }
                                {
                                    webAppRuleViolation.isResolved
                                    && (
                                        <Panel header="Resolutions" key="1">
                                            <ResolutionTable
                                                resolutions={webAppRuleViolation.resolutions}
                                            />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppRuleViolation.hasPackage()
                                    && (
                                        <Panel header="Details" key="2">
                                            <PackageDetails webAppPackage={webAppPackage} />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppRuleViolation.hasPackage()
                                    && webAppPackage.hasLicenses()
                                    && (
                                        <Panel header="Licenses" key="3">
                                            <PackageLicenses webAppPackage={webAppPackage} />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppRuleViolation.hasPackage()
                                    && webAppPackage.hasPaths()
                                    && (
                                        <Panel header="Paths" key="4">
                                            <PackagePaths paths={webAppPackage.paths} />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppRuleViolation.hasPackage()
                                    && webAppPackage.hasFindings()
                                    && (
                                        <Panel header="Scan Results" key="5">
                                            <PackageFindingsTable
                                                webAppPackage={webAppPackage}
                                            />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppRuleViolation.hasPackage()
                                    && webAppPackage.hasPathExcludes()
                                    && (
                                        <Panel header="Path Excludes" key="6">
                                            <PathExcludesTable
                                                excludes={webAppPackage.pathExcludes}
                                            />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppRuleViolation.hasPackage()
                                    && webAppPackage.hasScopeExcludes()
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
                    emptyText: 'No violations'
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
                        showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} violations`
                    }
                }
                rowKey="key"
                size="small"
            />
        );
    };
}

RuleViolationsTable.propTypes = {
    onChange: PropTypes.func.isRequired,
    ruleViolations: PropTypes.array.isRequired,
    showExcludesColumn: PropTypes.bool,
    state: PropTypes.object.isRequired
};

RuleViolationsTable.defaultProps = {
    showExcludesColumn: false
};

export default RuleViolationsTable;
