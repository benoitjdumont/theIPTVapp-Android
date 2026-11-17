# theIPTVapp — Android & Google TV

Lecteur IPTV pour **téléphone Android** et **Google TV / Android TV**, version mobile de theIPTVapp (Windows).
Une seule application : tactile sur téléphone, télécommande (flèches, OK, Retour) sur la télé.

*Créé par Benoît Dumont à l'aide de l'IA — Studio Gyro.* theIPTVapp est un lecteur : il ne fournit aucune chaîne.

## Fonctions
- Sources **M3U** (adresse web) et **Xtream Codes** : plusieurs listes, que l'on peut **modifier** à tout moment.
- **TV, Films, Séries** : colonne de gauche (noms au départ, icônes une fois un choix fait), catégories, puis liste ou murs d'affiches en plein écran ; poignée sur le bord gauche pour rouvrir les colonnes.
- **Ma liste** : favoris de la TV, des films et des séries, chacun dans sa propre catégorie. **Enregistrements** : accès direct depuis la colonne.
- **Recherche** dans sa propre fenêtre : TV, Films et Séries à la fois, avec l'endroit où chaque résultat a été trouvé.
- **Lecteur** Media3/ExoPlayer (HLS, TS, AC-3, HEVC selon l'appareil) : pause et enregistrement au centre, historique des chaînes, menu d'options (image dans l'image, résolution, sous-titres, audio, minuterie…) accessible à la télécommande comme au toucher.
- **Guide EPG** (Xtream) en grille ; reprise de lecture des films et épisodes ; saisons et épisodes.
- **Enregistrement** des chaînes en direct.
- **Préférences** dans un panneau qui glisse par-dessus l'interface : apparence (taille du texte, couleur, image de fond, transparence réglable à la manette), groupes (masquer ou verrouiller), réseau (User-Agent, proxy), sauvegarde, contrôle parental, mises à jour.
- Plein écran bord à bord, téléphone (tactile) et Google TV (télécommande).
- Essais automatiques de lecture : `.m3u8`, puis `.ts`, puis HLS forcé.

## Installer
Onglet **Actions** → dernière exécution verte → artefact **theIPTVapp-android-apk** → décompresser → installer `app-debug.apk`
(autoriser « sources inconnues » ; sur Google TV, passer par une clé USB ou une application de transfert de fichiers).

## Construire soi-même
Android Studio (ouvrir le dossier) ou `gradle assembleDebug` (Gradle 8.10, JDK 17, Android SDK 35).

## Mises à jour
L'appli vérifie les nouvelles versions (GitHub Releases) au démarrage ; Préférences → Mises à jour pour vérifier à la main.
