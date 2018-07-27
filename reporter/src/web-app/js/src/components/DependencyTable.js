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
import {
    Col, Icon, List, Row, Steps, Table, Tag
} from 'antd';
import 'antd/dist/antd.css';
import { LicenseTag } from './LicenseTag';

const Step = Steps.Step;

export class DependencyTable extends React.Component {
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
                            {row.levels.map(level => (
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

        return (
            <Table
                columns={this.columns}
                expandedRowRender={record => (
                    <div>
                        <PackageExpandedRowInfo data={record} />
                    </div>
                )}
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
}

// Generates the HTML for the additional package information in an expanded row of projectTable
const PackageExpandedRowInfo = (props) => {
    const { data } = props;

    if (!data) {
        return (
            <span>
                No additional data available for this package
            </span>
        );
    }

    return (
        <div>
            <PackageDetails data={data} />
            <PackageDependencyPaths data={data} />
            <PackageErrors data={data} expanded />
        </div>
    );
};

const PackageDetails = (props) => {
    const { data } = props;
    const pkgObj = data;
    const renderBinaryArtifact = () => {
        if (pkgObj.binary_artifact && pkgObj.binary_artifact.url) {
            return (
                <tr>
                    <th>
                        Binary Artifact
                    </th>
                    <td>
                        <a
                            href={pkgObj.binary_artifact.url}
                            rel="noopener noreferrer"
                            target="_blank"
                        >
                            {pkgObj.binary_artifact.url}
                        </a>
                    </td>
                </tr>
            );
        }

        return null;
    };
    const renderDescription = () => {
        if (pkgObj.description) {
            return (
                <tr>
                    <th>
                        Description
                    </th>
                    <td>
                        {pkgObj.description}
                    </td>
                </tr>
            );
        }

        return null;
    };
    const renderHomepage = () => {
        if (pkgObj.homepage_url) {
            return (
                <tr>
                    <th>
                        Homepage
                    </th>
                    <td>
                        <a
                            href={pkgObj.homepage_url}
                            rel="noopener noreferrer"
                            target="_blank"
                        >
                            {pkgObj.homepage_url}
                        </a>
                    </td>
                </tr>
            );
        }

        return null;
    };
    const renderSourceArtifact = () => {
        if (pkgObj.source_artifact && pkgObj.source_artifact.url) {
            return (
                <tr>
                    <th>
                        Source Artifact
                    </th>
                    <td>
                        <a
                            href={pkgObj.source_artifact.url}
                            rel="noopener noreferrer"
                            target="_blank"
                        >
                            {pkgObj.source_artifact.url}
                        </a>
                    </td>
                </tr>
            );
        }

        return null;
    };
    const renderVcs = () => {
        const vcs = pkgObj.vcs_processed.type || pkgObj.vcs.type;
        const vcsUrl = pkgObj.vcs_processed.url || pkgObj.vcs.url;
        const vcsRevision = pkgObj.vcs_processed.revision || pkgObj.vcs.revision;
        const vcsPath = pkgObj.vcs_processed.path || pkgObj.vcs.path;
        let vcsText = `${vcs}+${vcsUrl}`;

        if (vcsRevision && vcsPath) {
            vcsText = `${vcsText}@${vcsRevision}#${vcsPath}`;
        }

        if (vcs && vcsUrl) {
            return (
                <tr>
                    <th>
                        Repository
                    </th>
                    <td>
                        {vcsText}
                    </td>
                </tr>
            );
        }

        return null;
    };

    return (
        <Row>
            <Col span={22}>
                <h4>
                    Package Details
                </h4>
                <table className="ort-package-props">
                    <tbody>
                        <tr>
                            <th>
                                Id
                            </th>
                            <td>
                                {pkgObj.id}
                            </td>
                        </tr>
                        {renderDescription()}
                        {renderHomepage()}
                        {renderVcs()}
                        {renderSourceArtifact()}
                        {renderBinaryArtifact()}
                    </tbody>
                </table>
            </Col>
        </Row>
    );
};

// Generates the HTML to display the path(s) from root package to current package
class PackageDependencyPaths extends React.Component {
    constructor(props) {
        super();

        this.state = {
            expanded: props.expanded || false
        };

        if (props.data) {
            this.state = {
                ...this.state,
                data: props.data
            };
        }
    }

    onClick = () => {
        this.setState(prevState => ({ expanded: !prevState.expanded }));
    };

    render() {
        const { data: pkgObj, expanded } = this.state;

        if (Array.isArray(pkgObj.paths) && pkgObj.paths.length === 0) {
            return null;
        }

        if (!expanded) {
            return (
                <h4 onClick={this.onClick} className="ort-clickable">
                    <span>
                        Package Dependency Paths
                        {' '}
                    </span>
                    <Icon type="plus-square-o" />
                </h4>
            );
        }

        return (
            <div className="ort-package-deps-paths">
                <h4 onClick={this.onClick} className="ort-clickable">
                    Package Dependency Paths
                    {' '}
                    <Icon type="minus-square-o" />
                </h4>
                <List
                    grid={{
                        gutter: 16, xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2
                    }}
                    itemLayout="vertical"
                    size="small"
                    pagination={{
                        hideOnSinglePage: true,
                        pageSize: 2,
                        size: 'small'
                    }}
                    dataSource={pkgObj.paths}
                    renderItem={pathsItem => (
                        <List.Item>
                            <h5>
                                {pathsItem.scope}
                            </h5>
                            <Steps progressDot direction="vertical" size="small" current={pathsItem.path.length + 1}>
                                {pathsItem.path.map(item => <Step key={item} title={item} />)}
                                <Step key={pkgObj.id} title={pkgObj.id} />
                            </Steps>
                        </List.Item>
                    )}
                />
            </div>
        );
    }
}

// Generates the HTML for packages errors in an expanded row of projectTable
class PackageErrors extends React.Component {
    constructor(props) {
        super();

        this.state = {
            expanded: props.expanded || false
        };

        if (props.data) {
            this.state = {
                ...this.state,
                data: props.data
            };
        }
    }

    onClick = () => {
        this.setState(prevState => ({ expanded: !prevState.expanded }));
    };

    render() {
        const { data: pkgObj, expanded } = this.state;

        if (Array.isArray(pkgObj.errors) && pkgObj.errors.length === 0) {
            return null;
        }

        if (!expanded) {
            return (
                <h4 onClick={this.onClick} className="ort-clickable">
                    <span>
                        Package Errors
                        {' '}
                    </span>
                    <Icon type="plus-square-o" />
                </h4>
            );
        }

        return (
            <div className="ort-package-erors">
                <h4 onClick={this.onClick} className="ort-clickable">
                    Package Errors
                    {' '}
                    <Icon type="minus-square-o" />
                </h4>
                {pkgObj.errors.map(error => (
                    <p key={`package-error-${error.hash}`}>
                        {error.message}
                    </p>
                ))}
            </div>
        );
    }
}
