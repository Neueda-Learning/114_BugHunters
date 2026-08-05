pipeline {
	agent any

	options {
		timestamps()
	}

	environment {
		BACKEND_IMAGE = 'payment-backend:latest'
		FRONTEND_IMAGE = 'payment-frontend:latest'
	}

	stages {
		stage('Checkout') {
			steps {
				checkout scm
			}
		}

		stage('Build Backend') {
			steps {
				script {
					if (isUnix()) {
						sh 'mvn -B clean package'
					} else {
						bat 'mvn -B clean package'
					}
				}
			}
		}

		stage('Build Frontend') {
			steps {
				dir('client') {
					script {
						if (isUnix()) {
							sh 'npm ci'
							sh 'npm run build'
						} else {
							bat 'npm ci'
							bat 'npm run build'
						}
					}
				}
			}
		}

		stage('Build Docker Images') {
			steps {
				script {
					if (isUnix()) {
						sh "docker build -t ${BACKEND_IMAGE} ."
						sh "docker build -t ${FRONTEND_IMAGE} ./client"
					} else {
						bat "docker build -t ${BACKEND_IMAGE} ."
						bat "docker build -t ${FRONTEND_IMAGE} ./client"
					}
				}
			}
		}

		stage('Deploy with Docker Compose') {
			steps {
				script {
					if (isUnix()) {
						sh 'docker compose up -d --build'
					} else {
						bat 'docker compose up -d --build'
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
