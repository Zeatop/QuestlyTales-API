# 🎮 QuestlyTales Backend

Backend Spring Boot pour l'application de création et de jeu d'histoires interactives **QuestlyTales**.

---

## 🚀 Démarrage Rapide

### Prérequis

- **Java 25**
- **Maven 3.9+**
- **MongoDB 7.0+**
- **Git**

### Installation

```bash
# 1. Cloner le repository
git clone https://github.com/votre-repo/questlytales-backend.git
cd questlytales-backend

# 2. Installer MongoDB
brew install mongodb-community
brew services start mongodb-community

# 3. Configurer le token GitHub
export GITHUB_TOKEN="your_token_here"

# 4. Installer les dépendances
mvn clean install

# 5. Lancer l'application
mvn spring-boot:run
```

L'application démarre sur **http://localhost:8080**

---

## 📚 Documentation

- **Swagger UI (doc vivante)** : http://localhost:8080/swagger-ui.html — référence interactive de **toutes** les routes, avec bouton « Authorize » (JWT bearer).
- **Spec OpenAPI** : http://localhost:8080/v3/api-docs
- **[Documentation Complète](./DOCUMENTATION.md)** - Fonctionnement global détaillé

> ⚠️ `FINAL_RECAP.md` est un historique partiellement périmé. La référence à jour des endpoints est la table ci-dessous et Swagger UI.

---

## 🎯 Fonctionnalités

### ✅ Authentification (JWT)
- Inscription / connexion, mot de passe haché (BCrypt)
- Sécurité stateless : lecture publique, écriture authentifiée
- Chaque story appartient à son auteur (contrôle de propriété sur modification/suppression)

### ✅ Gestion des Stories
- Création avec validation complète, modification, suppression
- **Catalogue paginé** avec filtres (genre, tag, auteur, recherche) + « mes histoires »
- Deux modes de récupération :
  - **Éditeur** : données complètes avec métadonnées (coordonnées, couleurs)
  - **Jeu** : **graphe complet téléchargeable** (tous les nœuds + médias) pour jouer hors-ligne sur mobile
- **Génération assistée** d'histoire (DeepSeek)

### ✅ Types Dynamiques
- Chargement des types depuis GitHub (pas de code à modifier !)
- Validation automatique des types et valeurs

### ✅ Validation Avancée
- Vérification des types d'attributs et des valeurs selon leur type
- Validation du graphe de nœuds (IDs uniques, cibles existantes, coordonnées ≥ 0)

---

## 📡 API Endpoints

Auth : 🔓 public · 🔒 JWT requis (header `Authorization: Bearer <token>`)

### Authentification

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| `POST` | `/api/auth/register` | 🔓 | Inscription, renvoie un JWT |
| `POST` | `/api/auth/login` | 🔓 | Connexion, renvoie un JWT |

### Stories

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| `GET` | `/api/stories` | 🔓 | Catalogue paginé (`?page=&size=&genre=&tag=&author=&search=`) |
| `GET` | `/api/stories/mine` | 🔒 | Mes histoires (mêmes filtres de pagination) |
| `POST` | `/api/stories` | 🔒 | Créer une story (auteur = propriétaire) |
| `POST` | `/api/stories/generate` | 🔒 | Générer une story via DeepSeek |
| `GET` | `/api/stories/{id}/edit` | 🔓 | Récupérer pour l'éditeur (données complètes) |
| `GET` | `/api/stories/{id}/play` | 🔓 | Télécharger pour le jeu (graphe complet) |
| `PUT` | `/api/stories/{id}` | 🔒 | Mettre à jour (propriétaire uniquement → 403 sinon) |
| `DELETE` | `/api/stories/{id}` | 🔒 | Supprimer (propriétaire uniquement → 403 sinon) |

### Types de Données

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| `GET` | `/api/datatypes` | 🔓 | Liste des types disponibles |
| `GET` | `/api/datatypes/{type}/actions` | 🔓 | Actions pour un type |
| `POST` | `/api/datatypes/refresh` | 🔒 | Recharger depuis GitHub |

---

## 🧪 Exemples d'Utilisation

### Créer une Story

```bash
curl -X POST http://localhost:8080/api/stories \
  -H "Content-Type: application/json" \
  -d '{
    "title": "L'\''aventure commence",
    "description": "Une histoire épique",
    "attributes": [
      {
        "name": "health",
        "type": "Int",
        "value": "100"
      }
    ],
    "nodes": [
      {
        "nodeId": "node1",
        "description": "Vous êtes dans une forêt...",
        "x": 100,
        "y": 200,
        "isEndNode": false,
        "choices": []
      }
    ]
  }'
```

