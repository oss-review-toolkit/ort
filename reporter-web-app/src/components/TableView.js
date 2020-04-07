/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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
    Button,
    Collapse,
    Table,
    Tooltip
} from 'antd';
import {
    FileAddOutlined,
    FileExcelOutlined
} from '@ant-design/icons';
import PropTypes from 'prop-types';
import {
    getOrtResult,
    getTableView,
    getTableViewShouldComponentUpdate,
    getTableViewDeclaredLicensesSelections,
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

const { Panel } = Collapse;

class TableView extends React.Component {
    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    render() {
        const {
            tableView: {
                filter: {
                    filteredInfo,
                    sortedInfo
                }
            },
            tableDeclaredLicensesSelections,
            tableDetectedLicensesSelections,
            tableLevelFilterSelections,
            tableProjectFilterSelections,
            tableScopeFilterSelections,
            webAppOrtResult
        } = this.props;

        // Specifies table columns as per
        // https://ant.design/components/table/
        const columns = [];

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
                    if (value === 'excluded') {
                        return webAppPackage.isExcluded;
                    }

                    if (value === 'included') {
                        return !webAppPackage.isExcluded;
                    }

                    return false;
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
            filters: tableProjectFilterSelections,
            filteredValue: filteredInfo.id || null,
            onFilter: (value, webAppPackage) => webAppPackage.projectIndexes.has(value),
            sorter: (a, b) => {
                const idA = a.id.toUpperCase();
                const idB = b.id.toUpperCase();
                if (idA < idB) {
                    return -1;
                }
                if (idA > idB) {
                    return 1;
                }

                return 0;
            },
            sortOrder: sortedInfo.field === 'id' && sortedInfo.order,
            title: 'Package'
        });

        if (webAppOrtResult.hasScopes()) {
            columns.push({
                align: 'left',
                dataIndex: 'scopeIndexes',
                filters: tableScopeFilterSelections,
                filteredValue: filteredInfo.scopeIndexes || null,
                onFilter: (value, webAppPackage) => webAppPackage.hasScopeIndex(value),
                title: 'Scopes',
                render: (scopeIndexes, webAppPackage) => (
                    <span>
                        {Array.from(webAppPackage.scopeNames).join(',')}
                    </span>
                )
            });
        }

        if (webAppOrtResult.hasLevels()) {
            columns.push({
                align: 'center',
                dataIndex: 'levels',
                filters: tableLevelFilterSelections,
                filteredValue: filteredInfo.levels || null,
                onFilter: (value, webAppPackage) => webAppPackage.hasLevel(value),
                textWrap: 'word-break',
                title: 'Levels',
                render: (levels) => (
                    <span>
                        {Array.from(levels).join(', ')}
                    </span>
                ),
                width: 80
            });
        }

        if (webAppOrtResult.hasDeclaredLicenses()) {
            columns.push({
                align: 'left',
                dataIndex: 'declaredLicenses',
                filters: tableDeclaredLicensesSelections,
                filteredValue: filteredInfo.declaredLicenses || null,
                key: 'declaredLicenses',
                onFilter: (value, webAppPackage) => webAppPackage.declaredLicenses.has(value),
                textWrap: 'word-break',
                title: 'Declared Licenses',
                render: (declaredLicenses) => (
                    <span>
                        {Array.from(declaredLicenses).join(', ')}
                    </span>
                ),
                width: '18%'
            });
        }

        if (webAppOrtResult.hasDetectedLicenses()) {
            columns.push({
                align: 'left',
                dataIndex: 'detectedLicenses',
                filters: tableDetectedLicensesSelections,
                filteredValue: filteredInfo.detectedLicenses || null,
                onFilter: (license, webAppPackage) => webAppPackage.detectedLicenses.has(license),
                textWrap: 'word-break',
                title: 'Detected Licenses',
                render: (detectedLicenses) => (
                    <span>
                        {Array.from(detectedLicenses).join(', ')}
                    </span>
                ),
                width: '18%'
            });
        }

        return (
            <div>
                <div className="ort-table-operations">
                    <Button
                        onClick={() => {
                            store.dispatch({ type: 'TABLE::CLEAR_FILTERS_TABLE' });
                        }}
                        size="small"
                    >
                        Clear filters
                    </Button>
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
                                filter: {
                                    filteredInfo: filters,
                                    sortedInfo: sorter
                                },
                                filterData: extra.currentDataSource
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
