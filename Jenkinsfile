pipeline {
    agent any

    options {
        timestamps()
    }

    environment {
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
                    string(credentialsId: 'mysql-root-password', variable: 'MYSQL_ROOT_PASSWORD')
                ]) {
                    script {
                        if (isUnix()) {
                            sh 'docker-compose down'
                            sh 'docker-compose up -d --build --remove-orphans'
                        } else {
                            bat 'docker-compose down'
                            bat 'docker-compose up -d --build --remove-orphans'
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
