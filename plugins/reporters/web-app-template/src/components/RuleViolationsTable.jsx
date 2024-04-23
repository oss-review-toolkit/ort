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

// Generates the HTML to display violations as a Table
class RuleViolationsTable extends React.Component {
    render() {
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
                onFilter: (value, webAppRuleViolation) => {
                    if (!webAppRuleViolation.hasPackage()) return true;

                    const { isExcluded } = webAppRuleViolation.package;

                    return (isExcluded && value === 'excluded') || (!isExcluded && value === 'included');
                },
                render: (webAppRuleViolation) => {
                    const webAppPackage = webAppRuleViolation.package;

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
                        )
                    : (
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
                rowKey="key"
                size="small"
                expandable={{
                    expandedRowRender: (webAppRuleViolation) => {
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
                                    }

                                    if (webAppRuleViolation.hasPackage() && webAppPackage.hasLicenses()) {
                                        collapseItems.push({
                                            label: 'Licenses',
                                            key: 'rule-violation-package-licenses',
                                            children: (
                                                <PackageLicenses webAppPackage={webAppPackage} />
                                            )
                                        });
                                    }

                                    if (webAppRuleViolation.hasPackage() && webAppPackage.hasPaths()) {
                                        collapseItems.push({
                                            label: 'Paths',
                                            key: 'rule-violation-package-paths',
                                            children: (
                                                <PackagePaths paths={webAppPackage.paths} />
                                            )
                                        });
                                    }

                                    if (webAppRuleViolation.hasPackage() && webAppPackage.hasFindings()) {
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

                                    if (webAppRuleViolation.hasPackage() && webAppPackage.hasPathExcludes()) {
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

                                    if (webAppRuleViolation.hasPackage() && webAppPackage.hasScopeExcludes()) {
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
                        defaultPageSize: 25,
                        hideOnSinglePage: true,
                        pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                        position: 'bottom',
                        showQuickJumper: true,
                        showSizeChanger: true,
                        showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} violations`
                    }
                }
                onChange={onChange}
            />
        );
    }
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
