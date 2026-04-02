# ---------- Stage 1: Build ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy all files
COPY . .

# Build the project
RUN mvn clean package -DskipTests


# ---------- Stage 2: Run ----------
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]