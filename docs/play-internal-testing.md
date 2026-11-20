# Publishing a new Claro Internal Testing build

This is a **local build/signing + Android Publisher REST** workflow. Use the
existing service connection; do not recreate it for each release. No command here
authorizes a release unless the user requested publication.

## 1. Select source and inspect live Play state

Check `git status --short` and `git rev-parse HEAD`. Include the approved integrated
source, not the historical source used for an earlier release. With local changes,
record what is included and obtain a clear source decision if their scope is
uncertain. Do not commit, stash, reset, or switch branches as a shortcut.

The package is `dev.hollander.claro`, the REST track is `internal`, and the listing's
current default language is `en-GB`. Read live state instead of hardcoding a prior
release version or reusing its artifact.

The existing private connection is:

- Auth interpreter: `~/.local/share/claro/google-play/venv/bin/python`
- Service-account JSON: `~/.local/share/claro/google-play/service-account.json`
- OAuth scope: `https://www.googleapis.com/auth/androidpublisher`

Use official Google auth to refresh credentials in memory. Do not print the JSON,
bearer token, authorization headers, or signing passwords.

```python
from pathlib import Path
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

credentials = service_account.Credentials.from_service_account_file(
    str(Path.home() / ".local/share/claro/google-play/service-account.json"),
    scopes=["https://www.googleapis.com/auth/androidpublisher"],
)
http = AuthorizedSession(credentials)
package = "dev.hollander.claro"
base = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}/edits"
response = http.post(base, json={}, timeout=60)
response.raise_for_status()
edit_id = response.json()["id"]
edit_url = f"{base}/{edit_id}"
```

This creates an uncommitted edit, **not** a release. GET `edit_url + "/bundles"` and
`edit_url + "/tracks"` with timeouts and `raise_for_status()`; inspect their version
codes and track statuses. Check older used codes in Play's artifact history when
the REST inventory is insufficient. Choose a code above all previously used
codes, within Android's `1..2100000000` range. The local choice is not a reservation.
DELETE this inspection edit afterward.

If the existing connection fails, distinguish an expired/missing credential,
`SERVICE_DISABLED`, and insufficient app permissions. Reuse the existing enabled
Android Publisher API and dedicated app-scoped uploader. Account/key creation,
rotation, API enablement, or expanded permissions require an explicit setup
decision; a failure does not authorize browser upload or another credential.

## 2. Configure the production release

Internal Testing is a distribution track. This app's Play release normally uses
`production` telemetry; debug `.localtest` builds use `staging` and must not be
mistaken for the Play candidate.

Both `app/build.gradle.kts` and `feature/billing-observability/build.gradle.kts`
resolve telemetry configuration from process environment variables, then ignored
`local.properties`, then legacy `telemetry.local.properties` entries, in that
order. File entries are prefixed with the selected environment.
`CLARO_TELEMETRY_ENVIRONMENT` overrides the Gradle environment property.

Required telemetry inputs are:

- `CLARO_MIXPANEL_TOKEN`
- `CLARO_FIREBASE_APPLICATION_ID`, `CLARO_FIREBASE_API_KEY`
- `CLARO_FIREBASE_PROJECT_ID`, `CLARO_FIREBASE_SENDER_ID`

The Firebase application must match the production package/project, not the
local-test registration. Supply the approved Mixpanel SDK token privately; do not
assume a previous environment-only build persisted it. This machine's approved
private SDK configuration may be stored in `local.properties`; keep its existing
`sdk.dir` entry and use keys such as `production.CLARO_MIXPANEL_TOKEN` and
`production.CLARO_FIREBASE_APPLICATION_ID`. Keep the file Git-ignored and mode
`0600`. Never show its values in chat, terminal output, screenshots, commits, or
Copilot artifacts. The allowed server URLs
are `https://api.mixpanel.com`, `https://api-eu.mixpanel.com`, and
`https://api-in.mixpanel.com`; the default is US. Verify the intended region.

### Reusing and retrieving private inputs next time

Inspect configuration **key names and presence only**, never dump either private
properties file. Reuse existing approved values before opening consoles.

