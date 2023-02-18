FROM adoptopenjdk/openjdk11:alpine-jre
ARG JAR_FILE=target/spring-docker-simple-0.0.1.jar
WORKDIR /opt/app
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","app.jar"]