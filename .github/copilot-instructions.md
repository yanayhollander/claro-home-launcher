# Claro repository instructions

## Deployment

- For a new Google Play Internal Testing build, use
  [the deployment skill](skills/deploy-internal-testing/SKILL.md) and
  [the release runbook](../docs/play-internal-testing.md).
- Deploy from the local machine through the Android Publisher REST API and the
  existing dedicated service-account connection. Do not substitute GitHub Actions
  or a Play Console browser upload.
- A request for instructions, a skill update, or an emulator check is not
  permission to publish. Publish only when the user requests a release.
- Build the approved current source, including integrated features. Never reuse
  a historical baseline or an existing AAB merely because it is already signed.
  Check local changes before building; preserve other work and record the source
  used. Do not commit, push, switch branches, stash, or reset without permission.
- The Play package is `dev.hollander.claro`. `.localtest` builds are staging
  verification builds, not Play release candidates. Internal Testing is a Play
  track, not a telemetry environment: this app's Play builds use production
  telemetry unless the user explicitly requests a different configuration.
- Inspect live Play bundles and every track before selecting a new version code.
  Never assume the next code from a prior conversation or a local Gradle default.
- Use the existing upload key; keep signing passwords, private keys, service-account
  JSON, and access tokens outside Git, logs, chat, and Copilot session storage.
  Do not create replacement accounts or keys when the existing connection fails.
- Require complete production telemetry configuration for release candidates.
  Do not silently ship an unconfigured Mixpanel/Firebase build, enable Meta
  advertising, or change consent defaults to make a check pass.
- Reuse this machine's Git-ignored `local.properties` for approved private SDK
  configuration. Environment variables take precedence over `local.properties`,
  which takes precedence over the legacy `telemetry.local.properties` file.
  Keep private configuration mode `0600`; never print, commit, or copy it into
  Copilot storage. Follow the runbook's retrieval steps for missing inputs rather
  than extracting credentials from conversation history or creating new keys.
- `scripts/android-local bundle` builds and signs; it does **not** publish.
  `scripts/android-local internal` additionally runs the full test suite; use it
  only when that testing was explicitly requested. Run other tests only when
  requested, using the narrowest relevant selectors.
- Validate the exact signed AAB, its package/version/upload certificate, R8 mapping,
  available native symbols, and final telemetry/manifest configuration. Emulator
  success alone does not verify an optimized production artifact or billing,
  carrier, OEM, and policy behavior.
- Change only the requested Play track. Validate the REST edit, commit it, then
  confirm the persisted release and matching bundle SHA256 in a fresh edit.
  Upload acceptance or edit validation alone is not a completed release.
- Preserve listing assets and policy declarations unless their update was
  requested. Never fabricate tester identities, legal answers, Data Safety
  declarations, native symbols, or successful runtime evidence.
- Reuse the approved existing emulator and preserve app/account data. Always
  select the device serial explicitly; never default to a connected personal phone.
- Keep build output in the normal repository build directories or task-specific
  `/private/tmp` directories, not Copilot session storage. Remove task-specific
  temporary output and report any intentionally retained artifact over 500 MB.
- Report what was actually built, uploaded, and committed to Play, including
  version, source, artifact path/hash, and unresolved warnings or tester gates.
