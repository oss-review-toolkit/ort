/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
    useEffect,
    useRef,
    useState
} from 'react';

import pako from 'pako';

import WebAppOrtResult from './models/WebAppOrtResult';
import AppPage from './pages/AppPage';
import ErrorPage from './pages/ErrorPage';
import LoadingPage from './pages/LoadingPage';
import './App.css';

let webAppOrtResult;

function pause(seconds) {
    return new Promise((resolve) => {
        setTimeout(resolve, seconds * 1000);
    });
}

async function loadOrtResultData(setLoadingBarStatus) {
    // Parse JSON report data embedded in HTML page
    const ortResultDataNode = document.querySelector('script[id="ort-report-data"]');
    let ortResultData;

    if (ortResultDataNode) {
        const { textContent: ortResultDataNodeContents, type: ortResultDataNodeType } = ortResultDataNode;

        // Check report is WebApp template e.g. contains 'ORT_REPORT_DATA_PLACEHOLDER'
        if (!!ortResultDataNodeContents && ortResultDataNodeContents.length !== 27) {
            setLoadingBarStatus({ percentage: 10, text: 'Loading result data...' });

            if (ortResultDataNodeType === 'application/gzip') {
                // Decode Base64 (convert ASCII to binary).
                const decodedBase64Data = atob(ortResultDataNodeContents);

                await pause(1);

                // Convert binary string to character-number array.
                const charData = decodedBase64Data.split('').map((x) => x.charCodeAt(0));

                // Turn number array into byte-array.
                const binData = new Uint8Array(charData);

                setLoadingBarStatus({ percentage: 20, text: 'Uncompressing result data...' });
                await pause(1);

                // Decompress byte-array.
                const data = pako.inflate(binData);

                await pause(1);
                setLoadingBarStatus({ percentage: 40, text: 'Uncompressed result data...' });

                ortResultData = JSON.parse(new TextDecoder('utf-8').decode(data));
            } else {
                await pause(1);

                ortResultData = JSON.parse(ortResultDataNodeContents);
            }

            setLoadingBarStatus({ percentage: 55, text: 'Processing result data...' });
            await pause(2);

            webAppOrtResult = new WebAppOrtResult(ortResultData);
            await pause(2);
            setLoadingBarStatus({ percentage: 95, text: 'Processed report data...' });

            // Make webAppOrtResult inspectable via Browser's console
            window.ORT = webAppOrtResult;

            setLoadingBarStatus({ percentage: 99, text: 'Almost ready to display scan report...' });
            await pause(2);
            setLoadingBarStatus({ percentage: 100 });
        } else {
            setLoadingBarStatus({ percentage: -2, text: 'No review results could be loaded...' });
        }
    } else {
        setLoadingBarStatus({ percentage: -1 });
    }
}

function App() {
    const isOrtResultLoaded = useRef(false);
    const [currentPage, setCurrentPage] = useState('loading');
    const [loadingBarStatus, setLoadingBarStatus] = useState({ percentage: 0, text: '' });

    useEffect(() => {
        if (!isOrtResultLoaded.current) {
            isOrtResultLoaded.current = true
            loadOrtResultData(setLoadingBarStatus);
        }
    }, []);

    useEffect(() => {
        if (loadingBarStatus.percentage === -2) {
            setCurrentPage('error');
        }

        if (loadingBarStatus.percentage === -1) {
            setCurrentPage('loading-error');
        }

        if (loadingBarStatus.percentage === 100) {
            setCurrentPage('app');
        }
    }, [loadingBarStatus]);

    const renderPage = () => {
        switch (currentPage) {
            case 'loading':
                return (
                    <LoadingPage status={loadingBarStatus} />
                );
            case 'loading-error':
                return (
                    <ErrorPage
                        message="No review results could be loaded..."
                        submessage="Either something went wrong or you are looking at an ORT report template file."
                    />
                );
            case 'app':
                return <AppPage webAppOrtResult= { webAppOrtResult }/>;
            case 'error':
            default:
                return (
                    <ErrorPage
                        message="Oops, something went wrong..."
                        submessage="Try reloading this report. If that does not solve the issue please
                                contact your OSS Review Toolkit admin(s) for support."
                    />
                );
        }
    };

    return (renderPage())
}

export default App;
