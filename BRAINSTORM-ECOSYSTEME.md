# Bundle Contacts · Téléphone · SMS — Scan & brainstorm écosystème

*Scan du 22/11/2026. Objectif : trois apps Android modernes (Material 3 Expressive) qui fonctionnent comme un seul système, avec les fonctionnalités des apps natives Google (Contacts / Téléphone / Messages) et Samsung (Contacts / Téléphone / Messages).*

---

## 1. Scan de `jawsh207/Contacts`

| | |
|---|---|
| Langage / UI | Kotlin, Jetpack Compose, **Material 3 Expressive** (`material3 1.5.0-beta01`), Google Sans Flex, couleurs du fond d'écran |
| Cible | minSdk 31 (Android 12), compileSdk 37, AGP 9.1.1, arm64 uniquement |
| Licence | **Apache-2.0** (réutilisable, y compris en fork fermé) |
| Dépendances | très peu : core-ktx, compose, datastore, WorkManager, zxing (QR). **Pas de permission INTERNET** |
| Qualité | CI GitHub Actions (build dev + release signés), tests unitaires (vCard, dates, doublons, recherche, numéros), doc claire (`docs/HOW-IT-WORKS.md`) |
| Archi | `data/` (repository sur `ContactsContract`), `ui/` par écran, un `ContactsViewModel`, observation du provider → tout se rafraîchit seul |

**Fonctions déjà présentes :** liste A–Z + barre alphabétique, favoris, *Your info* + QR vCard, onglet Highlights (favoris, anniversaires à 30 j, récents appels/SMS), recherche plein-texte (nom, numéro, email, société, notes, adresse), éditeur complet (phonétique, relations, SIP, IM, photo recadrée), numéro/email par défaut, multi-sélection, suppression avec Undo 8 s, labels (groupes) + SMS/email groupé, fusion de doublons *par liaison* (comme Google), dissociation, **Fix contacts** (vides, sans nom, doublons de numéro, indicatif manquant), import/export .vcf, calendrier « Anniversaires » auto, intents système (PICK, INSERT, QUICK_CONTACT…).

**Point clé : ce repo fait déjà partie d'un mini-écosystème du même auteur**, avec le même thème :

