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
    CloseCircleOutlined,
    ExclamationCircleOutlined,
    FileAddOutlined,
    FileExcelOutlined,
    FileProtectOutlined,
    InfoCircleOutlined,
    IssuesCloseOutlined,
    QuestionCircleOutlined,
    WarningOutlined
} from '@ant-design/icons';
import { Collapse, Table, Tooltip } from 'antd';
import PropTypes from 'prop-types';

import PackageDetails from './PackageDetails';
import PackagePaths from './PackagePaths';
import PathExcludesTable from './PathExcludesTable';
import ResolutionTable from './ResolutionTable';
import ScopeExcludesTable from './ScopeExcludesTable';
import { getColumnSearchProps } from './Shared';

// Generates the HTML to display vulnerabilities as a Table
class VulnerabilitiesTable extends React.Component {
    render() {
        const {
            onChange,
            vulnerabilities,
            showExcludesColumn,
            state
        } = this.props;
        const {
            filteredInfo = {},
            sortedInfo = {}
        } = state;

        const renderAhref = (element, href, key, target = '_blank') => (
            <a
                href={href}
                key={key}
                rel="noopener noreferrer"
                target={target}
            >
                {element}
            </a>
        );

        // If return null to prevent React render error
        if (!vulnerabilities) {
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
                onFilter: (value, webAppVulnerability) => {
                    const { isExcluded } = webAppVulnerability.package;

                    return (isExcluded && value === 'excluded') || (!isExcluded && value === 'included');
                },
                render: (webAppVulnerability) => {
                    const webAppPackage = webAppVulnerability.package;

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
                    text: 'Critical',
                    value: 0
                },
                {
                    text: 'High',
                    value: 1
                },
                {
                    text: 'Medium',
                    value: 2
                },
                {
                    text: 'Low',
                    value: 3
                },
                {
                    text: 'Resolved',
                    value: 5
                },
                {
                    text: 'Unknown',
                    value: 4
                }
            ],
            filteredValue: filteredInfo.severityIndex || null,
            onFilter: (value, webAppVulnerability) => webAppVulnerability.severityIndex === Number(value),
            render: (text, webAppVulnerability) => (
                webAppVulnerability.isResolved
                    ? (
                        <Tooltip
                            placement="right"
                            title={Array.from(webAppVulnerability.resolutionReasons).join(', ')}
                        >
                            <IssuesCloseOutlined
                                className="ort-ok"
                            />
                        </Tooltip>
                        )
                    : (
                        <span>
                            {
                                webAppVulnerability.severityIndex === 0
                                && (
                                    <CloseCircleOutlined
                                        className="ort-critical"
                                    />
                                )
                            }
                            {
                                webAppVulnerability.severityIndex === 1
                                && (
                                    <ExclamationCircleOutlined
                                        className="ort-high"
                                    />
                                )
                            }
                            {
                                webAppVulnerability.severityIndex === 2
                                && (
                                    <WarningOutlined
                                        className="ort-medium"
                                    />
                                )
                            }
                            {
                                webAppVulnerability.severityIndex === 3
                                && (
                                    <InfoCircleOutlined
                                        className="ort-low"
                                    />
                                )
                            }
                            {
                                webAppVulnerability.severityIndex === 4
                                && (
                                    <QuestionCircleOutlined
                                        className="ort-unknown"
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
                ...getColumnSearchProps('packageName', filteredInfo, this)
            },
            {
                dataIndex: 'id',
                key: 'id',
                responsive: ['md'],
                sorter: (a, b) => a.id.localeCompare(b.id),
                sortOrder: sortedInfo.field === 'id' && sortedInfo.order,
                title: 'Id',
                ...getColumnSearchProps('id', filteredInfo, this)
            },
            {
                dataIndex: 'references',
                key: 'references',
                render: (references) => {
                    if (references.length === 0) {
                        return null;
                    }

                    const domainRegex = /(?:[\w-]+\.)+[\w-]+/;

                    return (
                        <span>
                            {
                                references.map((reference, index) =>
                                    renderAhref(
                                        (
                                            <Tooltip
                                                placement="top"
                                                title={domainRegex.exec(reference.url)}
                                            >
                                                <FileProtectOutlined />
                                            </Tooltip>
                                        ),
                                        reference.url,
                                        `ort-vulnerability-ref-${index}`
                                    )
                                )
                            }
                        </span>
                    );
                },
                responsive: ['md'],
                title: 'References'
            }
        );

        return (
            <Table
                className="ort-table-vulnerabilities"
                columns={columns}
                dataSource={vulnerabilities}
                rowKey="key"
                size="small"
                expandable={{
                    expandedRowRender: (webAppVulnerability) => {
                        const defaultActiveKey = webAppVulnerability.isResolved
                            ? 'vulnerability-resolutions'
                            : 'vulnerability-package-details';
                        const webAppPackage = webAppVulnerability.package;

                        return (
                            <Collapse
                                className="ort-package-collapse"
                                bordered={false}
                                defaultActiveKey={defaultActiveKey}
                                items={(() => {
                                    const collapseItems = [];

                                    if (webAppVulnerability.isResolved) {
                                        collapseItems.push({
                                            label: 'Resolutions',
                                            key: 'vulnerability-resolutions',
                                            children: (
                                                <ResolutionTable
                                                    resolutions={webAppVulnerability.resolutions}
                                                />
                                            )
                                        });
                                    }

                                    collapseItems.push({
                                        label: 'Details',
                                        key: 'vulnerability-package-details',
                                        children: (
                                            <PackageDetails webAppPackage={webAppPackage} />
                                        )
                                    });

                                    if (webAppPackage.hasPaths()) {
                                        collapseItems.push({
                                            label: 'Paths',
                                            key: 'vulnerability-package-path',
                                            children: (
                                                <PackagePaths paths={webAppPackage.paths} />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasPathExcludes()) {
                                        collapseItems.push({
                                            label: 'Path Excludes',
                                            key: 'vulnerability-package-path-excludes',
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
                                            key: 'vulnerability-package-scope-excludes',
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
                    emptyText: 'No vulnerabilities'
                }}
                pagination={
                    {
                        defaultPageSize: 25,
                        hideOnSinglePage: true,
                        pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                        position: 'bottom',
                        showQuickJumper: true,
                        showSizeChanger: true,
                        showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} vulnerabilities`
                    }
                }
                onChange={onChange}
            />
        );
    }
}

VulnerabilitiesTable.propTypes = {
    onChange: PropTypes.func.isRequired,
    vulnerabilities: PropTypes.array.isRequired,
    showExcludesColumn: PropTypes.bool,
    state: PropTypes.object.isRequired
};

VulnerabilitiesTable.defaultProps = {
    showExcludesColumn: false
};

export default VulnerabilitiesTable;
