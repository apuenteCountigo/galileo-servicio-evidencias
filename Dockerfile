FROM openjdk:17-alpine
VOLUME /tmp
ADD ./servicio-evidencias.jar servicio-evidencias.jar
ENTRYPOINT ["java","-jar","/servicio-evidencias.jar"]
