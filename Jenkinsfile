#!/usr/bin/env groovy
pipeline {
    agent {label 'java'}  

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timeout(time: 1, unit: 'HOURS')
        timestamps()
    }

    environment {
        POM_VERSION = getVersion()
        JAR_NAME = getJarName()
        AWS_ECR_REGION = 'ca-central-1'
        AWS_ECS_SERVICE = 'spring'
        AWS_ECS_TASK_DEFINITION = 'spring'
        AWS_ECS_COMPATIBILITY = 'EC2'
        AWS_ECS_NETWORK_MODE = 'bridge'
        AWS_ECS_CPU = '256'
        AWS_ECS_MEMORY = '128'
        AWS_ECS_CLUSTER = 'production'
        AWS_ECS_TASK_DEFINITION_PATH = './ecs/container_definitions.json'
    }

    stages {
        stage('BUILD & TEST JAVA APPLICATION') {
            steps {
                withMaven(options: [artifactsPublisher(), mavenLinkerPublisher(), dependenciesFingerprintPublisher(disabled: true), jacocoPublisher(disabled: true), junitPublisher(disabled: true)]) {
                    sh "./mvnw package"
                    }
            }
        }
        stage('BUILD DOCKER IMAGE') {
            steps {
                withCredentials([string(credentialsId: 'AWS_REPOSITORY_URL_SECRET', variable: 'AWS_ECR_URL')]) {
                    script {
                        sh('#!/bin/sh -e\n' + "set -x")
                        docker.build("${AWS_ECR_URL}:${POM_VERSION}", "--build-arg JAR_FILE=target/${JAR_NAME} .")
                    }
                }
            }
        }

        stage('PUSH IMAGE TO ECR') {
            steps {
                withCredentials([string(credentialsId: 'AWS_REPOSITORY_URL_SECRET', variable: 'AWS_ECR_URL')]) {
                    withAWS(region: "${AWS_ECR_REGION}", credentials: 'personal-aws') {
                        script {
                            def login = ecrLogin()
                            sh('#!/bin/sh -e\n' + "${login}") // hide logging
                            docker.image("${AWS_ECR_URL}:${POM_VERSION}").push()
                        }
                    }
                }
            }
        }

        stage('DEPLOY IN ECS') {
            steps {
                withCredentials([string(credentialsId: 'AWS_REPOSITORY_URL_SECRET', variable: 'AWS_ECR_URL')]) {
                    withAWS(region: "${AWS_ECR_REGION}", credentials: 'personal-aws') {
                    script {
                        updateContainerDefinitionJsonWithImageVersion()
                        sh("aws ecs register-task-definition --region ${AWS_ECR_REGION} --family ${AWS_ECS_TASK_DEFINITION} --requires-compatibilities ${AWS_ECS_COMPATIBILITY} --network-mode ${AWS_ECS_NETWORK_MODE} --cpu ${AWS_ECS_CPU} --memory ${AWS_ECS_MEMORY} --container-definitions file://${AWS_ECS_TASK_DEFINITION_PATH}")
                        def DESIRED_COUNT = sh(script: "aws ecs describe-services --services ${AWS_ECS_SERVICE} --cluster ${AWS_ECS_CLUSTER} --region ${AWS_ECR_REGION} | jq .services[].desiredCount", returnStdout: true)
                        def taskRevision = sh(script: "aws ecs describe-task-definition --task-definition ${AWS_ECS_TASK_DEFINITION} | egrep \"revision\" | tr \"/\" \" \" | awk '{print \$2}' | sed 's/\"\$//'", returnStdout: true)
                        sh("aws ecs update-service --cluster ${AWS_ECS_CLUSTER} --service ${AWS_ECS_SERVICE} --task-definition ${AWS_ECS_TASK_DEFINITION} --desired-count ${DESIRED_COUNT}")
                    }
                }
            }
        }
    }
}

   post {
        always {
            withCredentials([string(credentialsId: 'AWS_REPOSITORY_URL_SECRET', variable: 'AWS_ECR_URL')]) {
                deleteDir()
                sh "docker rmi ${AWS_ECR_URL}:${POM_VERSION}"
            }
        }
    }
}

def getJarName() {
    def jarName = getName() + '-' + getVersion() + '.jar'
    echo "jarName: ${jarName}"
    return  jarName
}

def getVersion() {
    def pom = readMavenPom file: './pom.xml'
    return pom.version
}

def getName() {
    def pom = readMavenPom file: './pom.xml'
    return pom.name
}

def updateContainerDefinitionJsonWithImageVersion() {
    def containerDefinitionJson = readJSON file: AWS_ECS_TASK_DEFINITION_PATH, returnPojo: true
    containerDefinitionJson[0]['image'] = "${AWS_ECR_URL}:${POM_VERSION}".inspect()
    echo "task definiton json: ${containerDefinitionJson}"
    writeJSON file: AWS_ECS_TASK_DEFINITION_PATH, json: containerDefinitionJson
}