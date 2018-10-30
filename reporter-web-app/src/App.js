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

import React, { Component } from 'react';
import {
    Alert, Col, Progress, Row, Tabs
} from 'antd';
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import SummaryView from './components/SummaryView';
import TableView from './components/TableView';
import TreeView from './components/TreeView';
import 'antd/dist/antd.css';
import './App.css';
import store from './store';

const { TabPane } = Tabs;

/* TODO for combine CSS, JS and fonts into single HTML file look into https://webpack.js.org
 * combined with https://www.npmjs.com/package/html-webpack-inline-source-plugin or
 * https://www.npmjs.com/package/miku-html-webpack-inline-source-plugin
 */

class ReporterApp extends Component {
    constructor(props) {
        super(props);

        store.dispatch({ type: 'LOADING_START' });
    }

    onChangeTab = (activeKey) => {
        store.dispatch({ type: 'VIEW_ONCHANGE_TAB', activeTabKey: activeKey });
    }

    render() {
        const { data: { loading }, view } = this.props;
        const {
            percentage: loadingPercentage,
            state: loadingState,
            text: loadingText
        } = loading;

        switch (loadingState) {
        case 'DONE': {
            return (
                <Row className="ort-app">
                    <Tabs
                        activeKey={view.activeTabKey}
                        animated={false}
                        onChange={this.onChangeTab}
                    >
                        <TabPane tab="Summary" key="summary">
                            <SummaryView />
                        </TabPane>
                        <TabPane tab="Table" key="table">
                            <TableView />
                        </TabPane>
                        <TabPane tab="Tree" key="tree">
                            <TreeView />
                        </TabPane>
                    </Tabs>
                </Row>
            );
        }
        case 'LOADING':
            return (
                <Row className="ort-app" type="flex" justify="space-around" align="middle">
                    <Col span={6}>
                        <p>
                            OSS Review Toolkit:
                            {' '}
                            {loadingText}
                        </p>
                        {loadingPercentage === 100 ? (
                            <Progress percent={100} />
                        ) : (
                            <Progress percent={loadingPercentage} status="active" />
                        )}
                    </Col>
                </Row>
            );
        case 'NO_REPORT_DATA':
            return (
                <Row className="ort-app" type="flex" justify="space-around" align="middle">
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
                                            href="https://github.com/heremaps/oss-review-toolkit/issues"
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
                <Row className="ort-app" type="flex" justify="space-around" align="middle">
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
                                            href="https://github.com/heremaps/oss-review-toolkit/issues"
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
    data: PropTypes.object.isRequired,
    view: PropTypes.object.isRequired
};

export default connect(
    state => ({ data: state.data, view: state.view }),
    () => ({})
)(ReporterApp);
