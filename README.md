# 🚀 DeployFast Task Manager API

**Application REST Spring Boot** avec pipeline CI/CD complet — Examen BC01 EADL4


## Lancer le projet

### Prérequis
- Docker & Docker Compose installés
- Java 17 + Maven (pour développement local)
- Compte GitHub + DockerHub + SonarCloud

### Démarrage rapide avec Docker Compose

```bash
# 1. Cloner le repo
git clone https://github.com/deployfast/taskmanager.git
cd taskmanager

# 2. Configurer les variables d'environnement
cp .env.example .env
# Éditer .env avec vos valeurs

# 3. Démarrer l'application (app + PostgreSQL)
docker-compose up -d

# 4. Vérifier le bon fonctionnement
curl http://localhost:8080/actuator/health
```

### En développement local

```bash
# Démarrer uniquement PostgreSQL
docker-compose up -d db

# Lancer l'application Spring Boot
./mvnw spring-boot:run

# Lancer les tests avec couverture
./mvnw test jacoco:report
# Rapport disponible dans : target/site/jacoco/index.html
```

### Configurer les secrets GitHub (CI/CD)

Aller dans **Settings → Secrets and variables → Actions** et ajouter :

| Secret | Comment l'obtenir |
|--------|-------------------|
| `DOCKERHUB_USERNAME` | Votre nom d'utilisateur DockerHub |
| `DOCKERHUB_TOKEN` | DockerHub → Account Settings → Security → New Access Token |
| `SONAR_TOKEN` | SonarCloud → My Account → Security → Generate Token |
| `SONAR_PROJECT_KEY` | SonarCloud → votre projet → Information → Project Key |
| `SONAR_ORGANIZATION` | SonarCloud → votre organisation |


---

---

## QUESTION 1 — Conception architecturale et modélisation

### 1.1 Reformulation du besoin fonctionnel

L'application **DeployFast Task Manager** est une API REST permettant à des utilisateurs authentifiés de gérer leurs tâches personnelles.

**Acteurs identifiés :**
- **Utilisateur non authentifié** — Peut s'inscrire (`/auth/register`) et se connecter (`/auth/login`)
- **Utilisateur authentifié (ROLE_USER)** — Peut créer, consulter, modifier et supprimer ses propres tâches
- **Administrateur (ROLE_ADMIN)** — Accès étendu (extension future via `@PreAuthorize`)
- **Pipeline CI/CD** — Déclenche les builds, tests et déploiements automatiquement

**Principales fonctionnalités :**
1. Authentification sécurisée par JWT (inscription + connexion)
2. CRUD complet des tâches avec validation stricte des entrées
3. Pagination et filtrage des tâches par statut
4. Gestion centralisée des exceptions avec codes HTTP cohérents
5. Contrôle des accès : chaque utilisateur ne voit que ses propres tâches

**Contraintes techniques :**
- Java 17 + Spring Boot 3.2 (LTS)
- Base de données PostgreSQL (production) / H2 en mémoire (tests)
- Conteneurisation Docker obligatoire
- Couverture de tests minimale de 60% vérifiée par JaCoCo
- Analyse de qualité continue via SonarCloud

---

### 1.2 Modélisation des données

#### Tables principales

**Table `users`**
```
id          BIGINT          PRIMARY KEY AUTO_INCREMENT
username    VARCHAR(50)     UNIQUE NOT NULL
email       VARCHAR(255)    UNIQUE NOT NULL
password    VARCHAR(255)    NOT NULL  ← BCrypt encodé, jamais en clair
role        ENUM            DEFAULT 'ROLE_USER'  ← ROLE_USER | ROLE_ADMIN
created_at  TIMESTAMP       NOT NULL
```

**Table `tasks`**
```
id           BIGINT       PRIMARY KEY AUTO_INCREMENT
title        VARCHAR(100) NOT NULL
description  VARCHAR(500)
status       ENUM         DEFAULT 'TODO'    ← TODO | IN_PROGRESS | DONE | CANCELLED
priority     ENUM         DEFAULT 'MEDIUM'  ← LOW | MEDIUM | HIGH | CRITICAL
owner_id     BIGINT       FK → users(id) ON DELETE CASCADE
created_at   TIMESTAMP    NOT NULL
updated_at   TIMESTAMP    mis à jour automatiquement (@PreUpdate)
due_date     TIMESTAMP    optionnel
```