- Mixpanel: the currently approved project is **4085377**, US-region. In an
  authenticated browser, open
  `https://mixpanel.com/project/4085377/app/settings/project`, confirm the project
  ID and Data Residency, then use **Access Keys > Project Token**. Retrieve the
  existing SDK token, not a service-account secret or newly created token. The
  authorized Mixpanel integration's **Get-Project-Token** is an alternative when
  browser retrieval was not specifically requested. Transfer the value privately
  into `production.CLARO_MIXPANEL_TOKEN`; do not return it in tool output. If a
  temporary browser download is necessary, use a mode-`0700` temporary directory
  and remove both the transfer file and any automatic browser download copy.
- Firebase: reuse the existing production values. If missing, use the approved
  Firebase project's **Project settings > General > Your apps** Android
  registration matching `dev.hollander.claro`. Obtain the application ID,
  project ID, sender ID, and API key from that registration's configuration.
  Staging/local-test registrations are separate; do not substitute their values.
- Play REST: reuse the service-account JSON and auth interpreter in section 1.
  Keep the existing JSON file separate and private; do not embed its contents in
  `local.properties`, recreate credentials, or recover a key from chat history.
- Signing: reuse the existing private upload keystore and alias. The `bundle`
  helper currently accepts signing paths/passwords through environment variables
  or local password prompts, **not** properties-file signing entries. Do not
  imply that saving a signing entry in `local.properties` makes the helper read
  it. Use an approved password manager or have the user enter the password
  privately; do not capture passwords from history or expose them in arguments.

The telemetry preflight checks presence/environment/endpoint, **not** successful
SDK credentials or backend ingestion. If billing is part of this release, also
check `CLARO_REVENUECAT_PUBLIC_KEY`, store products, and required purchase evidence:
the telemetry preflight does not validate RevenueCat.

Leave `CLARO_META_ENABLED` off unless enabling advertising was explicitly approved.
If enabled, valid Meta app/client configuration and the corresponding release/
consent checks are required. Never force consent on for a release or verification.

Check the final manifest and shipped behavior against privacy/Data Safety
declarations, particularly changed analytics, advertising-ID permissions, SDK
initialization, and consent handling. Do not turn unresolved behavior into a
stronger policy claim or fabricate missing declarations.

## 3. Build and sign one exact candidate

Use an existing compatible JDK (17 or newer) and Android SDK 37. Keep the existing
private upload keystore outside Git and Copilot session storage; never replace
the established upload identity.

Set these variables to the live-state-selected version and existing key location.
The values below are placeholders, not deployable versions or credentials:

```sh
export CLARO_TELEMETRY_ENVIRONMENT=production
export CLARO_VERSION_CODE='<unused-code-above-all-previous-codes>'
export CLARO_VERSION_NAME='<release-version-name>'
export CLARO_UPLOAD_KEYSTORE='/private/path/to/existing-upload-key.jks'
export CLARO_UPLOAD_KEY_ALIAS='<existing-upload-alias>'
scripts/android-local bundle
```

`bundle` requires explicit code/name, selects production, attaches
`-Pclaro.requireTelemetry=true` to the release build, and signs into a new
`app/build/outputs/internal-testing/candidate.*/claro-internal.aab`. It does not
run tests, use a device, upload, or publish.

Passwords are entered in the local signing prompt. For authorized unattended
signing, inject **both** `CLARO_UPLOAD_STORE_PASSWORD` and
`CLARO_UPLOAD_KEY_PASSWORD` from the approved password manager into the process
environment; the helper uses `jarsigner`'s `-storepass:env`/`-keypass:env`.
Never place password values in shell command arguments, chat, logs, tracked files,
or Copilot artifacts.

Run tests only when explicitly requested. `scripts/android-local internal`
additionally runs the **full** JVM/lint/device suite; it is not the default
deployment command. For requested emulator checks, use the existing designated
serial and staging/local-test configuration. Do not wipe/uninstall to fix a
signature mismatch or act on a connected personal phone by default.

## 4. Verify the signed artifact

Use the candidate path printed by the successful signing invocation, not the
unsigned `app/build/outputs/bundle/release/app-release.aab`, the separately tracked
`app/release/app-release.aab`, or an old candidate directory.

Before upload:

1. Verify the JAR signature and match the signer certificate SHA256 to Play's
   registered **upload** certificate, not the Play app-signing certificate.
