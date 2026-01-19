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
    Col,
    Descriptions,
    Row
} from 'antd';

import { convertIso8601Date2Sentence, renderAnchor } from './Shared';

const { Item } = Descriptions;

// Renders scanner details such start/end times and provenance
// for the scan results of a package.
const PackageScanResultsDetails = ({ webAppPackage }) => {
    const { scanResults } = webAppPackage;

    return (
        <Row gutter={[16, 16]}>
            {scanResults.map((webAppScanResult) => {
                const {
                    endTime,
                    provenance: {
                        sourceArtifact,
                        vcsInfo
                    },
                    scanner,
                    startTime
                } = webAppScanResult;

                return (
                    <Col
                        key={webAppScanResult.key} xs={24} sm={24} md={12} lg={12} xl={10}
                    >
                        <Descriptions
                            className="ort-package-scanresult"
                            column={1}
                            size="small"
                        >
                            {
                                !!sourceArtifact && sourceArtifact.url !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-vcs-url"
                                        label="Scanned Source Artifact"
                                    >
                                        {renderAnchor(sourceArtifact.url)}
                                    </Item>
                                )
                            }
                            {
                                !!sourceArtifact && sourceArtifact.hash.value !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-source-artifact-hash"
                                        label="Scanned Source Artifact Hash"
                                    >
                                        {sourceArtifact.hash.value}
                                    </Item>
                                )
                            }
                            {
                                !!sourceArtifact && sourceArtifact.hash.algorithm !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-source-artifact-hash-algo"
                                        label="Scanned Source Artifact Hash Algorithm"
                                    >
                                        {sourceArtifact.hash.algorithm}
                                    </Item>
                                )
                            }
                            {
                                !!vcsInfo && vcsInfo.url !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-vcs-url"
                                        label="Scanned Repository"
                                    >
                                        {renderAnchor(vcsInfo.url)}
                                    </Item>
                                )
                            }
                            {
                                !!vcsInfo && vcsInfo.revision !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-vcs-revision"
                                        label="Scanned Repository Revision"
                                    >
                                        {vcsInfo.revision}
                                    </Item>
                                )
                            }
                            {
                                !!vcsInfo && vcsInfo.path !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-vcs-path"
                                        label="Scanned Repository Path"
                                    >
                                        {vcsInfo.path}
                                    </Item>
                                )
                            }
                            {
                                !!scanner && scanner.name !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-scanner-version"
                                        label="Scanner"
                                    >
                                        {scanner.name}
                                    </Item>
                                )
                            }
                            {
                                !!scanner && scanner.version !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-scanner-version"
                                        label="Scanner Version"
                                    >
                                        {scanner.version}
                                    </Item>
                                )
                            }
                            {
                                !!scanner && scanner.configuration !== ''
                                && (
                                    <Item
                                        key="ort-package-scanresult-scanner-config"
                                        label="Scanner Configuration"
                                    >
                                        {scanner.configuration}
                                    </Item>
                                )
                            }
                            {
                                !!startTime
                                && (
                                    <Item
                                        key="ort-package-scanresult-start-time"
                                        label="Scanner Start Time"
                                    >
                                        {convertIso8601Date2Sentence(startTime)}
                                    </Item>
                                )
                            }
                            {
                                !!endTime
                                && (
                                    <Item
                                        key="ort-packagescanresult-end-time"
                                        label="Scanner End Time"
                                    >
                                        {convertIso8601Date2Sentence(endTime)}
                                    </Item>
                                )
                            }
                        </Descriptions>
                    </Col>
                );
            })}
        </Row>
    )
};

export default PackageScanResultsDetails;
