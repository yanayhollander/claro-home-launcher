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
- `core:design`: accessible typography, color, spacing, and target-size tokens.
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

Camera capture is an external system handoff backed by scoped `MediaStore`
content. Imported photos use the system Photo Picker, references are removable
without deleting the original, and owned-photo deletion uses Android's delete
authorization flow where required. The Apps screen launches only caregiver-
allowlisted launcher activities and does not claim to resize, lock, or control
third-party apps.

Protected settings are the only route to phone-style changes, allowed-app/Home
customization, SOS configuration, membership and diagnostics. Mode switching
preserves the shared repositories and defers while a call, SOS countdown/call or
purchase is critical. Billing and diagnostics are not consulted by phone,
messaging, Home or SOS eligibility.

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
./gradlew :app:assembleDebug test lint
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
