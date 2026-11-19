# Reusable Claro demo kit

Use this kit when asked to install Claro with demo assets, prepare a demo device
or refresh store screenshots. Reuse these fictional people and existing artwork;
do not generate replacement portraits unless requested.

## Saved assets

| Path, relative to the repository | Purpose |
| --- | --- |
| `marketing/store/demo/contacts.json` | Fictional names, reserved phone numbers and ordered Home tile recipe |
| `marketing/store/demo/portraits/` | Six AI-generated contact portraits |
| `marketing/store/demo/claro-fictional-contacts.vcf` | Importable contacts with embedded photos and stable fictional UIDs |
| `marketing/store/demo/generated-portrait-sheet.png` | Original saved portrait grid for regenerating the contact assets |
| `marketing/store/demo/provenance.json` | Portrait provenance and fictional-data designation |
| `marketing/store/demo/demo-contacts-review.png` | Visual contact reference, not a Play screenshot |
| `marketing/store/captures/` | Reviewed native captures, source hashes and in-app preview provenance |
| `marketing/store/assets/` | Selected logo, app icon and feature graphic |
| `marketing/store/en-US/listing.json` | Reusable English store copy |
| `marketing/store/screenshot-templates/templates.json` | Editable store screenshot composition recipe |
| `marketing/store/screenshots/` | Clean phone screenshot exports and their source/output hashes |

The capture directory has a narrow Git ignore exception for the reviewed source
files and manifests. Unreviewed device screenshots remain ignored. Build output,
keystores, service-account credentials, emulator snapshots and account data are
not part of this kit.

## Future demo installation

1. Select the existing designated test emulator explicitly. Preserve its app and
   account data; do not wipe it, create a duplicate AVD or target a personal phone.
   The previous demo used the existing Pixel 9 Pro emulator, but verify the live
   device serial instead of assuming it is still `emulator-5554`.
2. Build the requested checkout as an isolated local-test debug app using the
   repository's Android toolchain:

   ```sh
   ./gradlew -Pclaro.localInstrumentation=true \
     -Pclaro.telemetryEnvironment=staging :app:assembleDebug
   ```

   This selects `dev.hollander.claro.localtest`, separate from
   `dev.hollander.claro`. Do not substitute a release AAB, fold unrelated changes
   into an approved baseline or select production telemetry. This command builds
   only; run tests when requested.
3. After the user requests installation on the designated emulator, install the
   APK without clearing data and stage the saved vCard. Replace the sample serial
   with the verified one; use the SDK's `adb` path if it is not on `PATH`.

   ```sh
   adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s emulator-5554 push marketing/store/demo/claro-fictional-contacts.vcf \
     /sdcard/Download/claro-fictional-contacts.vcf
   ```

   If Android rejects an update because of a signing mismatch, stop rather than
   uninstalling the existing app or deleting its data.
4. Inspect existing contacts before importing. If this kit is already present,
   reuse those contacts; do not blindly import again. Stable vCard UIDs do not
   guarantee that every Contacts importer deduplicates entries. Import through
   Android Contacts into device-only storage, never a synced Google account.
5. Open Claro and complete setup through its supported UI. Home, Phone and SMS
   default-role prompts and Android permission dialogs need the user's approval.
   Do not grant roles silently or bypass a settings PIN.
6. Configure Home tiles in the order saved in `contacts.json`: Maya, Daniel,
   Facebook, WhatsApp, Phone/Dialer, Camera and SOS. Resolve contact shortcuts
   against the imported contacts; Android contact IDs are device-specific.
   Choose the desired available theme through Claro Settings.
7. Leave SOS unconfigured. Never tap contact shortcuts, send messages, place calls
   or activate SOS while arranging or capturing the demo.

Facebook and WhatsApp shortcuts refer to genuine installed third-party apps.
Reuse them if present. Their APKs, sessions and accounts are not bundled here;
if absent, obtain installation approval and use verified publisher sources.
No third-party login is needed merely to show their launcher shortcuts.

Installing the APK does **not** automatically import contacts or restore Home
settings. The kit supplies the reusable data and setup recipe, not a device
backup or an automatic seeding feature. It contains no seeded SMS/call history
and no saved settings PIN or recovery secret.

## Refreshing artwork

After intentional edits to the fictional contact data, regenerate its vCard and
review image from the saved portrait sheet:

```sh
python3 scripts/prepare-play-demo.py \
  --portrait-sheet marketing/store/demo/generated-portrait-sheet.png
```

The rendering scripts use Pillow from `scripts/brand-requirements.txt` and the
macOS fonts referenced by the scripts. No image-generation service is required
to reuse the saved portraits.

After a Home/theme design change, capture fresh native examples with this
fictional data, update the source capture hashes, then regenerate the in-app
theme-selection images and store screenshots:

```sh
python3 scripts/render-style-previews.py
python3 scripts/render-play-screenshots.py
python3 scripts/render-play-screenshots.py --clean
```

Keep actual demo captures and provenance together. Existing capture manifests
describe their historical builds; they do not certify a newly installed build.
Never upload review boards or watermarked previews as Play screenshots. Clean
exports still require creative review; this kit is not Play publication approval.
