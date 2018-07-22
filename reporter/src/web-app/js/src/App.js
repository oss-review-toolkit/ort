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
import { Row, Tabs } from 'antd';
import { connect } from 'react-redux';
import SummaryView from './components/SummaryView';
import TableView from './components/TableView';
import TreeView from './components/TreeView';
import 'antd/dist/antd.css';
import './App.css';
import store from './store/setup';

const TabPane = Tabs.TabPane;

// TODO for state management look into https://github.com/solkimicreb/react-easy-state

/* TODO for combine CSS, JS and fonts into single HTML file look into https://webpack.js.org
 * combined with https://www.npmjs.com/package/html-webpack-inline-source-plugin or
 * https://www.npmjs.com/package/miku-html-webpack-inline-source-plugin
 */

class ReporterApp extends Component {
    constructor(props) {
        super(props);
        store.dispatch({ type: 'CONVERT_REPORT_DATA' });
    }

    render() {
        return (
            <Row className="ort-app">
                <Tabs>
                    <TabPane tab="Summary" key="1">
                        <SummaryView />
                    </TabPane>
                    <TabPane tab="Table" key="2">
                        <TableView />
                    </TabPane>
                    <TabPane tab="Tree" key="3">
                        <TreeView />
                    </TabPane>
                </Tabs>
            </Row>
        );
    }
}

export default connect(
    (state) => ({ reportData: state }),
    () => ({})
)(ReporterApp);
