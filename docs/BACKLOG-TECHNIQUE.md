# Backlog technique — audit du code (27/11/2026)

Ce document reprend les recommandations de l'audit fonctionnel (et de `BRAINSTORM-ECOSYSTEME.md`)
et les **vérifie dans le code**. Pour chacune, il indique les fichiers à modifier ou à créer.
Les chemins sont relatifs à la racine du dépôt.

Préfixes utilisés :

- `C/` = `apps/contacts/app/src/main/java/app/people/contacts/`
- `P/` = `apps/phone/app/src/main/java/app/dialtone/phone/`
- `M/` = `apps/messages/src/com/android/messaging/`

## 0. État réel du code

| Module | Taille | Dépend de |
|---|---|---|
| `core/appearance` | 10 fichiers, 1 500 lignes | — (thème partagé, langue, icônes, mises à jour) |
| `core/design` | 5 fichiers, 360 lignes | — (thème, avatars, segments) |
| `core/sync` | 6 fichiers + 6 tests | — (Nextcloud chiffré) |
| `core/telephony` | 1 fichier + 1 test | — (comparaison des numéros) |
| Contacts | 69 fichiers Kotlin, 9 tests | design, telephony, sync, appearance |
| Appel | 51 Kotlin + 138 Java (VVM AOSP), 4 tests | design, telephony, appearance |
| SMS | ~1 500 fichiers, 398 tests | **seulement** `core/appearance` (sources incluses) et les images d'avatars |

### L'audit contre le code

