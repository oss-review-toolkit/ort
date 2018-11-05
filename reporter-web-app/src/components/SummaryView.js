import React from 'react';
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import {
    Col, Row
} from 'antd';
import SummaryViewLicenses from './SummaryViewLicenses';
import SummaryViewTableErrors from './SummaryViewTableErrors';
import SummaryViewTimeline from './SummaryViewTimeline';
import {
    getSummaryDeclaredLicenses,
    getSummaryDeclaredLicensesChart,
    getSummaryDeclaredLicensesFilter,
    getSummaryDeclaredLicensesTotal,
    getSummaryDetectedLicenses,
    getSummaryDetectedLicensesChart,
    getSummaryDetectedLicensesFilter,
    getSummaryDetectedLicensesTotal,
    getSummaryViewShouldComponentUpdate,
    getSummaryRepository,
    getReportMetaData,
    getReportErrorsAdressed,
    getReportErrorsOpen,
    getReportErrorsAdressedTotal,
    getReportErrorsOpenTotal,
    getReportLevelsTotal,
    getReportPackagesTotal,
    getReportProjectsTotal,
    getReportScopesTotal
} from '../reducers/selectors';
import store from '../store';

class SummaryView extends React.Component {
    shouldComponentUpdate() {
        const { shouldComponentUpdate } = this.props;
        return shouldComponentUpdate;
    }

    render() {
        const { errors, licenses } = this.props;

        return (
            <div className="ort-summary">
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryViewTimeline {...this.props} />
                    </Col>
                </Row>
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryViewTableErrors data={errors} />
                    </Col>
                </Row>
                <Row>
                    <Col span={22} offset={1}>
                        <SummaryViewLicenses
                            data={licenses}
                            onChangeDeclaredLicensesTable={
                                (pagination, filters, sorter, extra) => {
                                    store.dispatch({
                                        type: 'SUMMARY::CHANGE_DECLARED_LICENSES_TABLE',
                                        payload: {
                                            declaredChart: extra.currentDataSource,
                                            declaredFilter: {
                                                filteredInfo: filters,
                                                sortedInfo: sorter
                                            }
                                        }
                                    });
                                }
                            }
                            onChangeDetectedLicensesTable={
                                (pagination, filters, sorter, extra) => {
                                    store.dispatch({
                                        type: 'SUMMARY::CHANGE_DETECTED_LICENSES_TABLE',
                                        payload: {
                                            detectedChart: extra.currentDataSource,
                                            detectedFilter: {
                                                filteredInfo: filters,
                                                sortedInfo: sorter
                                            }
                                        }
                                    });
                                }
                            }
                        />
                    </Col>
                </Row>
            </div>
        );
    }
}

SummaryView.propTypes = {
    errors: PropTypes.object.isRequired,
    licenses: PropTypes.object.isRequired
};

const mapStateToProps = state => ({
    errors: {
        addressed: getReportErrorsAdressed(state),
        addressedTotal: getReportErrorsAdressedTotal(state),
        open: getReportErrorsOpen(state),
        openTotal: getReportErrorsOpenTotal(state)
    },
    levelsTotal: getReportLevelsTotal(state),
    licenses: {
        declared: getSummaryDeclaredLicenses(state),
        declaredChart: getSummaryDeclaredLicensesChart(state),
        declaredFilter: getSummaryDeclaredLicensesFilter(state),
        declaredTotal: getSummaryDeclaredLicensesTotal(state),
        detected: getSummaryDetectedLicenses(state),
        detectedChart: getSummaryDetectedLicensesChart(state),
        detectedFilter: getSummaryDetectedLicensesFilter(state),
        detectedTotal: getSummaryDetectedLicensesTotal(state)
    },
    metadata: getReportMetaData(state),
    packagesTotal: getReportPackagesTotal(state),
    projectsTotal: getReportProjectsTotal(state),
    scopesTotal: getReportScopesTotal(state),
    shouldComponentUpdate: getSummaryViewShouldComponentUpdate(state),
    repository: getSummaryRepository(state)
});

export default connect(
    mapStateToProps,
    () => ({})
)(SummaryView);
