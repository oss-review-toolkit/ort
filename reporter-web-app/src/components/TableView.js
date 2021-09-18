/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import { connect } from 'react-redux';
import {
    Collapse,
    Dropdown,
    Menu,
    Table,
    Tooltip
} from 'antd';
import {
    CloudDownloadOutlined,
    EyeOutlined,
    EyeInvisibleOutlined,
    FileAddOutlined,
    FileExcelOutlined,
    LaptopOutlined
} from '@ant-design/icons';
import PropTypes from 'prop-types';
import {
    getOrtResult,
    getTableView,
    getTableViewShouldComponentUpdate,
    getTableViewDeclaredLicensesSelections,
    getTableViewDeclaredLicensesProcessedSelections,
    getTableViewDetectedLicensesSelections,
    getTableViewLevelFilterSelections,
    getTableViewProjectFilterSelections,
    getTableViewScopeFilterSelections
} from '../reducers/selectors';
import store from '../store';
import PackageDetails from './PackageDetails';
import PackageFindingsTable from './PackageFindingsTable';
import PackageLicenses from './PackageLicenses';
import PackagePaths from './PackagePaths';
import PathExcludesTable from './PathExcludesTable';
import ScopeExcludesTable from './ScopeExcludesTable';
import { getColumnSearchProps } from './Shared';

const { Panel } = Collapse;

class TableView extends React.Component {
    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    onClickToggleColumnsMenu = (e) => {
        store.dispatch({
            payload: {
                columnKey: e.key
            },
            type: 'TABLE::CHANGE_COLUMNS_PACKAGES_TABLE'
        });
    }

