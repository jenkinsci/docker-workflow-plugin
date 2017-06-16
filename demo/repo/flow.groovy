node {
    git '/tmp/repo'
    // We are pushing to a private secure Docker registry in this demo.
    // 'docker-registry-login' is the username/password credentials ID as defined in Jenkins Credentials.
    // This is used to authenticate the Docker client to the registry.
    withCredentials([usernamePassword(usernameVariable: 'USER', passwordVariable: 'PASS', credentialsId: 'docker-registry-login')]) {
        sh 'docker login -u $USER -p $PASS https://localhost/'
        try {
            stage('Build image') {
                // Build the new image, and push a snapshot of it for the record.
                sh 'sh docker-build.sh && sh docker-push-snapshot.sh'
            }
            stage('Test Image') {
                // Spin up a Maven + Xvnc test container, linking it to the petclinic app container
                // allowing the Maven tests to send HTTP requests between the containers.
                sh 'sh docker-test.sh'
                archiveArtifacts 'screenshot.jpg'
                input "How do you like ${BUILD_URL}artifact/screenshot.jpg?"
            }
            stage('Promote Image') {
                // All the tests passed. We can now retag and push the 'latest' image.
                sh 'sh docker-promote.sh'
            }
        } finally {
            sh 'docker logout https://localhost/'
        }
    }
}
