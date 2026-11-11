# Android foundation

The native Android scaffold lives alongside the dependency-free storyboard. The
storyboard remains the GitHub Pages root; Android modules do not change its
routes, assets, or build behavior.

## Architecture

Dependencies point inward:

```text
app
├── presentation:touch / presentation:classic
├── feature:* contracts
├── core:data / core:platform
└── core:design
    └── core:domain
        └── core:model
```

- `app`: Hilt composition root, startup recovery, and typed top-level navigation.
- `core:model`: stable cross-boundary values.
- `core:domain`: repositories, protected-route policy, billing, and observability
  ports. Feature code depends on these ports, never vendor SDKs.
- `core:data`: Room, typed Proto DataStore, deterministic fake repositories, and
  migration/schema ownership.
- `core:design`: accessible typography, color, spacing, target-size tokens,
  storyboard SVG vector icons (`ClaroIcons`), illustrated portraits
  (`ClaroAvatar`), 2G tactile keypad/softkey/D-pad haptics (`ClaroHaptics`
  using Android 10+ predefined `EFFECT_CLICK`, `EFFECT_TICK`,
  `EFFECT_HEAVY_CLICK`, and `EFFECT_DOUBLE_CLICK` with `USAGE_TOUCH` plus view
  fallback), battery/signal meters, headers, info cards, rows, and docked
  screen scaffolds (`ClaroComponents`).
- `core:platform`: replaceable Android platform adapters and Hilt bindings.
- `core:testing`: shared unit-test dependencies and future deterministic fixtures.
- `presentation:*`: Touch Home and Classic Phone shells consuming the same
  launcher snapshot.
- `feature:launcher-home`: HOME-role eligibility/request/repair, scoped
  launchable-app discovery, device status reads, app allowlisting and home-tile
  policy.
- `feature:onboarding-settings`: resumable setup, date/PIN protection, readiness,
  localization/display settings, and blocker-aware visual-mode switching.
- `feature:communication`: shared contacts, Telecom/default-dialer state, call
  history, SMS/MMS provider duties, drafts, inbox and send outcomes.
- `feature:safety-media-apps`: persisted SOS countdown, camera/photo-picker
  handoffs, gallery ownership semantics and allowed-app launching.
- `feature:billing-observability`: membership, store lifecycle, RevenueCat,
  consent-aware Mixpanel/Firebase routing and Crashlytics redaction.

`MainActivity` is the integration boundary. Both Touch and Classic shells route
to the same phone, contacts, messaging, SOS, media, apps, settings and membership
state. Home projects authoritative missed-call and unread-message counts; missing
authority is shown as unknown rather than zero. Opening the inbox does not clear
messages, and the communication repositories clear only the opened conversation
or call-history record. Active Telecom calls project a return-to-call action.

The phone/contacts layer now qualifies and requests the dialer role, handles
`ACTION_DIAL`, declares the default-phone `InCallService`, and requests phone,
call-log, contacts, and incoming-call notification permissions in role-first
order. Messaging separately requests the SMS role before restricted permissions
and declares SMS delivery, MMS WAP push, `SENDTO` and respond-via-message
components. Role loss produces repair/fallback state instead of fabricated data.

## Phone and contacts implementation

- `feature:communication` owns the shared call/contact state machine, capability
  contracts, persisted speed-dial references and call outcomes.
- Telecom callbacks are authoritative for dialing, ringing, connection, hold,
  audio routes, duration and disconnect outcomes. Submitting a call to Telecom
  never fabricates a connected state.
- Emergency numbers hand off through the system dialer. Claro never silently
  selects a SIM/account or claims emergency completion.
- Android Contacts Provider remains the source of truth. Lists are paged,
  multiple numbers require explicit selection, and saved assignment snapshots
  become unresolved when a contact or selected number changes.
- Touch and Classic presentations consume the same immutable communication
  state. Classic keeps its integrated control deck and permanent keypad.
- Incoming calls use a high-priority full-screen notification request with
  visible Answer and Decline actions. Actual lock-screen presentation remains
  subject to Android/OEM full-screen-intent policy.

