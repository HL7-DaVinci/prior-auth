FROM openjdk:8
EXPOSE 9000/tcp
COPY . /prior-auth/
WORKDIR /prior-auth/
RUN ./gradlew install
RUN ./gradlew clean check
CMD ["./gradlew", "run"]