#### Relations
- Un `User` possède **zéro ou plusieurs** `Task` (relation `1..N`)
- `Task.owner_id` est une clé étrangère vers `users(id)` avec `ON DELETE CASCADE`

#### Clés primaires / étrangères
- Clés primaires auto-incrémentées (stratégie `IDENTITY`)
- Contrainte d'unicité sur `username` et `email`
- Contrainte d'intégrité référentielle sur `owner_id`

#### Contraintes d'intégrité
- `title` : NOT NULL, longueur 2–100 caractères
- `username` : NOT NULL, longueur 3–50 caractères, unique
- `email` : NOT NULL, format email valide, unique
- `password` : NOT NULL (toujours hashé BCrypt avant persistance)

---

### 1.3 Structure REST de l'API

#### Routes principales

| Méthode | Route | Auth | Code | Description |
|---------|-------|------|------|-------------|
| POST | `/api/v1/auth/register` | Non | 201 | Inscription |
| POST | `/api/v1/auth/login` | Non | 200 | Connexion → JWT |
| GET | `/api/v1/tasks` | JWT | 200 | Liste paginée (`?page=0&size=10&status=TODO`) |
| GET | `/api/v1/tasks/{id}` | JWT | 200 | Détail d'une tâche |
| POST | `/api/v1/tasks` | JWT | 201 | Créer une tâche |
| PUT | `/api/v1/tasks/{id}` | JWT | 200 | Modifier une tâche |
| DELETE | `/api/v1/tasks/{id}` | JWT | 200 | Supprimer une tâche |
| GET | `/actuator/health` | Non | 200 | Healthcheck Docker/CI |

#### Méthodes HTTP utilisées
- `GET` — Lecture (idempotent, sans effet de bord)
- `POST` — Création d'une nouvelle ressource
- `PUT` — Remplacement complet d'une ressource existante
- `DELETE` — Suppression d'une ressource

#### Codes de réponse
- `200 OK` — Succès (lecture, mise à jour, suppression)
- `201 Created` — Ressource créée avec succès
- `400 Bad Request` — Erreur de validation des champs
- `401 Unauthorized` — Token JWT manquant ou invalide
- `403 Forbidden` — Accès refusé (pas propriétaire de la ressource)
- `404 Not Found` — Ressource introuvable
- `500 Internal Server Error` — Erreur serveur inattendue

#### Versioning
Le préfixe `/api/v1/` permet d'introduire une version v2 sans casser les clients existants. Toutes les réponses suivent un format unifié :
```json
{
  "success": true,
  "message": "Tâche créée",
  "status": 201,
  "data": { ...}
}
```

#### Gestion des erreurs
Centralisée dans `GlobalExceptionHandler` (`@RestControllerAdvice`) qui intercepte toutes les exceptions et retourne des réponses JSON cohérentes avec les bons codes HTTP.

---

### 1.4 Architecture applicative Spring Boot

#### Organisation des dossiers

```
src/
├── controller/          ← Couche présentation : reçoit les requêtes HTTP, délègue au service
│   ├── AuthController.java
│   └── TaskController.java
├── service/impl/        ← Couche métier : logique applicative, règles business
│   ├── AuthServiceImpl.java
│   └── TaskServiceImpl.java
├── repository/          ← Couche données : accès JPA à la base de données
│   ├── UserRepository.java
│   └── TaskRepository.java
├── entity/              ← Modèles JPA : représentation des tables
│   ├── User.java
│   └── Task.java
├── dto/                 ← Data Transfer Objects : découplage couches
│   ├── request/         ← Requêtes entrantes avec validation @Valid
│   └── response/        ← Réponses sortantes typées (masquent les données sensibles)
├── security/            ← Composants sécurité JWT
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── UserDetailsServiceImpl.java
├── config/              ← Configuration Spring Security
│   └── SecurityConfig.java
└── exception/           ← Gestion centralisée des erreurs
    ├── GlobalExceptionHandler.java
    └── ResourceNotFoundException.java
```

