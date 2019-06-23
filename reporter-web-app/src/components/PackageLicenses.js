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
// import LicenseTag from './LicenseTag';

// Generates the HTML for licenses declared or detected in a package
const PackageLicenses = (props) => {
    const { pkg } = props;
    const {
        concludedLicense,
        detectedLicenses,
        declaredLicenses,
        declaredLicensesProcessed
    } = pkg;

    if (declaredLicenses.length === 0 && detectedLicenses.length === 0) {
        return null;
    }

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

    const renderTrLicenses = (label, id, licenses) => (
        <tr>
            <th>
                {label}
            </th>
            <td className="ort-package-licenses">
                {
                    licenses.map((license, index) => (
                        <span key={`ort-package-license-${license}`}>
                            {license}
                            {index !== (licenses.length - 1) && ', '}
                        </span>
                        /*
                        <LicenseTag
                            key={`ort-package-declared-license-${id}-${license}`}
                            text={license}
                        />
                        */
                    ))
                }
            </td>
        </tr>
    );

    return (
        <table className="ort-package-props">
            <tbody>
                {
                    concludedLicense.length !== 0 && (
                        renderTr(
                            'Concluded SPDX',
                            concludedLicense
                        )
                    )
                }
                {
                    declaredLicenses.length !== 0
                    && renderTrLicenses(
                        'Declared',
                        pkg.id,
                        declaredLicenses
                    )
                }
                {
                    declaredLicensesProcessed.spdxExpression.length !== 0 && (
                        renderTr(
                            'Declared (SPDX)',
                            declaredLicensesProcessed.spdxExpression
                        )
                    )
                }
                {
                    detectedLicenses.length !== 0
                    && renderTrLicenses(
                        'Detected',
                        pkg.id,
                        detectedLicenses
                    )
                }
            </tbody>
        </table>
    );
};

PackageLicenses.propTypes = {
    pkg: PropTypes.object.isRequired
};

export default PackageLicenses;
