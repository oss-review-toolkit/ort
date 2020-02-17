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

final DOCKER_BUILD_ARGS = '--build-arg http_proxy=$http_proxy --build-arg https_proxy=$https_proxy'

// Disable the entry point to work around https://issues.jenkins-ci.org/browse/JENKINS-51307.
final DOCKER_RUN_ARGS = '-e http_proxy -e https_proxy --entrypoint=""'

pipeline {
    agent none

    parameters {
        string(
            name: 'VCS_URL',
            description: 'VCS clone URL of the project',
            defaultValue: 'https://github.com/vdurmont/semver4j.git'
        )

        string(
            name: 'VCS_REVISION',
            description: 'VCS revision of the project (prefix tags with "refs/tags/")',
            defaultValue: 'master'
        )

        credentials(
            name: 'VCS_CREDENTIALS',
            description: 'Optional Jenkins credentials id to use for VCS checkout',
            defaultValue: ''
        )

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

        choice(
            name: 'LOG_LEVEL',
            description: 'Log message level',
            choices: ['--info', '--debug', '']
        )

        string(
            name: 'DOWNSTREAM_JOB',
            description: 'Optional name of a downstream job to trigger',
            defaultValue: ''
        )
    }

    stages {
        stage('Clone project') {
            agent any

            environment {
                HOME = "${env.WORKSPACE}@tmp"
                PROJECT_DIR = "${env.HOME}/project"
            }

            steps {
                sh 'rm -fr $PROJECT_DIR'

                // See https://jenkins.io/doc/pipeline/steps/git/.
                checkout([$class: 'GitSCM',
                    userRemoteConfigs: [[url: params.VCS_URL, credentialsId: params.VCS_CREDENTIALS]],
                    branches: [[name: "${params.VCS_REVISION}"]],
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${env.PROJECT_DIR}/source"]]
                ])
            }
        }

        stage('Run the ORT analyzer') {
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
                    if [ "$ALLOW_DYNAMIC_VERSIONS" = "true" ]; then
                        ALLOW_DYNAMIC_VERSIONS_PARAM="--allow-dynamic-versions"
                    fi

                    if [ "$USE_CLEARLY_DEFINED_CURATIONS" = "true" ]; then
                        USE_CLEARLY_DEFINED_CURATIONS_PARAM="--clearly-defined-curations"
                    fi

                    rm -fr analyzer/out/results
                    /opt/ort/bin/ort $LOG_LEVEL analyze $ALLOW_DYNAMIC_VERSIONS_PARAM $USE_CLEARLY_DEFINED_CURATIONS_PARAM -f JSON,YAML -i $PROJECT_DIR/source -o analyzer/out/results
                '''
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'analyzer/out/results/*',
                        fingerprint: true
                    )
                }
            }
        }

        stage('Run the ORT scanner') {
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
                    rm -fr scanner/out/results
                    /opt/ort/bin/ort $LOG_LEVEL scan -f JSON,YAML -i analyzer/out/results/analyzer-result.yml -o scanner/out/results
                '''
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'scanner/out/results/*',
                        fingerprint: true
                    )
                }
            }
        }

        stage('Run the ORT reporter') {
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
                    rm -fr reporter/out/results
                    /opt/ort/bin/ort $LOG_LEVEL report -f CycloneDX,NoticeByPackage,NoticeSummary,StaticHTML,WebApp -i scanner/out/results/scan-result.yml -o reporter/out/results
                '''
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'reporter/out/results/*',
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
