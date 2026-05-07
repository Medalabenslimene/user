pipeline {
    agent any
    tools {
        maven 'Maven3'
        jdk 'JDK17'
    }
    environment {
        DOCKER_IMAGE = 'medala10/user-service'
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
        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                    sh "docker push ${DOCKER_IMAGE}:latest"
                }
            }
        }
    }
    post {
        success {
            build job: 'user-pipeline-CD', wait: false
        }
        always {
            sh 'docker logout || true'
        }
    }
}
