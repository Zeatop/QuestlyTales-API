# syntax=docker/dockerfile:1

#############################################
# Stage 1 : build de l'application avec Maven
#############################################
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copie d'abord les fichiers nécessaires à la résolution des dépendances
# pour profiter du cache Docker (les couches ci-dessous ne sont reconstruites
# que si pom.xml ou le wrapper Maven changent).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Copie du code source et construction du jar (tests ignorés pour l'image)
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

#############################################
# Stage 2 : image d'exécution légère (JRE)
#############################################
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Utilisateur non-root pour la sécurité
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Récupère uniquement le jar produit au stage précédent
COPY --from=build /app/target/QuestlyTales-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
