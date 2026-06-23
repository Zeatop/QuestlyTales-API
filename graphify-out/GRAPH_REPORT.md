# Graph Report - .  (2026-06-21)

## Corpus Check
- 11 files · ~32,548 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 561 nodes · 1207 edges · 48 communities (29 shown, 19 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 110 edges (avg confidence: 0.75)
- Token cost: 68,000 input · 4,142 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Story DTOs|Story DTOs]]
- [[_COMMUNITY_JWT Auth Filter & DTOs|JWT Auth Filter & DTOs]]
- [[_COMMUNITY_Dynamic Data Types|Dynamic Data Types]]
- [[_COMMUNITY_Claude Story Service|Claude Story Service]]
- [[_COMMUNITY_DataType Controller|DataType Controller]]
- [[_COMMUNITY_Story Controller & Ownership|Story Controller & Ownership]]
- [[_COMMUNITY_DeepSeek Story Service|DeepSeek Story Service]]
- [[_COMMUNITY_Generation Pipeline & Config|Generation Pipeline & Config]]
- [[_COMMUNITY_Dynamic Types from GitHub|Dynamic Types from GitHub]]
- [[_COMMUNITY_Mongo Config & Story Tests|Mongo Config & Story Tests]]
- [[_COMMUNITY_Paged Response & Validation Tests|Paged Response & Validation Tests]]
- [[_COMMUNITY_Pluggable Engine & Mermaid|Pluggable Engine & Mermaid]]
- [[_COMMUNITY_Action Registry & GitHub Fetch|Action Registry & GitHub Fetch]]
- [[_COMMUNITY_Constraints Model|Constraints Model]]
- [[_COMMUNITY_Newman Test Harness|Newman Test Harness]]
- [[_COMMUNITY_App Config Beans|App Config Beans]]
- [[_COMMUNITY_Security Config|Security Config]]
- [[_COMMUNITY_Editor Interface & Validation|Editor Interface & Validation]]
- [[_COMMUNITY_MongoDB Migration & Docker|MongoDB Migration & Docker]]
- [[_COMMUNITY_Project Stack & User|Project Stack & User]]
- [[_COMMUNITY_User Auth Controller|User Auth Controller]]
- [[_COMMUNITY_Story Ownership Control|Story Ownership Control]]
- [[_COMMUNITY_JWT Security & Swagger|JWT Security & Swagger]]
- [[_COMMUNITY_Editor vs Game Responses|Editor vs Game Responses]]
- [[_COMMUNITY_DataType Config Bean|DataType Config Bean]]
- [[_COMMUNITY_OpenAPI Config|OpenAPI Config]]
- [[_COMMUNITY_API Controllers|API Controllers]]
- [[_COMMUNITY_App Context Tests|App Context Tests]]
- [[_COMMUNITY_Application Entrypoint|Application Entrypoint]]
- [[_COMMUNITY_ActionDTO|ActionDTO]]
- [[_COMMUNITY_AttributeDTO|AttributeDTO]]
- [[_COMMUNITY_ChoiceDTO|ChoiceDTO]]
- [[_COMMUNITY_ConditionDTO|ConditionDTO]]
- [[_COMMUNITY_GenerateStoryRequestDTO|GenerateStoryRequestDTO]]
- [[_COMMUNITY_EditorStoryResponseDTO|EditorStoryResponseDTO]]
- [[_COMMUNITY_StoryMetadataDTO|StoryMetadataDTO]]
- [[_COMMUNITY_StoryPreviewDTO|StoryPreviewDTO]]
- [[_COMMUNITY_StorySubmissionDTO|StorySubmissionDTO]]
- [[_COMMUNITY_EditorNodeResponseDTO|EditorNodeResponseDTO]]
- [[_COMMUNITY_GameNodeResponseDTO|GameNodeResponseDTO]]
- [[_COMMUNITY_AuthResponseDTO|AuthResponseDTO]]
- [[_COMMUNITY_LoginRequestDTO|LoginRequestDTO]]
- [[_COMMUNITY_RegisterRequestDTO|RegisterRequestDTO]]
- [[_COMMUNITY_Config Package|Config Package]]
- [[_COMMUNITY_Constraints Node|Constraints Node]]
- [[_COMMUNITY_Editor Endpoint|Editor Endpoint]]
- [[_COMMUNITY_User Entity|User Entity]]

## God Nodes (most connected - your core abstractions)
1. `ClaudeStoryService` - 34 edges
2. `String` - 29 edges
3. `Map` - 24 edges
4. `Object` - 24 edges
5. `DeepSeekService` - 21 edges
6. `StoryMongoService` - 20 edges
7. `String` - 16 edges
8. `Document` - 15 edges
9. `Document` - 14 edges
10. `Autowired` - 12 edges

## Surprising Connections (you probably didn't know these)
- `ClaudeStoryService.generateStory` --semantically_similar_to--> `DeepSeekService.generateStory`  [INFERRED] [semantically similar]
  QuestlyTales/src/main/java/org/tpi/questlytales/services/ClaudeStoryService.java → QuestlyTales/src/main/java/org/tpi/questlytales/services/DeepSeekService.java