    render() {
        const {
            tableView: {
                columns: {
                    filteredInfo,
                    sortedInfo,
                    showKeys
                }
            },
            tableDeclaredLicensesSelections,
            tableDeclaredLicensesProcessedSelections,
            tableDetectedLicensesSelections,
            tableLevelFilterSelections,
            tableProjectFilterSelections,
            tableScopeFilterSelections,
            webAppOrtResult
        } = this.props;

        // Specifies table columns as per
        // https://ant.design/components/table/
        const columns = [];
        let toggleColumnMenuItems = [];

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
                onFilter: (value, webAppPackage) => {
                    const { isExcluded } = webAppPackage;

                    return (isExcluded && value === 'excluded') || (!isExcluded && value === 'included');

                },
                render: (webAppPackage) => (
                    webAppPackage.isExcluded ? (
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
                    )
                ),
                width: '2em'
            });
        }

        columns.push({
            align: 'left',
            dataIndex: 'id',
            ellipsis: true,
            sorter: (a, b) => a.id.localeCompare(b.id),
            sortOrder: sortedInfo.field === 'id' && sortedInfo.order,
            title: 'Package',
            ...getColumnSearchProps('id', filteredInfo, this)
        });

        if (webAppOrtResult.hasScopes()) {
            toggleColumnMenuItems.push({ text: 'Scopes', value: 'scopeIndexes' });

            if (showKeys.includes('scopeIndexes')) {
                columns.push({
                    align: 'left',
                    dataIndex: 'scopeIndexes',
                    filters: tableScopeFilterSelections,
                    filteredValue: filteredInfo.scopeIndexes || null,
                    onFilter: (value, webAppPackage) => webAppPackage.hasScopeIndex(value),
                    render: (scopeIndexes, webAppPackage) => (
                        <span>
                            {Array.from(webAppPackage.scopeNames).join(',')}
                        </span>
                    ),
                    responsive: ['md'],
                    title: 'Scopes'
                });
            }
        }

        if (webAppOrtResult.hasLevels()) {
            toggleColumnMenuItems.push({ text: 'Levels', value: 'levels' });

            if (showKeys.includes('levels')) {
                columns.push({
                    align: 'center',
                    dataIndex: 'levels',
                    filters: tableLevelFilterSelections,
                    filteredValue: filteredInfo.levels || null,
                    onFilter: (value, webAppPackage) => webAppPackage.hasLevel(value),
                    render: (levels) => (
                        <span>
                            {Array.from(levels).join(', ')}
                        </span>
                    ),
                    responsive: ['md'],
                    textWrap: 'word-break',
                    title: 'Levels',
                    width: 80
                });
            }
        }

        if (webAppOrtResult.hasConcludedLicenses()) {
            toggleColumnMenuItems.push({ text: 'Concluded License', value: 'concludedLicense' });

            if (showKeys.includes('concludedLicense')) {
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

        if (webAppOrtResult.hasDeclaredLicensesProcessed()) {
            toggleColumnMenuItems.push({ text: 'Declared Licenses', value: 'declaredLicensesProcessed' });

            if (showKeys.includes('declaredLicensesProcessed')) {
                columns.push({
                    align: 'left',
                    dataIndex: 'declaredLicensesMapped',
                    filters: tableDeclaredLicensesProcessedSelections,
                    filteredValue: filteredInfo.declaredLicensesMapped || null,
                    key: 'declaredLicensesMapped',
                    onFilter: (value, webAppPackage) => webAppPackage.declaredLicensesMapped.has(value),
                    render: (declaredLicensesMapped) => (
                        <span>
                            {Array.from(declaredLicensesMapped).join(', ')}
                        </span>
                    ),
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

            if (showKeys.includes('declaredLicenses')) {
                columns.push({
                    align: 'left',
                    dataIndex: 'declaredLicenses',
                    filters: tableDeclaredLicensesSelections,
                    filteredValue: filteredInfo.declaredLicenses || null,
                    key: 'declaredLicenses',
                    onFilter: (value, webAppPackage) => webAppPackage.declaredLicenses.has(value),
                    render: (declaredLicenses) => (
                        <span>
                            {Array.from(declaredLicenses).join(', ')}
                        </span>
                    ),
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

            if (showKeys.includes('detectedLicensesProcessed')) {
                columns.push({
                    align: 'left',
                    dataIndex: 'detectedLicensesProcessed',
                    filters: tableDetectedLicensesSelections,
                    filteredValue: filteredInfo.detectedLicensesProcessed || null,
                    onFilter: (license, webAppPackage) => webAppPackage.detectedLicensesProcessed.has(license),
                    render: (detectedLicensesProcessed) => (
                        <span>
                            {Array.from(detectedLicensesProcessed).join(', ')}
                        </span>
                    ),
                    sorter: (a, b) => a.detectedLicensesProcessed.size - b.detectedLicensesProcessed.size,
                    sortOrder: sortedInfo.field === 'detectedLicensesProcessed' && sortedInfo.order,
                    textWrap: 'word-break',
                    title: 'Detected Licenses',
                    width: '18%'
                });
            }
        }

        toggleColumnMenuItems.push({ text: 'Homepage', value: 'homepageUrl' });
        if (showKeys.includes('homepageUrl')) {
            columns.push({
                align: 'left',
                dataIndex: 'homepageUrl',
                render: (homepageUrl) => (
                    <span>
                        {homepageUrl}
                    </span>
                ),
                responsive: ['md'],
                sorter: (a, b) => a.homepageUrl.localeCompare(b.homepageUrl),
                sortOrder: sortedInfo.field === 'homepageUrl' && sortedInfo.order,
                textWrap: 'word-break',
                title: 'Homepage',
                ...getColumnSearchProps('Homepage', filteredInfo, this)
            });
        }

        toggleColumnMenuItems.push({ text: 'Repository', value: 'vcsProcessedUrl' });
        if (showKeys.includes('vcsProcessedUrl')) {
            columns.push({
                align: 'left',
                dataIndex: 'vcsProcessedUrl',
                render: (vcsProcessedUrl) => (
                    <span>
                        {vcsProcessedUrl}
                    </span>
                ),
                responsive: ['md'],
                sorter: (a, b) => a.vcsProcessedUrl.localeCompare(b.vcsProcessedUrl),
                sortOrder: sortedInfo.field === 'vcsProcessedUrl' && sortedInfo.order,
                textWrap: 'word-break',
                title: 'Repository',
                ...getColumnSearchProps('vcsProcessedUrl', filteredInfo, this)
            });
        }

        toggleColumnMenuItems.push({ text: 'Project', value: 'projectIndexes' });
        if (showKeys.includes('projectIndexes')) {
            columns.push({
                align: 'left',
                dataIndex: 'projectIndexes',
                ellipsis: true,
                filters: tableProjectFilterSelections,
                filteredValue: filteredInfo.projectIndexes || null,
                onFilter: (value, webAppPackage) => webAppPackage.projectIndexes.has(value),
                render: (text, webAppPackage) => (
                    webAppPackage.isProject ? (
                        <Tooltip
                            placement="right"
                            title={webAppPackage.definitionFilePath}
                        >
                            <LaptopOutlined />
                        </Tooltip>
                    ) : (
                        <CloudDownloadOutlined />
                    )
                ),
                title: 'Project',
                width: 85
            });
        }

        toggleColumnMenuItems = toggleColumnMenuItems.sort((a, b) => a.text.localeCompare(b.text));

        return (
            <div>
                <div className="ort-table-buttons">
                    <Dropdown.Button
                        onClick={() => {
                            store.dispatch({ type: 'TABLE::RESET_COLUMNS_TABLE' });
                        }}
                        overlay={(
                            <Menu
                                className="ort-table-toggle-columns"
                                onClick={this.onClickToggleColumnsMenu}
                                selectedKeys={showKeys}
                            >
                                {
                                    toggleColumnMenuItems.map(
                                        (item) => (
                                            <Menu.Item key={item.value}>
                                                {
                                                    showKeys.includes(item.value)
                                                        ? <EyeOutlined />
                                                        : <EyeInvisibleOutlined />
                                                }
                                                {' '}
                                                {item.text}
                                            </Menu.Item>
                                        )
                                    )
                                }
                            </Menu>
                        )}
                        size="small"
                    >
                        Clear filters
                    </Dropdown.Button>
                </div>
                <Table
                    columns={columns}
                    expandedRowRender={
                        (webAppPackage) => (
                            <Collapse
                                className="ort-package-collapse"
                                bordered={false}
                                defaultActiveKey={[0, 1]}
                            >
                                <Panel header="Details" key="0">
                                    <PackageDetails webAppPackage={webAppPackage} />
                                </Panel>
                                {
                                    webAppPackage.hasLicenses()
                                    && (
                                        <Panel header="Licenses" key="1">
                                            <PackageLicenses webAppPackage={webAppPackage} />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppPackage.hasPaths()
                                    && (
                                        <Panel header="Paths" key="2">
                                            <PackagePaths paths={webAppPackage.paths} />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppPackage.hasFindings()
                                    && (
                                        <Panel header="Scan Results" key="3">
                                            <PackageFindingsTable
                                                webAppPackage={webAppPackage}
                                            />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppPackage.hasPathExcludes()
                                    && (
                                        <Panel header="Path Excludes" key="4">
                                            <PathExcludesTable
                                                excludes={webAppPackage.pathExcludes}
                                            />
                                        </Panel>
                                    )
                                }
                                {
                                    webAppPackage.hasScopeExcludes()
                                    && (
                                        <Panel header="Scope Excludes" key="5">
                                            <ScopeExcludesTable
                                                excludes={webAppPackage.scopeExcludes}
                                            />
                                        </Panel>
                                    )
                                }
                            </Collapse>
                        )
                    }
                    dataSource={webAppOrtResult.packages}
                    expandRowByClick
                    indentSize={0}
                    locale={{
                        emptyText: 'No packages'
                    }}
                    onChange={(pagination, filters, sorter, extra) => {
                        store.dispatch({
                            type: 'TABLE::CHANGE_PACKAGES_TABLE',
                            payload: {
                                columns: {
                                    filteredInfo: filters,
                                    sortedInfo: sorter,
                                    filterData: extra.currentDataSource
                                }
                            }
                        });
                    }}
                    pagination={
                        {
                            defaultPageSize: 100,
                            hideOnSinglePage: true,
                            pageSizeOptions: ['50', '100', '250', '500'],
                            position: 'both',
                            showSizeChanger: true
                        }
                    }
                    size="small"
                    rowClassName="ort-package"
                    rowKey="key"
                />
            </div>
        );
    }
}

TableView.propTypes = {
    shouldComponentUpdate: PropTypes.bool.isRequired,
    tableDeclaredLicensesSelections: PropTypes.array.isRequired,
    tableDeclaredLicensesProcessedSelections: PropTypes.array.isRequired,
    tableDetectedLicensesSelections: PropTypes.array.isRequired,
    tableLevelFilterSelections: PropTypes.array.isRequired,
    tableProjectFilterSelections: PropTypes.array.isRequired,
    tableScopeFilterSelections: PropTypes.array.isRequired,
    tableView: PropTypes.object.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

const mapStateToProps = (state) => ({
    shouldComponentUpdate: getTableViewShouldComponentUpdate(state),
    tableDeclaredLicensesSelections: getTableViewDeclaredLicensesSelections(state),
    tableDeclaredLicensesProcessedSelections: getTableViewDeclaredLicensesProcessedSelections(state),
    tableDetectedLicensesSelections: getTableViewDetectedLicensesSelections(state),
    tableLevelFilterSelections: getTableViewLevelFilterSelections(state),
    tableProjectFilterSelections: getTableViewProjectFilterSelections(state),
    tableScopeFilterSelections: getTableViewScopeFilterSelections(state),
    tableView: getTableView(state),
    webAppOrtResult: getOrtResult(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(TableView);
