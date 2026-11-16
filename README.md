# Forge (Stage 2)

Offline Android fitness app: onboarding, profile, equipment, calendar, gym and run logging, progress charts, backup/restore.

## Get the APK (no Android Studio needed)
1. Create a new GitHub repo (e.g. `forge`).
2. Upload everything in this folder to it (include the hidden `.github` folder), on branch `main`.
3. Repo > Actions > "Build Forge APK" > wait for green tick > open the run > Artifacts > download `Forge-APK`.
4. Unzip, copy the .apk to your phone, tap it, allow "install unknown apps" if asked.

## Updating
Edit the code, push to `main`, download the new APK and install over the old one. The fixed signing key keeps your data.

## Android Studio
Open this folder, let Gradle sync, press Run.

## Not built yet
Programme generators (HYROX, hypertrophy, strength, running, hybrid), recovery, nutrition, AI coach, reminders, wearable integrations.
