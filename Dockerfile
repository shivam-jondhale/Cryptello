# Stage 1: Build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy maven wrapper and configuration
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Fix windows line endings for mvnw if present
RUN sed -i 's/\r$//' mvnw
RUN chmod +x mvnw

# Download dependencies (go offline to cache dependencies)
RUN ./mvnw dependency:go-offline

# Copy source and build
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create a non-root user for security (optional but recommended)
# RUN addgroup --system spring && adduser --system --group spring
# USER spring:spring

# Copy the built artifact from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the configured port
EXPOSE 1106

# Entrypoint
ENTRYPOINT ["java", "-jar", "app.jar"]
