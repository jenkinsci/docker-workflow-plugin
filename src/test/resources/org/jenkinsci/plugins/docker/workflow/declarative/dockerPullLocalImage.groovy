/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
    agent any
    stages {
        stage("build image") {
            steps {
                sh 'docker build -t maven:3-eclipse-temurin-17-alpine .'
            }
        }
        stage("in built image") {
            agent {
                docker {
                    image "maven:3-eclipse-temurin-17-alpine"
                    args "-v /tmp:/tmp"
                    reuseNode true
                }
            }
            steps {
                sh 'cat /hi-there'
                sh 'echo "The answer is 42"'
            }
        }
        stage("in pulled image") {
            agent {
                docker {
                    image "maven:3-eclipse-temurin-17-alpine"
                    alwaysPull true
                }
            }
            steps {
                sh 'mvn --version'
            }
        }
    }
}



