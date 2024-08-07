/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

pipeline {
    agent {
        docker {
            image "httpd:2.4.59"
            label "docker"
        }
    }
    options {
        newContainerPerStage()
    }
    stages {
        stage("foo") {
            steps {
                sh 'ls -la'
                sh 'echo "The answer is 42"'
                sh 'echo "${NODE_NAME}" > tmp.txt'
                sh 'echo $HOSTNAME > host.txt'
            }
        }
        stage("bar") {
            steps {
                sh 'test -f Jenkinsfile'
                sh 'test -f tmp.txt'
                script {
                    def oldHn = readFile('host.txt')
                    def newHn = sh(script:'echo $HOSTNAME', returnStdout:true)
                    if (oldHn == newHn) {
                        error("HOSTNAMES SHOULD NOT MATCH")
                    }
                }
            }

        }
    }
}



