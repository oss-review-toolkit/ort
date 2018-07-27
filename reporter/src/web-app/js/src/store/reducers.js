import { convertToRenderFormat } from '../utils';

// Parse JSON report data embedded in HTML page
const jsonNode = document.querySelector('script[role="ort-results"]');
const jsonText = jsonNode.textContent;
const reportData = JSON.parse(jsonText);

export default (initialState = reportData, action) => {
    switch (action.type) {
    case 'CONVERT_REPORT_DATA':
        return convertToRenderFormat(initialState);
    default:
        return initialState;
    }
};
