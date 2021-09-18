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

import React from 'react';
import PropTypes from 'prop-types';
import { Descriptions } from 'antd';

const { Item } = Descriptions;

// Generates the HTML for packages details like
// description, source code location(s), etc.
const PackageDetails = (props) => {
    const { webAppPackage } = props;
    const {
        id,
        isProject,
        definitionFilePath,
        purl,
        description,
        homepageUrl,
        binaryArtifact,
        sourceArtifact,
        vcs: {
            path: vcsPath,
            revision: vcsRevision = webAppPackage.vcs.resolvedRevision,
            url: vcsUrl,
        },
        vcsProcessed: {
            path: vcsProcessedPath,
            revision: vcsProcessedRevision = webAppPackage.vcsProcessed.resolvedRevision,
            url: vcsProcessedUrl
        }
    } = webAppPackage;

    const renderAhref = (text, href) => (
        <a
            href={href || text}
            rel="noopener noreferrer"
            target="_blank"
        >
            {text}
        </a>
    );

    return (
        <Descriptions
            className="ort-package-details"
            column={1}
            size="small"
        >
            <Item label="Id">
                {id}
            </Item>
            {
                !!purl
                && (
                    <Item
                        label="Package URL"
                        key="ort-package-purl"
                    >
                        {purl}
                    </Item>
                )
            }
            {
                isProject
                && (
                    <Item
                        label="Defined in"
                        key="ort-package-definition-file-path"
                    >
                        {definitionFilePath}
                    </Item>
                )
            }
            {
                description
                && (
                    <Item
                        label="Description"
                        key="ort-package-description"
                    >
                        {description}
                    </Item>
                )
            }
            {
                homepageUrl
                && (
                    <Item
                        label="Homepage"
                        key="ort-package-homepage"
                    >
                        {renderAhref(homepageUrl)}
                    </Item>
                )
            }
            {
                !!vcsUrl
                && (
                    <Item
                        label="Declared Repository"
                        key="ort-package-vcs-url"
                    >
                        {renderAhref(vcsUrl)}
                    </Item>
                )
            }
            {
                !!vcsRevision
                && vcsRevision !== vcsProcessedRevision
                && (
                    <Item
                        label="Declared Repository Revision"
                        key="ort-package-vcs-revision"
                    >
                        {vcsRevision}
                    </Item>
                )
            }
            {
                !!vcsPath
                && (
                    <Item
                        label="Declared Repository Sources Path"
                        key="ort-package-vcs-path"
                    >
                        {vcsPath}
                    </Item>
                )
            }
            {
                !!vcsProcessedUrl
                && (
                    <Item
                        label="Processed Repository"
                        key="ort-package-vcs-processed-url"
                    >
                        {renderAhref(vcsProcessedUrl)}
                    </Item>
                )
            }
            {
                !!vcsProcessedRevision
                && (
                    <Item
                        label="Processed Repository Revision"
                        key="ort-package-vcs-processed-resolved-revision"
                    >
                        {vcsProcessedRevision}
                    </Item>
                )
            }
            {
                !!vcsProcessedPath
                && (
                    <Item
                        label="Processed Repository Sources Path"
                        key="ort-package-vcs-processed-path"
                    >
                        {vcsProcessedPath}
                    </Item>
                )
            }
            {
                !!sourceArtifact.url
                && (
                    <Item
                        label="Source Artifact"
                        key="ort-package-source-artifact"
                    >
                        {renderAhref(sourceArtifact.url)}
                    </Item>
                )
            }
            {
                !!binaryArtifact.url
                && (
                    <Item
                        label="Binary Artifact"
                        key="ort-package-binary-artifact"
                    >
                        {renderAhref(binaryArtifact.url)}
                    </Item>
                )
            }
        </Descriptions>
    );
};

PackageDetails.propTypes = {
    webAppPackage: PropTypes.object.isRequired
};

export default PackageDetails;
