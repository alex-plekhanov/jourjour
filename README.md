# JourJour — your travelling companion

*[Русская версия](README.ru.md)*

**JourJour** — *journey journal* — is an Android app for planning a trip and keeping track
of it while you are on it. It needs no internet connection to work, has no ads, no accounts
and no tracking, and its currencies, payment methods and expense categories adapt to the
way you travel.

---

## What it does

- **Planning a budget.** What the trip is expected to cost, line by line under the category
  it belongs to, beside what has actually gone on it — in the currencies each was written
  in, and as one figure in yours.

- **Putting together a schedule.** What happens and when, day by day: a flight with the
  clocks moving under it, a transfer that begins where the flight ends, two plans claiming
  the same hours, and what each of them is expected to cost.

- **Tracking what a trip costs.** Entries in any number of currencies, receipt photos,
  coordinates of where the money went, export and import for anything else you keep records
  in, and statistics on your spending with charts.

- **Reconciling wallet balances and turnover.** What each wallet holds now, what the last
  recount found, and what the bank actually settled a card payment at.

- **Keeping checklists.** What to do before leaving, section by section, copied from one
  trip to the next.

---

## Building

**You will need** JDK 17 or newer, the Android SDK with platform 36, and nothing else —
Gradle fetches the rest.

```bash
git clone https://github.com/alex-plekhanov/jourjour.git
cd jourjour

./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew installDebug           # onto a connected device or emulator

./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests; needs a device
```

`assembleRelease` produces an **unsigned** APK: the repository carries no keystore and no
signing configuration, so a signed build needs your own.

The app is written in Kotlin with Jetpack Compose, Room, Hilt and Navigation Compose.
`DESIGN.md` states what it has to do and why the code is arranged the way it is — read that
before changing anything.

---

## Licence

Apache License 2.0. See [LICENSE](LICENSE) for the full text.

---

## Copyright

© 2026 Alexey Plekhanov

Most of the code was written with the help of AI.