### Télécharger pour le Jeu

```bash
curl http://localhost:8080/api/stories/{id}/play
```

**Réponse** (graphe complet : le mobile télécharge une fois puis joue en local en suivant `choices[].nextNodeId`) :
```json
{
  "id": "65f...",
  "metadata": { "title": "L'aventure commence", "genre": "fantasy" },
  "startNodeId": 1,
  "nodes": [
    { "id": 1, "actions": [], "choices": [ { "choiceText": "Avancer", "nextNodeId": 2 } ] },
    { "id": 2, "actions": [], "choices": [] }
  ],
  "playerAttributes": [ { "label": "health", "type": "Int", "value": "100" } ],
  "images": [], "videos": [], "sounds": []
}
```

---

## 🏗️ Architecture

```
src/main/java/org/tpi/questlytales/
├── controllers/        # API REST endpoints
├── services/           # Logique métier (Mongo, validation, JWT, auth, DeepSeek, registries)
├── security/           # JwtAuthFilter, AuthenticatedUser
├── dtos/               # Data Transfer Objects
├── models/             # Modèles de données (User, DynamicDataType, Constraints)
├── config/             # Configuration Spring (security, mongo, openapi…)
└── utils/              # Utilitaires (GithubFileFetcher)
```

> L'accès aux données MongoDB se fait directement via `MongoTemplate` (pas de couche `repositories/` ni de mappers).

### Technologies

- **Spring Boot 3.5.7** - Framework backend
- **Spring Security + JWT (jjwt)** - Authentification stateless
- **MongoDB** (`MongoTemplate`) - Base de données NoSQL
- **springdoc-openapi** - Documentation Swagger / OpenAPI
- **Lombok** - Génération automatique de code
- **Maven** - Gestion des dépendances

---

## ⚙️ Configuration

### `application.properties`

```properties
# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/questlytales
spring.data.mongodb.database=questlytales

# GitHub Token
github.token=${GITHUB_TOKEN:your_token_here}
```

---

## 🎨 Types de Données Dynamiques

Les types sont chargés depuis GitHub : [actionsFromTypes.json](https://github.com/Zeatop/distantCheck/blob/origin/actionsFromTypes.json)

```json
{
  "Int": ["Decrease", "Increase", "Set"],
  "String": ["Set"],
  "Boolean": ["Toggle", "Set"],
  "List": ["Add", "Remove", "Set"]
}
```

**Pour ajouter un nouveau type** : Modifier le JSON sur GitHub, aucun code à changer !

---

## 🧪 Tests

```bash
mvn test            # lance la suite (20 tests, hors-ligne, sans Mongo réel)
mvn clean compile   # compiler
mvn clean package   # packager
```

Couverture actuelle (tests unitaires Mockito) :
- `JwtServiceTest` — génération/validation des tokens, expiration, signature
- `StoryValidationServiceTest` — règles de validation des stories
- `StoryMongoServiceTest` — contrôle de propriété (create/update/delete)
- `QuestlyTalesApplicationTests` — démarrage du contexte (registries GitHub mockés)

> Les propriétés de test (`src/test/resources/application.properties`) fournissent des valeurs factices : aucun appel réseau réel pendant les tests.

---

## 📝 Structure MongoDB

```javascript
{
  "_id": ObjectId("..."),
  "title": "L'aventure commence",
  "attributes": [
    { "name": "health", "type": "Int", "value": "100" }
  ],
  "nodes": [
    {
      "nodeId": "node1",
      "description": "...",
      "x": 100,
      "y": 200,
      "choices": [...]
    }
  ],
  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}
```

---

## 🤝 Contribution

1. Fork le projet
2. Créer une branche (`git checkout -b feature/AmazingFeature`)
3. Commit (`git commit -m 'Add AmazingFeature'`)
4. Push (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

---

## 📄 Licence

Ce projet est sous licence MIT.

---

## 👥 Auteurs

- **QuestlyTales Team**

---

## 📞 Support

Pour toute question :
- 📧 Email: support@questlytales.com
- 📚 Documentation: [DOCUMENTATION.md](./DOCUMENTATION.md)

---

**Développé avec ❤️ par l'équipe QuestlyTales**
