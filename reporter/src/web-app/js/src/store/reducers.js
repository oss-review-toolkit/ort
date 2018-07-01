// Parse JSON report data embedded in HTML page
const jsonNode = document.querySelector('script[type="application/json"]');
const jsonText = jsonNode.textContent;
const reportData = JSON.parse(jsonText);

export default (initialState = reportData, action) => {
    switch(action.type) {
    default:
        return initialState;
    }
};