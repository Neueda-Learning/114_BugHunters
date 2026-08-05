pipeline {
    agent any

    options {
        timestamps()
    }

    environment {
        IMAGE_TAG = 'latest'
        MYSQL_DATABASE = 'paymentdb'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Deploy with Docker Compose') {
            steps {
                withCredentials([
                    string(credentialsId: 'dockerhub-username', variable: 'DOCKER_USERNAME'),
                    string(credentialsId: 'mysql-root-password', variable: 'MYSQL_ROOT_PASSWORD')
                ]) {
                    script {
                        if (isUnix()) {
                            sh 'docker-compose pull'
                            sh 'docker-compose up -d --remove-orphans'
                        } else {
                            bat 'docker-compose pull'
                            bat 'docker-compose up -d --remove-orphans'
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                if (isUnix()) {
                    sh 'docker image prune -f'
                } else {
                    bat 'docker image prune -f'
                }
            }
        }
    }
}
