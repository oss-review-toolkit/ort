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
import ExpandablePanel from './ExpandablePanel';
import ExpandablePanelContent from './ExpandablePanelContent';
import ExpandablePanelTitle from './ExpandablePanelTitle';

// Generates the HTML for errors related to a package
const PackageErrors = (props) => {
    const { data, show } = props;
    const pkgObj = data;

    // Do not render anything if no errors
    if (Array.isArray(pkgObj.errors) && pkgObj.errors.length === 0) {
        return null;
    }

    return (
        <ExpandablePanel key="ort-package-errors" show={show}>
            <ExpandablePanelTitle titleElem="h4">Package Errors</ExpandablePanelTitle>
            <ExpandablePanelContent>
                {pkgObj.errors.map(error => (
                    <p key={`ort-package-error-${error.code}`}>
                        {error.message}
                    </p>
                ))}
            </ExpandablePanelContent>
        </ExpandablePanel>
    );
};

PackageErrors.propTypes = {
    data: PropTypes.object.isRequired,
    show: PropTypes.bool
};

PackageErrors.defaultProps = {
    show: false
};

export default PackageErrors;
