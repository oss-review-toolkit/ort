/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import React, { Component } from 'react';
import {
    Alert,
    Col,
    Progress,
    Row,
    Tabs
} from 'antd';
import {
    ControlOutlined,
    PartitionOutlined,
    PieChartOutlined,
    TableOutlined
} from '@ant-design/icons';
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import AboutModal from './components/AboutModal';
import SummaryView from './components/SummaryView';
import TableView from './components/TableView';
import TreeView from './components/TreeView';
import 'antd/dist/antd.css';
import './App.css';
import store from './store';
import {
    getAppView,
    getOrtResult
} from './reducers/selectors';

const { TabPane } = Tabs;

/* TODO for combine CSS, JS and fonts into single HTML file look into https://webpack.js.org
 * combined with https://www.npmjs.com/package/html-webpack-inline-source-plugin or
 * https://www.npmjs.com/package/miku-html-webpack-inline-source-plugin
 */

class ReporterApp extends Component {
    constructor(props) {
        super(props);

        store.dispatch({ type: 'APP::LOADING_START' });
    }

    onChangeTab = (activeKey) => {
        store.dispatch({ type: 'APP::CHANGE_TAB', key: activeKey });
    }

    onClickAbout = () => {
        store.dispatch({ type: 'APP::SHOW_ABOUT_MODAL' });
    }

    render() {
        const {
            appView: {
                loading,
                showAboutModal,
                showKey
            },
            webAppOrtResult
        } = this.props;

        switch (showKey) {
        case 'ort-tabs-summary':
        case 'ort-tabs-table':
        case 'ort-tabs-tree':
        case 'ort-tabs': {
            return (
                <Row
                    className="ort-app"
                    key="ort-tabs"
                >
                    <Col span={24}>
                        {
                            showAboutModal && (<AboutModal webAppOrtResult={webAppOrtResult} />)
                        }
                        <Tabs
                            activeKey={showKey}
                            animated={false}
                            onChange={this.onChangeTab}
                            tabBarExtraContent={(
                                <ControlOutlined
                                    className="ort-control"
                                    onClick={this.onClickAbout}
                                />
                            )}
                        >
                            <TabPane
                                tab={(
                                    <span>
                                        <PieChartOutlined />
                                        Summary
                                    </span>
                                )}
                                key="ort-tabs-summary"
                            >
                                <SummaryView />
                            </TabPane>
                            <TabPane
                                tab={(
                                    <span>
                                        <TableOutlined />
                                        Table
                                    </span>
                                )}
                                key="ort-tabs-table"
                            >
                                <TableView />
                            </TabPane>
                            <TabPane
                                tab={(
                                    <span>
                                        <PartitionOutlined />
                                        Tree
                                    </span>
                                )}
                                key="ort-tabs-tree"
                            >
                                <TreeView />
                            </TabPane>
                        </Tabs>
                    </Col>
                </Row>
            );
        }
        case 'ort-loading': {
            const {
                percentage: loadingPercentage,
                text: loadingText
            } = loading;

            return (
                <Row
                    align="middle"
                    justify="space-around"
                    className="ort-app"
                    key="ort-loading"
                    type="flex"
                >
                    <Col span={6}>
                        <a
                            href="https://github.com/oss-review-toolkit/ort"
                            rel="noopener noreferrer"
                            target="_blank"
                        >
                            <div className="ort-loading-logo ort-logo" />
                        </a>
                        <span>
                            {loadingText}
                        </span>
                        {loadingPercentage === 100 ? (
                            <Progress percent={100} />
                        ) : (
                            <Progress percent={loadingPercentage} status="active" />
                        )}
                    </Col>
                </Row>
            );
        }
        case 'ort-no-report-data':
            return (
                <Row
                    align="middle"
                    className="ort-app"
                    justify="space-around"
                    key="ort-no-report-data"
                    type="flex"
                >
                    <Col span={8}>
                        <Alert
                            message="No review results could be loaded..."
                            description={(
                                <div>
                                    <p>
                                        Either something went wrong or you are looking at an ORT report template file.
                                    </p>
                                    <p>
                                        If you believe you found a bug please fill a
                                        {' '}
                                        <a
                                            href="https://github.com/oss-review-toolkit/ort/issues"
                                            rel="noopener noreferrer"
                                            target="_blank"
                                        >
                                            issue on GitHub
                                        </a>
                                        .
                                    </p>
                                </div>
                            )}
                            type="error"
                        />
                    </Col>
                </Row>
            );
        default:
            return (
                <Row
                    align="middle"
                    className="ort-app"
                    justify="space-around"
                    key="ort-error-msg"
                    type="flex"
                >
                    <Col span={8}>
                        <Alert
                            message="Oops, something went wrong..."
                            description={(
                                <div>
                                    <p>
                                        Try reloading this report. If that does not solve the issue please
                                        contact your OSS Review Toolkit admin(s) for support.
                                    </p>
                                    <p>
                                        If you believe you found a bug please fill a
                                        {' '}
                                        <a
                                            href="https://github.com/oss-review-toolkit/ort/issues"
                                            rel="noopener noreferrer"
                                            target="_blank"
                                        >
                                            issue on GitHub
                                        </a>
                                        .
                                    </p>
                                </div>
                            )}
                            type="error"
                        />
                    </Col>
                </Row>
            );
        }
    }
}

ReporterApp.propTypes = {
    appView: PropTypes.object.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

const mapStateToProps = (state) => ({
    appView: getAppView(state),
    webAppOrtResult: getOrtResult(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(ReporterApp);