2. Inspect the compiled base manifest with a trusted bundle tool: package must
   be `dev.hollander.claro`, code/name must match the approved release, and the
   application/entry points and SDK/permission declarations must be correct.
3. Inspect final production Firebase/Mixpanel configuration and consent defaults
   without printing credential values. Included SDK dependencies are not evidence
   that collection or delivery works.
4. Verify release R8 optimization/resource shrinking and the exact AAB's
   `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`.
   Firebase resources resolved by name are retained by
   `app/src/main/res/raw/keep.xml`.
5. Preserve the matching map and any genuine native debug metadata alongside this
   candidate. `FULL` cannot reconstruct symbols stripped by upstream vendors;
   never fabricate symbol files or reuse another build's diagnostics.
6. Calculate local AAB SHA256, record source/dirty-state provenance and intended
   release notes, and ensure source/config did not change during preparation.
   Rebuild and repeat verification if they did.

Automatic Crashlytics mapping upload is disabled because this app does not apply
the Google Services plugin. Bundled Play mapping is not proof of Firebase
deobfuscation; any required Firebase map upload is a separate matching-artifact
operation. Clearly report unverified minified-runtime, billing, carrier/OEM, or
policy behavior instead of substituting debug emulator success.

## 5. Upload and commit through REST

Create a **fresh** edit, snapshot all tracks, and recheck the selected code before
writing. Use `AuthorizedSession` throughout, with explicit timeouts (allow a
longer timeout for the binary upload).

| Operation | Request |
| --- | --- |
| Create edit | `POST {base}` with `{}` |
| Inspect bundles/tracks | `GET {edit_url}/bundles`, `GET {edit_url}/tracks` |
| Upload signed AAB | `POST https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{package}/edits/{edit_id}/bundles?uploadType=media` with the exact binary and `Content-Type: application/octet-stream` |
| Update internal track only | `PUT {edit_url}/tracks/internal` with the approved track body |
| Validate | `POST {edit_url}:validate` |
| Commit | `POST {edit_url}:commit` |
| Delete unused inspection edit | `DELETE {edit_url}` |

Check HTTP status before parsing each response; do not turn errors into success.
After upload, compare the returned version code and SHA256 to the approved local
candidate. An existing code can be reused **only** if its server hash matches
exactly and reusing that artifact is the approved action. Otherwise select a
fresh code and rebuild; never overwrite or blindly retry a used version.

For an explicitly requested rollout, use this track body with the selected values.
Use `draft` instead only when the user requested a draft:

```json
{
  "track": "internal",
  "releases": [{
    "name": "<release-version-name>",
    "versionCodes": ["<approved-version-code>"],
    "status": "completed",
    "releaseNotes": [{"language": "en-GB", "text": "<accurate-release-notes>"}]
  }]
}
```

This intentionally replaces the internal release list with the selected release.
Do not use it if retaining another internal release is required without first
reconciling that state. Preserve other tracks, listing assets, and policy answers.

Validate before commit. For this app's verified REST workflow, **omit**
`changesNotSentForReview`; Google rejected it with a response saying changes are
sent for review automatically. Do not retry an uncertain commit or upload blindly.
Inspect live state first after a timeout or concurrent-edit conflict.

Play can consume the mapping bundled in the AAB. If a diagnostic warning remains,
inspect the actual release and use the documented matching deobfuscation upload
API when needed; never treat a successful AAB upload as proof all warnings cleared.

## 6. Read back and report

After commit, create another edit and GET its bundles and internal track.
Independently confirm code, name, notes, status, and matching server/local SHA256.
Check that other tracks were not changed, then DELETE the read-only edit.

`completed` on the internal track is not proof that processing/review finished or
that testers can install. Check tester configuration and the opt-in link separately.
`GET {edit_url}/testers/internal` may return `{}` even when Console email-list
configuration needs separate inspection; do not infer absence or invent testers.

Finish with the published code/name, source, exact signed artifact path/hash,
verified internal-track status, and outstanding tester, policy, diagnostic, or
runtime gates. Preserve release artifacts in ignored build output when required;
clean task-specific temporary output, leave shared caches/emulator data intact,
and report intentionally retained artifacts over 500 MB.
