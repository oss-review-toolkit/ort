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
import { Col, Row } from 'antd';

export const PackagesTableDetails = (props) => {
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