Physical-device validation is still required for carrier connection/disconnect
causes, no-service and dual-SIM account selection, Bluetooth and proximity
behavior, call waiting/conference capability reporting, ringtone/DND behavior,
locked-screen incoming calls, emergency handoff, and OEM dialer-role repair.

The safety feature routes a configured trusted-contact
SOS through the shared Telecom call gateway. If the required role or permission
is absent, the request fails into a repair state instead of simulating success.
Emergency access is a separate native dialer handoff and is never gated by Claro
configuration or entitlement.

Camera opens the system camera immediately (no intermediate Claro screen) and,
after a capture, lands in the Gallery. The Gallery lists every image on the
device from `MediaStore` (newest first, `READ_MEDIA_IMAGES`, with
`READ_MEDIA_VISUAL_USER_SELECTED` for partial access on Android 14+ and
`READ_EXTERNAL_STORAGE` up to API 32); there is no "add picture" import step.
This deliberately overrides the original Claro-only gallery wording of
REQ-MEDIA-01/02 at the product owner's request. Deleting a photo still uses
Android's delete authorization flow. The Apps screen and Home/menu tiles show
each app's real launcher icon (`ClaroAppIcon`, falling back to a generic glyph),
launch only caregiver-allowlisted launcher activities and do not claim to
resize, lock, or control third-party apps.

Messaging UX rules: drafts persist automatically, so Compose and Confirmation
have no Cancel/Keep-draft buttons (system/product Back is the exit and Classic
Back deletes the last typed character first); Classic Options no longer repeat
Delete-character/Cancel; the failed-send screen keeps its PRD Cancel. A
successful send clears the draft and recipient query, and leaving Messages
resets the inbox search. Call history rows show only the time plus a trailing
incoming/outgoing/missed direction icon and read the system `CallLog`.
Onboarding offers Classic first (left) and preselected, and Favorites persist.

Protected settings are the only route to phone-style changes, allowed-app/Home
customization, contact creation/editing/deletion, SOS recipient configuration
and caregiver test calls, local Claro reset, membership, and diagnostics. Senior
SOS never exposes recipient inputs or test controls; tapping `Settings` from
SOS, `Edit apps` from Apps, or `Add person`/`Edit`/`Delete` from Contacts routes
through the configured Date (`dd/MM/YYYY`) or 6-digit Caregiver PIN gate and
continues directly to the intended destination after unlock. Mode switching
preserves the shared repositories, defers while a call, SOS countdown/call or
purchase is critical, and automatically applies once blockers clear. `Reset
Claro` clears local Claro preferences, favorites, speed dials, drafts, SOS
state, and consent (blocked during an active call) while leaving device
contacts, SMS, photos, and Google Play subscriptions untouched. Billing uses
`DisabledBillingRepository` when `CLARO_REVENUECAT_PUBLIC_KEY` is absent so
unconfigured builds never invent fake plans or grant trial/lifetime access;
billing and diagnostics are never consulted by phone, messaging, Home or SOS
eligibility.

The production debug application does not seed storyboard people, phone numbers,
missed calls, unread messages, allowed apps, photos, plans or entitlements.
Unconfigured values render as Add person, unknown counts, empty lists or explicit
configuration-required states. Storyboard fixtures exist only in tests and
screenshots.

Role and permission readiness is read from Android after launch and every resume:

- HOME, DIALER and SMS are requested separately with `RoleManager`.
- restricted call-log/phone and SMS permissions are requested only after the
  corresponding role is held;
- Contacts and Android 13+ notifications have separate contextual requests;
- a denied request remains Needs attention, and a repeated request after
  permanent denial opens the app settings page;
- media uses the system picker/scoped ownership path and does not invent a
  storage permission grant.

