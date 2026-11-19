FROM eclipse-temurin:21-jre-alpine@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372
ARG MODULE
WORKDIR /app
COPY ${MODULE}/target/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-Xms96m","-Xmx256m","-jar","/app/app.jar"]
