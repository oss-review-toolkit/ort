/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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
        vcs,
        vcsProcessed
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
                purl.length !== 0
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
                vcs.url.length !== 0
                && (
                    <Item
                        label="Repository Declared"
                        key="ort-package-vcs-url"
                    >
                        {renderAhref(vcs.url)}
                    </Item>
                )
            }
            {
                vcsProcessed.url.length !== 0
                && (
                    <Item
                        label="Repository Processed"
                        key="ort-package-vcs-processed-url"
                    >
                        {renderAhref(vcsProcessed.url)}
                    </Item>
                )
            }
            {
                sourceArtifact.url.length !== 0
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
                binaryArtifact.url.length !== 0
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
