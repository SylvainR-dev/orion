# [1.5.0](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.4.5...v1.5.0) (2026-07-17)


### Features

* ajout du déploiement automatisé via Ansible sur l'environnement de démonstration ([0d129be](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/0d129be8352bc68c54578d8f16e7665641545230))

## [1.4.5](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.4.4...v1.4.5) (2026-07-16)


### Bug Fixes

* mise à jour de Tomcat vers 10.1.55 pour corriger 3 CVE critiques additionnelles (CVE-2026-41293, CVE-2026-43512, CVE-2026-43515) ([8312981](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/83129810a8e5d0a1f6d36f4b3986bb42e30545c3))

## [1.4.4](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.4.3...v1.4.4) (2026-07-16)


### Bug Fixes

* mise à jour de Tomcat embarqué pour corriger 4 CVE critiques (CVE-2025-24813, CVE-2026-41293, CVE-2026-43512, CVE-2026-43515) ([934cdfe](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/934cdfeb9c6a3cbb789b5b3a7dc6546c7d12063f))

## [1.4.3](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.4.2...v1.4.3) (2026-07-15)


### Bug Fixes

* utilisation de l'image Docker officielle Trivy pour fiabiliser le scan ([1871629](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/1871629f5c88077ffb268a99b0794c8021fdb716))

## [1.4.2](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.4.1...v1.4.2) (2026-07-15)


### Bug Fixes

* correction du nom du binaire Trivy (convention linux-amd64) ([38e7056](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/38e705611c934dac82a90f022251fe5918105466))

## [1.4.1](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.4.0...v1.4.1) (2026-07-15)


### Bug Fixes

* téléchargement direct du binaire Trivy pour fiabiliser l'installation ([e4ebddb](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/e4ebddb3fd8bd807bf179387a88d41e388408955))

# [1.4.0](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.3.0...v1.4.0) (2026-07-15)


### Features

* ajout du scan de sécurité Trivy avant publication des images ([f31c63a](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/f31c63a3ff0ccb6e3518c34bf35279b24c23e185))

# [1.3.0](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.2.1...v1.3.0) (2026-07-15)


### Features

* intégration de SonarQube Cloud pour l'analyse du frontend ([aa4f26c](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/aa4f26cce9acc10d85216b55e86b698775d6f4ae))

## [1.2.1](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.2.0...v1.2.1) (2026-07-15)


### Bug Fixes

* correction de la commande gradle pour le job SonarQube (usage du wrapper) ([dfa9a4e](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/dfa9a4e6fb5f7a4f229f6b9f21e16aea56921acc))

# [1.2.0](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.1.0...v1.2.0) (2026-07-15)


### Features

* intégration de SonarQube Cloud pour l'analyse du backend ([ddc0900](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/ddc09009dee43f4d754c78c4b8b1868c6e8442c0))

# [1.1.0](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/compare/v1.0.0...v1.1.0) (2026-07-15)


### Features

* ajout du build et de la publication des images Docker vers le registre GitLab ([1e2ea7a](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/1e2ea7a13b286437bfae2a9972d46dce5473a548))

# 1.0.0 (2026-07-15)


### Features

* add docker images build instructions ([4298760](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/42987602d3f33e1dfe1d24c58df136a5df949aa8))
* initial commit ([0b1e7fc](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/0b1e7fcbe03c67006cd27e4f293bfed40c923b18))
* mise en place du versioning automatique avec semantic-release ([8ad67bc](https://gitlab.com/sylvainr-dev-group/projet-6-demarche-devops/commit/8ad67bc1b290efe8257808f85e30c8996c133a91))
