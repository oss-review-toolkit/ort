/*
 * Copyright (c) 2018 HERE Europe B.V.
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
import { Table, Tag } from 'antd';
import 'antd/dist/antd.css';
import { LicenseTag } from './LicenseTag';
import { PackagesTableDetails } from './PackagesTableDetails';
import { PackagesTableErrors } from './PackagesTableErrors';
import { PackagesTablePaths } from './PackagesTablePaths';
import { PackagesTableScanSummary } from './PackagesTableScanSummary';

export class PackagesTable extends React.Component {
    constructor(props) {
        super(props);

        if (props.project) {
            this.state = {
                ...this.state,
                data: props.project
            };

            // Specifies table columns as per
            // https://ant.design/components/table/
            this.columns = [
                {
                    align: 'left',
                    dataIndex: 'id',
                    key: 'id',
                    onFilter: (value, record) => record.id.indexOf(value) === 0,
                    sorter: (a, b) => a.id.length - b.id.length,
                    title: 'Id',
                    render: text => (
                        <span className="ort-package-id">
                            {text}
                        </span>
                    )
                },
                {
                    align: 'left',
                    dataIndex: 'scopes',
                    filters: (() => Array.from(this.state.data.packages.scopes).sort().map(
                        scope => ({
                            text: scope,
                            value: scope
                        })
                    ))(),
                    key: 'scopes',
                    onFilter: (scope, component) => component.scopes.includes(scope),
                    title: 'Scopes',
                    render: (text, row) => (
                        <ul className="ort-table-list">
                            {row.scopes.map(scope => (
                                <li key={`scope-${scope}`}>
                                    {scope}
                                </li>
                            ))}
                        </ul>
                    )
                },
                {
                    align: 'left',
                    dataIndex: 'levels',
                    filters: (() => Array.from(this.state.data.packages.levels).sort().map(
                        level => ({
                            text: level,
                            value: level
                        })
                    ))(),
                    filterMultiple: true,
                    key: 'levels',
                    onFilter: (level, component) => component.levels.includes(parseInt(level, 10)),
                    render: (text, row) => (
                        <ul className="ort-table-list">
                            {row.levels.sort().map(level => (
                                <li key={`level-${level}`}>
                                    {level}
                                </li>
                            ))}
                        </ul>
                    ),
                    title: 'Levels',
                    width: 80
                },
                {
                    align: 'left',
                    dataIndex: 'declared_licenses',
                    filters: (() => this.state.data.packages.licenses.declared.sort().map(
                        license => ({
                            text: license,
                            value: license
                        })
                    ))(),
                    filterMultiple: true,
                    key: 'declared_licenses',
                    onFilter: (value, record) => record.declared_licenses.includes(value),
                    title: 'Declared Licenses',
                    render: (text, row) => (
                        <ul className="ort-table-list">
                            {row.declared_licenses.map(license => (
                                <li key={license}>
                                    <LicenseTag text={license} ellipsisAtChar={20} />
                                </li>
                            ))}
                        </ul>
                    ),
                    width: 160
                },
                {
                    align: 'left',
                    dataIndex: 'detected_licenses',
                    filters: (() => this.state.data.packages.licenses.detected.sort().map(
                        license => ({
                            text: license,
                            value: license
                        })
                    ))(),
                    filterMultiple: true,
                    key: 'detected_licenses',
                    onFilter: (license, component) => component.detected_licenses.includes(license),
                    title: 'Detected Licenses',
                    render: (text, row) => (
                        <ul className="ort-table-list">
                            {row.detected_licenses.map(license => (
                                <li key={license}>
                                    <LicenseTag text={license} ellipsisAtChar={20} />
                                </li>
                            ))}
                        </ul>),
                    width: 160
                },
                {
                    align: 'left',
                    filters: (() => [
                        { text: 'Errors', value: 'errors' },
                        { text: 'OK', value: 'ok' }
                    ])(),
                    filterMultiple: true,
                    key: 'status',
                    onFilter: (status, component) => {
                        if (status === 'ok') {
                            return component.errors.length === 0;
                        }

                        if (status === 'errors') {
                            return component.errors.length !== 0;
                        }

                        return false;
                    },
                    render: (text, row) => {
                        const nrErrorsText = errors => `${errors.length} error${(errors.length > 1) ? 's' : ''}`;

                        if (Array.isArray(row.errors) && row.errors.length > 0) {
                            return (
                                <Tag className="ort-status-error" color="red">
                                    {nrErrorsText(row.errors)}
                                </Tag>
                            );
                        }

                        return (
                            <Tag className="ort-status-ok" color="blue">
                                OK
                            </Tag>
                        );
                    },
                    title: 'Status',
                    width: 80
                }
            ];
        }
    }

    render() {
        const { data } = this.state;

        if (data.packages && data.packages.list) {
            return (
                <Table
                    columns={this.columns}
                    expandedRowRender={(record) => {
                        if (!record) {
                            return (
                                <span>
                                    No additional data available for this package
                                </span>
                            );
                        }

                        return (
                            <div>
                                <PackagesTableDetails data={record} />
                                <PackagesTablePaths data={record} />
                                <PackagesTableErrors data={record} expanded />
                                <PackagesTableScanSummary data={record} />
                            </div>
                        );
                    }}
                    dataSource={data.packages.list}
                    expandRowByClick
                    locale={{
                        emptyText: 'No packages'
                    }}
                    pagination={false}
                    size="small"
                    rowKey="id"
                />
            );
        }

        return null;
    }
}