| Affirmation de l'audit | Dans le code | Preuve |
|---|---|---|
| SMS : conversations épinglées | ✅ présent | `M/…/ConversationPinStore.kt` |
| SMS : archivage | ✅ présent (« à ajouter » dans l'audit) | `ConversationArchiveEvents.kt`, `ConversationsRepository.kt` |
| SMS : messages vocaux | ✅ enregistrement présent | `ConversationAudioRecordingDelegate.kt` |
| SMS : recherche | 🟡 texte seulement, pas de filtres par type | `M/twill/SmsMessageSearch.kt`, `M/ui/twill/search/` |
| SMS : réactions | 🟡 Nostr / Delta Chat seulement, pas les réactions SMS d'iPhone / Google | `nostr/NostrEvent.kt`, `DeltaChatProtocol.kt` |
| SMS : OTP, catégories, corbeille, favoris, sauvegarde | ❌ absent | aucune occurrence |
| SMS : blocage | ✅ liste système | `BlockedParticipantsRepository.kt` |
| SMS : widget | 🟡 widget AOSP d'origine (Views) | `BugleWidgetProvider.kt` |
| Appel : speed dial, statistiques, anti-spam, préfixes ARCEP, notes, widget | ❌ absent | aucune occurrence |
| Appel : blocage | ✅ `BlockedNumberContract` | `P/screening/Blocking.kt` |
| Contacts : historique appels + SMS | 🟡 SMS seulement (pas MMS), pas la messagerie vocale ni Nostr, 60 entrées max | `C/data/ContactTimeline.kt` |
| Contacts : corbeille, ICE, contacts SIM, « fréquents », widget, OCR | ❌ absent | aucune occurrence |
| Contacts : sonnerie par contact | 🟡 lecture seulement | `C/data/ContactsRepository.kt` |
| « Contacts et Appel sans Internet » | ⚠️ **faux aujourd'hui** | `INTERNET` déclaré par `core/appearance` (mises à jour) et par Contacts (synchro) |

## 1. Dette à régler avant toute nouvelle fonction

| # | Tâche | Fichiers | Taille |
|---|---|---|---|
| D1 | ~~Avatars en double~~ **Sans objet** : la copie de SMS (`M/ui/common/components/participant/CartoonAvatarArt.kt`) est générée par le même script que celle de `core/design` (`core/design/avatars/generate.py`). Les deux ne peuvent pas diverger. Inclure les sources de `core/design` dans SMS casserait les `R` (espaces de noms différents). | — | — |
| D2 | ~~Numéros en double~~ **À faire avec T2 seulement** : le `PhoneNumberFormatter` de SMS sert à l'affichage et à la saisie (code AOSP), pas à comparer des numéros entre apps. Quand SMS lira le journal d'appels (T2), compiler `core/telephony` dans SMS (`kotlin.srcDir`) et utiliser `numberKey()`. | `apps/messages/app/build.gradle.kts`, `core/telephony/.../PhoneNumbers.kt` | S |
| D3 | **CI** ✅ fait : `.github/workflows/tests.yml` lance les tests unitaires de core, Contacts, Appel et SMS. Les workflows de `apps/*/.github/` viennent des dépôts d'origine et ne s'exécutent pas. | `.github/workflows/tests.yml` | S |
| D4 | **Permission Internet** : sortir `Updater`/`UpdateScreen` de `core/appearance` vers un module `core/update`, optionnel par app ou par variante (`foss` sans mise à jour intégrée). Sinon, corriger le README et ne plus promettre « sans Internet ». | `core/appearance/src/main/AndroidManifest.xml`, `Updater.kt`, `UpdateScreen.kt` | M |
| D5 | Peu de tests dans Contacts (9) et Appel (4) : en ajouter avant de refactorer `ContactTimeline`, `CallLogRepository` et `Blocking`. | `apps/*/app/src/test/` | M |

## 2. Phase 1 — Identité commune

| # | Tâche | Fichiers | Taille |
|---|---|---|---|
| I1 | SMS utilise `BundleTheme` (core/design) au lieu de son propre `MaterialExpressiveTheme`. | `M/ui/core/Theme.kt`, `core/design/.../BundleTheme.kt` | M |
| I2 | Barre « Contacts · Appel · SMS » commune : un composable `BundleAppSwitcher` dans `core/design`. Il lance les autres apps par `PackageManager.getLaunchIntentForPackage("${prefix}.contacts")` (préfixe tiré de `gradle.properties`, via `BuildConfig`). | nouveau `core/design/.../AppSwitcher.kt`, `C/ui/components/BottomBar.kt`, `P/ui/components/BottomBar.kt`, la barre de `M/ui/navigation/` | M |
| I3 | **Feuille « contact rapide » commune** (appeler, SMS, historique, fiche) dans `core/design`, utilisée par le journal d'appels et par le fil SMS. | nouveau `core/design/.../QuickContactSheet.kt`, `P/ui/home/HomeScreen.kt`, `M/ui/conversation/screen/` | M |
| I4 | Composants de liste partagés (`Segments.kt` existe) : brancher `C/ui/components/Rows.kt`, `P/ui/components/Components.kt` et `M/ui/common/components/` dessus. | les trois | M |

## 3. Phase 2 — Fonctions transversales

### T1. Hub partagé (prérequis de T3 à T5) — ✅ fait (27/11/2026)

`core/hub` est en place : `HubProvider` dans Contacts (contrôle de signature), `Hub` côté client. Il garde la SIM préférée et les notes par numéro, et la couleur des libellés. Utilisé par Appel (menu du journal : « Ajouter une note », « SIM pour ce numéro » ; la SIM choisie sert à l'appel) et par Contacts (section Notes sur la fiche, couleur dans l'écran d'un libellé). Vérifié sur l'émulateur : une note écrite dans Appel s'affiche sur la fiche Contacts. SMS ne l'utilise pas encore.

Plan d'origine :

`AppearanceSyncProvider` vérifie déjà la signature de l'appelant (`checkSignatures`), mais ne
transporte que l'apparence. Créer un module **`core/hub`** :

- `HubProvider` exposé par **Contacts** (l'app propriétaire de l'identité) : tables `contact_prefs`
  (clé = `LOOKUP_KEY`), `profiles`, `spam_numbers`, `call_notes`. Stockage Room ou SQLite simple.
- `HubClient`, utilisé par Appel et SMS (même contrôle de signature, cache en mémoire et
  `ContentObserver`).
- À compiler dans SMS comme `core/appearance` (`kotlin.srcDir`).

Fichiers : nouveau `core/hub/`, `settings.gradle.kts`, `apps/contacts/app/src/main/AndroidManifest.xml`.
Taille : L.

### Autres fonctions transversales

| # | Tâche | Fichiers | Taille |
|---|---|---|---|
| T2 | **Historique unifié** : ajouter MMS (`Telephony.Mms` + `addr`), messagerie vocale (`VoicemailContract`) et pagination à la place de `limit = 60`. Ensuite, afficher les appels dans le fil SMS (lecture de `CallLog`, permission `READ_CALL_LOG` à ajouter à SMS). | `C/data/ContactTimeline.kt`, `C/ui/contact/TimelineSection.kt` ; côté SMS, une nouvelle source dans `M/data/conversation/` | M + L |
| T3 | **Réglages par contact** (tonalité SMS, vibration, SIM préférée, silencieux jusqu'à…) : écran dans la fiche, stocké dans le hub. La sonnerie reste dans `CUSTOM_RINGTONE` (à rendre modifiable). Appel lit la SIM préférée dans `Dialer.kt`, SMS lit la tonalité dans ses notifications. | `C/ui/contact/ContactSections.kt`, `C/data/ContactWriter.kt`, `P/telecom/Dialer.kt`, `M/…/TwillNotifications.kt` | M |
| T4 | **Blocage et anti-spam unifiés** : la liste système est déjà partagée. Ajouter dans `core/telephony` une liste des préfixes de démarchage ARCEP (`SpamRules.kt`) et l'appliquer dans le `CallScreeningService`, avec les signalements locaux stockés dans le hub. | `P/screening/SendToVoicemailScreeningService.kt`, `P/screening/Blocking.kt`, nouveau `core/telephony/.../SpamRules.kt`, `M/…/BlockedParticipantsRepository.kt` | M |
| T5 | **Profils colorés** (Famille, Travail…) : s'appuyer sur les libellés existants (`C/data/Labels.kt`, groupes `ContactsContract`) et y associer une couleur dans le hub, plutôt que créer un second système de groupes. | `C/data/Labels.kt`, `C/ui/organize/LabelScreen.kt`, hub | M |
| T6 | Recherche universelle : à repousser. Chaque app garde sa recherche, et les résultats des autres apps sont proposés par un lien (intent) en bas de la liste. | `C/ui/search/SearchScreen.kt`, `P/ui/keypad/`, `M/ui/twill/search/` | M |

## 4. Phase 3 — Fonctions par app

| # | App | Tâche | Fichiers | Taille |
|---|---|---|---|---|
| A1 | SMS | **OTP** : détecter le code par regex dans `core/telephony` (`Otp.kt`, testé sur la JVM), avec un bouton « Copier » dans la notification et une puce dans la bulle. Suppression automatique optionnelle. | nouveau `core/telephony/.../Otp.kt`, `M/…/TwillNotifications.kt`, `M/ui/conversation/messages/ui/message/` | M |
| A2 | SMS | **Catégories** (Perso, OTP, Transactions, Promos) calculées en local, en filtres (chips) dans la liste des conversations. | `M/ui/conversationlist/`, `ConversationListRepository.kt` | L |
| A3 | SMS | **Réactions iPhone / Google** : reconnaître « A aimé “…” » / « Liked “…” » et les afficher comme réaction sur le message cité. | `M/ui/conversation/messages/` (mapper), nouveau parseur testé | M |
| A4 | SMS | Filtres de recherche (photos, liens, par contact). | `M/twill/SmsMessageSearch.kt`, `TwillInboxSearch.kt` | M |
| A5 | SMS / Contacts | **Corbeille 30 jours** : SMS, copie dans une table locale avant suppression ; Contacts, export vCard dans le stockage interne de l'app au lieu de l'Undo de 8 s. | `M/datamodel/action/` (suppression), `C/data/ContactWriter.kt`, `C/ui/ContactsViewModel.kt` | M |
| A6 | Appel | **Speed dial 1–9** : appui long sur un chiffre ; le stockage passe par le hub. | `P/ui/components/DialPad.kt`, `P/ui/keypad/KeypadScreen.kt` | S |
| A7 | Appel | **Statistiques** calculées depuis `CallLog` : nouvel écran dans les paramètres. | `P/data/CallLogRepository.kt`, nouveau `P/ui/stats/StatsScreen.kt` | M |
| A8 | Appel | **Rappel de rappel** sur la notification d'appel manqué (AlarmManager). | `P/telecom/MissedCalls.kt`, `P/telecom/CallActionReceiver.kt` | S |
| A9 | Appel | **Notes d'appel** stockées dans le hub, affichées dans la fiche contact. | `P/incall/InCallScreen.kt`, `C/ui/contact/TimelineSection.kt` | M |
| A10 | Contacts | **ICE** : un libellé « Urgence » et les infos médicales. L'accès depuis l'écran verrouillé reste limité pour une app tierce : à vérifier sur l'appareil. | `C/data/Labels.kt`, `C/ui/contact/`, `C/ui/highlights/` | M |
| A11 | Contacts | **Highlights** : ajouter « Récemment ajoutés » (`CONTACT_LAST_UPDATED_TIMESTAMP`) et « Incomplets » (réutiliser `Fixes.kt`). | `C/ui/highlights/HighlightsTab.kt`, `C/data/Fixes.kt` | S |
| A12 | Les trois | **Widgets** Glance (favoris, appels manqués, conversations). Le widget AOSP de SMS est remplacé. | nouveaux `*/widget/`, `M/…/BugleWidgetProvider.kt` | L |

## 5. Phase 4 — Sauvegarde et confidentialité

| # | Tâche | Fichiers | Taille |
|---|---|---|---|
| B1 | **Sauvegarde complète** : `core/sync` sait déjà chiffrer (`Crypto.kt`, `Vault.kt`) et écrire en WebDAV (`WebDav.kt`). Il reste à ajouter un format d'archive `core/backup` (contacts vCard + journal d'appels + SMS/MMS en JSON, inspiré de sms-ie, + hub), où chaque app exporte sa partie via le hub. | nouveau `core/backup/`, réutilise `core/sync/Crypto.kt` | L |
| B2 | Restauration sur un nouveau téléphone (ordre : Contacts → hub → SMS, qui doit être l'app SMS par défaut). | `core/backup/`, écrans de restauration | L |
| B3 | Photos dans la synchro Nextcloud (non synchronisées aujourd'hui, cf. README). | `core/sync/Model.kt`, `C/sync/RawContactStore.kt` | M |
| B4 | OCR des cartes de visite : sans ML Kit (dépendance Google), Tesseract est lourd. Priorité basse. | — | L |

## 6. Ordre conseillé

1. **D1 → D3** (petits travaux qui évitent de diverger encore).
2. **I1 + I2** (effet « écosystème » visible).
3. **T1 hub**, puis **T3 / T4 / T5** qui en dépendent.
4. **T2 historique**, **A1 OTP**, **A6 speed dial**, **A8 rappel** (bonne valeur pour peu d'effort).
5. Le reste de la phase 3, puis **B1 / B2**.

D4 (Internet) est une décision produit : soit on la prend avant la publication, soit on retire la
promesse « sans Internet ».

Limites de cet audit : il repose sur la lecture et la recherche dans le code (grep). Je n'ai rien
compilé, et aucune fonction n'a été essayée sur l'émulateur. Une fonction marquée « absent » a été
cherchée par mots-clés : elle peut exister sous un autre nom.
