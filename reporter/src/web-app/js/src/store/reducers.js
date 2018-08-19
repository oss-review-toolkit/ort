import { convertToRenderFormat } from '../utils';
const protobuf = require("protobufjs");

// Parse JSON report data embedded in HTML page
const reportDataNode = document.querySelector('script[role="ort-report-data"]');
const reportDataText = reportDataNode ? reportDataNode.textContent : undefined;
let reportData = {};

if (reportDataText !== undefined && reportDataText.trim().length !== 0) {
   if (reportDataNode.type) {
       switch (reportDataNode.type) {
       case 'application/json':
           reportData = JSON.parse(reportDataText);
           break;
       case 'application/protobuf':
           console.log('application/protobuf is not yet supported');
           break;
       default:
           break;
       }
   }
}

export default (initialState = reportData, action) => {
    switch (action.type) {
    case 'CONVERT_REPORT_DATA':
        return convertToRenderFormat(initialState);
    default:
        return initialState;
    }
};
