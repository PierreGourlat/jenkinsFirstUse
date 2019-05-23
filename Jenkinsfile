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
		sh 'cd WatchDir'
		sh 'ls'
		sh 'mvn package'
		sh 'ls target/'
                sh 'echo HAHAHAHA'
            }
        }
    }
}
