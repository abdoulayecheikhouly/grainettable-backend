# Grain & Table — API Java 21

Spring Boot 3.5, Spring Security, JDBC, Flyway, PostgreSQL en production et H2 en développement. API française utilisée par le frontend Angular du dépôt `grainettable-frontend`.

## Démarrer

Java **21** et Maven 3.9 : `mvn spring-boot:run`. L’API écoute sur `localhost:8080`. Angular utilise le proxy `/api` sur `localhost:4200`. `mvn verify` lance les tests. `SEED_DEMO=true` ajoute des exemples uniquement hors du profil `prod` et dans une base vide.

## Accès

L’inscription crée uniquement un client. Les sessions utilisent un cookie HttpOnly et SameSite Strict, Secure en production. Angular envoie le jeton CSRF du cookie XSRF-TOKEN dans X-XSRF-TOKEN. Toute mutation exige l’origine configurée. Les routes `/api/admin/**` vérifient le rôle ADMIN côté serveur.

Pour créer le premier administrateur, fournir temporairement `ADMIN_EMAIL` et un `ADMIN_PASSWORD` aléatoire d’au moins 16 caractères au démarrage, puis retirer ces variables. Aucun mot de passe de production n’est commité. Un compte existant n’est ni promu ni écrasé par ce mécanisme.

## API

- `/api/session`, `/api/auth/register`, `/api/auth/login`, `/api/auth/logout`
- `/api/categories`, `/api/products` : q, category, min, max, available, sort, page, limit
- `/api/products/{id}`, `/api/cart/quote`
- `/api/orders` : POST authentifié avec Idempotency-Key UUID et expected_total, GET pour ses propres demandes
- `/api/admin/products`, `/api/admin/categories` : liste, ajout, modification, retrait/suppression
- `/api/admin/uploads` : multipart file JPEG/PNG, 5 Mo, décodage et réencodage, limites de dimensions
- `/api/admin/orders` : liste et PATCH statut

Les prix sont des entiers FCFA. La demande enregistre les lignes et leurs prix puis fournit `whatsapp_url`. La redirection n’est pas une preuve d’envoi ou d’acceptation. Le stock diminue uniquement à la confirmation administrateur et est restauré si cette confirmation est annulée. Un retry avec la même clé ne crée pas de doublon.

## Production et CI/CD

`Dockerfile` compile et teste avec Java 21. `deploy/compose.yml` isole PostgreSQL et les médias dans des volumes durables. Le frontend rejoint uniquement le réseau du proxy existant `samakeur_default`. Le domaine prévu est `https://grainesetable.duckdns.org`. Le bloc Caddy de ce domaine doit pointer vers `grainettable-frontend:8080` sans modifier les sites existants.

Le workflow vérifie chaque PR et chaque push main. Le job deploy reste désactivé tant que `DEPLOY_ENABLED=true` n’est pas défini. Secrets : `VPS_SSH_KEY`, `VPS_KNOWN_HOSTS`. Le compte attendu est `deploy-grainettable`, avec une clé à commande forcée vers le script root-owned `deploy/grainettable-ci.sh`. La clé backend ne peut déclencher que le backend ; la clé frontend, uniquement le frontend. Pas de mot de passe root dans GitHub Actions.

Le script de déploiement vérifie que le commit appartient à main, sérialise les mises en ligne, vérifie la santé et restaure le tag précédent en cas d’échec. Les migrations doivent rester rétrocompatibles pour un retour de version ; ne pas supprimer de colonnes dans une migration de déploiement automatique.

`deploy/backup.sh` sauvegarde PostgreSQL et les photos. Définir une rétention et une copie hors VPS. Les fichiers .env et les sauvegardes restent privés. Aucun paiement en ligne n’est activé : le champ payment_status est prévu pour une future intégration, sans clé ni prestataire présélectionné.
