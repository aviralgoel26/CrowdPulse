pipeline {
    agent any

    environment {
        DOCKERHUB_USERNAME = "aviralgoel26"
        DOCKERHUB_CREDS = credentials('dockerhub-creds')
    }

    stages {

       

        stage('Build Backend') {
            steps {
                dir('backend') {
                    bat 'mvn clean package -DskipTests'
                    bat 'docker build -t aviralgoel26/crowdpulse-backend:latest .'
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    bat 'docker build -t aviralgoel26/crowdpulse-frontend:latest .'
                }
            }
        }

        stage('Docker Login') {
            steps {
                bat 'docker login -u %DOCKERHUB_USERNAME% -p %DOCKERHUB_CREDS_PSW%'
            }
        }

        stage('Push Images') {
            steps {
                bat 'docker push aviralgoel26/crowdpulse-backend:latest'
                bat 'docker push aviralgoel26/crowdpulse-frontend:latest'
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                bat 'kubectl apply -f k8s/ --validate=false'
            }
        }
    }
}