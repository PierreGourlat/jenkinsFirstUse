pipeline {
agent {
    docker {
        image 'maven:3-alpine'
    }
}
    stages {
        stage('Test') {
            steps {
		sh 'mvn --version'
                sh 'echo HAHAHAHA'
            }
        }
    }
}
