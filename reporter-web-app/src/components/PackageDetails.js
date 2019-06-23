/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

// Generates the HTML for packages details like
// description, source code location(s), etc.
const PackageDetails = (props) => {
    const { pkg, webAppOrtResult } = props;
    const {
        id,
        purl,
        description,
        homepageUrl,
        binaryArtifact,
        sourceArtifact,
        vcs,
        vcsProcessed
    } = pkg;

    const renderAhref = (text, href) => (
        <a
            href={href || text}
            rel="noopener noreferrer"
            target="_blank"
        >
            {text}
        </a>
    );
    const renderTr = (thVal, tdVal) => (
        <tr>
            <th>
                {thVal}
            </th>
            <td>
                {tdVal}
            </td>
        </tr>
    );

    return (
        <table className="ort-package-props">
            <tbody>
                {renderTr('Id', id) }
                { purl.length !== 0 && (renderTr('Package URL', purl)) }
                { webAppOrtResult.hasProjectId(id) && (
                    renderTr(
                        'Defined in',
                        webAppOrtResult.getProjectById(id).definitionFilePath
                    ))
                }
                { description.length !== 0 && (renderTr('Description', description)) }
                { homepageUrl.length !== 0 && (
                    renderTr(
                        'Homepage',
                        renderAhref(homepageUrl)
                    ))
                }
                { vcs.url.length !== 0 && (
                    renderTr(
                        'Repository Declared',
                        renderAhref(vcs.url)
                    ))
                }
                { vcsProcessed.url.length !== 0 && (
                    renderTr(
                        'Repository Processed',
                        renderAhref(vcsProcessed.url)
                    ))
                }
                { sourceArtifact.url.length !== 0 && (
                    renderTr(
                        'Source Artifact',
                        renderAhref(sourceArtifact.url)
                    ))
                }
                { binaryArtifact.url.length !== 0 && (
                    renderTr(
                        'Binary Artifact',
                        renderAhref(binaryArtifact.url)
                    ))
                }
            </tbody>
        </table>
    );
};

PackageDetails.propTypes = {
    pkg: PropTypes.object.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

export default PackageDetails;
