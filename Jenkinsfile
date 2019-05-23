pipeline {
    agent {
        docker {
            image 'maven:3-alpine'
        }
    }
    stages {
        stage('DataCollector') {
            steps {
                dir("WatchDir") {
                    sh 'mvn --version'
                    sh 'mvn package'
                    sh 'ls target/'
                    sh 'echo HAHAHA'
                }
            
            }
        }
    }
}
