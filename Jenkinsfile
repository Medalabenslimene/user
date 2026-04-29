pipeline {
    agent any
    tools {
        maven 'Maven3'
        jdk 'JDK17'
    }
    environment {
        DOCKER_IMAGE = 'medalabenslimene/user-service'
        DOCKER_TAG = "${BUILD_NUMBER}"
        CONTAINER_NAME = 'user-service-app'
    }
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/Medalabenslimene/user'
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean compile'
            }
        }
        stage('Test & Coverage') {
            steps {
                sh 'mvn verify -Dmaven.test.failure.ignore=true'
            }
        }
        stage('Package') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }
        stage('SonarQube Analysis') {
            steps {
                sh 'mvn sonar:sonar -Dsonar.login=sqp_673c297eee70335ed247fb46e1600e5b8344d380'
            }
        }
        stage('Docker Build') {
            steps {
                sh "docker build -t ${DOCKER_IMAGE}:latest ."
            }
        }
        stage('Deploy') {
            steps {
                sh """
                    docker stop ${CONTAINER_NAME} || true
                    docker rm ${CONTAINER_NAME} || true
                    docker run -d --name ${CONTAINER_NAME} --network devops-net -p 8083:8080 ${DOCKER_IMAGE}:latest
                """
            }
        }
    }
}