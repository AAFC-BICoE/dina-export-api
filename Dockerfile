FROM eclipse-temurin:17-jre-jammy

RUN useradd -s /bin/bash user
USER user
COPY --chown=644 target/report-label-api-*.jar /report-label-api.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/report-label-api.jar"]
