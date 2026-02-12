# Google Play Release Runbook (Connect Printer)

This runbook is for publishing this app to Google Play in 2026.

## 1) Confirm policy baseline (current)

- Target API level requirement:
  - New apps and updates must target Android 15 (API 35) since August 31, 2025.
  - Extension deadline was November 1, 2025.
  - Source: https://support.google.com/googleplay/android-developer/answer/11926878
- Upload format:
  - New apps must be uploaded as Android App Bundles (`.aab`).
  - Source: https://developer.android.com/guide/app-bundle

## 2) One-time local setup

1. Create a release keystore (keep this safe forever):
```bash
keytool -genkeypair -v \
  -keystore release-keystore.jks \
  -alias connectprinter \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```
2. Create `keystore.properties`:
```bash
cp keystore.properties.template keystore.properties
```
3. Edit `keystore.properties` with your real keystore path/passwords.

## 3) Build the signed Play bundle

```bash
./gradlew clean bundleRelease
```

Output:
- `app/build/outputs/bundle/release/app-release.aab`

If `keystore.properties` is missing, release signing falls back to default behavior and will not produce a Play-uploadable final signed bundle. Always provide the keystore for production builds.

## 4) Create the Play Console app

1. Create app in Play Console.
2. Enroll in Play App Signing (recommended and standard flow).
3. Upload `app-release.aab` to `Internal testing`.
4. Add testers and run a validation cycle.

If your developer account type requires testing gates before production, complete those first in Console:
- https://support.google.com/googleplay/android-developer/answer/14151465

## 5) Complete mandatory Store listing + App content

1. Store listing:
   - App name
   - Short description
   - Full description
   - Phone screenshots
   - 512x512 app icon
   - Feature graphic
   - Source: https://developer.android.com/distribute/google-play/resources/icon-design-specifications
2. App content forms:
   - Privacy policy URL
   - Data safety
   - App access (if applicable)
   - Content rating questionnaire
   - Ads declaration
   - Government apps declaration (if applicable)
3. Sensitive permissions:
   - This app uses Bluetooth and Wi-Fi related permissions.
   - Ensure declarations match real behavior in app and in policy text.

## 6) Privacy policy hosting

- Use `PRIVACY_POLICY.md` as your baseline.
- Host it at a public HTTPS URL (for example GitHub Pages) and paste that URL into Play Console.

## 7) Release checklist before production

1. Internal test build installs and runs on at least one Android 12+ device and one Android 10/11 device.
2. Bluetooth scan and printer configuration flow works end-to-end.
3. No crashes in Play pre-launch report.
4. Data safety answers match runtime behavior.
5. Version bump for every new upload (`versionCode` must always increase).

## 8) Rollout

1. Create Production release from tested artifact.
2. Use staged rollout (for example 10% -> 50% -> 100%).
3. Monitor Android vitals and crash reports for 24-48 hours.
