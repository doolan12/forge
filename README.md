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

## Programme builder (Stage 4)
Build gym (hypertrophy or strength), running and HYROX plans, alone or combined. Running paces come from your own best race time (Daniels VDOT). HYROX sessions use the standard division race weights. A scheduler places heavy leg days, the long run and hard sessions apart, and gives your main focus the freshest days. Add a race date to get a taper and the event on your calendar.

## Not built yet
Recovery and readiness, nutrition, AI coach, reminders, HYROX station time logging, wearable integrations.