| Repo | Rôle | Licence | Notes |
|---|---|---|---|
| [jawsh207/Contacts](https://github.com/jawsh207/Contacts) | Contacts | Apache-2.0 | décrit ci-dessus |
| [jawsh207/Dialtone](https://github.com/jawsh207/Dialtone) | Téléphone | Apache-2.0 | style Pixel Dialer, M3 Expressive, **messagerie vocale visuelle** (moteur AOSP), transcription via [Distillery](https://github.com/jawsh207/Distillery), filtrage « Envoyer vers messagerie », double appel / fusion, réponse par SMS, multi-SIM |
| [jawsh207/twill](https://github.com/jawsh207/twill) | SMS/MMS | Apache-2.0 | basé sur **GrapheneOS Messaging** (réécriture Compose) + Nostr + Delta Chat, envoi différé, SMS programmés, aperçus de liens, bulles |
| jawsh207/Distillery | STT/TTS offline | — | Moonshine + Kokoro, sert à Dialtone |
| jawsh207/Notes, Gallery, Loom (launcher), Weft (clavier) | — | — | potentiels satellites de l'écosystème |

> ⚠️ Twill n'est **pas encore aligné visuellement** sur Contacts/Dialtone (il hérite du design GrapheneOS Messaging). C'est le premier chantier d'unification.

---

## 2. Autres repos pour SMS et appels

### Les plus pertinents (modernes, actifs)

| Repo | Type | ★ | Licence | Pourquoi le regarder |
|---|---|---|---|---|
| [arditore/Seca](https://github.com/arditore/Seca) | **Contacts + Téléphone + Messages** | 60 | GPL-3.0 | Exactement ton concept : 3 apps, un design M3 Expressive, **profils colorés partagés**, **sauvegarde suite chiffrée unique**, réglages d'apparence communs aux 3 apps, provider protégé par signature, blocage des plages démarchage FR, effacement auto des codes OTP, rangement des pubs. **La meilleure référence d'architecture.** |
| [GrapheneOS/Messaging](https://github.com/GrapheneOS/Messaging) | SMS/MMS | 242 | (AOSP) | Base Compose moderne, maintenue, solide sur MMS/APN. Base de Twill |
| [user-grinch/RivoPhone](https://github.com/user-grinch/RivoPhone) | Téléphone + contacts | 273 | ⚠️ non déclarée | M3 Expressive, très orienté personnalisation, T9, speed dial. Bonne source d'idées UI, **pas de code réutilisable sans licence** |
| [dhilipmpms/NovaDial](https://github.com/dhilipmpms/NovaDial) | Téléphone + contacts | 134 | GPL-3.0 | Moderne, AMOLED, sur F-Droid |
| [Secure-Phone-apps/Secure-Dialer](https://github.com/Secure-Phone-apps/Secure-Dialer) | Téléphone | 71 | GPL-3.0 | 100 % offline, rappels de rappel (« callback reminder ») |
| [FossifyOrg/Phone](https://github.com/FossifyOrg/Phone) | Téléphone | 1.4k | GPL-3.0 | Très complet et éprouvé (blocage, multi-SIM, speed dial), UI Views (pas Compose) |
| [FossifyOrg/Messages](https://github.com/FossifyOrg/Messages) | SMS/MMS | 1.7k | GPL-3.0 | Très complet, archivage, corbeille, blocage par mot-clé |
| [FossifyOrg/Contacts](https://github.com/FossifyOrg/Contacts) | Contacts | 957 | GPL-3.0 | Référence fonctionnelle |
| [quik-sms/quik](https://github.com/quik-sms/quik) | SMS/MMS | 2.9k | GPL-3.0 | Fork actif de QKSMS : SMS programmés, réponses rapides, thèmes par conversation, sauvegarde |
| [Chooloo/koler](https://github.com/Chooloo/koler) | Téléphone | 1.1k | ⚠️ custom | Dialer minimaliste et esthétique |
| [vayun-mathur/Modern-Apps](https://github.com/vayun-mathur/Modern-Apps) | Écosystème complet | 916 | GPL-3.0 | Monorepo Compose/M3 avec composants communs ; ancêtre de jawsh207/Contacts |

### Utiles pour des briques précises

| Repo | Brique |
|---|---|
| [tmo1/sms-ie](https://github.com/tmo1/sms-ie) | Import/export SMS, MMS, journal d'appels, contacts (format JSON) → base de la sauvegarde suite |
| [GrapheneOS/platform_packages_apps_Dialer](https://github.com/GrapheneOS/platform_packages_apps_Dialer) / [LineageOS Dialer](https://github.com/LineageOS/android_packages_apps_Dialer) | Code AOSP de référence (VVM, RTT, appels vidéo, conférence) |
| [jimvixx/ShortMessageSecure](https://github.com/jimvixx/ShortMessageSecure) | SMS chiffrés (successeur de Silence) |
| [mollyim/mollyim-android](https://github.com/mollyim/mollyim-android) | Inspiration UX messagerie moderne (réactions, réponses, médias) |
| [chatmail/core](https://github.com/chatmail/core) | Déjà utilisé dans Twill |

### ⚖️ Point licences (important pour décider)

- **Contacts, Dialtone, Twill = Apache-2.0** → base idéale : tu peux forker, modifier, et garder la licence de ton choix.
- **Seca, Fossify, Quik, NovaDial = GPL-3.0** → on peut s'en **inspirer** librement, mais copier leur code oblige à passer tout le projet en GPL-3.0.
- **RivoPhone, Koler** → pas de licence standard : lecture seule, **ne pas copier**.

---

## 3. Brainstorm — l'écosystème

### 3.1 Principe fondateur : Android est déjà le « bus » de données

Ne pas créer de base de données propre pour les données de base. Les trois apps lisent/écrivent les **providers système**, ce qui garantit la cohérence entre elles *et* avec toutes les autres apps :

| Donnée | Source unique | Qui écrit |
|---|---|---|
| Contacts, favoris, photo, sonnerie, « envoyer vers messagerie » | `ContactsContract` (colonnes natives `STARRED`, `CUSTOM_RINGTONE`, `SEND_TO_VOICEMAIL`) | Contacts |
| Journal d'appels | `CallLog` | Téléphone (rôle `ROLE_DIALER`) |
| SMS/MMS | `Telephony` | Messages (rôle `ROLE_SMS`) |
| Numéros bloqués | `BlockedNumberContract` (lisible/écrivable seulement par l'app Téléphone ou SMS par défaut) | Téléphone **et** Messages → **une seule liste de blocage** |
| Messagerie vocale | `VoicemailContract` | Téléphone |
| Anniversaires | `CalendarContract` | Contacts |

**Ce que les providers système ne couvrent pas** (à mettre dans un *hub partagé*) : tonalité SMS par contact, vibration par contact, SIM préférée par contact, notes d'appel, profils/catégories colorés, réglages d'apparence communs, base anti-spam, sauvegarde suite.

→ **Hub partagé** = un `ContentProvider` exposé par l'une des apps (ou les trois, avec synchro), protégé par une permission `protectionLevel="signature"` : seules tes apps signées avec la même clé y accèdent (c'est ce que fait Seca).

### 3.2 Architecture technique recommandée

```
bundle/                         (monorepo Gradle)
├── core/design        thème M3 Expressive, Google Sans Flex, MaterialShapes,
│                      avatar (couleur + forme déterministes par contact),
│                      cartes groupées, barre de recherche, bottom bar
├── core/telephony     normalisation des numéros (libphonenumber), matching
│                      « 10 derniers chiffres », formatage FR, détection OTP
├── core/contacts      lecture ContactsContract + cache lookup numéro → contact
├── core/hub           client du ContentProvider partagé (réglages, profils,
│                      spam, notes, préférences par contact)
├── core/backup        format de sauvegarde suite (contacts + appels + SMS + hub)
├── app/contacts       ← fork de jawsh207/Contacts
├── app/phone          ← fork de jawsh207/Dialtone
└── app/messages       ← fork de jawsh207/twill (ou GrapheneOS Messaging)
```

- **Même clé de signature** pour les 3 apps (obligatoire pour le hub `signature`).
- **Navigation inter-apps par intents explicites** (`ACTION_DIAL`, `ACTION_SENDTO smsto:`, `QUICK_CONTACT`) + deep links internes pour aller directement à un écran (ex. « ouvrir la conversation de X »).
- **Une barre de navigation commune optionnelle** : les 3 apps affichent les mêmes onglets Contacts / Téléphone / Messages et sautent de l'une à l'autre (comme Seca : « l'ordre des 3 apps en bas »). Option alternative : **une seule app** qui détient les 2 rôles — Android le permet, mais 3 APK restent plus propres (permissions minimales, pas d'INTERNET pour Contacts/Téléphone).

### 3.3 Fonctionnalités transversales (le cœur « écosystème »)

| # | Fonction | Ce que ça donne | Inspiré de |
|---|---|---|---|
| 1 | **Identité unique** | même avatar (couleur + forme), même nom, même photo dans les 3 apps | Google (couleurs), Contacts/Dialtone (déjà fait) |
| 2 | **Historique de communication unifié** dans la fiche contact | onglet « Historique » : appels + SMS + messagerie vocale + Nostr/Delta Chat, filtrable | Samsung Contacts (onglet Historique) |
| 3 | **Appels dans les conversations** | un appel manqué/passé apparaît en ligne dans le fil SMS de la personne | différenciant (ni Google ni Samsung ne le font bien) |
| 4 | **Liste de blocage et anti-spam unique** | bloquer depuis n'importe quelle app = bloqué partout (appels + SMS) ; base spam partagée ; plages démarchage ARCEP (0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949…) | Samsung « Smart Call », Google « Protection contre le spam », Seca |
| 5 | **Réglages par contact centralisés** dans la fiche | sonnerie, tonalité SMS, vibration, SIM préférée, envoyer vers messagerie, silencieux jusqu'à… | Samsung Contacts |
| 6 | **Réponse par message intelligente** | refuser un appel → Messages s'ouvre pré-rempli ; « Rappeler / Envoyer un SMS » depuis notif d'appel manqué | Google Phone |
| 7 | **Anniversaire → SMS programmé** | depuis Highlights : « Souhaiter » → SMS programmé pour 9 h le jour J | idée cross-app (Contacts + Twill déjà capable de programmer) |
| 8 | **Rappel de rappel** | sur un appel manqué : « Me rappeler dans 1 h / ce soir » | Secure-Dialer, Samsung |
| 9 | **Notes d'appel** | après un appel, prendre une note liée au contact (visible dans la fiche ; export vers jawsh207/Notes) | Samsung « Notes d'appel » |
| 10 | **Recherche universelle** | taper un nom/numéro dans n'importe quelle app → contacts, conversations, appels | Samsung Finder, Google |
| 11 | **Profils / catégories colorés** | Famille, Travail… avec couleur, filtrage, silence par profil, couleur des bulles par profil | Seca |
| 12 | **Apparence réglée une fois pour les 3** | Material You / palette perso, noir pur OLED, forme des avatars, taille de police | Seca, One UI |
| 13 | **Sauvegarde suite** | 1 fichier chiffré : contacts + profils + journal d'appels + SMS/MMS + réglages ; planifiée (nuit, quotidien/hebdo), rotation | Samsung Smart Switch, Seca, sms-ie |
| 14 | **Transcription locale** | messagerie vocale (déjà) **et messages vocaux MMS** via Distillery | Google (transcription), Samsung (Bixby text) |
| 15 | **Widgets partagés** | favoris (appel/SMS en 1 tap), conversations récentes, appels manqués | Google & Samsung |
| 16 | **Raccourcis de partage** (Direct Share) & raccourcis launcher | les contacts fréquents apparaissent dans la feuille de partage Android → ouvre Messages | Google Messages |

### 3.4 Par app : ce qui manque vs Google / Samsung

Légende : ✅ déjà dans le repo jawsh207 · 🟡 partiel · ❌ à faire · ⛔ impossible pour une app tierce

#### 📇 Contacts
| Fonction | Google | Samsung | État |
|---|---|---|---|
| Fusion de doublons, Fix contacts, labels | ✓ | ✓ | ✅ |
| QR « Ma carte » | ✓ | ✓ | ✅ |
| Anniversaires + calendrier | ✓ | ✓ | ✅ |
| **Corbeille 30 jours** (au lieu d'Undo 8 s) | ✓ | ✓ | ❌ |
| **Infos d'urgence / ICE** (groupe d'urgence, infos médicales sur écran verrouillé) | ✓ | ✓ | ❌ |
| Onglet **Historique** (appels + SMS) dans la fiche | — | ✓ | 🟡 (Recents globaux seulement) |
| Sonnerie / tonalité SMS / vibration par contact | 🟡 | ✓ | 🟡 (sonnerie seulement) |
| **Numérotation rapide** (1–9) attribuée depuis la fiche | — | ✓ | ❌ |
| **Scanner de carte de visite** (OCR on-device) | ✓ | ✓ | ❌ (ML Kit = Google ; alternative : Tesseract) |
| Contacts SIM : voir / importer / copier | — | ✓ | ❌ |
| « Récemment ajoutés », tri par fréquence | ✓ | ✓ | ❌ |
| Partage de contact (vCard, QR, texte) | ✓ | ✓ | ✅ |
| Gestion des comptes / stockage par défaut | ✓ | ✓ | ✅ |
| Widget contact / raccourci écran d'accueil | ✓ | ✓ | ❌ |
| Suggestions (« ajouter ce numéro souvent appelé ? ») | ✓ | — | ❌ |

#### 📞 Téléphone
| Fonction | Google | Samsung | État |
|---|---|---|---|
| Journal groupé, favoris, T9, clavier DTMF | ✓ | ✓ | ✅ |
| Messagerie vocale visuelle + transcription | ✓ | ✓ | ✅ |
| Double appel, attente, fusion, audio BT | ✓ | ✓ | ✅ |
| Réponse rapide par SMS | ✓ | ✓ | ✅ |
| **Identification de l'appelant / spam** (base locale + listes communautaires) | ✓ | ✓ | ❌ |
| **Blocage démarchage FR** (préfixes ARCEP), « inconnus en silencieux » | 🟡 | ✓ | 🟡 (blocage manuel) |
| **Numérotation rapide** | ✓ | ✓ | ❌ à vérifier |
| Fond d'écran d'appel (photo plein écran / vidéo par contact) | ✓ (Pixel) | ✓ | ❌ |
| Gestes : retourner pour couper le son, bouton power raccroche | ✓ | ✓ | ❌ |
| **Statistiques d'appels** (durée par contact, par période) | — | ✓ | ❌ |
| SIM préférée par contact | ✓ | ✓ | ❌ |
| Appels vidéo opérateur (ViLTE), RTT, indicateurs VoLTE/VoWiFi | ✓ | ✓ | ❌ (via Telecom, dépend opérateur) |
| Annonce vocale de l'appelant (TTS via Distillery/Kokoro) | ✓ | ✓ | ❌ (faisable, Distillery a le TTS !) |
| Notes d'appel | — | ✓ | ❌ |
| **Enregistrement d'appel** | ✓ (certaines régions) | ✓ | ⛔ (exige privilèges système) |
| **Filtrage d'appel IA** (Call Screen, Hold for me, Direct my call) | ✓ | ✓ (Bixby text call) | ⛔ (pas d'accès à l'audio de l'appel) |

#### 💬 Messages
| Fonction | Google | Samsung | État |
|---|---|---|---|
| SMS/MMS, groupes, bulles | ✓ | ✓ | ✅ |
| SMS programmés, envoi différé | ✓ | ✓ | ✅ |
| Aperçus de liens | ✓ | ✓ | ✅ |
| **Détection des codes OTP** : chip « Copier le code », suppression auto après 24 h | ✓ | ✓ | ❌ |
| **Catégories** (Perso / Transactions / OTP / Promos) et filtres | ✓ | ✓ | ❌ |
| **Archives, messages épinglés, favoris (étoile)** | ✓ | ✓ | 🟡 (épinglage conv. via base GrapheneOS ?) |
| Recherche avec filtres (photos, liens, lieux, par contact) | ✓ | ✓ | 🟡 |
| **Réactions SMS interopérables** (parser « A aimé “…” » envoyé par iPhone/Google) | ✓ | ✓ | ❌ |
| Réponses intelligentes / suggestions (on-device) | ✓ | ✓ | ❌ |
| Rappels de message (« me rappeler de répondre ») | ✓ (nudges) | ✓ | ❌ |
| Messages vocaux avec transcription | ✓ | ✓ | ❌ (Distillery) |
| Éditeur photo, GIF, stickers, emoji | ✓ | ✓ | 🟡 |
| Corbeille (30 jours) | — | ✓ | ❌ |
| Spam : signaler, dossier spam, filtre par mot-clé | ✓ | ✓ | ❌ |
| Couleur/thème par conversation | — | ✓ | ❌ |
| Sauvegarde/restauration SMS | ✓ (Drive) | ✓ (Smart Switch) | ❌ |
| **RCS** (accusés, saisie, gros fichiers, édition, chiffrement) | ✓ | ✓ | ⛔ (pas d'API RCS ouverte aux apps tierces) — **compensé par Nostr / Delta Chat** déjà dans Twill |

### 3.5 Interface moderne — lignes directrices communes

- **M3 Expressive partout** : `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `MaterialShapes` pour les avatars, `ShortNavigationBar`, `LoadingIndicator`, barres ondulées. (Déjà en place dans Contacts/Dialtone.)
- **Hiérarchie de surfaces identique** : pages `surfaceContainerLow`, cartes `surfaceBright`, recherche `surfaceContainerHighest`, listes « segmentées » en cartes groupées.
- **Barre de recherche persistante en haut** + bouton réglages à droite + onglets swipables en bas (pattern des 3 apps).
- **Ergonomie One UI** à emprunter : zone de lecture en haut / zone d'action en bas (« grand titre qui se replie »), atteignable au pouce.
- **Bouton d'action expressif** (FAB qui se transforme en menu) : nouveau contact / nouvel appel / nouveau message.
- **Feuille « Quick contact » commune** (bottom sheet) utilisée dans les 3 apps : appeler, SMS, vidéo, email, historique, ouvrir la fiche.
- **Écran d'appel entrant** : photo/forme de l'avatar en grand, glisser pour répondre, couleur du profil.
- **Haptique** cohérente (clavier, glissements, confirmations).
- **Accessibilité** : tailles de police dynamiques, contraste, TalkBack, mode une main.
- **Tablettes/pliables** : `ListDetailPaneScaffold` (liste + détail côte à côte).

---

## 4. Feuille de route proposée

| Phase | Contenu | Pourquoi d'abord |
|---|---|---|
| **0. Base** | Monorepo ; forks Apache de Contacts + Dialtone + Twill ; même clé ; extraire `core/design` + `core/telephony` | Tout le reste en dépend |
| **1. Unifier le look** | Aligner Twill sur le thème Contacts/Dialtone ; avatar commun ; Quick contact sheet ; barre de nav inter-apps | Effet « écosystème » immédiatement visible |
| **2. Hub partagé** | ContentProvider `signature` : réglages d'apparence, réglages par contact, profils colorés | Débloque les fonctions transversales |
| **3. Transversal** | Historique unifié dans la fiche, appels dans les fils SMS, blocage/spam unique (+ préfixes ARCEP), OTP, anniversaire → SMS | Le différenciant |
| **4. Parité native** | Corbeilles, ICE, speed dial, catégories SMS, réactions interop, notes d'appel, statistiques, widgets, Direct Share | Combler les écarts Google/Samsung |
| **5. Sauvegarde suite** | Format unique chiffré, planification, restauration sur nouveau téléphone | Indispensable avant une diffusion publique |
| **6. Bonus** | Annonce de l'appelant (TTS), transcription des messages vocaux, OCR carte de visite, Wear OS | Confort |

---

## 5. Décisions à prendre

1. **Fork des apps jawsh207 (Apache) ou partir de zéro** en s'inspirant de Seca ? → recommandation : **forker** (≈80 % du travail déjà fait, même design, licence libre).
2. **3 APK séparés ou 1 seule app** ? → recommandation : **3 APK + hub signature** (permissions minimales, Contacts/Téléphone sans INTERNET).
3. **Garder Nostr / Delta Chat dans Messages** ? → c'est la seule façon d'avoir des fonctions « type RCS » (accusés, réactions, chiffrement) sans Google.
4. **Licence finale** : Apache-2.0 (permet d'intégrer à des projets fermés) ou GPL-3.0 (permet de reprendre du code Seca/Fossify/Quik) ?
5. **Cible** : GrapheneOS / ROM sans Google, ou aussi téléphones Samsung/Pixel standards (impact sur VVM, spam, RCS) ?
