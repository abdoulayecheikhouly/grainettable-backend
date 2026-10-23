# Contrôle des modifications concurrentes des produits

Le projet utilise JdbcTemplate. La migration V4 ajoute `product.version BIGINT NOT NULL DEFAULT 0` ; les versions sont renvoyées dans les lectures admin et publiques. Pas de dépendance JPA ni d’annotation @Version inopérante sur les requêtes SQL.

PUT `/api/admin/products/{id}` exige le champ `version` lu lors de l’ouverture de la fiche (création POST sans version possible). PATCH `/api/admin/products/{id}/stock` exige aussi `version`. DELETE `/api/admin/products/{id}?version=N` vérifie la version avant archivage. Une version absente sur PUT/PATCH est refusée avec 400 ; une version périmée renvoie 409 et laisse produit, photos et historique inchangés.

Chaque modification de fiche, ajustement de stock ou archivage incrémente la version. Les confirmations et déconfirmations des commandes l’incrémentent pour chaque produit affecté. Les notes visiteurs ne changent pas les données éditables du produit et n’incrémentent donc pas sa version. Les verrous de ligne et transactions existants restent utilisés pour calculer un historique de stock exact et garantir le rollback.

L’interface conserve les saisies sur conflit et propose un rechargement après confirmation explicite de l’abandon des saisies. Aucun retry automatique. La lecture admin par identifiant permet de retrouver un produit même si son SKU a été changé ailleurs.

Déploiement : backend puis frontend. Pendant l’intervalle, les anciens formulaires sans version ne peuvent plus enregistrer ; actualiser l’application après déploiement du frontend. Ne jamais rendre la version facultative pour contourner cette incompatibilité. Tout script d’import existant doit désormais lire et transmettre la version avant modification.

Tests : ProductVersionTest (commande 20→17 puis ancienne fiche refusée ; deux admins ; archivage ; ajustements ; photos et historique préservés) et scénario navigateur product-version.spec.ts (saisies conservées et rechargement explicite de fiche et stock).
