# Synchronisation des contacts par Nextcloud (WebDAV), chiffrée

Spécification de la fonction « Synchronisation » de l'app Contacts. Elle reprend la demande
d'origine (« synchroniser les contacts entre plusieurs téléphones via Nextcloud, tout chiffré, avec
une fusion intelligente ») et la rend vérifiable.

## Objectif

Une personne qui a plusieurs téléphones retrouve les mêmes contacts partout, sans compte Google, en
n'utilisant que son propre Nextcloud. Le serveur ne voit jamais un nom, un numéro ni une note.

## Règles non négociables

1. **Aucune perte de données.** Une synchronisation ne supprime et n'écrase rien sans pouvoir le
   retrouver : avant d'appliquer des changements, l'app garde une sauvegarde chiffrée de tous les
   contacts du téléphone (les 5 dernières).
2. **Tout est chiffré avant de quitter le téléphone** (AES-256-GCM, clé dérivée d'une phrase secrète
   par PBKDF2-HMAC-SHA256, 600 000 itérations). Le serveur ne reçoit que des fichiers chiffrés ; la
   phrase secrète et la clé n'y vont jamais. Sur le téléphone, la clé est protégée par l'Android
   Keystore ; les copies de travail (état de synchro, sauvegardes) sont chiffrées avec elle.
3. **Une seule écriture par appareil.** Chaque téléphone n'écrit que son propre fichier
   (`devices/<id>.bsync`) et ne fait que lire ceux des autres : pas de conflit d'écriture, même si
   deux téléphones synchronisent en même temps.
4. **HTTPS obligatoire** (sauf `localhost` et `10.0.2.2`, qui ne sortent jamais de la machine).
5. **Rien de destructeur en masse.** Si une synchronisation voulait supprimer plus de 10 contacts
   ou plus de 20 % du carnet, elle s'arrête pour ces suppressions et le signale.

## Ce qui est synchronisé

Noms (dont phonétiques), surnom, société, poste, notes, numéros, e-mails, adresses, dates, sites,
relations, adresses d'appel Internet, messageries, libellés (par leur nom) et favoris. Les photos ne
le sont pas encore. Seuls les contacts enregistrés **sur le téléphone** (pas ceux d'un compte Google
ou autre) sont concernés, pour ne pas dupliquer ce qu'un autre compte synchronise déjà.

## Comparer et fusionner

Chaque contact a un identifiant de synchro stable. Quand un contact arrive d'un autre téléphone :

| Situation | Résultat |
|---|---|
| Déjà connu (même identifiant) | **Fusion à trois voies** avec la dernière version synchronisée : un champ modifié d'un seul côté est repris ; modifié des deux côtés, le plus récent gagne ; numéros, e-mails, adresses… sont réunis, en respectant les retraits faits d'un côté. |
| Inconnu, et aucun contact ressemblant | Ajouté au téléphone. |
| Inconnu, même numéro, **un côté n'a pas de nom** (seulement le numéro) | Un seul contact : **celui qui a un nom est gardé**, il reprend les autres informations. |
| Inconnu, même nom exact et un numéro ou e-mail en commun | Considérés comme la même personne : réunis. |
| Inconnu, même numéro mais **noms différents** | Les deux sont gardés. Ils apparaissent dans **Organiser › Fusionner les doublons**, où vous décidez (et Android les relie déjà s'ils ont le même nom, dans n'importe quelle app de contacts). |

## Suppressions

Supprimer un contact crée une **marque de suppression** datée. Un autre téléphone ne supprime le
contact que s'il n'a pas été modifié depuis la dernière synchro ; sinon il le garde (une
modification l'emporte sur une suppression). Les marques sont gardées 90 jours.

## Erreurs prévues

Identifiants refusés, serveur injoignable, certificat invalide, phrase secrète incorrecte (détectée
avant toute écriture), fichier d'un autre appareil illisible ou d'une version plus récente (ignoré,
le reste continue), coupure en cours de route (rien n'est marqué comme synchronisé tant que les
changements locaux ne sont pas tous appliqués). Une nouvelle tentative est planifiée automatiquement.

## Dossier sur Nextcloud

`BundleSync/vault.json` (sel, itérations, contrôle de la phrase secrète : aucune donnée) et
`BundleSync/devices/<id>.bsync` (un fichier chiffré par téléphone). Utiliser un **mot de passe
d'application** Nextcloud, pas le mot de passe du compte.

## Ce que ça ne fait pas (encore)

Photos, restauration en un clic d'une sauvegarde de sécurité, contacts d'autres comptes, résolution
manuelle d'un conflit champ par champ.
