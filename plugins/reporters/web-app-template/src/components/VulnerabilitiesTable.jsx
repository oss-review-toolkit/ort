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
    FileExcelOutlined,
    FileProtectOutlined
} from '@ant-design/icons';
import {
    Collapse,
    Table,
    Tag,
    Tooltip
} from 'antd';

import PackageDetails from './PackageDetails';
import PackagePaths from './PackagePaths';
import PathExcludesTable from './PathExcludesTable';
import ScopeExcludesTable from './ScopeExcludesTable';
import { getColumnSearchProps } from './Shared';
import VulnerabilitiesResolutionTable from './VulnerabilitiesResolutionTable';
import VulnerabilityRatingTag from './VulnerabilityRatingTag';
import VulnerabilityReferencesList from './VulnerabilityReferencesList';

// Generates the HTML to display vulnerabilities as a table
const VulnerabilitiesTable = ({ webAppVulnerabilities = [], showExcludesColumn = true }) => {
    // Convert issues as Antd only accepts vanilla objects as input
    const vulnerabilities = useMemo(
        () => {
            return webAppVulnerabilities
                .map(
                    (webAppVulnerability) => ({
                        id: webAppVulnerability.id,
                        isResolved: webAppVulnerability.isResolved,
                        key: webAppVulnerability.key,
                        packageId: webAppVulnerability.package.id,
                        references: webAppVulnerability.references,
                        resolutionReasonsText: `Resolved with ${
                            Array.from(webAppVulnerability.resolutionReasons).join(', ')
                        } resolution${
                            webAppVulnerability.resolutionReasons.size > 0 ? 's' : ''
                        }`,
                        severity: webAppVulnerability.severity,
                        severityIndex: webAppVulnerability.severityIndex,
                        webAppVulnerability
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
        id: [],
        packageId: [],
        references: [],
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
                const { isExcluded } = record.webAppVulnerability.package;

                return isExcluded === value;
            },
            render: (record) => {
                const webAppPackage = record.webAppVulnerability.package;

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
                text: (<VulnerabilityRatingTag severity={'critical'}/>),
                value: 0
            },
            {
                text: (<VulnerabilityRatingTag severity={'high'}/>),
                value: 1
            },
            {
                text: (<VulnerabilityRatingTag severity={'medium'}/>),
                value: 2
            },
            {
                text: (<VulnerabilityRatingTag severity={'low'}/>),
                value: 3
            },
            {
                text: (<VulnerabilityRatingTag severity={'none'}/>),
                value: 4
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
                    <span>
                        {
                            record.severityIndex === 10
                            && (
                                <VulnerabilityRatingTag
                                    isResolved={true}
                                    severity={'critical'}
                                    tooltipText={record.resolutionReasonsText}
                                />
                            )
                        }
                        {
                            record.severityIndex === 11
                            && (
                                <VulnerabilityRatingTag
                                    isResolved={true}
                                    severity={'high'}
                                    tooltipText={record.resolutionReasonsText}
                                />
                            )
                        }
                        {
                            record.severityIndex === 12
                            && (
                                <VulnerabilityRatingTag
                                    isResolved={true}
                                    severity={'medium'}
                                    tooltipText={record.resolutionReasonsText}
                                />
                            )
                        }
                        {
                            record.severityIndex === 13
                            && (
                                <VulnerabilityRatingTag
                                    isResolved={true}
                                    severity={'low'}
                                    tooltipText={record.resolutionReasonsText}
                                />
                            )
                        }
                        {
                            record.severityIndex === 14
                            && (
                                <VulnerabilityRatingTag
                                    isResolved={true}
                                    severity={'none'}
                                    tooltipText={record.resolutionReasonsText}
                                />
                            )
                        }
                    </span>
                    )
                : (
                    <span>
                        {
                            record.severityIndex === 0
                            && (
                                <VulnerabilityRatingTag severity={'critical'}/>
                            )
                        }
                        {
                            record.severityIndex === 1
                            && (
                                <VulnerabilityRatingTag severity={'high'}/>
                            )
                        }
                        {
                            record.severityIndex === 2
                            && (
                                <VulnerabilityRatingTag severity={'medium'}/>
                            )
                        }
                        {
                            record.severityIndex === 3
                            && (
                                <VulnerabilityRatingTag severity={'low'}/>
                            )
                        }
                        {
                            record.severityIndex === 4
                            && (
                                <VulnerabilityRatingTag severity={'none'}/>
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
            key: 'packageId',
            responsive: ['md'],
            sorter: (a, b) => a.packageId.localeCompare(b.packageId),
            sortOrder: sortedInfo.field === 'packageId' && sortedInfo.order,
            title: 'Package',
            ...getColumnSearchProps(
                'packageId',
                filteredInfo.packageId,
                (value) => setFilteredInfo({ ...filteredInfo, packageId: value })
            )
        },
        {
            dataIndex: 'id',
            key: 'id',
            responsive: ['md'],
            sorter: (a, b) => a.id.localeCompare(b.id),
            sortOrder: sortedInfo.field === 'id' && sortedInfo.order,
            title: 'Id',
            ...getColumnSearchProps(
                'id',
                filteredInfo.id,
                (value) => setFilteredInfo({ ...filteredInfo, id: value })
            )
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

    // Handle for table pagination changes
    const handlePaginationChange = (page, pageSize) => {
        setPagination({ current: page, pageSize });
    };

    // Handle for any table content changes
    const handleTableChange = (pagination, filters, sorter) => {
        setFilteredInfo(filters);
        setSortedInfo(sorter);
    };

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

    return (
        <Table
            className="ort-table-vulnerabilities"
            columns={columns}
            dataSource={vulnerabilities}
            rowKey="key"
            size="small"
            expandable={{
                expandedRowRender: (record) => {
                    const webAppVulnerability = record.webAppVulnerability;
                    const webAppPackage = webAppVulnerability.package;
                    const defaultActiveKey = record.isResolved
                        ? [
                                'vulnerability-resolutions',
                                'vulnerability-references'
                            ]
                        : [
                                'vulnerability-references',
                                'vulnerability-package-details',
                                'vulnerability-package-paths'
                            ];

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
                                            <VulnerabilitiesResolutionTable
                                                resolutions={webAppVulnerability.resolutions}
                                            />
                                        )
                                    });
                                }

                                if (webAppVulnerability.hasReferences()) {
                                    collapseItems.push({
                                        label: 'References',
                                        key: 'vulnerability-references',
                                        children: (
                                            <VulnerabilityReferencesList
                                                references={webAppVulnerability.references}
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
                                        key: 'vulnerability-package-paths',
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

export default VulnerabilitiesTable;
