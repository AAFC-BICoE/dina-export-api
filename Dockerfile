FROM eclipse-temurin:17-jre-jammy

RUN useradd -s /bin/bash user
USER user
COPY --chown=644 target/dina-export-api-*.jar /dina-export-api.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/dina-export-api.jar"]
