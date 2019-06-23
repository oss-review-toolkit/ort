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
import ScanFindingsTable from './ScanFindingsTable';

// Generates the HTML to display scan results for a package
const PackageScanResults = (props) => {
    const {
        filter,
        pkg,
        webAppOrtResult
    } = props;

    return (
        <ScanFindingsTable
            filter={filter}
            pkg={pkg}
            webAppOrtResult={webAppOrtResult}
        />
    );
};

PackageScanResults.propTypes = {
    filter: PropTypes.object,
    pkg: PropTypes.object.isRequired,
    webAppOrtResult: PropTypes.object.isRequired
};

PackageScanResults.defaultProps = {
    filter: {
        type: [],
        value: []
    }
};

export default PackageScanResults;
