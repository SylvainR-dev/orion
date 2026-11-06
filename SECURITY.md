# Sécurité — MicroCRM (Orion)

## Historique et évolution

Au début du projet, le seul contrôle de sécurité était un scan Trivy réalisé par l'équipe Ops, après réception des images par e-mail. C'est justement ce scan, trop tardif, qui avait fait revenir des failles sur une image déjà transmise et retardé le premier déploiement. Mon premier choix a donc été de déplacer les contrôles le plus tôt possible : Trivy tourne maintenant juste après la construction de chaque image, avant toute publication, et SonarCloud analyse le code avant même de construire quoi que ce soit.

Côté AWS, j'ai commencé par donner à l'utilisateur IAM de Terraform des droits d'administration pour avancer vite sur EKS. Je l'ai noté comme dette de sécurité, puis je l'ai corrigée en le remplaçant par une policy restreinte par service et par ressource. Plus tard, quand j'ai remplacé EKS par une instance k3s, j'ai aussi supprimé une exposition que je trouvais gênante : l'API du cluster était publique, sans restriction d'adresse. Aujourd'hui, le seul port ouvert est le SSH, et uniquement depuis mon adresse. J'accède à l'API de k3s par un tunnel SSH.

La migration vers GitHub m'a posé une question : je voulais un vrai déploiement continu dans Actions, mais sans stocker de clé AWS dans GitHub. J'ai choisi l'authentification OIDC : le job de déploiement obtient un jeton temporaire de GitHub, qu'AWS accepte uniquement pour un rôle précis. Au premier essai, AWS l'a refusé. J'ai écrit un petit workflow de diagnostic qui affiche les champs du jeton (jamais le jeton lui-même), et j'ai découvert que GitHub émet maintenant le champ `sub` avec les identifiants numériques du propriétaire et du dépôt, et non plus avec leurs noms. J'ai adopté ce format plutôt que de passer à un joker : les identifiants ne changent pas si le dépôt est renommé ou transféré, donc un autre compte ne pourrait pas reprendre mon nom de dépôt pour obtenir le rôle.

Comme le dépôt est public, j'ai aussi fait attention à ce qui s'y trouve. J'ai cherché dans tout l'historique git des formats de clés et de jetons courants (clés AWS, jetons GitHub et GitLab, clés privées), sans aucun résultat, puis j'ai ajouté Gitleaks à la chaîne. J'ai également ajouté un scan de la configuration de l'infrastructure (Helm et Terraform) avec Trivy.

Le premier passage réel de la chaîne m'a donné un exemple concret : Trivy a bloqué la publication de l'image du backend à cause de cinq failles critiques. J'ai corrigé les quatre failles de Tomcat en montant sa version, et j'ai ignoré la cinquième avec une justification écrite (voir la section 4).

## 1. Approche générale — Shift-left

Le principe est d'intégrer les contrôles **le plus tôt possible**, plutôt qu'en fin de chaîne.

| Contrôle | Outil | Positionnement dans le pipeline |
|---|---|---|
| Analyse statique du code (SAST) | SonarCloud | Après les tests, avant la création de la version |
| Détection de secrets | Gitleaks | En parallèle des tests, avant la création de la version |
| Configuration de l'infrastructure | Trivy (mode `config`) | En parallèle des tests, avant la création de la version |
| Vulnérabilités des images | Trivy (mode `image`) | Juste après la construction de chaque image, avant le `push` vers GHCR |
| Déploiement | Approbation manuelle (environnement `production`) | Avant toute commande envoyée à l'instance |

Rien n'est versionné, publié ni déployé tant que les tests, SonarCloud, Gitleaks et le scan de configuration n'ont pas réussi.

## 2. Sécurité applicative — Backend

L'architecture initiale (Spring Data REST) exposait directement les entités JPA en HTTP, avec un CORS ouvert (`allowedOrigins("*")`) et des méthodes `PATCH` et `DELETE` accessibles sur toutes les routes. J'ai refondu le backend en couches Controller / Service / Repository, ce qui a corrigé plusieurs points :

- **CORS restreint** : une `CorsConfig` classique, avec une origine configurable, remplace le joker.
- **Validation des entrées** : les DTO (des `record` Java) sont annotés `@NotBlank` et `@Email` (Jakarta Validation). Avant, on pouvait créer une personne avec un e-mail vide ou mal formé.
- **Pas d'exposition directe du modèle** : le contrat d'API est découplé du schéma de la base.
- **Gestion centralisée des erreurs** : `GlobalExceptionHandler` renvoie des réponses normalisées (404, 409, 400) au lieu de laisser fuiter des détails d'implémentation.
- **Bug d'intégrité** : la cascade `ALL` sur la relation `Organization` ↔ `Person` supprimait les personnes avec leur organisation. Je l'ai limitée à `{PERSIST, MERGE}`. C'est un test d'intégration qui me l'a fait découvrir.

