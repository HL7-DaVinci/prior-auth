# Base image
FROM amazoncorretto:17-alpine-jdk

# embedCdsLibrary task requires git
RUN apk add --no-cache git bash

WORKDIR /app

# Copy project files
COPY . .

# Ensure gradlew is executable
RUN chmod +x gradlew

# Embed CDS Library
RUN ./gradlew embedCdsLibrary

# Expose port to access the app
EXPOSE 9015
# Command to run our app
CMD ["./gradlew", "bootRun", "-Pdebug"]