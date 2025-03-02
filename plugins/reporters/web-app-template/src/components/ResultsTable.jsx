/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
    CloudDownloadOutlined,
    DeleteOutlined,
    EyeOutlined,
    EyeInvisibleOutlined,
    FileAddOutlined,
    FileExcelOutlined,
    LaptopOutlined
} from '@ant-design/icons';
import {
    Col,
    Collapse,
    Dropdown,
    Empty,
    Row,
    Space,
    Table,
    Tooltip
} from 'antd';

import PackageDetails from './PackageDetails';
import PackageFindingsTable from './PackageFindingsTable';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PathExcludesTable from './PathExcludesTable';
import ScopeExcludesTable from './ScopeExcludesTable';
import { getColumnSearchProps } from './Shared';

const ResultsTable = ({ webAppOrtResult }) => {
    // Convert packages as Antd only accepts vanilla objects as input
    const packages = useMemo(
        () => {
            return webAppOrtResult.packages
                .map(
                    (webAppPackage) => ({
                        concludedLicense: webAppPackage.concludedLicense || '',
                        declaredLicenses: webAppPackage.declaredLicenses,
                        declaredLicensesText: Array.from(webAppPackage.declaredLicenses).join(', '),
                        declaredLicensesMapped: webAppPackage.declaredLicensesMapped,
                        declaredLicensesMappedIndexes: webAppPackage.declaredLicensesIndexes,
                        declaredLicensesMappedText: Array.from(webAppPackage.declaredLicensesMapped).join(', '),
                        declaredLicensesSpdxExpression: webAppPackage.declaredLicensesSpdxExpression,
                        definitionFilePath: webAppPackage.definitionFilePath || '',
                        detectedLicensesProcessed: webAppPackage.detectedLicensesProcessed,
                        detectedLicensesProcessedIndexes: webAppPackage.detectedLicensesProcessedIndexes,
                        detectedLicensesProcessedText: Array.from(webAppPackage.detectedLicensesProcessed).join(', '),
                        effectiveLicense: webAppPackage.effectiveLicense || '',
                        excludeReasonsText: Array.from(webAppPackage.excludeReasons).join(', '),
                        homepageUrl: webAppPackage.homepageUrl || '',
                        id: webAppPackage.id,
                        isExcluded: webAppPackage.isExcluded,
                        isProject: webAppPackage.isProject,
                        key: webAppPackage.key,
                        levels: webAppPackage.levels,
                        levelsText: Array.from(webAppPackage.levels).join(', '),
                        repository: webAppPackage.vcsProcessedUrl || '',
                        scopesText: Array.from(webAppPackage.scopeNames).join(', '),
                        scopeIndexes: webAppPackage.scopeIndexes
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
        id: [],
        homepageUrl: [],
        vcsProcessedUrl: []
    };
    const [filteredInfo, setFilteredInfo] = useState(filteredInfoDefault);

    // State variable for sorting table columns
    const [sortedInfo, setSortedInfo] = useState({});

    // State variable for showing or hiding table columns
    const [columnsToShow, setColumnsToShow] = useState([
        'declaredLicensesProcessed',
        'detectedLicensesProcessed',
        'levels',
        'scopeIndexes'
    ]);

    /* === Table columns === */

    const columns = [];
    let toggleColumnMenuItems = [];

    const sortTableColumnFilterSelectors = (a, b) => a.text.localeCompare(b.text);
    const columnDeclaredLicensesSelections = useMemo(
        () => {
            const { declaredLicenses } = webAppOrtResult;
            return declaredLicenses
                .map(
                    (license) => (
                        {
                            text: license,
                            value: license
                        }
                    )
                )
                .sort(sortTableColumnFilterSelectors);
        },
        []
    );
    const columnDeclaredLicensesProcessedSelections = useMemo(
        () => {
            const { declaredLicensesProcessed } = webAppOrtResult;
            return declaredLicensesProcessed
                .map(
                    (license) => (
                        {
                            text: license,
                            value: webAppOrtResult.getLicenseIndexByName(license)
                        }
                    )
                )
                .sort(sortTableColumnFilterSelectors);
        },
        []
    );
    const columnDetectedLicensesSelections = useMemo(
        () => {
            const { detectedLicenses } = webAppOrtResult;
            return detectedLicenses
                .map(
                    (license) => (
                        {
                            text: license,
                            value: webAppOrtResult.getLicenseIndexByName(license)
                        }
                    )
                )
                .sort(sortTableColumnFilterSelectors);
        },
        []
    );

    const columnEffectiveLicensesSelections = useMemo(
        () => {
            const { effectiveLicenses } = webAppOrtResult;
            return effectiveLicenses
                .map(
                    (license) => (
                        {
                            text: license,
                            value: license
                        }
                    )
                )
                .sort(sortTableColumnFilterSelectors);
        },
        []
    );

    const columnLevelFilterSelections = useMemo(
        () => {
            const { levels } = webAppOrtResult;
            return levels
                .map(
                    (level) => ({ text: level, value: level })
                );
        },
        []
    );

    const columnProjectFilterSelections = useMemo(
        () => {
            const { projects } = webAppOrtResult;
            return projects
                .sort((a, b) => a.id.localeCompare(b.id))
                .map(
                    (webAppPackage) => {
                        const text = webAppPackage.definitionFilePath
                            ? webAppPackage.definitionFilePath
                            : webAppPackage.id;

                        if (webAppOrtResult.hasPathExcludes()) {
                            if (webAppPackage.isExcluded) {
                                return {
                                    text: (
                                        <span>
                                            <FileExcelOutlined
                                                className="ort-excluded"
                                            />
                                            {' '}
                                            {text}
                                        </span>
                                    ),
                                    value: webAppPackage.packageIndex
                                };
                            }

                            return {
                                text: (
                                    <span>
                                        <FileAddOutlined />
                                        {' '}
                                        {text}
                                    </span>
                                ),
                                value: webAppPackage.packageIndex
                            };
                        }

                        return {
                            text,
                            value: webAppPackage.packageIndex
                        };
                    }
                );
        },
        []
    );
    const tableScopeFilterSelections = useMemo(
        () => {
            const { scopes } = webAppOrtResult;
            return scopes
                .sort((a, b) => a.name.localeCompare(b.name))
                .map(
                    (webAppScope) => {
                        if (webAppOrtResult.hasScopeExcludes()) {
                            if (webAppScope.isExcluded) {
                                return {
                                    text: (
                                        <span>
                                            <FileExcelOutlined
                                                className="ort-excluded"
                                            />
                                            {' '}
                                            {
                                                webAppScope.name
                                            }
                                        </span>
                                    ),
                                    value: webAppScope.id
                                };
                            }

                            return {
                                text: (
                                    <span>
                                        <FileAddOutlined />
                                        {' '}
                                        {
                                            webAppScope.name
                                        }
                                    </span>
                                ),
                                value: webAppScope.id
                            };
                        }

                        return {
                            text: webAppScope.name,
                            value: webAppScope.id
                        };
                    }
                );
        },
        []
    );

    if (webAppOrtResult.hasExcludes()) {
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
            onFilter: (value, record) => record.isExcluded === value,
            render: (record) => (
                record.isExcluded
                    ? (
                        <span className="ort-excludes">
                            <Tooltip
                                placement="right"
                                title={record.excludeReasonsText}
                            >
                                <FileExcelOutlined className="ort-excluded" />
                            </Tooltip>
                        </span>
                        )
                    : (
                        <FileAddOutlined />
                        )
            ),
            width: '2em'
        });
    }

    columns.push({
        align: 'left',
        dataIndex: 'id',
        ellipsis: true,
        key: 'id',
        sorter: (a, b) => a.id.localeCompare(b.id),
        sortOrder: sortedInfo.field === 'id' && sortedInfo.order,
        title: 'Package',
        ...getColumnSearchProps('id', filteredInfo.id, (value) => setFilteredInfo({ ...filteredInfo, id: value }))
    });

    if (webAppOrtResult.hasScopes()) {
        toggleColumnMenuItems.push({ text: 'Scopes', value: 'scopeIndexes' });

        if (columnsToShow.includes('scopeIndexes')) {
            columns.push({
                align: 'left',
                dataIndex: 'scopeIndexes',
                filters: tableScopeFilterSelections,
                filteredValue: filteredInfo.scopeIndexes || null,
                onFilter: (value, record) => record.scopeIndexes.has(value),
                responsive: ['md'],
                render: (text, record) => record.scopesText,
                title: 'Scopes'
            });
        }
    }

    if (webAppOrtResult.hasLevels()) {
        toggleColumnMenuItems.push({ text: 'Levels', value: 'levels' });

        if (columnsToShow.includes('levels')) {
            columns.push({
                align: 'center',
                dataIndex: 'levels',
                filters: columnLevelFilterSelections,
                filteredValue: filteredInfo.levels || null,
                onFilter: (value, record) => record.levels.has(value),
                render: (text, record) => record.levelsText,
                responsive: ['md'],
                textWrap: 'word-break',
                title: 'Levels',
                width: 80
            });
        }
    }

    if (webAppOrtResult.hasConcludedLicenses()) {
        toggleColumnMenuItems.push({ text: 'Concluded License', value: 'concludedLicense' });

        if (columnsToShow.includes('concludedLicense')) {
            columns.push({
                align: 'left',
                dataIndex: 'concludedLicense',
                sorter: (a, b) => {
                    const lenA = a.concludedLicense ? a.concludedLicense.length : 0;
                    const lenB = b.concludedLicense ? b.concludedLicense.length : 0;

                    return lenA - lenB;
                },
                sortOrder: sortedInfo.field === 'concludedLicense' && sortedInfo.order,
                textWrap: 'word-break',
                title: 'Concluded License',
                width: '18%'
            });
        }
    }

    if (webAppOrtResult.hasEffectiveLicenses()) {
        toggleColumnMenuItems.push({ text: 'Effective License', value: 'effectiveLicense' });

        if (columnsToShow.includes('effectiveLicense')) {
            columns.push({
                align: 'left',
                dataIndex: 'effectiveLicense',
                filters: columnEffectiveLicensesSelections,
                filteredValue: filteredInfo.effectiveLicense || null,
                onFilter: (value, record) => record.effectiveLicense.includes(value),
                sorter: (a, b) => {
                    const lenA = a.effectiveLicense ? a.effectiveLicense.length : 0;
                    const lenB = b.effectiveLicense ? b.effectiveLicense.length : 0;

                    return lenA - lenB;
                },
                sortOrder: sortedInfo.field === 'effectiveLicense' && sortedInfo.order,
                textWrap: 'word-break',
                title: 'Effective License',
                width: '18%'
            });
        }
    }

    if (webAppOrtResult.hasDeclaredLicensesProcessed()) {
        toggleColumnMenuItems.push({ text: 'Declared Licenses', value: 'declaredLicensesProcessed' });

        if (columnsToShow.includes('declaredLicensesProcessed')) {
            columns.push({
                align: 'left',
                dataIndex: 'declaredLicensesMapped',
                filters: columnDeclaredLicensesProcessedSelections,
                filteredValue: filteredInfo.declaredLicensesMapped || null,
                key: 'declaredLicensesMapped',
                onFilter: (value, record) => record.declaredLicensesMappedIndexes.has(value),
                render: (text, record) => record.declaredLicensesMappedText,
                responsive: ['md'],
                sorter: (a, b) => a.declaredLicensesMapped.size - b.declaredLicensesMapped.size,
                sortOrder: sortedInfo.field === 'declaredLicensesMapped' && sortedInfo.order,
                textWrap: 'word-break',
                title: 'Declared Licenses',
                width: '18%'
            });
        }
    }

    if (webAppOrtResult.hasDeclaredLicenses()) {
        toggleColumnMenuItems.push({ text: 'Unprocessed Declared Licenses', value: 'declaredLicenses' });

        if (columnsToShow.includes('declaredLicenses')) {
            columns.push({
                align: 'left',
                dataIndex: 'declaredLicenses',
                filters: columnDeclaredLicensesSelections,
                filteredValue: filteredInfo.declaredLicenses || null,
                key: 'declaredLicenses',
                onFilter: (value, record) => record.declaredLicenses.has(value),
                render: (text, record) => record.declaredLicensesText,
                responsive: ['md'],
                sorter: (a, b) => a.declaredLicenses.size - b.declaredLicenses.size,
                sortOrder: sortedInfo.field === 'declaredLicenses' && sortedInfo.order,
                textWrap: 'word-break',
                title: 'Unprocessed Declared Licenses'
            });
        }
    }

    if (webAppOrtResult.hasDetectedLicenses()) {
        toggleColumnMenuItems.push({ text: 'Detected Licenses', value: 'detectedLicensesProcessed' });

        if (columnsToShow.includes('detectedLicensesProcessed')) {
            columns.push({
                align: 'left',
                dataIndex: 'detectedLicensesProcessed',
                filters: columnDetectedLicensesSelections,
                filteredValue: filteredInfo.detectedLicensesProcessed || null,
                onFilter: (value, record) => record.detectedLicensesProcessedIndexes.has(value),
                render: (text, record) => record.detectedLicensesProcessedText,
                sorter: (a, b) => a.detectedLicensesProcessed.size - b.detectedLicensesProcessed.size,
                sortOrder: sortedInfo.field === 'detectedLicensesProcessed' && sortedInfo.order,
                textWrap: 'word-break',
                title: 'Detected Licenses',
                width: '18%'
            });
        }
    }

    toggleColumnMenuItems.push({ text: 'Homepage', value: 'homepageUrl' });
    if (columnsToShow.includes('homepageUrl')) {
        columns.push({
            align: 'left',
            dataIndex: 'homepageUrl',
            responsive: ['md'],
            sorter: (a, b) => a.homepageUrl.localeCompare(b.homepageUrl),
            sortOrder: sortedInfo.field === 'homepageUrl' && sortedInfo.order,
            textWrap: 'word-break',
            title: 'Homepage',
            ...getColumnSearchProps(
                'homepageUrl',
                filteredInfo.homepageUrl,
                (value) => setFilteredInfo({ ...filteredInfo, homepageUrl: value })
            )
        });
    }

    toggleColumnMenuItems.push({ text: 'Repository', value: 'vcsProcessedUrl' });
    if (columnsToShow.includes('vcsProcessedUrl')) {
        columns.push({
            align: 'left',
            dataIndex: 'vcsProcessedUrl',
            responsive: ['md'],
            sorter: (a, b) => a.vcsProcessedUrl.localeCompare(b.vcsProcessedUrl),
            sortOrder: sortedInfo.field === 'vcsProcessedUrl' && sortedInfo.order,
            textWrap: 'word-break',
            title: 'Repository',
            ...getColumnSearchProps(
                'vcsProcessedUrl',
                filteredInfo.vcsProcessedUrl,
                (value) => setFilteredInfo({ ...filteredInfo, vcsProcessedUrl: value })
            )
        });
    }

    toggleColumnMenuItems.push({ text: 'Project', value: 'projectIndexes' });
    if (columnsToShow.includes('projectIndexes')) {
        columns.push({
            align: 'left',
            dataIndex: 'projectIndexes',
            ellipsis: true,
            filters: columnProjectFilterSelections,
            filteredValue: filteredInfo.projectIndexes || null,
            onFilter: (value, record) => record.projectIndexes.has(value),
            render: (text, record) => (
                record.isProject
                    ? (
                        <Tooltip
                            placement="right"
                            title={record.definitionFilePath}
                        >
                            <LaptopOutlined />
                        </Tooltip>
                        )
                    : (
                        <CloudDownloadOutlined />
                        )
            ),
            title: 'Project',
            width: 85
        });
    }

    toggleColumnMenuItems = toggleColumnMenuItems.sort((a, b) => a.text.localeCompare(b.text));

    /* === Table event handling === */

    // Handle for clearing column filters and sorting changes
    const handleClearAllFiltersAndSorting = () => {
        setFilteredInfo(filteredInfoDefault);
        setSortedInfo({});
    };

    // Handle for table pagination changes
    const handlePaginationChange = (page, pageSize) => {
        setPagination({ current: page, pageSize });
    };

    // Handle for any table content changes
    const handleTableChange = (pagination, filters, sorter) => {
        setFilteredInfo(filters);
        setSortedInfo(sorter);
    };

    // Handle for hiding or showing table column changes
    const handleToggleColumns = (item) => {
        if (item && item.key) {
            const cols = new Set(columnsToShow);
            const { key: col } = item;

            if (cols.has(col)) {
                cols.delete(col);
            } else {
                cols.add(col);
            }

            setColumnsToShow(Array.from(cols));
        }
    };

    return (
        <div>
            <Row justify="end">
                <Col>
                    <Space
                        style={{
                            marginBottom: 16
                        }}
                    >
                        <Dropdown.Button
                            icon={<EyeOutlined />}
                            size="small"
                            menu={{
                                className: 'ort-table-toggle-columns',
                                items: toggleColumnMenuItems.map(
                                    (item) => ({
                                        key: item.value,
                                        label: (
                                            <span>
                                                {
                                                    columnsToShow.includes(item.value)
                                                        ? <EyeOutlined />
                                                        : <EyeInvisibleOutlined />
                                                }
                                                {' '}
                                                {item.text}
                                            </span>
                                        )
                                    })
                                ),
                                onClick: handleToggleColumns,
                                selectedKeys: columnsToShow
                            }}
                            onClick={handleClearAllFiltersAndSorting}
                        >
                            <DeleteOutlined />
                            Clear filters and sorters
                        </Dropdown.Button>
                    </Space>
                </Col>
            </Row>
            <Table
                columns={columns}
                dataSource={packages}
                indentSize={0}
                rowClassName="ort-package"
                rowKey="key"
                size="small"
                expandable={{
                    expandedRowRender: (pkg) => {
                        const webAppPackage = webAppOrtResult.getPackageByKey(pkg.key);
                        return (
                            <Collapse
                                className="ort-package-collapse"
                                bordered={false}
                                defaultActiveKey={['package-details', 'package-licenses']}
                                items={(() => {
                                    const collapseItems = [
                                        {
                                            label: 'Details',
                                            key: 'package-details',
                                            children: (
                                                <PackageDetails webAppPackage={webAppPackage} />
                                            )
                                        }
                                    ];

                                    if (webAppPackage.hasLicenses()) {
                                        collapseItems.push({
                                            label: 'Licenses',
                                            key: 'package-licenses',
                                            children: (
                                                <PackageLicenses webAppPackage={webAppPackage} />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasPaths()) {
                                        collapseItems.push({
                                            label: 'Paths',
                                            key: 'package-paths',
                                            children: (
                                                <PackagePaths paths={webAppPackage.paths} />
                                            )
                                        });
                                    }

                                    if (webAppPackage.hasFindings()) {
                                        collapseItems.push({
                                            label: 'Scan Results',
                                            key: 'package-scan-results',
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
                                            key: 'package-path-excludes',
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
                                            key: 'package-scope-excludes',
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
                        )
                    },
                    expandRowByClick: true
                }}
                locale={{
                    emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No packages"></Empty>
                }}
                pagination={{
                    current: pagination.current,
                    hideOnSinglePage: true,
                    onChange: handlePaginationChange,
                    pageSize: pagination.pageSize,
                    pageSizeOptions: ['50', '100', '250', '500', '1000', '5000'],
                    position: 'both',
                    showSizeChanger: true
                }}
                onChange={handleTableChange}
            />
        </div>
    );
};

export default ResultsTable;
