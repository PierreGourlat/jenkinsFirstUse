pipeline {
    agent { docker { image 'maven:3.3.3' } }
    stages {
        stage('build') {
            steps {
                sh 'mvn --version'
		sh 'cd WatchDir'
		sh 'mvn package'
            }
        }
    }
}