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
                    string(credentialsId: 'mysql-root-password', variable: 'MYSQL_ROOT_PASSWORD'),
                    string(credentialsId: 'spring-mail-username', variable: 'SPRING_MAIL_USERNAME'),
                    string(credentialsId: 'spring-mail-password', variable: 'SPRING_MAIL_PASSWORD'),
                    string(credentialsId: 'otp-recipient-email', variable: 'APP_OTP_RECIPIENT_EMAIL')
                ]) {
                    script {
                        if (isUnix()) {
                            sh 'docker-compose down 2>/dev/null || true'
                            sh 'docker rm -f payment-backend payment-frontend 2>/dev/null || true'
                            sh 'docker-compose up -d --build --remove-orphans'
                        } else {
                            bat 'docker-compose down 2>nul || exit /b 0'
                            bat 'docker rm -f payment-backend payment-frontend 2>nul || exit /b 0'
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
