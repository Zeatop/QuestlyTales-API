pipeline {
    agent any
    environment {
        REGISTRY   = '10.0.0.10:5000'
        IMAGE      = 'questlytales-backend'
        TAG        = "${BUILD_NUMBER}"
        // Chemins relatifs à la racine du repo (QuestlyTales-API)
        MODULE_DIR = 'QuestlyTales'        // contexte de build Docker (contient le Dockerfile)
        K8S_FILE   = 'k8s/deployment.yaml'
    }
    stages {
        // Pas de stage 'Checkout' explicite : Jenkins clone déjà le repo
        // configuré dans le job (Declarative: Checkout SCM) sur le bon commit.
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh """
                        docker run --rm \
                            --volumes-from jenkins \
                            -w \$WORKSPACE/${MODULE_DIR} \
                            -e SONAR_HOST_URL=\$SONAR_HOST_URL \
                            -e SONAR_TOKEN=\$SONAR_AUTH_TOKEN \
                            sonarsource/sonar-scanner-cli \
                            -Dsonar.projectKey=questlytales-backend \
                            -Dsonar.sources=src/main/java \
                            -Dsonar.host.url=\$SONAR_HOST_URL \
                            -Dsonar.token=\$SONAR_AUTH_TOKEN
                    """
                }
            }
        }
        stage('Docker Build') {
            steps {
                // Le Dockerfile est multistage : il compile le jar (Maven) puis produit
                // l'image d'exécution. Aucun build-arg : les secrets sont injectés au
                // runtime via le Secret K8s questlytales-secrets.
                sh """
                    docker build \
                        -t ${REGISTRY}/${IMAGE}:${TAG} \
                        -t ${REGISTRY}/${IMAGE}:latest \
                        ${MODULE_DIR}
                """
            }
        }
        stage('Docker Push') {
            steps {
                sh "docker push ${REGISTRY}/${IMAGE}:${TAG}"
                sh "docker push ${REGISTRY}/${IMAGE}:latest"
            }
        }
        stage('Deploy to K8s') {
            environment {
                // Le nom de la variable (à gauche) = clé du Secret K8s = placeholder
                // ${...} dans application.properties. La string dans credentials() = ID
                // du credential Jenkins. Les deux peuvent différer, mais le nom de
                // variable DOIT correspondre à ce que lit l'appli.
                JWT_SECRET_QUESTLYTALES        = credentials('JWT_SECRET_QUESTLYTALES')
                QUESTLYTALES_GITHUB_TOKEN      = credentials('QUESTLYTALES_GITHUB_TOKEN')
                DEEPSEEK_API_KEY               = credentials('DEEPSEEK_API_KEY_QUESTLYTALES')
                ANTHROPIC_API_KEY              = credentials('ANTHROPIC_API_KEY_QUESTLYTALES')
                MONGO_URI_QUESTLYTALES         = credentials('MONGO_URI_QUESTLYTALES')
                MONGO_URI_USERS_QUESTLYTALES   = credentials('MONGO_URI_USERS_QUESTLYTALES')
            }
            steps {
                // Provisionne / met à jour le Secret K8s à partir des credentials Jenkins.
                // Quotes simples : pas d'interpolation Groovy -> les secrets ne fuitent
                // pas dans le log et restent gérés côté shell.
                sh '''
                    kubectl create secret generic questlytales-secrets \
                        --from-literal=JWT_SECRET_QUESTLYTALES="$JWT_SECRET_QUESTLYTALES" \
                        --from-literal=QUESTLYTALES_GITHUB_TOKEN="$QUESTLYTALES_GITHUB_TOKEN" \
                        --from-literal=DEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
                        --from-literal=ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
                        --from-literal=MONGO_URI_QUESTLYTALES="$MONGO_URI_QUESTLYTALES" \
                        --from-literal=MONGO_URI_USERS_QUESTLYTALES="$MONGO_URI_USERS_QUESTLYTALES" \
                        --dry-run=client -o yaml | kubectl apply -f -
                '''
                sh "sed -i 's|${REGISTRY}/${IMAGE}:latest|${REGISTRY}/${IMAGE}:${TAG}|' ${K8S_FILE}"
                sh "kubectl apply -f ${K8S_FILE}"
                // Redémarre les pods pour qu'ils relisent le Secret s'il a changé.
                sh "kubectl rollout restart deployment/questlytales-backend"
                sh "kubectl rollout status deployment/questlytales-backend --timeout=180s"
            }
        }
    }
    post {
        success {
            echo "Déploiement réussi ! QuestlyTales backend accessible sur le port 30080"
        }
        failure {
            echo "Le pipeline a échoué"
        }
    }
}
