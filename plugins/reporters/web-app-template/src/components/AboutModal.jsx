/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
    FileTextOutlined,
    InfoCircleOutlined,
    TagsOutlined
} from '@ant-design/icons';
import {
    Descriptions,
    Modal,
    Tabs
} from 'antd';
import yaml from 'react-syntax-highlighter/dist/esm/languages/hljs/yaml';
import lioshi from 'react-syntax-highlighter/dist/esm/styles/hljs/lioshi';

import { Light as SyntaxHighlighter } from 'react-syntax-highlighter';

const { Item } = Descriptions;

SyntaxHighlighter.registerLanguage('yaml', yaml);

const AboutModal = ({ webAppOrtResult, isModalVisible, handleModalCancel }) => {
    const {
        labels,
        metadata,
        repositoryConfiguration
    } = webAppOrtResult;

    const analyzerStartDate = new Date(metadata.analyzerStartTime).toLocaleDateString(
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
            height="90%"
            width="90%"
            open={isModalVisible}
            onCancel={handleModalCancel}
        >
            <Tabs
                animated={false}
                items={(() => {
                    const tabItems = [];

                    if (webAppOrtResult.hasRepositoryConfiguration()) {
                        tabItems.push({
                            label: (
                                <span>
                                    <FileTextOutlined style={{ marginRight: 5 }}/>
                                    Excludes (.ort.yml)
                                </span>
                            ),
                            key: 'ort-tabs-excludes',
                            children: (
                                <SyntaxHighlighter
                                    language="yaml"
                                    showLineNumbers={true}
                                    style={lioshi}
                                >
                                    {repositoryConfiguration}
                                </SyntaxHighlighter>
                            )
                        });
                    }

                    if (webAppOrtResult.hasLabels()) {
                        tabItems.push({
                            label: (
                                <span>
                                    <TagsOutlined style={{ marginRight: 5 }}/>
                                    Labels
                                </span>
                            ),
                            key: 'ort-tabs-labels',
                            children: (
                                <Descriptions
                                    bordered={true}
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
                            )
                        });
                    }

                    tabItems.push({
                        label: (
                            <span>
                                <InfoCircleOutlined style={{ marginRight: 5 }}/>
                                About
                            </span>
                        ),
                        key: 'ort-tabs-about',
                        children: (
                            <span>
                                <a
                                    href="https://github.com/oss-review-toolkit/ort"
                                    rel="noopener noreferrer"
                                    target="_blank"
                                >
                                    <div
                                        className="ort-about-logo ort-logo"
                                        style={{ width: '420px', height: '90px', marginBottom: '10px' }}
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
                                    third-party software components under other open source licenses.<br/>
                                    See <a href="https://github.com/oss-review-toolkit/ort">
                                        OSS Review Toolkit code repository
                                    </a> for further details.
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
                            </span>
                        )
                    });

                    return tabItems;
                })() }
            />
        </Modal>
    );
};

export default AboutModal;
