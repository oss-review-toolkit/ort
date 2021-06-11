/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import {
    Descriptions,
    Modal,
    Tabs
} from 'antd';
import {
    FileTextOutlined,
    InfoCircleOutlined,
    TagsOutlined
} from '@ant-design/icons';
import { Light as SyntaxHighlighter } from 'react-syntax-highlighter';
import lioshi from 'react-syntax-highlighter/dist/esm/styles/hljs/lioshi';
import yaml from 'react-syntax-highlighter/dist/esm/languages/hljs/yaml';
import {
    getOrtResult
} from '../reducers/selectors';
import store from '../store';

const { Item } = Descriptions;
const { TabPane } = Tabs;

SyntaxHighlighter.registerLanguage('yaml', yaml);

const AboutModal = (props) => {
    const { webAppOrtResult } = props;
    const { repositoryConfiguration } = webAppOrtResult;
    const {
        labels,
        metaData
    } = webAppOrtResult;

    const { analyzerStartTime } = metaData;
    const analyzerStartDate = new Date(analyzerStartTime).toLocaleDateString(
        undefined,
        {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        }
    );

    return (
        <Modal
            footer={null}
            visible
            height="90%"
            width="90%"
            onCancel={
                () => {
                    store.dispatch({ type: 'APP::HIDE_ABOUT_MODAL' });
                }
            }
        >
            <Tabs animated={false}>
                {
                    webAppOrtResult.hasRepositoryConfiguration()
                    && (
                        <TabPane
                            tab={(
                                <span>
                                    <FileTextOutlined />
                                    Excludes (.ort.yml)
                                </span>
                            )}
                            key="ort-tabs-excludes"
                        >
                            <SyntaxHighlighter
                                language="yaml"
                                showLineNumbers
                                style={lioshi}
                            >
                                {repositoryConfiguration}
                            </SyntaxHighlighter>
                        </TabPane>
                    )
                }
                {
                    webAppOrtResult.hasLabels()
                    && (
                        <TabPane
                            tab={(
                                <span>
                                    <TagsOutlined />
                                    Labels
                                </span>
                            )}
                            key="ort-tabs-labels"
                        >
                            <Descriptions
                                bordered
                                column={1}
                                size="small"
                            >
                                {
                                    Object.entries(labels).map(([key, value]) => (
                                        <Item
                                            key={`ort-label-${key}`}
                                            label={key}
                                        >
                                            {
                                                value.startsWith('http')
                                                    ? (
                                                        <a
                                                            href={value}
                                                            rel="noopener noreferrer"
                                                            target="_blank"
                                                        >
                                                            {value}
                                                        </a>
                                                    )
                                                    : value
                                            }
                                        </Item>
                                    ))
                                }
                            </Descriptions>
                        </TabPane>
                    )
                }
                <TabPane
                    tab={(
                        <span>
                            <InfoCircleOutlined />
                            About
                        </span>
                    )}
                    key="ort-tabs-about"
                >
                    <a
                        href="https://github.com/oss-review-toolkit/ort"
                        rel="noopener noreferrer"
                        target="_blank"
                    >
                        <div
                            className="ort-about-logo ort-logo"
                        />
                    </a>
                    <p>
                        For documentation on how to create this report please see
                        {' '}
                        <a
                            href="https://github.com/oss-review-toolkit/ort"
                            rel="noopener noreferrer"
                            target="_blank"
                        >
                            https://oss-review-toolkit.org/
                        </a>
                        .
                    </p>
                    <p>
                        Licensed under Apache License, Version 2.0 (SPDX: Apache-2.0) but also includes
                        third-party software components under other open source licenses.
                        See OSS Review Toolkit code repository for further details.
                    </p>
                    {
                        !!analyzerStartDate
                        && (
                            <p>
                                This ORT report is based on an analysis started on
                                {' '}
                                {analyzerStartDate}
                                .
                            </p>
                        )
                    }
                </TabPane>
            </Tabs>
        </Modal>
    );
};

AboutModal.propTypes = {
    webAppOrtResult: PropTypes.object.isRequired
};

const mapStateToProps = (state) => ({
    webAppOrtResult: getOrtResult(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(AboutModal);
