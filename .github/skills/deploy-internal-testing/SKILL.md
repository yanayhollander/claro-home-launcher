---
name: deploy-internal-testing
description: Build, sign, and publish a new Claro Android release to Google Play Internal Testing from the local machine using the existing service-account REST connection. Use for internal-testing deployment, upload, release preparation, and deployment troubleshooting.
---

# Deploy Claro to Internal Testing

Read [the release runbook](../../../docs/play-internal-testing.md) before acting.
It contains the command contract, REST endpoints, verification requirements, and
recovery rules. Repository deployment instructions also apply.

## Scope and prerequisites

1. Distinguish preparation from publication. A documentation/skill request or a
   staging emulator installation must not trigger a Play upload or rollout.
2. Inspect the current checkout and local changes. Select the source the user
   approved; do not silently exclude integrated analytics or reuse the old
   release baseline. Preserve unrelated work. Do not mutate Git without permission.
3. Reuse the existing local service-account JSON and Python auth environment.
   Check their availability without reading credentials into chat or tool output.
   If authentication/API access fails, diagnose that connection rather than
   creating another account/key or switching to browser upload.
4. Read all live tracks and bundles in a temporary REST edit. Choose an unused
   version code above every known previously used code and a clear version name.
   Delete the inspection edit when finished.
5. Check production Firebase/Mixpanel configuration, the intended Mixpanel region,
   and any billing configuration needed by this release. Do not copy staging
   Firebase identifiers into the Play build. Leave optional advertising disabled
   unless explicitly approved and correctly configured.
   Reuse approved private values from Git-ignored `local.properties` first.
   Follow the runbook's credential-retrieval instructions if a value is missing;
   do not print credentials or recover them from session history.
6. Reconcile changed SDK/manifest behavior with privacy and Data Safety statements.
   Stop on unresolved required declarations; never invent facts or infer absence
   of identifier collection from manifest permission presence/absence alone.

## Build and artifact gate

- Use `scripts/android-local bundle` with explicit version overrides, the existing
  private upload keystore, and production telemetry. This helper enforces the
  required telemetry preflight and signs a unique candidate directory.
- Tests are not implicit deployment work. Use `internal` only for an explicitly
  requested full validation run; use targeted commands for narrower requests.
- Never upload the unsigned Gradle output, `app/release/app-release.aab`, an old
  `optimized-v3` candidate, or a `.localtest` APK as the new release.
- Confirm package, code/name, signed upload certificate, SHA256, R8 mapping,
  available native symbols, and final manifest/configuration on the **same**
  candidate. Record source provenance separately from the artifact hash.
- SDK initialization, included dependencies, and a passing config preflight are
  not proof of event delivery. If analytics verification is requested, use real
  app events correlated to installation/environment, not synthetic `/track`
  requests; distinguish staging verification from production artifact evidence.
- Pause other writes during preparation. If source/config changes, discard that
  candidate's release decision and rebuild/reverify; do not substitute another AAB.

## Publish through REST

1. Create a fresh edit and recheck the selected version against live state.
2. Upload the exact signed AAB through `bundles.upload`. If that code already
   exists, reuse it only when its server SHA256 exactly matches the approved
   candidate. A mismatched or used code requires a newly selected code and rebuild.
3. Check Play's version/hash response and diagnostic metadata. Keep mapping and
   symbols paired with this artifact; do not fabricate missing upstream symbols.
4. PUT only the `internal` track with the approved version, release name, notes,
   and intended status. For a requested rollout use `completed`; for an explicitly
   requested draft use `draft`. Preserve other tracks and unrelated store edits.
5. Validate the edit, then commit. Omit `changesNotSentForReview` for the app's
   verified workflow; Google previously rejected that parameter here.
6. Read back bundles and the internal track in a **new** edit. Verify version,
   status, release name/notes, and server/local SHA256; then delete that read-only
   edit. Distinguish track completion from processing, review, or tester access.
7. Check the tester gate. An empty REST tester response does not establish that
   Console email lists are absent. Do not invent testers or promise availability.

## Recovery and completion

- Stop on authentication, permission, configuration, signing, or validation errors;
  expose the actionable failure without logging credentials.
- After a timeout/uncertain commit, inspect live state before retrying. Do not
  blindly reupload, create duplicate releases, increment versions, or roll back.
- Do not overwrite conflicting concurrent Play edits. Refresh state and reapply
  only the approved internal-track change.
- Clean task-specific temporary outputs without deleting shared caches or emulator
  data. Preserve the exact candidate and diagnostics in ignored build output when
  needed for this release, never in Copilot session artifacts.
- Finish with source, version, artifact path/hash, verified Play status, and any
  unresolved diagnostics, tester, policy, or runtime gates. Do not call the whole
  store setup complete just because an internal-track release was committed.