The onboarding and product surfaces use the storyboard palette and hierarchy:
white content, charcoal headers/deck, Claro blue `#075BD8`, call green `#078534`,
danger red `#CC1731`, 3-4dp dense separators and 64dp primary targets. Visual mode
selection requires an explicit choice and previews the two actual shell
structures. All UI and notification copy is stored in module-prefixed Android
string resources across `values` (English), `values-es` (Spanish), and
`values-iw` (Hebrew), wrapped at runtime by `ClaroLocaleContainer` for immediate
locale and RTL switching while keeping phone numbers, dates, durations, and
physical Classic keypad positions LTR (`bundle.language.enableSplit = false`
bundles all three languages offline). Native Compose reflows for system insets
and font scaling, so exact browser pixels are not copied when that would break
accessibility.

## Billing and diagnostics configuration

The app is safe to build with no commercial or telemetry configuration. Empty
values keep RevenueCat unavailable and analytics/crash collection disabled:

```sh
export CLARO_REVENUECAT_PUBLIC_KEY='public_sdk_key'
export CLARO_MIXPANEL_TOKEN='public_project_token'
export CLARO_FIREBASE_APPLICATION_ID='firebase_application_id'
export CLARO_FIREBASE_API_KEY='firebase_public_api_key'
export CLARO_FIREBASE_PROJECT_ID='firebase_project_id'
export CLARO_FIREBASE_SENDER_ID='firebase_sender_id'
```

Only public Android client values are supported. Do not add RevenueCat secret
keys, service credentials or `google-services.json`. Firebase manifest defaults
disable Analytics and Crashlytics before startup; runtime collection additionally
requires the matching explicit consent toggle. The event API accepts only
allowlisted coarse events and enum outcomes, never contact, call, message, media,
recovery or SOS content.

## Toolchain and dependency policy

Versions are pinned in `gradle/libs.versions.toml`; dynamic and preview versions
are prohibited. The baseline uses JDK 17 bytecode, minSdk 29, and compile/target
SDK 37. Dependabot proposes grouped weekly updates, but upgrades must be checked
against official Android, Kotlin, Firebase, Mixpanel, and RevenueCat release
notes before merging.

CI runs the wrapper with JDK 17. To refresh dependency checksums after an approved
upgrade, run:

```sh
./gradlew --write-verification-metadata sha256 :app:assembleDebug
```

Review the generated `gradle/verification-metadata.xml` diff and commit it only
after confirming repositories and artifacts. Do not copy SDKs, Gradle caches,
emulator data, or build directories into repository or session artifact storage.

## Local verification

Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to an SDK containing Android 37, then run:

```sh
./gradlew clean :app:assembleDebug test lint
./gradlew :app:installDebug :app:connectedDebugAndroidTest
```

Use an existing emulator/device when UI or platform validation is needed. The
unit tests require no emulator.

## Remaining release gates

The build is integrated but does not replace physical-device and store evidence.
Before release, verify combined HOME/DIALER/SMS role eligibility and loss/regrant,
carrier calls and SMS/MMS, locked-screen incoming calls/full-screen intent,
dual-SIM/default-account behavior, emergency handoff, Bluetooth/proximity/call
waiting, camera/picker URI restoration, RevenueCat sandbox products and Play
policy declarations. Instrumented tests should run only on an approved existing
device/emulator and cannot prove carrier, OEM or store behavior.

Automated emulator screenshots are written by `StoryboardRegressionTest` and
`MainActivityLaunchTest` under the app external-files Pictures directory and
captured in `artifacts/android-review/` (`1.4 MB` across 19 PNGs). They verify
cold-start onboarding, side-by-side visual-mode previews, honest role/permission
repair without fake grants, Touch Home and Classic Home shells, Touch/Classic
dialer and call outcomes, SMS inbox and composer, senior SOS protection,
protected date challenge, and unconfigured membership state. Direct desktop
window-manager UI binding to `qemu-system-aarch64` remains unsupported by local
Computer Use, so live emulator verification runs through Android Gradle
instrumentation (`createAndroidComposeRule<MainActivity>()`); carrier,
lock-screen, Bluetooth, dual-SIM and Play purchase screens remain
physical-device/store gates rather than simulated success.