#### Séparation des responsabilités
- **Controller** : ne contient aucune logique métier, uniquement la réception HTTP et la délégation
- **Service** : contient toute la logique métier, aucune dépendance directe à HTTP
- **Repository** : abstraction de la base de données via Spring Data JPA
- **DTO** : évite d'exposer les entités JPA directement (sécurité, découplage)

#### Principes SOLID appliqués

| Principe | Application concrète |
|----------|----------------------|
| **S** — SRP | Chaque classe a une seule responsabilité (`AuthServiceImpl` gère uniquement l'auth) |
| **O** — OCP | Extension par ajout de nouvelles implémentations sans modifier l'existant |
| **D** — DIP | Injection de dépendances par constructeur via `@RequiredArgsConstructor` (Lombok) |

**Justification des choix techniques :**
- **Spring Boot 3.2** : framework mature, autoconfiguration, large écosystème
- **Spring Security + JWT** : standard industriel pour APIs REST stateless
- **Spring Data JPA** : abstraction de la base de données, requêtes paramétrées (anti-injection SQL)
- **Lombok** : réduction du code boilerplate (getters, constructeurs, builders)
- **DTOs** : séparation claire entre la représentation HTTP et le modèle de données interne
- **BCrypt** : algorithme de hashage robuste avec sel intégré

---

---

## QUESTION 2 — Réalisation, qualité et sécurité

### 2.1 Authentification sécurisée (JWT)

L'application utilise **JSON Web Tokens (JWT)** signés avec HMAC-SHA256 pour l'authentification stateless.

Flux d'authentification :
```
1. Client → POST /api/v1/auth/register ou /login  { username, password }
2. Serveur vérifie les credentials via AuthenticationManager (Spring Security)
3. Si valide → génère un JWT signé avec la clé secrète (HS256)
4. Client reçoit : { token: "eyJhbGci...", type: "Bearer", username: "...", role: "..." }
5. Pour chaque requête suivante → Header: Authorization: Bearer <token>
6. JwtAuthenticationFilter intercepte, valide le token, charge l'utilisateur en contexte
```

Le token JWT contient :
- `subject` : nom d'utilisateur
- `issuedAt` : date d'émission
- `expiration` : expiration (24h par défaut, configurable via `JWT_EXPIRATION`)
- Signature HMAC-SHA256 avec la clé secrète

### 2.2 CRUD complet des tâches

Toutes les opérations vérifient que l'utilisateur authentifié est bien propriétaire de la ressource (`ensureOwnership()`). La pagination est gérée nativement par Spring Data avec `Pageable`.

### 2.3 Validation des données

Chaque DTO entrant utilise les annotations Bean Validation :
- `@NotBlank` : champ obligatoire non vide
- `@Size(min, max)` : contrainte de longueur
- `@Email` : format email valide
- `@Valid` sur les paramètres du contrôleur déclenche la validation automatique

### 2.4 Gestion centralisée des exceptions

`GlobalExceptionHandler` (`@RestControllerAdvice`) intercepte toutes les exceptions et retourne des réponses JSON cohérentes. Aucun `try-catch` dispersé dans les contrôleurs ou services.

---

### 2.5 Tests automatisés

#### Tests unitaires — `TaskServiceTest`

Testent la logique métier de `TaskServiceImpl` avec Mockito (dépendances mockées) :
- Création d'une tâche avec succès
- Récupération paginée des tâches
- `ResourceNotFoundException` si tâche introuvable
- `AccessDeniedException` si l'utilisateur n'est pas propriétaire
- Mise à jour et suppression d'une tâche

#### Tests Feature — `TaskControllerTest`

Testent le flux HTTP complet avec `MockMvc` et H2 en mémoire (profil `test`) :
- Création d'une tâche avec token JWT valide
- Rejet des requêtes sans token (403)
- Pagination de la liste des tâches
- Mise à jour et suppression
- 404 pour une tâche inexistante

#### Tests d'authentification — `AuthControllerTest`

Testent les routes publiques `/auth/register` et `/auth/login` :
- Inscription réussie avec retour du token JWT
- Rejet d'un username déjà existant
- Connexion réussie avec token Bearer
- Rejet des mauvais identifiants (401)
- Validation des champs obligatoires

#### Couverture minimale de 60%

Vérifiée automatiquement par JaCoCo à chaque build :
```xml
<limit>
    <counter>LINE</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.60</minimum>
</limit>
```
Le build échoue si la couverture est inférieure à 60%.

---

### 2.6 Bonnes pratiques de sécurité

**Validation stricte des entrées** : toutes les données entrantes passent par des DTOs annotés `@Valid`. Aucune donnée brute n'atteint la couche service sans validation préalable.

**Protection contre les injections SQL** : Spring Data JPA utilise exclusivement des requêtes paramétrées (PreparedStatement). Aucune concaténation de chaîne dans les requêtes SQL.

**Gestion des rôles** : Spring Security avec `@EnableMethodSecurity` permet l'annotation `@PreAuthorize` sur les méthodes sensibles. La vérification de propriété (`ensureOwnership`) empêche qu'un utilisateur accède aux ressources d'un autre.

**Protection CSRF/XSS** : CSRF désactivé car l'API est stateless (JWT, pas de cookies de session). Les DTOs de réponse ne retournent que des champs typés, jamais de HTML brut.

**Secrets externalisés** : `JWT_SECRET`, `DB_PASSWORD` sont lus depuis des variables d'environnement, jamais codés en dur. Le fichier `.env` est dans `.gitignore`.

---

### 2.7 Principes Clean Code

**Méthodes courtes** : chaque méthode a une seule responsabilité.
```java
private User findUserOrThrow(String username) { ... }        // ~5 lignes
private Task findTaskOrThrow(Long id) { ... }                // ~4 lignes
private void ensureOwnership(Task task, String username) { } // ~3 lignes
```

**Pas de duplication (DRY)** :
- `TaskResponse.from(task)` : méthode statique de mapping centralisée
- `ApiResponse.success()` et `ApiResponse.error()` : factories pour les réponses HTTP
- `GlobalExceptionHandler` : gestion d'erreur en un seul endroit

**Nommage explicite** :
- `findUserOrThrow()` : le nom indique qu'il lève une exception si non trouvé
- `ensureOwnership()` : sémantique claire
- `buildAuthResponse()` : méthode privée à rôle unique et bien nommée

**Commentaires pertinents** : chaque classe possède un JavaDoc décrivant sa responsabilité. Les commentaires expliquent le *pourquoi*, pas le *comment*.

**Choix d'implémentation justifiés :**
- **Lombok** évite des centaines de lignes de boilerplate tout en gardant le code lisible
- **Builder pattern** (`@Builder`) pour la création des entités : lisibilité et immutabilité
- **`@Transactional(readOnly = true)`** sur les méthodes de lecture : optimisation JPA + signalement d'intention
- **Profil `test`** avec H2 : les tests n'ont pas besoin de PostgreSQL, pipeline plus rapide et isolé

---

---

## QUESTION 3 — Conception du pipeline

### 3.1 Analyse et stratégie CI/CD

#### Stratégie de branches (GitFlow simplifié)

```
main      → Production. Tout push déclenche le déploiement production.
develop   → Intégration. Push → déploiement staging automatique.
feature/* → Fonctionnalités. Pull Request vers develop avant merge.
hotfix/*  → Corrections urgentes. Pull Request directement vers main.
```

#### Déclencheurs du pipeline

| Événement | Branches | Jobs déclenchés |
|-----------|----------|-----------------|
| `push` | `main` | Build → Test → Sonar → Security → Docker Build+Push DockerHub |
| `push` | `develop` | Build → Test → Sonar → Security → Docker Build+Push DockerHub |
| `pull_request` | vers `main` | Build → Test → Sonar (vérification avant merge) |
| `release created` | — | Build → Docker avec tag sémantique (v1.0.0) |

#### Les différentes étapes du pipeline

1. **Build & Lint** — Compilation Maven + analyse Checkstyle
2. **Tests + Coverage** — JUnit 5 + JaCoCo (seuil 60%)
3. **SonarCloud** — Qualité, bugs, dette technique, security hotspots
4. **Security Scan** — OWASP Dependency Check + Gitleaks
5. **Docker Build + Trivy + Push DockerHub** — Image multi-stage, scan CVE, push sur DockerHub ← déploiement final

#### Schéma textuel du pipeline

```
  DÉCLENCHEUR GIT (push / pull_request / release)
                          │
          ┌───────────────▼────────────────┐
          │       [1] BUILD & LINT         │
          │  mvn compile + checkstyle      │
          └──────┬─────────────────────────┘
         ┌───────┴────────┐
         ▼                ▼
  ┌──────────────┐  ┌──────────────────┐
  │ [2] TESTS +  │  │ [4] SECURITY     │
  │   COVERAGE   │  │  OWASP+Gitleaks  │
  └──────┬───────┘  └────────┬─────────┘
         ▼                   │
  ┌──────────────┐            │
  │ [3] SONAR    │            │
  │ Quality Gate │            │
  └──────┬───────┘            │
         └──────────┬─────────┘
                    ▼
         ┌──────────────────────┐
         │  [5] DOCKER BUILD    │
         │  + TRIVY SCAN        │
         │  + PUSH → DockerHub  │ ← DÉPLOIEMENT FINAL
         └──────────────────────┘
```

---

### 3.2 Définition des étapes CI et rôle de chacune

**Étape 1 — Build**
Compile le code source avec Maven (`mvn compile`). Détecte les erreurs de compilation avant toute autre opération. C'est la porte d'entrée : si le build échoue, rien d'autre ne s'exécute. Contribution à la **maintenabilité** : un code qui ne compile pas est détecté immédiatement.

**Étape 2 — Lint (Checkstyle)**
Analyse statique du style de code via les règles Google. Garantit l'uniformité du code entre les développeurs : un code cohérent est plus facile à relire et faire évoluer.

**Étape 3 — Tests unitaires**
Exécution de JUnit 5 avec Mockito. Vérifie la logique métier de chaque unité isolément. Génère le rapport JaCoCo. Si la couverture est inférieure à 60%, le build échoue.

**Étape 4 — Analyse SonarCloud**
Envoie le code et le rapport de couverture à SonarCloud pour évaluer bugs, vulnerabilités, code smells, dette technique, duplications. Le **Quality Gate** bloque le pipeline si les seuils ne sont pas atteints. Contribue à la **traçabilité et l'observabilité** : chaque PR a son rapport qualité horodaté.

**Étape 5 — Scan de sécurité**
OWASP Dependency Check détecte les CVE dans les dépendances Maven. Gitleaks détecte les secrets accidentellement commités. Garantit la sécurité de la chaîne d'approvisionnement.

**Étape 6 — Build Docker**
Construit l'image Docker multi-stage, la tague automatiquement selon la branche/version, la pousse sur DockerHub. Scan Trivy de l'image résultante pour détecter les CVE au niveau OS et runtime.

**Étape 5 — Build Docker + Push DockerHub**
Construit l'image Docker multi-stage, la tague automatiquement selon la branche/version et la pousse sur DockerHub. C'est l'étape finale du pipeline : l'image est disponible publiquement pour être tirée par n'importe quel environnement cible. Scan Trivy intégré sur l'image résultante.

#### Schéma des interactions entre composants

```
Développeur → git push → GitHub
                              │
                              ▼
                     GitHub Actions Runner
                              │
                   ┌──────────┼──────────┐
                   ▼          ▼          ▼
               Maven        OWASP      SonarCloud
             (build+test)  (CVE scan)  (qualité)
                   │                     │
                   └─────────┬───────────┘
                             ▼
                     Docker Build + Trivy
                             │
                             ▼
                         DockerHub  ← DÉPLOIEMENT FINAL
                   (image taguée + disponible)
```

---

---

## QUESTION 4 — Implémentation du pipeline

Le pipeline complet est dans `.github/workflows/ci-cd.yml`.

### 4.1 Intégration Continue — Configuration CI

```yaml
test:
  name: Tests & Coverage
  runs-on: ubuntu-latest
  needs: build
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Cache Maven packages
      uses: actions/cache@v4
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    # Installation des dépendances + exécution des tests + rapport couverture
    - run: mvn test jacoco:report -B
    # Vérification du seuil 60% — build échoue si non atteint
    - run: mvn jacoco:check -B
```

**Critères d'évaluation :**
- Automatisation complète : aucune intervention manuelle requise
- Couverture vérifiée automatiquement (seuil 60% bloquant via JaCoCo)
- Rapport JaCoCo uploadé comme artefact GitHub pour consultation post-run
- Aucune intervention manuelle ne peut bypasser ces vérifications

---

### 4.2 Intégration SonarCloud

```yaml
sonarcloud:
  needs: test
  steps:
    - uses: actions/checkout@v4
      with:
        fetch-depth: 0  # Obligatoire pour le blame analysis SonarCloud
    - name: Cache SonarCloud packages
      uses: actions/cache@v4
      with:
        path: ~/.sonar/cache
        key: ${{ runner.os }}-sonar
    - run: mvn test jacoco:report -B
    - name: SonarCloud Scan
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: |
        mvn sonar:sonar \
          -Dsonar.projectKey=${{ secrets.SONAR_PROJECT_KEY }} \
          -Dsonar.organization=${{ secrets.SONAR_ORGANIZATION }} \
          -Dsonar.host.url=https://sonarcloud.io \
          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

**Ce qui est configuré :**
- **Analyse automatique** : déclenchée à chaque push sur `main` et `develop`
- **Affichage de la couverture** : rapport JaCoCo XML transmis à SonarCloud
- **Détection de bugs** : analyse du bytecode Java
- **Dette technique** : identification des Code Smells
- `fetch-depth: 0` obligatoire pour le blame analysis (historique git complet)

**Quality Gate configuré sur SonarCloud :**

| Condition | Seuil |
|-----------|-------|
| Couverture nouveau code | ≥ 60% |
| Duplications | ≤ 3% |
| Maintenabilité | Grade A |
| Fiabilité | Grade A |
| Sécurité | Grade A |

**Rapport visible dans SonarCloud** : https://sonarcloud.io/dashboard?id=deployfast_taskmanager

---

### 4.3 Scan de sécurité (DevSecOps)

```yaml
security-scan:
  needs: build
  steps:
    # Scan des dépendances Maven (CVE via NVD)
    - name: OWASP Dependency Check
      run: |
        mvn org.owasp:dependency-check-maven:check \
          -DfailBuildOnCVSS=9 -B

    # Détection de secrets dans le code source et l'historique git
    - name: Secret Detection with Gitleaks
      uses: gitleaks/gitleaks-action@v2

    # Scan de l'image Docker après build
    - name: Scan Docker image with Trivy
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: deployfast/taskmanager:latest
        severity: 'CRITICAL,HIGH'
```

**Vulnérabilités détectées et corrections appliquées :**

| Type | Outil | Correction appliquée |
|------|-------|----------------------|
| CVE dans dépendances | OWASP | Mise à jour de la version dans `pom.xml` |
| Secrets dans le code | Gitleaks | Variables d'environnement + `.gitignore` sur `.env` |
| CVE dans l'image OS | Trivy | Image base `eclipse-temurin:17-jre-alpine` régulièrement mise à jour |
| Ports exposés | Docker | Seul le port 8080 est exposé, utilisateur non-root dans le conteneur |

---

---

## QUESTION 5 — Déploiement automatique

### 5.1 Build automatique de l'image Docker

**Dockerfile multi-stage** :
- **Stage 1 (builder)** : `maven:3.9.6-eclipse-temurin-17-alpine` — compile et package l'application
- **Stage 2 (runtime)** : `eclipse-temurin:17-jre-alpine` — image légère, uniquement le JRE

Avantages du multi-stage :
- Image finale ~180MB au lieu de ~700MB avec une image Maven complète
- Le code source n'est pas inclus dans l'image de production
- Utilisateur non-root (`appuser`) pour la sécurité en production

### 5.2 Tag automatique

Les tags sont générés automatiquement par `docker/metadata-action` selon la branche et l'événement :

```
push main    → deployfast/taskmanager:latest
             → deployfast/taskmanager:main-abc1234  (sha court)
push develop → deployfast/taskmanager:develop
             → deployfast/taskmanager:develop-abc1234
release v1.2 → deployfast/taskmanager:1.2.0
             → deployfast/taskmanager:1.2
             → deployfast/taskmanager:latest
```

### 5.3 Push vers DockerHub

Le job `docker-build` est la dernière étape du pipeline. Une fois les jobs SonarCloud et Security Scan passés, il :

1. Compile le JAR via Maven
2. Construit l'image Docker multi-stage
3. Scanne l'image avec Trivy
4. Pousse l'image sur DockerHub avec les tags automatiques

```yaml
- name: Build and Push to DockerHub
  uses: docker/build-push-action@v5
  with:
    context: .
    push: true
    tags: ${{ steps.meta.outputs.tags }}
    cache-from: type=gha
    cache-to: type=gha,mode=max
    platforms: linux/amd64,linux/arm64
```

### 5.4 Récupération de l'image depuis DockerHub

Une fois poussée, l'image est disponible publiquement :

```bash
# Tirer la dernière image
docker pull deployfast/taskmanager:latest

# Tirer une version spécifique
docker pull deployfast/taskmanager:1.0.0

# Lancer l'image localement avec docker-compose
docker-compose up -d
```

### 5.5 Option bonus — Environnement staging

Le pipeline pousse également une image taguée `develop` pour les pushs sur la branche `develop`, permettant de distinguer les images de staging des images de production :

```bash
# Image de production (branche main)
docker pull deployfast/taskmanager:latest

# Image de staging (branche develop)
docker pull deployfast/taskmanager:develop
```

---

---

## QUESTION 6 — Optimisation & Clean Code

### 6.1 Structuration — Modularité du projet

**Comment le projet respecte les principes de modularité :**

La séparation stricte en couches (Controller → Service → Repository) garantit qu'une modification dans la couche données n'impacte pas la couche HTTP, et vice versa. Chaque couche communique avec la suivante via des interfaces bien définies (DTOs pour controller↔service, entités pour service↔repository).

Les DTOs jouent un rôle clé : si la table `tasks` change de structure, seul le service est impacté, pas les clients de l'API.

**Améliorations apportées au code :**
- Extraction de méthodes privées réutilisables dans les services (`findUserOrThrow`, `findTaskOrThrow`, `ensureOwnership`, `buildAuthResponse`) pour éviter toute répétition
- `ApiResponse<T>` générique avec factories statiques : uniformité des réponses sans duplication
- `TaskResponse.from(task)` : mapping centralisé, appelé depuis chaque méthode du service
- Profil Spring `test` avec H2 : tests totalement isolés de PostgreSQL, aucune configuration supplémentaire

---

### 6.2 Optimisation du pipeline

#### Mise en cache des dépendances Maven

Le cache des dépendances (`~/.m2`) est stocké par GitHub Actions avec une clé basée sur le hash de `pom.xml`. Si le fichier `pom.xml` ne change pas entre deux runs, les dépendances sont restaurées depuis le cache, **économisant 2 à 3 minutes** par exécution.

```yaml
- uses: actions/cache@v4
  with:
    path: ~/.m2
    key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    restore-keys: ${{ runner.os }}-m2
```

#### Parallélisation des jobs

Les jobs `build` (étape 1) et `security-scan` (étape 4) s'exécutent en parallèle car indépendants. Le job `docker-build` attend la fin des deux simultanément :

```yaml
docker-build:
  needs: [sonarcloud, security-scan]  # les 2 s'exécutent en parallèle avant
```

Gain estimé : **~30% de réduction du temps total** (de ~18 min à ~12 min).

#### Optimisation du temps d'exécution Docker

Le Dockerfile copie `pom.xml` seul avant le code source pour bénéficier du cache des couches Docker. La couche des dépendances Maven n'est reconstruite que si `pom.xml` change :

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -B   # ← couche cachée si pom.xml inchangé

COPY src ./src
RUN mvn clean package -DskipTests -B
```

Le build Docker utilise également le cache GitHub Actions (`type=gha`) pour éviter de reconstruire les couches inchangées entre les runs.

---

### 6.3 Guide opérationnel complet

#### Comment exécuter le pipeline

```bash
# Déclencher le pipeline staging (branche develop)
git checkout develop
git add .
git commit -m "feat: nouvelle fonctionnalité"
git push origin develop
# → GitHub Actions démarre automatiquement

# Déclencher le pipeline production (branche main)
git checkout main
git merge develop
git push origin main
# → Pipeline prod + image poussée sur DockerHub:latest

# Créer une release avec tag sémantique
git tag -a v1.2.0 -m "Release 1.2.0"
git push origin v1.2.0
# → Image Docker taguée deployfast/taskmanager:1.2.0
```

Suivre l'avancement dans **GitHub → Actions** : chaque job est visible avec ses logs en temps réel.

---

#### Comment corriger un échec de pipeline

1. Aller dans **GitHub → Actions** → cliquer sur le run en rouge
2. Identifier le **job en échec** (icône rouge)
3. Cliquer sur l'**étape en échec** pour voir les logs détaillés
4. Analyser le message d'erreur selon le tableau suivant :

| Erreur | Cause probable | Solution |
|--------|----------------|----------|
| Erreur de compilation | Code Java invalide | Corriger la syntaxe, repousser |
| Test en échec | Assertion incorrecte | Analyser le message d'assertion, corriger la logique |
| Coverage < 60% | Manque de tests | Ajouter des tests unitaires sur les services |
| Docker push failed | Mauvais credentials | Vérifier `DOCKERHUB_TOKEN` dans les secrets GitHub |
| SonarCloud Quality Gate failed | Code non conforme | Voir section suivante |
| App ne démarre pas | Variable d'env manquante | `docker-compose logs app`, vérifier `.env` |

---

#### Comment interpréter les rapports SonarCloud

1. Aller sur **https://sonarcloud.io** → votre projet
2. **Vue d'ensemble** : Quality Gate — `PASSED` (vert) ou `FAILED` (rouge)
3. **Onglet Issues** → filtrer par `Type` et `Severity` :
   - `BUG` 🐛 : erreur de logique pouvant causer un crash → corriger en priorité
   - `VULNERABILITY` 🔒 : faille de sécurité exploitable → corriger immédiatement
   - `CODE SMELL` 💨 : mauvaise pratique réduisant la maintenabilité → améliorer
   - `SECURITY HOTSPOT` ⚠️ : code sensible → analyser et marquer Reviewed ou Fixed
4. **Onglet Coverage** : lignes en rouge = non couvertes → ajouter des cas de test
5. **Onglet Duplications** : blocs en rouge = code dupliqué → extraire dans une méthode commune

---

#### Comment corriger une vulnérabilité détectée

```bash
# 1. Identifier la CVE (rapport OWASP ou Trivy dans les artefacts GitHub)
# Exemple : CVE-2023-XXXX dans spring-security 6.1.0

# 2. Trouver la version corrigée
# → https://spring.io/security (Spring Security advisories)
# → https://nvd.nist.gov (détail CVE)

# 3. Mettre à jour pom.xml
# <spring-security.version>6.2.1</spring-security.version>

# 4. Vérifier localement que la CVE est résolue
mvn org.owasp:dependency-check-maven:check

# 5. Committer avec un message conventionnel
git add pom.xml
git commit -m "fix(security): update spring-security to fix CVE-2023-XXXX"
git push origin main
# → Pipeline relancé, vulnérabilité disparaît du rapport au prochain run
```

---

## Structure des livrables Git

```
taskmanager/
├── .github/
│   └── workflows/
│       └── ci-cd.yml                ← Pipeline CI/CD complet (7 jobs)
├── src/
│   ├── main/java/com/deployfast/taskmanager/
│   │   ├── controller/              ← AuthController, TaskController
│   │   ├── service/impl/            ← AuthServiceImpl, TaskServiceImpl
│   │   ├── repository/              ← UserRepository, TaskRepository
│   │   ├── entity/                  ← User, Task
│   │   ├── dto/                     ← Request + Response DTOs
│   │   ├── security/                ← JWT Filter + Provider
│   │   ├── config/                  ← SecurityConfig
│   │   └── exception/               ← GlobalExceptionHandler
│   ├── main/resources/
│   │   ├── application.yml          ← Config prod (PostgreSQL)
│   │   └── application-test.yml     ← Config test (H2)
│   └── test/                        ← Tests unitaires + Feature + Auth
├── Dockerfile                       ← Multi-stage build
├── docker-compose.yml               ← App + PostgreSQL (production)
├── docker-compose.staging.yml       ← Environnement staging (bonus)
├── sonar-project.properties         ← Config SonarCloud
├── pom.xml                          ← Dépendances + JaCoCo + Sonar plugin
├── .env.example                     ← Template variables d'environnement
├── .gitignore
└── README.md                        ← Ce fichier (réponses aux 6 questions)
```

---

*"Il n'y a que dans le dictionnaire que réussite vient avant travail" — Pierre Fonerod*
