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

## Recovery (Stage 5)
A daily check-in (sleep, soreness, stress, energy, optional resting heart rate) gives a readiness score from your own answers. On a low day you can choose to ease today's session; nothing is ever pushed onto other days. Training load compares this week with your usual.

## Nutrition and body composition (Stage 6)
Estimated calorie and macro targets from your profile and goal, a manual food log (you enter the numbers, no made-up food database), and body measurements with trends plus a tape-measure body fat estimate.

## AI coach (Stage 7)
Chat with a coach that sees only what you have logged. Uses your own Anthropic API key, typed into the coach screen and stored only on the phone (not in backups, not in the code). It can propose changes to upcoming sessions; nothing happens until you tap Apply.

## Weather and Health Connect (Stage 8)
Forecast for planned runs from Open-Meteo (free, no key; only the town you choose is used). The daily check-in can fill sleep and resting heart rate from Android Health Connect when you tap the button; read-only, and you can edit the numbers.

## Reminders (Stage 9)
Optional daily training reminder (only when a session is planned) and check-in reminder (only if not done). Set in Profile. Works offline; Android may deliver a few minutes late.

## Not built yet
reminders, HYROX station time logging, wearable integrations.

## Stage 10: exercise demos
Exercise detail now has a "Watch a video demo" button (opens a YouTube form-tutorial search) and, when online, a picture from wger.de (free, CC-BY-SA). If offline or no match, the step-by-step cues still show.

## Stage 11: HYROX splits
Log race, simulation or partial results (16 segments: 8 runs and 8 stations) per division. The app predicts your finish from your last 3 times on each segment, flags run fade and your biggest stations. Partial-session times are lengthened 5% (runs) and 8% (stations) as a rough fatigue allowance. Database is now version 6.
