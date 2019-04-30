FROM gradle:jdk8-alpine
EXPOSE 9000/tcp
COPY --chown=gradle:gradle . /prior-auth/
WORKDIR /prior-auth/
RUN gradle install
CMD ["gradle", "run"]