- `DeepSeekService.fixBrokenChoiceRefs` --semantically_similar_to--> `ClaudeStoryService.reconcileChoicesWithSkeleton`  [INFERRED] [semantically similar]
  QuestlyTales/src/main/java/org/tpi/questlytales/services/DeepSeekService.java → QuestlyTales/src/main/java/org/tpi/questlytales/services/ClaudeStoryService.java
- `questlytales-api-tests (npm package)` --references--> `docker-compose MongoDB service`  [EXTRACTED]
  QuestlyTales/package.json → QuestlyTales/docker-compose.yml
- `Integration Tests README` --references--> `questlytales-api-tests (npm package)`  [EXTRACTED]
  QuestlyTales/tests/README.md → QuestlyTales/package.json
- `Integration Tests README` --references--> `QuestlyTales Postman Collection (A-D)`  [EXTRACTED]
  QuestlyTales/tests/README.md → QuestlyTales/tests/questlytales.postman_collection.json

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Story Generation Engine Strategy** — services_storygenerator_storygenerator, services_claudestoryservice_claudestoryservice, services_deepseekservice_deepseekservice, controllers_storycontroller_generator [EXTRACTED 1.00]
- **Claude Story Generation Pipeline** — services_claudestoryservice_generatestory, services_claudestoryservice_refinetopology, services_claudestoryservice_normalizeskeleton, services_claudestoryservice_ordernodestopologically, services_claudestoryservice_fillnodebatch, services_claudestoryservice_reconcilechoiceswithskeleton [EXTRACTED 1.00]
- **Newman Integration Test Harness** — package_questlytales_api_tests, questlytales_postman_collection_collection, local_postman_environment_env, questlytales_docker_compose_mongodb, tests_readme_integration_tests [EXTRACTED 1.00]

## Communities (48 total, 19 thin omitted)

### Community 0 - "Story DTOs"
Cohesion: 0.13
Nodes (23): ActionDTO, AttributeDTO, ChoiceDTO, ConditionDTO, Document, EditorStoryResponseDTO, GameNodeResponseDTO, GameStoryResponseDTO (+15 more)

### Community 1 - "JWT Auth Filter & DTOs"
Cohesion: 0.10
Nodes (22): AuthResponseDTO, BeforeEach, Claims, FilterChain, HttpServletRequest, HttpServletResponse, OncePerRequestFilter, Override (+14 more)

### Community 2 - "Dynamic Data Types"
Cohesion: 0.10
Nodes (19): Collection, Optional, DynamicDataType, List, Set, String, ActionDTO, AttributeDTO (+11 more)

### Community 3 - "Claude Story Service"
Cohesion: 0.23
Nodes (9): Integer, GenerateStoryRequestDTO, List, Map, Object, String, SuppressWarnings, ClaudeStoryService (+1 more)

### Community 4 - "DataType Controller"
Cohesion: 0.12
Nodes (19): Autowired, Boolean, Class, DataTypeController, DynamicDataType, DataTypeRegistry, GetMapping, Integer (+11 more)

### Community 5 - "Story Controller & Ownership"
Cohesion: 0.14
Nodes (22): AuthenticatedUser, Containerized Test DB Isolation, StoryController, DeleteMapping, GenerateStoryRequestDTO, GetMapping, QuestlyTales Local Postman Environment, questlytales-api-tests (npm package) (+14 more)

### Community 6 - "DeepSeek Story Service"
Cohesion: 0.28
Nodes (9): GenerateStoryRequestDTO, List, Map, Object, String, SuppressWarnings, DeepSeekService, StoryGenerator (+1 more)

### Community 7 - "Generation Pipeline & Config"
Cohesion: 0.10
Nodes (23): Authoritative Attribute Schema, Generator-Critic Multi-Pass Refinement, Heuristic Java Graph Rewiring, Topological-Order Content Generation, AppConfig.anthropicClient, AppConfig.mongoTimeoutCustomizer, AppConfig.restTemplate, ClaudeStoryService.callClaude (+15 more)

### Community 8 - "Dynamic Types from GitHub"
Cohesion: 0.13
Nodes (22): actionsFromTypes.json, Advanced Validation, DataTypeConfig, DataTypeController, DataTypeRegistry, Decision: Dynamic types from GitHub, DeepSeek, Types de Données Dynamiques (+14 more)

### Community 9 - "Mongo Config & Story Tests"
Cohesion: 0.19
Nodes (9): MongoConfig, MongoClient, MongoTemplate, Bean, Document, StorySubmissionDTO, String, Test (+1 more)

### Community 10 - "Paged Response & Validation Tests"
Cohesion: 0.25
Nodes (9): PagedResponseDTO, List, EditorNodeResponseDTO, StoryMetadataDTO, String, Test, StoryValidationServiceTest, T (+1 more)

### Community 11 - "Pluggable Engine & Mermaid"
Cohesion: 0.15
Nodes (14): LLM Duplicate Node ID Detection, Pluggable Story Generation Engine, StoryController.generateStory, StoryController.generator, GenerateStoryRequestDTO, Map, Object, String (+6 more)

