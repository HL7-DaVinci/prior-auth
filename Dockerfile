FROM gradle:jdk11
EXPOSE 9000/tcp
COPY --chown=gradle:gradle . /prior-auth/
WORKDIR /prior-auth/
RUN gradle installBootDist
CMD ["gradle", "bootRun"]
