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

// Generates the HTML for licenses declared or detected in a package
const PackageLicenses = (props) => {
    const { webAppPackage } = props;

    return (
        <Descriptions
            className="ort-package-details"
            column={1}
            size="small"
        >
            {
                webAppPackage.hasConcludedLicense()
                && (
                    <Item
                        label="Concluded SPDX"
                        key="ort-package-concluded-licenses"
                    >
                        {webAppPackage.concludedLicense}
                    </Item>
                )
            }
            {
                webAppPackage.hasDeclaredLicenses()
                && (
                    <Item
                        label="Declared"
                        key="ort-package-declared-licenses"
                    >
                        {Array.from(webAppPackage.declaredLicenses).join(', ')}
                    </Item>
                )
            }
            {
                webAppPackage.hasDeclaredLicensesSpdxExpression()
                && (
                    <Item
                        label="Declared (SPDX)"
                        key="ort-package-declared-spdx-licenses"
                    >
                        {webAppPackage.declaredLicensesSpdxExpression}
                    </Item>
                )
            }
            {
                webAppPackage.hasDeclaredLicensesUnmapped()
                && (
                    <Item
                        label="Declared (non-SPDX)"
                        key="ort-package-declared-non-spdx-licenses"
                    >
                        {Array.from(webAppPackage.declaredLicensesUnmapped).join(', ')}
                    </Item>
                )
            }
            {
                webAppPackage.hasDetectedLicenses()
                && (
                    <Item
                        label="Detected"
                        key="ort-package-detected-licenses"
                    >
                        {
                            !webAppPackage.hasDetectedExcludedLicenses()
                                ? Array.from(webAppPackage.detectedLicenses).join(', ')
                                : Array.from(webAppPackage.detectedLicenses)
                                    .reduce((acc, license) => {
                                        const { detectedExcludedLicenses } = webAppPackage;
                                        if (!detectedExcludedLicenses.has(license)) {
                                            acc.push(license);
                                        }

                                        return acc;
                                    }, [])
                                    .join(', ')
                        }
                    </Item>
                )
            }
            {
                webAppPackage.hasDetectedExcludedLicenses()
                && (
                    <Item
                        label="Detected Excluded"
                        key="ort-package-detected-excluded-licenses"
                    >
                        {Array.from(webAppPackage.detectedExcludedLicenses).join(', ')}
                    </Item>
                )
            }
        </Descriptions>
    );
};

PackageLicenses.propTypes = {
    webAppPackage: PropTypes.object.isRequired
};

export default PackageLicenses;
