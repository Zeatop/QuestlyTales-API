# Tests d'intégration API (Newman)

Tests boîte-noire HTTP de l'API QuestlyTales, exécutables en CLI via [Newman](https://github.com/postmanlabs/newman).

Couverture (catégories **A–D**) :
- **A. Auth** — register (toléré si déjà créé) + login → token réutilisé par les appels protégés.
- **B. DataTypes** — `GET /api/datatypes`, `/details`, `/{type}`, `/{type}/actions`, `/{type}/validate/{action}`, `/count`, et un type inexistant (404). Le type et l'action testés sont **découverts dynamiquement** depuis la réponse (pas de valeurs en dur).
- **C. Stories CRUD** — create → list (vérifie que c'est bien du *preview* sans `nodes`) → mine → edit (complet) → play → update → delete → 404 après suppression. La création **valide réellement** le passage par `StoryValidationService` (201).
- **D. Sécurité / erreurs** — `/generate` et `/mine` sans token (401/403), `/generate` avec body invalide (400).

> La **génération IA** (`POST /api/stories/generate`) n'est volontairement **pas** testée ici : lente, coûteuse et non-déterministe — validée à la main via le front avant soumission.

## Isolation de la base : conteneur, jamais Atlas
Newman ne choisit pas la base — c'est l'app lancée qui décide. Le script `app:test`
**force les URIs Mongo vers le conteneur** (`localhost:27017`) via des arguments de
ligne de commande, qui ont la **précédence maximale** dans Spring Boot et **écrasent
donc `application-local.properties`** (où se trouve éventuellement Atlas). Les stories
de test vont dans une base dédiée **`questlytales_test`**, supprimable à volonté.

> `application-local.properties` reste chargé (pour `github.token`, requis au démarrage),
> mais les URIs Mongo passées en arguments le surchargent — garantie « jamais Atlas ».

## Flux complet (3 terminaux / 3 étapes)
Toutes les commandes depuis `Backend/QuestlyTales/` :

```bash
npm install            # une fois : installe newman en local

# 1. Mongo conteneurisé
npm run db:up

# 2. App pointée sur la base de test (conteneur), pas Atlas
npm run app:test

# 3. (autre terminal) lancer les tests
npm test
```

Rapport HTML : `npm run test:report`.

Cibler un autre hôte : `npx newman run tests/questlytales.postman_collection.json --env-var baseUrl=http://localhost:8080`.

## Notes
- Les requêtes s'exécutent **dans l'ordre** : A (token) → B → C (storyId) → D. Ne pas réordonner.
- Idempotent : le register tolère un compte déjà existant ; chaque run crée puis supprime son histoire de test.
- Le fichier peut aussi être **importé dans l'app Postman** (Import → fichier) pour une exécution manuelle.
