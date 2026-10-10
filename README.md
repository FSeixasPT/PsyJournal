# PsyJournal

PsyJournal is an Android app to make recreational drug users safer. The aim is to provide features that are attractive to users as well as useful from a harm-reduction perspective.
This app is built natively with [Jetpack Compose](https://developer.android.com/jetpack/compose).

![A presentation of the App](metadata/en-US/images/Google%20Pixel%204%20XL%20Presentation.png)

## Origin and credits

PsyJournal is a fork and continuation of [PsychonautWiki Journal](https://github.com/isaakhanimann/psychonautwiki-journal-android), created by **Isaak Hanimann**.
The original repository was archived after its last open-source (GPL-3.0) release, and later versions of the official app are closed-source.
PsyJournal builds on that last open-source release and is not affiliated with or endorsed by the original author.

Huge thanks to Isaak Hanimann and to everyone who contributed to the original project. The full commit history of the original project is preserved in this repository.

## Building and verifying releases

Release APKs on the [releases page](https://github.com/FSeixasPT/PsyJournal/releases) are signed with this certificate:

    SHA-256: B9:DA:7A:E5:F0:0C:C2:32:23:F4:58:20:83:5F:94:A3:26:14:9D:63:45:59:79:92:CD:66:01:EF:44:B7:04:8F

From 12.1 on, the build is reproducible: building a release tag from source gives the same APK as the published one, apart from the signature.

1. Use JDK 21. The Gradle wrapper pins Gradle 8.13, and the Android Gradle Plugin downloads the SDK platform and build tools it needs.
2. Check out the release tag, for example `git checkout v12.1`.
3. Build an unsigned release APK (the blank store file turns signing off):

       ./gradlew assembleRelease -PPSYJOURNAL_STORE_FILE=

4. Compare `app/build/outputs/apk/release/app-release-unsigned.apk` with the published APK, for example with [apksigcopier](https://github.com/obfusk/apksigcopier) `compare`, which ignores the signature.

The GitHub "Build and test" check prints the checksum of the unsigned release APK for every commit on `main`.

## License

    Copyright (C) 2022 Isaak Hanimann.
    Copyright (C) 2026 Francisco Seixas.

    This file is part of PsyJournal, a fork of PsychonautWiki Journal.

    PsyJournal is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or (at
    your option) any later version.

    PsyJournal is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with PsyJournal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.

The full license text is in [COPYING](COPYING).
