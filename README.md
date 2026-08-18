<div align="center">
  <img src="docs/images/anilord-crest.webp" alt="Anilord Logo" width="140" />Anilord

An open-source Android client for Manga, Anime, and Novels.

"Android 6.0+" (https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)
"Kotlin" (https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
"License" (https://img.shields.io/badge/License-GPL--3.0-blue)

</div>About

Anilord is an open-source Android application that brings Manga, Anime, and Novels together in a single modern interface.

It supports multiple sources, search, favorites, reading and watch history, downloads for offline use, customizable reading, and built-in anime playback.

The project is based on "Kotatsu" (https://github.com/KotatsuApp/Kotatsu) and includes the parser source used by the application inside this repository to make building and reviewing the project easier.

Features

- Browse and search Manga, Anime, and Novels from multiple sources.
- Customizable Manga and Novel reader with Webtoon support.
- Built-in Anime player with multiple servers and support for HLS, MP4, and MKV.
- Download chapters and episodes for offline reading and watching.
- Favorites, categories, history, bookmarks, and update notifications.
- Material You interface optimized for phones and tablets.
- Completely ad-free.
- Optional synchronization, backup, and tracking integrations.
- Supports Android 6.0 and later.

Project Structure

Path| Description
"app/"| Android application, tests, and resources
"kotatsu-parsers/"| Parser sources used by the application
"gradle/"| Gradle Wrapper and version catalogs
"docs/images/"| Anilord project images and branding

Build Requirements

- Recent Android Studio.
- JDK 17.
- Android SDK 36.
- Android Build Tools 35.0.0.
- Internet connection during the first build to download dependencies.

Build the Development Version

git clone https://github.com/lo-oord/Anilord.git
cd Anilord
./gradlew :app:assembleDebug

On Windows:

.\gradlew.bat :app:assembleDebug

Android Studio will normally generate "local.properties" automatically with the local Android SDK path.

Private Configuration

The repository does not contain private signing keys, production Firebase configuration, bot tokens, crash-reporting credentials, or private OAuth secrets.

To enable Firebase, download your "google-services.json" from your Firebase project and place it inside "app/". This file is intentionally ignored by Git.

Private integrations and services that require credentials remain disabled until you provide your own configuration locally.

Do not upload any of the following to GitHub:

- "local.properties"
- "google-services.json"
- Keystore files
- Signing passwords
- API keys or private credentials

Build a Release

After configuring your local environment and signing credentials:

./gradlew :app:bundleRelease

The repository does not include a signing key or its password. Google Play signing configuration must remain outside the repository.

Contributing

Contributions and bug reports are welcome.

When reporting a source-related issue, include the source name, relevant URL, and the steps required to reproduce the problem.

Do not include login credentials, private tokens, or other sensitive information in issues or pull requests.

License & Attribution

This project is licensed under the "GNU General Public License v3.0" (LICENSE).

Anilord is based on "Kotatsu" (https://github.com/KotatsuApp/Kotatsu) and uses the "kotatsu-parsers" (https://github.com/KotatsuApp/kotatsu-parsers) project.

Copyright notices, attribution, and license requirements of the original projects must be preserved in accordance with the GPL-3.0 license.

See "NOTICE.md" (NOTICE.md) for additional attribution information.

Disclaimer

Anilord does not host Manga, Anime, or Novel content and is not affiliated with the third-party websites or services accessible through the application.

Content is provided by external sources available on the web. These sources may change, become unavailable, or operate under their own terms and policies.

Users and distributors are responsible for complying with applicable laws and third-party terms of service in their respective jurisdictions.
