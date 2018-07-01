import React from 'react';
import { connect } from 'react-redux';
import { convertToProjectTableFormat } from '../utils';
import { DependencyTable } from './DependencyTable';

class TableView extends React.Component {

    constructor(props) {
        super(props);
        this.state = {};
        if (props.reportData) {
            this.state = {
                ...this.state,
                data: this.convertData(props.reportData)
            };
        }

        // FIXME For debugging purposes print scan results to console 
        console.log('renderData', this.state.data);
        window.listData = this.state.data;
    }

    convertData = (reportData) => {
        return convertToProjectTableFormat(reportData);
    }

    render() {
        const { data } = this.state;
        return Object.keys(data.projects).map((definitionFilePath) => (
            <div key={definitionFilePath}>
                <h4>Packages resolved from ./{definitionFilePath}</h4>
                <DependencyTable 
                    key={definitionFilePath}
                    project={definitionFilePath}
                    data={data}/>
            </div>
        ));
    }
}

export default connect(
    (state) => ({reportData: state}),
    () => ({})
)(TableView); 