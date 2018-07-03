import React from 'react';
import { connect } from 'react-redux';
import { Col, Collapse, Row } from 'antd';
import { DependencyTable } from './DependencyTable';

const Panel = Collapse.Panel;

class TableView extends React.Component {

    constructor(props) {
        super(props);
        this.state = {};
        if (props.reportData) {
            this.state = {
                ...this.state,
                data: props.reportData
            };
        }

        // FIXME For debugging purposes print scan results to console 
        console.log('renderData', this.state.data);
        window.listData = this.state.data;
    }

    render() {
        const { data } = this.state;
        const panelHeader = (definitionFilePath) => {
            let packagesNrText = (num) => {
                switch(num) {
                    case 0:
                        return '';
                    case 1:
                        return '1 package';
                    default:
                        return num + ' packages';
                }
            }
            return (<Row>
                        <Col span={12}>{'./' + definitionFilePath}</Col>
                        <Col span={2} offset={10}>{packagesNrText(data.projects[definitionFilePath].length)}</Col>
                    </Row>
                   );
        }
        const panelItems = Object.keys(data.projects).map((definitionFilePath) => (
                <Panel header={panelHeader(definitionFilePath)} key={'./' + definitionFilePath}>
                    <DependencyTable 
                        key={definitionFilePath}
                        project={definitionFilePath}
                        data={data}/>
                </Panel>
            ))
        return (
            <Collapse defaultActiveKey={Object.keys(data.projects).map((item) => { return './' + item })} >
                {panelItems}
            </Collapse>
        );
    }
}

export default connect(
    (state) => ({reportData: state}),
    () => ({})
)(TableView); 