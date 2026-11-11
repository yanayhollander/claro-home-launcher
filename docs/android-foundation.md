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
- `core:platform`: replaceable Android and vendor adapters. Mixpanel, Firebase
  Analytics, Crashlytics, and RevenueCat are disabled until explicit
  privacy-reviewed configuration is supplied. Their current production versions
  remain pinned in the catalog but are intentionally not placed on the runtime
  classpath until configured, preventing automatic SDK startup.
- `core:testing`: shared unit-test dependencies and future deterministic fixtures.
- `presentation:*`: Touch Home and Classic Phone shells consuming the same
  launcher snapshot.
- `feature:launcher-home`: HOME-role eligibility/request/repair, scoped
  launchable-app discovery, device status reads, app allowlisting and home-tile
  policy.
- Other `feature:*` modules remain ownership contracts for independently stacked
  feature work.

The app now declares HOME intent handling and can request or repair the HOME role.
Unavailable phone/message authority is represented as unknown rather than a
fabricated zero, and launch failures remain visible.

The phone/contacts layer now qualifies and requests the dialer role, handles
`ACTION_DIAL`, declares the default-phone `InCallService`, and requests phone,
call-log, contacts, and incoming-call notification permissions in role-first
order. It does not claim the SMS role or send messages; messaging remains a
separate stacked feature.

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
foundation unit tests require no emulator.
