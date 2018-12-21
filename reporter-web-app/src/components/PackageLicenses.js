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
import PropTypes from 'prop-types';
import { Col, Row } from 'antd';
import ExpandablePanel from './ExpandablePanel';
import ExpandablePanelContent from './ExpandablePanelContent';
import ExpandablePanelTitle from './ExpandablePanelTitle';
import LicenseTag from './LicenseTag';

// Generates the HTML for licenses declared or detected
// in a package
const PackageLicenses = (props) => {
    const { data, show } = props;
    const pkgObj = data;
    const {
        detected_licenses: detected,
        declared_licenses: declared
    } = pkgObj;

    if (declared.length === 0 && detected.length === 0) {
        return 0;
    }

    const renderDeclaredLicenses = () => {
        if (declared.length !== 0) {
            return (
                <tr>
                    <th>
                        Declared
                    </th>
                    <td className="ort-package-licenses">
                        {
                            declared.map(license => (
                                <LicenseTag
                                    key={`ort-package-declared-license-${pkgObj.id}-${license}`}
                                    text={license}
                                />
                            ))
                        }
                    </td>
                </tr>
            );
        }

        return null;
    };

    const renderDetectedLicenses = () => {
        if (detected.length !== 0) {
            return (
                <tr>
                    <th>
                        Detected
                    </th>
                    <td className="ort-package-licenses">
                        {
                            detected.map(license => (
                                <LicenseTag
                                    key={`ort-package-detected-license-${pkgObj.id}-${license}`}
                                    text={license}
                                />
                            ))
                        }
                    </td>
                </tr>
            );
        }

        return null;
    };

    return (
        <ExpandablePanel key="ort-package-licenses" show={show}>
            <ExpandablePanelTitle titleElem="h4">Package Licenses</ExpandablePanelTitle>
            <ExpandablePanelContent>
                <Row>
                    <Col span={22}>
                        <table className="ort-package-props">
                            <tbody>
                                {renderDeclaredLicenses()}
                                {renderDetectedLicenses()}
                            </tbody>
                        </table>
                    </Col>
                </Row>
            </ExpandablePanelContent>
        </ExpandablePanel>
    );
};

PackageLicenses.propTypes = {
    data: PropTypes.object.isRequired,
    show: PropTypes.bool
};

PackageLicenses.defaultProps = {
    show: false
};

export default PackageLicenses;
