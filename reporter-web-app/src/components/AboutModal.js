/*
 * Copyright (C) 2019 HERE Europe B.V.
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
import ObjectInspector from 'react-inspector';
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import {
    Modal,
    Tabs
} from 'antd';
import {
    getOrtResult
} from '../reducers/selectors';
import store from '../store';

const { TabPane } = Tabs;

const AboutModal = (props) => {
    const { webAppOrtResult } = props;
    const { data, repository: { config } } = webAppOrtResult;
    const { excludes } = config;
    const { paths, projects, scopes } = excludes;
    const parameters = Array.from(data.values()).reduce(
        (acc, item) => {
            acc.push(...Object.entries(item));

            return acc;
        },
        []
    );

    return (
        <Modal
            footer={null}
            visible
            height="90%"
            width="80%"
            onCancel={
                () => {
                    store.dispatch({ type: 'APP::HIDE_ABOUT_MODAL' });
                }
            }
        >
            <Tabs animated={false}>
                {
                    (excludes.paths.length !== 0
                    || excludes.projects.length !== 0
                    || excludes.scopes.length !== 0)
                    && (
                        <TabPane tab="Excludes" key="ort-tabs-excludes">
                            <ObjectInspector
                                data={{
                                    paths,
                                    projects,
                                    scopes
                                }}
                                expandLevel={6}
                                name=".ort.yml"
                                showNonenumerable={false}
                            />
                        </TabPane>
                    )
                }
                {
                    parameters.length > 0
                    && (
                        <TabPane tab="Parameters" key="ort-tabs-report-params">
                            <table className="ort-params">
                                <tbody>
                                    {
                                        parameters.map(([key, value]) => {
                                            if (value.startsWith('http')) {
                                                return (
                                                    <tr key={`ort-params-value${key}`}>
                                                        <th>
                                                            {`${key} `}
                                                        </th>
                                                        <td>
                                                            <a
                                                                href={value}
                                                                rel="noopener noreferrer"
                                                                target="_blank"
                                                            >
                                                                {value}
                                                            </a>
                                                        </td>
                                                    </tr>
                                                );
                                            }

                                            return (
                                                <tr key={`ort-params-value-${key}`}>
                                                    <th>
                                                        {`${key} `}
                                                    </th>
                                                    <td>
                                                        {value}
                                                    </td>
                                                </tr>
                                            );
                                        })
                                    }
                                </tbody>
                            </table>
                        </TabPane>
                    )
                }
                <TabPane tab="About" key="ort-tabs-about">
                    <a
                        href="https://github.com/heremaps/oss-review-toolkit"
                        rel="noopener noreferrer"
                        target="_blank"
                    >
                        <div className="ort-loading-logo ort-logo" />
                    </a>
                    <p>
                        version
                        {' '}
                        {webAppOrtResult.getOrtVersion()}
                    </p>
                    <p>
                        For documentation on how to create this report please see
                        {' '}
                        <a
                            href="https://github.com/heremaps/oss-review-toolkit"
                            rel="noopener noreferrer"
                            target="_blank"
                        >
                            http://oss-review-toolkit.org
                        </a>
                        .
                    </p>
                    <p>
                        Licensed under Apache License, Version 2.0 (SPDX: Apache-2.0) but also includes
                        third-party software components under other open source licenses.
                        See OSS Review Toolkit code repository for further details.
                    </p>
                </TabPane>
            </Tabs>
        </Modal>
    );
};

AboutModal.propTypes = {
    webAppOrtResult: PropTypes.object.isRequired
};

const mapStateToProps = state => ({
    webAppOrtResult: getOrtResult(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(AboutModal);
