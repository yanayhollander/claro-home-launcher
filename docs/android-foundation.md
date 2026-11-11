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
It still deliberately does not request dialer, SMS, contacts, or notification
roles; place calls; send messages; purchase products; or upload telemetry.
Unavailable phone/message authority is represented as unknown rather than a
fabricated zero, and launch failures remain visible.

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
