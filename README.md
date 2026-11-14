# theIPTVapp — Android & Google TV

Lecteur IPTV pour **téléphone Android** et **Google TV / Android TV**, version mobile de theIPTVapp (Windows).
Une seule application : tactile sur téléphone, télécommande (flèches, OK, Retour) sur la télé.

*Créé par Benoît Dumont à l'aide de l'IA — Studio Gyro.* theIPTVapp est un lecteur : il ne fournit aucune chaîne.

## Fonctions (version 0.1.x)
- Sources **M3U** (adresse web) et **Xtream Codes**, plusieurs sources possibles.
- Catégories (colonne à gauche sur grand écran / puces sur téléphone), recherche, favoris.
- Lecteur Media3/ExoPlayer : HLS et TS, AC-3 et HEVC décodés par l'appareil quand il en est capable.
- Lecture : ↑/↓ (ou touches chaîne +/−) pour changer de chaîne, OK pour afficher/masquer les infos, Retour pour revenir à la liste.
- **Sous-menus** de catégories (« FR | Sports » → préfixe FR, puis sous-catégorie).
- **Guide EPG** (Xtream) : programme en cours et suivant, dans la liste et pendant la lecture.
- **Films et séries** (compte Xtream) : murs d'affiches, mêmes sous-menus, saisons et épisodes ; flèches gauche/droite = ±15 s.
- **Reprise de lecture** des films et épisodes (position mémorisée).
- **Contrôle parental** : code + mots à verrouiller (nom ou catégorie), déverrouillage par code.
- Écran toujours à l'horizontale (téléphone et TV).
- Contenu respectant les barres du téléphone (batterie, heure).
- Essais automatiques : `.m3u8`, puis `.ts` (Xtream), puis HLS forcé.

## À venir
Guide EPG pour les listes M3U (XMLTV), enregistrements, films et séries avec affiches, reprise de lecture, enregistrements, contrôle parental, sous-menus de catégories (« FR | … »).

## Installer
Onglet **Actions** → dernière exécution verte → artefact **theIPTVapp-android-apk** → décompresser → installer `app-debug.apk`
(autoriser « sources inconnues » ; sur Google TV, passer par une clé USB ou une application de transfert de fichiers).

## Construire soi-même
Android Studio (ouvrir le dossier) ou `gradle assembleDebug` (Gradle 8.10, JDK 17, Android SDK 35).

> ⚠️ Cette première version n'a pas encore été compilée ni testée sur un appareil : les premiers essais serviront à corriger les erreurs.

## Mises à jour
L'appli vérifie les nouvelles versions (GitHub Releases) au démarrage ; Réglages → Mises à jour pour vérifier à la main.