### Community 12 - "Action Registry & GitHub Fetch"
Cohesion: 0.21
Nodes (6): List, Map, String, String, ActionRegistry, GithubFileFetcher

### Community 13 - "Constraints Model"
Cohesion: 0.21
Nodes (4): Constraints, Integer, List, String

### Community 14 - "Newman Test Harness"
Cohesion: 0.14
Nodes (13): description, devDependencies, newman, newman-reporter-htmlextra, name, private, scripts, app:test (+5 more)

### Community 15 - "App Config Beans"
Cohesion: 0.29
Nodes (8): AnthropicClient, Bean, AppConfig, MongoClientSettingsBuilderCustomizer, PasswordEncoder, Bean, String, RestTemplate

### Community 16 - "Security Config"
Cohesion: 0.31
Nodes (8): AuthenticationConfiguration, AuthenticationManager, AuthenticationProvider, SecurityConfig, CorsConfigurationSource, HttpSecurity, Bean, SecurityFilterChain

### Community 17 - "Editor Interface & Validation"
Cohesion: 0.20
Nodes (12): AttributeDTO, ChoiceDTO, Interface Éditeur, POST /api/stories, DELETE /api/stories/{id}, GET /api/stories/{id}/edit, PUT /api/stories/{id}, NodeDTO (+4 more)

### Community 18 - "MongoDB Migration & Docker"
Cohesion: 0.22
Nodes (10): application.properties, Decision: No repositories layer, docker-compose.yml, MongoTemplate, MongoDB, local-mongodb container, Migration JPA/MySQL vers MongoDB, mongodb_data volume (+2 more)

### Community 19 - "Project Stack & User"
Cohesion: 0.22
Nodes (8): Java 25, Lombok, MapStruct, Maven, User, QuestlyTales, QuestlyTales Backend, Spring Boot

### Community 20 - "User Auth Controller"
Cohesion: 0.43
Nodes (5): UserController, LoginRequestDTO, PostMapping, RegisterRequestDTO, ResponseEntity

### Community 21 - "Story Ownership Control"
Cohesion: 0.29
Nodes (7): JWT Authentication Feature, BCrypt, DELETE /api/stories/{id}, GET /api/stories/mine, PUT /api/stories/{id}, StoryMongoServiceTest, Story Ownership Control

### Community 22 - "JWT Security & Swagger"
Cohesion: 0.29
Nodes (7): AuthenticatedUser, JwtAuthFilter, JwtServiceTest, OpenAPI Spec (/v3/api-docs), Spring Security + JWT (jjwt), springdoc-openapi, Swagger UI

### Community 23 - "Editor vs Game Responses"
Cohesion: 0.29
Nodes (7): Deux Réponses Différentes (Éditeur vs Jeu), EditorNodeResponseDTO, EditorStoryResponseDTO, GET /api/stories/{id}/play, GameNodeResponseDTO, GameStoryResponseDTO, Jeu Mobile

### Community 24 - "DataType Config Bean"
Cohesion: 0.60
Nodes (3): DataTypeConfig, Bean, DataTypeRegistry

### Community 25 - "OpenAPI Config"
Cohesion: 0.60
Nodes (3): OpenApiConfig, OpenAPI, Bean

### Community 26 - "API Controllers"
Cohesion: 0.40
Nodes (5): controllers package, POST /api/auth/login, POST /api/auth/register, POST /api/stories, GET /api/stories

### Community 27 - "App Context Tests"
Cohesion: 0.60
Nodes (3): QuestlyTalesApplicationTests, Test, Test

## Knowledge Gaps
- **53 isolated node(s):** `String`, `Boolean`, `PostMapping`, `Integer`, `PutMapping` (+48 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **19 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Autowired` connect `DataType Controller` to `Story DTOs`, `JWT Auth Filter & DTOs`, `Dynamic Data Types`, `Claude Story Service`, `Story Controller & Ownership`, `DeepSeek Story Service`, `Security Config`, `User Auth Controller`?**
  _High betweenness centrality (0.225) - this node is a cross-community bridge._
- **Why does `Document` connect `Story DTOs` to `Project Stack & User`?**
  _High betweenness centrality (0.182) - this node is a cross-community bridge._
- **Why does `ClaudeStoryService` connect `Claude Story Service` to `Pluggable Engine & Mermaid`, `DeepSeek Story Service`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **What connects `String`, `Boolean`, `PostMapping` to the rest of the system?**
  _58 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Story DTOs` be split into smaller, more focused modules?**
  _Cohesion score 0.12896405919661733 - nodes in this community are weakly interconnected._
- **Should `JWT Auth Filter & DTOs` be split into smaller, more focused modules?**
  _Cohesion score 0.09523809523809523 - nodes in this community are weakly interconnected._
- **Should `Dynamic Data Types` be split into smaller, more focused modules?**
  _Cohesion score 0.10256410256410256 - nodes in this community are weakly interconnected._