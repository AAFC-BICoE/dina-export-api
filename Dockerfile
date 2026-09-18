FROM eclipse-temurin:25-jre

RUN useradd -r -u 10001 appuser

RUN mkdir -p /data/templates
RUN mkdir -p /data/exports
RUN chown appuser:appuser /data/templates
RUN chown appuser:appuser /data/exports

USER appuser
COPY --chown=appuser:appuser target/dina-export-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