**Limite connue** : il n'y a pas de Spring Security, donc **l'API n'est pas authentifiée**. C'est cohérent avec un projet de démonstration qui n'est exposé que par `kubectl port-forward`, mais il faudrait traiter ce point avant toute exposition réelle.

## 3. Sécurité applicative — Frontend

- L'appel à l'API se fait avec une API REST classique (`/api/persons`, `/api/organizations`), sans dépendre du format HAL de Spring Data REST, qui exposait davantage la structure interne du modèle.
- La relation personne ↔ organisation est reconstruite côté client par filtrage, ce qui évite d'ajouter un endpoint, donc de la surface d'attaque.
- Angular applique par défaut l'échappement contextuel du DOM, ce qui réduit le risque de XSS. Je n'ai pas fait de vérification manuelle supplémentaire sur ce point.

## 4. Sécurité des images — Trivy

```bash
trivy image --severity CRITICAL --exit-code 1 --ignorefile .trivyignore <image>
```

- Trivy est exécuté avec l'image officielle `aquasec/trivy`, juste après la construction de chaque image et avant le `push`.
- **Bloquant** : toute faille critique interrompt la publication.
- **Exemple réel** : lors du premier passage sur GitHub, le scan du backend a signalé cinq failles critiques. Quatre concernaient `tomcat-embed-core` (contournements d'authentification et de contraintes de sécurité), que j'ai corrigées en fixant `ext['tomcat.version']` à `10.1.59` dans `back/build.gradle`, version qui les corrige toutes.
- **Exception documentée** : la cinquième, CVE-2026-47884, concerne `spring-webmvc` et la classe `XsltView` (SSRF et exécution de code). Le correctif open source n'existe que dans Spring Framework 7.0.9, donc avec Spring Boot 4, une migration que je n'ai pas prévue à ce stade. La faille n'est exploitable que si l'application utilise `XsltView` avec un mapping `/**` qui produit un rendu de vue. Mon backend n'a que des contrôleurs REST, et j'ai vérifié par recherche qu'il n'y a aucune référence à XSLT. Je l'ai donc ignorée dans `.trivyignore`, avec la justification en commentaire et une date d'expiration (2027-02-04), pour ne pas l'oublier.
- **Image de base** : Trivy signale qu'Alpine 3.19 n'est plus maintenue, donc que sa détection peut être incomplète. Je devrais passer à une version supportée.

## 5. Secrets et configuration de l'infrastructure

- **Gitleaks** analyse les commits poussés ou ceux d'une Pull Request à la recherche de secrets. En plus, j'ai cherché dans tout l'historique git, avant de rendre le dépôt public, des formats courants de clés et de jetons : aucun résultat.
- **Trivy en mode `config`** scanne `helm/` et `terraform-orion/` (sévérités élevée et critique). **Cette première version est non bloquante** : je lis d'abord le rapport, je corrige ou je justifie chaque constat, puis je la passerai en bloquant. Je n'ai pas encore analysé ce rapport.

## 6. Analyse statique — SonarCloud

- Deux projets SonarCloud, un par application : le backend (Java, avec le plugin Gradle `org.sonarqube`) et le frontend (TypeScript, avec le scanner en ligne de commande).
- La **Quality Gate** est bloquante : le pipeline s'arrête si elle échoue. Les taux de couverture sont dans `back/TESTING.md` et `front/TESTING.md`.

## 7. Gestion des secrets et des accès

| Élément | Emplacement | Portée |
|---|---|---|
| `SONAR_TOKEN` | Secret GitHub Actions | Le seul secret de la CI. Je l'ai régénéré lors de la migration, après l'exposition de l'ancien |
| Clés AWS (utilisateur `orion-terraform`) | `~/.aws` sur mon poste | Terraform et la CLI, depuis mon poste uniquement. **Aucune clé AWS n'est stockée dans GitHub** |
| Kubeconfig (`ansible/kubeconfig-orion.yaml`) | Mon poste, ignoré par git | Contient une clé d'administration |
| `terraform.tfvars`, plans Terraform (`tfplan`) | Mon poste, ignorés par git | Mon adresse IP et les plans |
| Mot de passe administrateur Grafana | Saisi sans écho (`read -s`), stocké par Helm dans un secret Kubernetes | Jamais écrit dans un fichier du dépôt |
| Images | Publiques sur GHCR | Le dépôt est public, donc aucun secret de téléchargement |

## 8. Déploiement continu sans clé : OIDC et AWS Systems Manager

Le job `deploy` fonctionne ainsi :

1. Il attend mon **approbation manuelle** (environnement GitHub `production`).
2. Il demande à GitHub un **jeton OIDC** à courte durée de vie, et AWS l'échange contre des identifiants temporaires pour le rôle `orion-github-deploy`. Aucune clé n'est stockée.
3. Il envoie à l'instance, par **AWS Systems Manager**, une commande qui clone la version taguée et lance `helm upgrade --atomic`. **Aucun port n'est ouvert** pour cela.

Ce que je contrôle :

- **Qui peut endosser le rôle** : le jeton doit venir de ce dépôt, avec l'audience `sts.amazonaws.com`, depuis l'environnement `production`. Un workflow lancé depuis un fork ou hors de cet environnement n'obtient pas le rôle.
- **Ce que le rôle peut faire** : envoyer une commande SSM avec le document `AWS-RunShellScript` **vers cette instance uniquement**, lire le résultat, et décrire les instances. Il ne peut rien créer ni modifier dans AWS.
- **Ce que la commande peut faire** : elle s'exécute en tant que `root` sur l'instance (c'est le fonctionnement de `AWS-RunShellScript`). Elle est écrite dans `ci.yml`, donc toute modification passe par une Pull Request.

## 9. Sécurité de l'infrastructure AWS

| Aspect | État actuel | Statut |
|---|---|---|
| Groupe de sécurité de l'instance | SSH (port 22) uniquement, depuis mon adresse en `/32`. L'API de k3s n'est pas ouverte | ✅ |
| Accès à l'API de k3s | Tunnel SSH vers `127.0.0.1:6443` | ✅ |
| Rôle `orion-github-deploy` | OIDC limité à ce dépôt et à l'environnement `production`, droits SSM limités à cette instance | ✅ |
| Rôle de l'instance (`orion-k3s-instance`) | Politique gérée `AmazonSSMManagedInstanceCore`, pour que l'agent SSM s'enregistre | ✅ |
| Utilisateur IAM `orion-terraform` | Policy `orion-terraform-scoped` (héritée de l'époque EKS, pas encore allégée) et policy `orion-terraform-cd` (profil d'instance d'Orion, `iam:PassRole` limité au rôle de l'instance avec la condition `ec2.amazonaws.com`) | ⚠️ Droits de l'époque EKS à retirer |
| État Terraform | Bucket S3 versionné, chiffré côté serveur, verrouillage natif | ✅ |
| RBAC Kubernetes | Configuration par défaut de k3s | ⚠️ Non durci |
| NetworkPolicy | Aucune | ⚠️ Absente |

## 10. Processus de traitement des vulnérabilités

1. **Détection** par SonarCloud (code), Trivy (images ou configuration) ou Gitleaks (secrets), lors de l'exécution du pipeline.
2. **Évaluation** : la sévérité est déterminée par l'outil (CVSS pour Trivy).
3. **Blocage** : une faille critique ou une Quality Gate en échec arrête le pipeline, donc aucune version, image ni déploiement n'est créé. Le scan de configuration, lui, ne bloque pas encore.
4. **Correction** sur une branche dédiée, qui repasse par le pipeline complet dans une Pull Request.
5. **Exception** : si une faille ne peut pas être corrigée, je la documente dans `.trivyignore` avec la raison pour laquelle elle n'est pas exploitable et une date d'expiration.

## 11. Dette de sécurité et pistes d'amélioration

| Élément | Risque | Statut |
|---|---|---|
| API backend non authentifiée (pas de Spring Security) | Moyen : exposée uniquement par `port-forward` | ⚠️ Assumé |
| Pas de NetworkPolicy | Faible à moyen : mouvement latéral possible entre pods | ⚠️ Assumé |
| Droits hérités de l'époque EKS dans `orion-terraform-scoped` | Moyen : droits inutiles | ⚠️ À alléger |
| Environnement `production` non restreint à la branche `main` | Moyen : à vérifier dans les réglages de l'environnement | ⚠️ À vérifier |
| Faille CVE-2026-47884 ignorée | Faible : `XsltView` n'est pas utilisée | ⚠️ Expire le 2027-02-04 |
| Image de base Alpine 3.19 en fin de vie | Moyen : détection de failles incomplète | ⚠️ À migrer |
| Scan de configuration non bloquant | Moyen | ⚠️ À rendre bloquant |
| HyperSQL embarqué | Faible : démonstration uniquement | ⚠️ Assumé |
| Pas d'alerte de budget AWS | Faible : le coût d'une instance oubliée allumée | ⚠️ À faire |
