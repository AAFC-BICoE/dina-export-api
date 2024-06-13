FROM eclipse-temurin:21-jre-jammy

RUN useradd -s /bin/bash user

RUN mkdir -p /data/templates
RUN mkdir -p /data/exports
RUN chown user:user /data/templates
RUN chown user:user /data/exports

USER user
COPY --chown=644 target/dina-export-api-*.jar /dina-export-api.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/dina-export-api.jar"]
