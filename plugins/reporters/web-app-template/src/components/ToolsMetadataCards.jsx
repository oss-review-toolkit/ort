/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
    Card,
    Col,
    Descriptions,
    Row,
    Typography
} from 'antd';

import { convertIso8601Date2Sentence } from './Shared';

const { Text } = Typography;

const ToolMetadataCards = ({ metadata }) => {
    const tools = [
        { key: 'analyzer', title: 'Analyzer' },
        { key: 'scanner', title: 'Scanner' },
        { key: 'advisor', title: 'Advisor' },
        { key: 'evaluator', title: 'Evaluator' }
    ];

    const durationMinutesSeconds = (startIso, endIso) => {
        if (!startIso || !endIso) return null;
        const toDate = (iso) => new Date(iso.replace(/(\.\d{3})\d*Z$/, '$1Z'));
        const s = toDate(startIso);
        const e = toDate(endIso);
        if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime())) return null;
        const totalSec = Math.max(0, Math.round((e - s) / 1000));
        const mins = Math.floor(totalSec / 60);
        const secs = totalSec % 60;
        return { mins, secs };
    };

    return (
        <Row gutter={[16, 16]}>
            {tools.map(({ key, title }) => {
                const tool = metadata[`${key}`];
                if (tool) {
                    const start = tool.startTime;
                    const end = tool.endTime;
                    const env = tool.environment || {};

                    const dur = durationMinutesSeconds(start, end);

                    return (
                        <Col xs={24} sm={12} md={12} lg={8} xl={6} key={key}>
                            <Card title={title} size="small">
                                <Descriptions column={1} size="small">
                                    <Descriptions.Item label="Started">
                                        <Text>{convertIso8601Date2Sentence(start)}</Text>
                                    </Descriptions.Item>

                                    <Descriptions.Item label="Duration">
                                        <Text>
                                            {dur != null
                                                ? `${dur.mins} minutes ${String(dur.secs).padStart(2, '0')} seconds`
                                                : '—'}
                                        </Text>
                                    </Descriptions.Item>

                                    <Descriptions.Item label="ORT version">
                                        <Text>{env.ortVersion ?? '—'}</Text>
                                    </Descriptions.Item>

                                    <Descriptions.Item label="Java version">
                                        <Text>{env.javaVersion ?? '—'}</Text>
                                    </Descriptions.Item>

                                    <Descriptions.Item label="JDK version">
                                        <Text>{env.buildJdk ?? '—'}</Text>
                                    </Descriptions.Item>

                                    <Descriptions.Item label="OS / CPUs">
                                        <Text>{env.os ? `${env.os} / ${env.processors ?? '—'} CPU` : '—'}</Text>
                                    </Descriptions.Item>

                                    <Descriptions.Item label="Max Memory">
                                        <Text>{env.maxMemory != null ? `${env.maxMemory / 1024 ** 2} MiB` : '—'}</Text>
                                    </Descriptions.Item>
                                </Descriptions>
                            </Card>
                        </Col>
                    );
                }

                return null;
            })}
        </Row>
    );
};

export default ToolMetadataCards;
