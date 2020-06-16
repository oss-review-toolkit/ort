/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * Required Jenkins plugins:
 * - https://plugins.jenkins.io/pipeline-utility-steps/
 */

import com.cloudbees.groovy.cps.NonCPS

import java.io.IOException

final DOCKER_BUILD_ARGS = '--build-arg http_proxy=$http_proxy --build-arg https_proxy=$https_proxy'

// Disable the entry point to work around https://issues.jenkins-ci.org/browse/JENKINS-51307.
final DOCKER_RUN_ARGS = '-e http_proxy -e https_proxy --entrypoint=""'

@NonCPS
static sortProjectsByPathDepth(projects) {
    return projects.toSorted { it.definition_file_path.count("/") }
}

def projectVcsCredentials = []
def ortConfigVcsCredentials = []

pipeline {
    agent none

    parameters {
        /*
         * Parameters about the project to run ORT on.
         */

        string(
            name: 'PROJECT_VCS_URL',
            description: 'VCS clone URL of the project',
            defaultValue: 'https://github.com/vdurmont/semver4j.git'
        )

        string(
            name: 'PROJECT_VCS_REVISION',
            description: 'VCS revision of the project (prefix Git tags with "refs/tags/")',
            defaultValue: ''
        )

        credentials(
            name: 'PROJECT_VCS_CREDENTIALS',
            description: 'Optional Jenkins credentials id to use for VCS checkout',
            defaultValue: ''
        )

        /*
         * General ORT parameters.
         */

        string(
            name: 'ORT_CONFIG_VCS_URL',
            description: 'Optional VCS clone URL of the ORT configuration',
            defaultValue: ''
        )

        string(
            name: 'ORT_CONFIG_VCS_REVISION',
            description: 'Optional VCS revision of the ORT configuration (prefix Git tags with "refs/tags/")',
            defaultValue: ''
        )

        credentials(
            name: 'ORT_CONFIG_VCS_CREDENTIALS',
            description: 'Optional Jenkins credentials id to use for VCS checkout',
            defaultValue: ''
        )

        choice(
            name: 'LOG_LEVEL',
            description: 'Log message level',
            choices: ['--info', '--debug', '']
        )

        /*
         * ORT analyzer tool parameters.
         */

        booleanParam(
            name: 'ALLOW_DYNAMIC_VERSIONS',
            defaultValue: false,
            description: 'Allow dynamic versions of dependencies (support projects without lock files)'
        )

        booleanParam(
            name: 'USE_CLEARLY_DEFINED_CURATIONS',
            defaultValue: true,
            description: 'Use package curation data from the ClearlyDefined service'
        )

        /*
         * ORT scanner tool parameters.
         */

        booleanParam(
            name: 'RUN_SCANNER',
            defaultValue: true,
            description: 'Run the scanner tool'
        )

        /*
         * ORT reporter tool parameters.
         */

        booleanParam(
            name: 'RUN_REPORTER',
            defaultValue: true,
            description: 'Run the reporter tool'
        )

        /*
         * Parameters for Jenkins job orchestration.
         */

        string(
            name: 'DOWNSTREAM_JOB',
            description: 'Optional name of a downstream job to trigger',
            defaultValue: ''
        )
    }

    stages {
        stage('Configure') {
            agent any

            steps {
                script {
                    if (!params.PROJECT_VCS_CREDENTIALS.allWhitespace) {
                        projectVcsCredentials += usernamePassword(credentialsId: params.PROJECT_VCS_CREDENTIALS, usernameVariable: 'LOGIN', passwordVariable: 'PASSWORD')
                    }

                    if (!params.ORT_CONFIG_VCS_CREDENTIALS.allWhitespace) {
                        ortConfigVcsCredentials += usernamePassword(credentialsId: params.ORT_CONFIG_VCS_CREDENTIALS, usernameVariable: 'LOGIN', passwordVariable: 'PASSWORD')
                    }
                }
            }
        }

        stage('Build ORT Docker image') {
            agent {
                dockerfile {
                    additionalBuildArgs DOCKER_BUILD_ARGS
                    args DOCKER_RUN_ARGS
                }
            }

            steps {
                sh '''
                    /opt/ort/bin/ort $LOG_LEVEL --version
                '''
            }
        }

        stage('Clone project') {
            agent {
                dockerfile {
                    additionalBuildArgs DOCKER_BUILD_ARGS
                    args DOCKER_RUN_ARGS
                }
            }

            environment {
                HOME = "${env.WORKSPACE}@tmp"
                PROJECT_DIR = "${env.HOME}/project"
            }

            steps {
                withCredentials(projectVcsCredentials) {
                    sh '''
                        echo "default login $LOGIN password $PASSWORD" > $HOME/.netrc

                        if [ -n "$PROJECT_VCS_REVISION" ]; then
                            VCS_REVISION_OPTION="--vcs-revision $PROJECT_VCS_REVISION"
                        fi

                        rm -fr $PROJECT_DIR
                        /opt/ort/bin/ort $LOG_LEVEL download --project-url $PROJECT_VCS_URL $VCS_REVISION_OPTION -o $PROJECT_DIR/source

                        rm -f $HOME/.netrc
                    '''
                }
            }
        }

        stage('Clone ORT configuration') {
            agent {
                dockerfile {
                    additionalBuildArgs DOCKER_BUILD_ARGS
                    args DOCKER_RUN_ARGS
                }
            }

            when {
                beforeAgent true

                expression {
                    !params.ORT_CONFIG_VCS_URL.allWhitespace
                }
            }

            environment {
                HOME = "${env.WORKSPACE}@tmp"
                ORT_DATA_DIR = "${env.HOME}/.ort"
            }

            steps {
                withCredentials(ortConfigVcsCredentials) {
                    sh '''
                        echo "default login $LOGIN password $PASSWORD" > $HOME/.netrc

                        if [ -n "$ORT_CONFIG_VCS_REVISION" ]; then
                            VCS_REVISION_OPTION="--vcs-revision $ORT_CONFIG_VCS_REVISION"
                        fi

                        rm -fr $ORT_DATA_DIR/config
                        /opt/ort/bin/ort $LOG_LEVEL download --project-url $ORT_CONFIG_VCS_URL $VCS_REVISION_OPTION -o $ORT_DATA_DIR/config

                        rm -f $HOME/.netrc
                    '''
                }
            }
        }

        stage('Run ORT analyzer') {
            agent {
                dockerfile {
                    additionalBuildArgs DOCKER_BUILD_ARGS
                    args DOCKER_RUN_ARGS
                }
            }

            environment {
                HOME = "${env.WORKSPACE}@tmp"
                PROJECT_DIR = "${env.HOME}/project"
            }

            steps {
                sh '''
                    /opt/ort/bin/set_gradle_proxy.sh

                    if [ "$ALLOW_DYNAMIC_VERSIONS" = "true" ]; then
                        ALLOW_DYNAMIC_VERSIONS_OPTION="--allow-dynamic-versions"
                    fi

                    if [ "$USE_CLEARLY_DEFINED_CURATIONS" = "true" ]; then
                        USE_CLEARLY_DEFINED_CURATIONS_OPTION="--clearly-defined-curations"
                    fi

                    rm -fr out/results
                    /opt/ort/bin/ort $LOG_LEVEL analyze $ALLOW_DYNAMIC_VERSIONS_OPTION $USE_CLEARLY_DEFINED_CURATIONS_OPTION -f JSON,YAML -i $PROJECT_DIR/source -o out/results/analyzer
                    ln -frs out/results/analyzer/analyzer-result.yml out/results/current-result.yml
                '''

                script {
                    try {
                        def result = readYaml file: 'out/results/analyzer/analyzer-result.yml'
                        def projects = result.analyzer?.result?.projects

                        if (projects) {
                            // Determine the / a root project simply by sorting by path depth.
                            def sortedProjects = sortProjectsByPathDepth(projects)

                            // There is always at least one (unmanaged) project.
                            def rootProjectId = sortedProjects.first().id

                            currentBuild.displayName += ": $rootProjectId"
                        }
                    } catch (IOException e) {
                        // Ignore and just skip setting a custom display name.
                    }
                }
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'out/results/analyzer/*',
                        fingerprint: true
                    )
                }
            }
        }

        stage('Run ORT scanner') {
            when {
                beforeAgent true

                expression {
                    params.RUN_SCANNER
                }
            }

            agent {
                dockerfile {
                    additionalBuildArgs DOCKER_BUILD_ARGS
                    args DOCKER_RUN_ARGS
                }
            }

            environment {
                HOME = "${env.WORKSPACE}@tmp"
            }

            steps {
                withCredentials(projectVcsCredentials) {
                    sh '''
                        echo "default login $LOGIN password $PASSWORD" > $HOME/.netrc

                        /opt/ort/bin/ort $LOG_LEVEL scan -f JSON,YAML -i out/results/current-result.yml -o out/results/scanner
                        ln -frs out/results/scanner/scan-result.yml out/results/current-result.yml

                        rm -f $HOME/.netrc
                    '''
                }
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'out/results/scanner/*',
                        fingerprint: true
                    )
                }
            }
        }

        stage('Run ORT reporter') {
            when {
                beforeAgent true

                expression {
                    params.RUN_REPORTER
                }
            }

            agent {
                dockerfile {
                    additionalBuildArgs DOCKER_BUILD_ARGS
                    args DOCKER_RUN_ARGS
                }
            }

            environment {
                HOME = "${env.WORKSPACE}@tmp"
            }

            steps {
                sh '''
                    /opt/ort/bin/ort $LOG_LEVEL report -f CycloneDX,NoticeByPackage,NoticeSummary,StaticHTML,WebApp -i out/results/current-result.yml -o out/results/reporter
                '''
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'out/results/reporter/*',
                        fingerprint: true
                    )
                }
            }
        }

        stage('Trigger downstream job') {
            agent any

            when {
                beforeAgent true

                expression {
                    !params.DOWNSTREAM_JOB.allWhitespace
                }
            }

            steps {
                build job: params.DOWNSTREAM_JOB, wait: false
            }
        }
    }
}
