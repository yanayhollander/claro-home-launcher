# Claro Home Launcher — Platform Feasibility Research

**Scope of this document:** Primary-source verification of Android / Google Play / RevenueCat / Firebase
platform constraints relevant to the Claro Home Launcher PRD and HTML storyboard. This is **research only**:
it does not claim Google Play Store approval, App Review outcomes, or that any RevenueCat/Firebase project has
been configured. All findings are cited to primary sources with access dates. Where no authoritative source
exists (e.g., "PIN recovery is not a security boundary"), this is flagged explicitly as an inferred
recommendation rather than a documented platform fact.

Access date for all citations below: **2026-11-11**, unless a specific page states a different "last updated" date (noted inline).

---

## 1. HOME role vs. default dialer role, and InCallService

### 1.1 Roles are independent, queryable, user-grantable
Android exposes "roles" via `RoleManager` (API 29+), including `ROLE_HOME`, `ROLE_DIALER`, and `ROLE_SMS` as
distinct constants. An app requests a role with `RoleManager.createRequestRoleIntent(roleName)` and
`startActivityForResult`; the user is shown a system confirmation dialog, and the app receives `RESULT_OK`/`RESULT_CANCELED`.
The API documentation explicitly warns that **the set of available roles can change with OS updates, so apps must call
`isRoleAvailable()` before assuming a role exists**, rather than hard-coding availability.
- Source: Android Developers, *RoleManager* API reference — https://developer.android.com/reference/android/app/role/RoleManager (accessed 2026-11-11)

**Interpretation for release scope:** Nothing in the `RoleManager` API or its documentation prohibits a single
app from holding `ROLE_HOME`, `ROLE_DIALER`, and `ROLE_SMS` simultaneously — each role is requested and granted
independently, and Android ships combined launcher+dialer+messaging apps in practice (e.g., accessibility/elder
launchers). However, **I could not find an explicit Google-published statement that explicitly confirms or
guarantees simultaneous multi-role holding is supported on all OEM skins**; this should be validated by functional
testing on target devices (notably Samsung/Xiaomi/Huawei skins, which are known to alter default-app pickers) before
committing to "set all defaults in one flow" as a v1 requirement. **Gap/uncertainty**, flagged for release testing.

### 1.2 Becoming the default phone/dialer app (InCallService)
To replace the default Phone app, Android's official guide ("Build a default phone application") requires:
- Declaring `MANAGE_OWN_CALLS` permission and a `ConnectionService` bound via `BIND_TELECOM_CONNECTION_SERVICE`.
- The calling-app UI "must not have any calling capability" beyond UI, and "must handle all calls that the
  Telecom framework is aware of" without assuming the call type (SIM-based vs. VoIP).
- Source: Android Developers, *Build a default phone application* — https://developer.android.com/develop/connectivity/telecom/dialer-app (last updated 2026-10-23 per page metadata; accessed 2026-11-11)

**Critical emergency-call constraint (verified, primary source):**
> "The preloaded dialer will ALWAYS be used when the user places an emergency call, even if your app fills the
> `RoleManager.ROLE_DIALER` role. To ensure an optimal experience when placing an emergency call, the default
> dialer should ALWAYS use `TelecomManager.placeCall(Uri, Bundle)` to place calls (including emergency calls)...
> If a non-preloaded dialer app uses `Intent#ACTION_CALL` to place an emergency call, it will be raised to the
> preloaded dialer app using `Intent#ACTION_DIAL` for confirmation; this is a suboptimal user [experience]."
- Source: same page as above.

**Release implication:** The SOS tile and long-press dialer slots **must never attempt to directly dial emergency
numbers via `ACTION_CALL`/`TelecomManager.placeCall`** expecting the app's own UI to remain in control; the OS will
intercept and hand off to the OEM's preloaded emergency dialer regardless of custom-dialer status. This is a hard
platform constraint, not a bug to work around — SOS design must assume handoff to the system/preloaded dialer UI for
true emergency numbers, and should treat "SOS contact" calling (a grandchild's number) as an **ordinary outgoing
call**, not an emergency-number call, since custom numbers are not emergency numbers and are unaffected by this
restriction.

### 1.3 `ACTION_CALL` cannot dial emergency numbers from a regular app context
The Android `Intent` reference and long-standing platform behavior (reiterated across SDK versions) establish that
`ACTION_CALL` intents directed at recognized emergency numbers are blocked for third-party apps without the
privileged, system-only `CALL_PRIVILEGED` permission ("Not for use by third-party applications"); apps must fall back
to `ACTION_DIAL` (which opens the dialer prefilled, requiring explicit user tap) for anything that could be an
emergency number.
- Source: Android Developers, `Intent` class reference (ACTION_CALL / ACTION_DIAL semantics) — https://developer.android.com/reference/android/content/Intent (accessed 2026-11-11)

**Release implication:** SOS "direct dial" must be scoped to **non-emergency, user-configured contacts** (e.g., a
caregiver or family number), dialed via `ACTION_CALL` (permitted for non-emergency numbers once `CALL_PHONE` is
granted) or `TelecomManager.placeCall` if the app holds the dialer role. True emergency numbers must route through
`ACTION_DIAL` or system emergency UI and cannot be "one tap, silently dialed" by any third-party app — this is a
safety-by-design constraint from the OS itself, not merely a store policy, and should be stated as a documented
product constraint in the PRD (SOS tile labeled for a trusted contact, not a 911/112/100-style number).

---

## 2. Default SMS role and restricted SMS/Call Log permissions

### 2.1 Registration-gated sensitive permissions
Google Play restricts `READ_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, `SEND_SMS`, `WRITE_SMS`
(SMS group) and `READ_CALL_LOG`/`WRITE_CALL_LOG` (Call Log group) to apps that are registered as the device's
**default handler** for the matching role (default SMS app for the SMS group; default Phone or Assistant handler for
call-log permissions), unless a narrow, Google-reviewed exception applies (e.g., anti-smishing providers with
"a track record of significant protection... as reflected in analyst reports").
- Source: Google Play Console Help, *Use of SMS or Call Log permission groups* — https://support.google.com/googleplay/android-developer/answer/10208820 (accessed 2026-11-11). Note: the page states an upcoming change effective **2027-01-27** removing call-log-based account verification as an allowed exception use case — not relevant to this app's declared use case but should be rechecked if the policy changes before submission.
- Source: Android Developers, *Permissions used only in default handlers* — https://developer.android.com/guide/topics/permissions/default-handlers (last updated 2026-10-23; accessed 2026-11-11)

### 2.2 Minimum duties and ordering requirement for default handlers
The developer guide lists explicit requirements before Google Play will allow declaring these permissions:
- The app must actually be able to perform the core functionality of that handler (e.g., a default SMS app must be able to send texts).
- The app must provide a privacy policy.
- The Play Store listing must describe the relevant core functionality (e.g., phone-related capabilities for a Phone handler).
- Declared permissions must match the use case (see the SMS/Call Log guidance link above).
- **Critical sequencing constraint:** "Your app must ask to become a default handler **before** it requests the
  permissions associated with being that handler." E.g., an app must prompt `ACTION_CHANGE_DEFAULT` (default SMS
  app request) *before* requesting `READ_SMS` at runtime.
- Source: https://developer.android.com/guide/topics/permissions/default-handlers (accessed 2026-11-11)

**Release implication:** Onboarding flow must sequence: (1) request `ROLE_SMS`/`ACTION_CHANGE_DEFAULT` role prompt →
(2) only then request runtime `READ_SMS`/`SEND_SMS` permissions → (3) similarly for dialer role before any call-log
read. Requesting permissions first and the role prompt second risks Play policy rejection and/or runtime permission
denial since the OS will not grant sensitive SMS/call-log permissions to a non-default handler regardless of prompt order.

### 2.3 Combining the SMS role with the dialer/home role
No evidence was found that `ROLE_SMS` registration precludes simultaneously holding `ROLE_DIALER` and `ROLE_HOME` —
these are independent roles granted via independent `RoleManager` calls. The practical constraint is **UX and policy
burden**, not an API prohibition: each role requires its own justified core functionality, privacy policy coverage,
and Play listing description. **Recommendation:** scope v1 to request only the roles actually required by shipped
features (home always; dialer and SMS only if the "custom easy dialer/contacts/SMS" feature set requires reading
call log / SMS content — if it only needs to *send* SMS/launch calls via intents, the default-handler role may not be
necessary at all; see §2.4).

### 2.4 Avoiding default-handler roles entirely where possible
If the SMS tile only needs to **compose and send** messages (not read/display the SMS inbox) and the dialer tile only
needs to **place calls** (not show/manage an in-app call-log UI with system call metadata), standard `ACTION_SENDTO`/`ACTION_SEND`
(SMS) and `ACTION_CALL`/`ACTION_DIAL` (phone) intents delegate to the OS without requiring default-handler status or
`READ_SMS`/`READ_CALL_LOG` permissions at all. This is the **lowest-risk v1 path**: the PRD's "custom easy dialer/contacts/SMS"
feature should be scoped per-screen to determine whether reading call history/SMS content is a true v1 requirement, or
whether a curated, app-local contact list (stored by the app itself, not synced from the system call log) can satisfy
the UX without claiming the SMS/Dialer default-handler roles. This significantly reduces Play policy review risk and
permission friction for a first release.

---

## 3. Notification, battery, and background-execution limitations (incl. multi-SIM)

### 3.1 Background execution and Doze/App Standby
Android's official power-management guidance describes tiered restrictions:
- Foreground/visible apps: no job/alarm execution limits.
- Apps running a foreground service: limits enforced based on "standby bucket" (changed in Android 16: prior to
  Android 16, foreground-service execution had no job-quota limit; starting Android 16 quota limits apply even then).
- Screen-off + Doze active: job execution deferred to maintenance windows; `setAndAllowWhileIdle()`/`setExactAndAllowWhileIdle()`
  alarms are capped at **no more than once per 9 minutes per app**, even during Doze.
- User-forced "Restricted" battery setting: both jobs and alarms are restricted outright.
- Source: Android Developers, *Power management resource limits* — https://developer.android.com/topic/performance/power/power-details (last updated 2026-05-19; accessed 2026-11-11)
- Source: Android Developers, *Optimize for Doze and App Standby* — https://developer.android.google.cn/training/monitoring-device-state/doze-standby (accessed 2026-11-11)

**Release implication:** Any "SOS delivery confirmation" or "trial countdown" logic that depends on background
alarms/jobs must assume Doze-induced delays of several minutes when the screen is off, and must not assume
background services run continuously. Firebase Cloud Messaging (FCM) is Google's recommended channel for
doze-resilient delivery of time-relevant server-to-device messages (e.g., SOS escalation to a caregiver, license
state changes) rather than relying on local background polling.

### 3.2 Notification permission (Android 13+)
Starting with Android 13 (API 33), posting most notifications (including from a foreground service) requires the
runtime permission `POST_NOTIFICATIONS`; the user can deny it or revoke it later, and the app should check
`areNotificationsEnabled()` before assuming any notification (including SOS status / low-trial-days reminders) will
be visible.
- Source: Android Developers, *Notification runtime permission* — https://developer.android.com/develop/ui/compose/notifications/notification-permission (last updated 2026-10-28; accessed 2026-11-11)

**Release implication:** "SOS delivery status" shown to the caregiver and "trial ending" reminders to the grandfather
both depend on notification permission being granted; the PRD should specify an **in-app fallback status indicator**
(not solely a system notification) for SOS delivery confirmation, since the permission can be denied or revoked.

### 3.3 Multi-SIM handling
Android's `SubscriptionManager`/`TelephonyManager` APIs expose per-SIM subscription info
(`getActiveSubscriptionInfoList()`, `getSimSlotIndex()`), default-voice/SMS subscription setters
(`setDefaultVoiceSubscriptionId`, `setDefaultSmsSubscriptionId`), and per-SIM messaging via
`SmsManager.getSmsManagerForSubscriptionId(subscriptionId)`. `TelecomManager.placeCall()` accepts an
`EXTRA_PHONE_ACCOUNT_HANDLE` extra to target a specific SIM's `PhoneAccountHandle` for an outgoing call.
- Source (API shape, cross-checked against AOSP naming): Android telephony API surface as documented across Android
  Developers `SubscriptionManager`/`TelecomManager`/`SmsManager` reference pages, and corroborated by third-party
  technical write-ups describing the same API names and call patterns — https://developer.android.com/reference/android/telephony/SubscriptionManager ,
  https://developer.android.com/reference/android/telecom/TelecomManager , https://developer.android.com/reference/android/telephony/SmsManager
  (accessed 2026-11-11). **Caveat:** I verified the class/method names exist in current Android reference docs, but did not
  independently execute multi-SIM code on a physical dual-SIM device in this research pass — treat as API-level
  confirmation, not device-tested confirmation.

**Release implication:** On dual-SIM devices, both the custom dialer and SMS composer should let the user pick a SIM
(or default to the OS's configured default voice/SMS subscription) rather than assuming single-SIM behavior; OEM
variance in dual-SIM implementations (noted even in secondary sources) means this must be QA'd on real dual-SIM
hardware, not simulated.

### 3.4 Signal/connectivity dependence
No Android API guarantees network/cellular availability; this is a device/environment condition, not a configurable
platform constraint. The PRD's SOS and trial-status features must be designed to degrade gracefully under "no
signal"/"no data" conditions (see §6 and §8), since neither standard calling nor FCM-based status delivery function
without connectivity.

---

## 4. Emergency dial restrictions and safe SOS behavior/delivery status

Combining §1.2 and §1.3 findings:
1. **Emergency numbers are platform-protected.** Any attempt to silently auto-dial a detected emergency number via
   `ACTION_CALL` without the privileged `CALL_PRIVILEGED` permission (system-only, "not for use by third-party
   applications") will fail or be intercepted/redirected by the OS to the preloaded dialer. This is true even if the
   app holds `ROLE_DIALER`.
   - Source: Android `Intent` reference ACTION_CALL/ACTION_DIAL semantics and CALL_PRIVILEGED permission description — https://developer.android.com/reference/android/content/Intent (accessed 2026-11-11)
2. **SOS should be modeled as a trusted-contact call/SMS, not an emergency-services call.** The SOS tile's "call a
   real emergency number" ambition (if any) is platform-constrained and should be scoped out of v1; a safe, verifiable
   release-scope SOS is: (a) call a caregiver-configured phone number via standard `ACTION_CALL`/`TelecomManager.placeCall`,
   and/or (b) send an SMS/app-level alert to one or more caregiver numbers with location/caregiver context, using
   `SmsManager.sendTextMessage(..., sentIntent, deliveryIntent)`.
3. **SMS delivery status is a documented, available signal.** `SmsManager.sendTextMessage()` accepts a `sentIntent`
   (broadcast when the SMS was handed to the radio layer / failed) and a `deliveryIntent` (broadcast when a carrier
   delivery report confirms delivery to the recipient's handset, when the carrier supports delivery reports). This is
   the correct primitive for showing "SOS message sent" vs. "SOS message delivered" status to the caregiver, with an
   important caveat: **delivery reports are carrier-dependent and not guaranteed on all networks/recipient devices**,
   so delivery-confirmed status should be treated as best-effort, with "sent" as the reliable baseline state.
   - Source: Android `SmsManager` class reference / AOSP telephony source (`sendTextMessage` deliveryIntent Javadoc: "if not NULL this PendingIntent is broadcast when the message is delivered to the recipient. The raw pdu of the status report is in the extended data") — https://android.googlesource.com/platform/frameworks/opt/telephony/+/tools_r22/src/java/android/telephony/SmsManager.java (accessed 2026-11-11)

**Release implication:** The SOS feature's "delivery status" UI must present three states (Sending → Sent →
Delivered*) with an explicit caveat that "Delivered" may never appear on some carriers/recipients, and must not block
the "Sent" confirmation on receiving a delivery report. SOS must never be marketed or implemented as dialing true
emergency services (112/911/100-style numbers) directly and silently — doing so is both a platform restriction and
almost certainly a Play Store policy/safety risk given the vulnerable target user base.

---

## 5. Camera/gallery safe delegation

Rather than implementing a custom in-app camera (which carries significant scoped-storage, `FileProvider`, and
runtime-permission complexity), the standard, lower-risk delegation pattern is:
- Launch the OS camera app via `MediaStore.ACTION_IMAGE_CAPTURE` (optionally with `MediaStore.EXTRA_OUTPUT` and a
  `FileProvider`-wrapped `Uri` for full-resolution capture on API 24+, since raw `file://` URIs are blocked by
  `StrictMode` from Android 7.0 onward).
- Launch the OS gallery/picker via `ACTION_PICK`/`ACTION_GET_CONTENT`, or (preferred on modern Android) the
  **Android Photo Picker** (`ACTION_PICK_IMAGES`), which requires **no storage permission at all** since it runs in a
  separate, system-trusted process and returns only the user-selected media URIs.
- Source (pattern description, cross-checked against Android scoped-storage/FileProvider guidance referenced within): community engineering guide corroborating `ACTION_IMAGE_CAPTURE` + `FileProvider` + scoped-storage pattern — https://www.tutorialpedia.org/blog/android-open-camera-from-button/ (accessed 2026-11-11); the same pattern is the one documented in Android's own developer guidance on scoped storage, `FileProvider`, and `ACTION_IMAGE_CAPTURE` referenced from that article. **Note:** this is a secondary source corroborating well-known Android SDK behavior; I recommend verifying `MediaStore.ACTION_PICK_IMAGES`/Photo Picker specifics directly against `https://developer.android.com/training/data-storage/shared/photopicker` before implementation, which I did not fetch directly in this pass. **Gap:** flagged for a follow-up fetch before implementation.

**Release implication:** Delegating to the OS camera/gallery/Photo Picker avoids requesting `READ_MEDIA_IMAGES` or
legacy storage permissions for the four photo-speed-dial tiles, which both simplifies the grandfather-facing
permission prompts and reduces the Data Safety disclosure surface (see §9). This should be the default v1 approach;
a custom in-app camera UI is an explicit non-goal for v1 given the complexity/benefit tradeoff.

---

## 6. Background restrictions (expanded release-scope framing)

Synthesizing §3.1 with product needs:
- **Do not rely on always-on background services** for trial-expiry checks, SOS escalation retries, or caregiver PIN
  session timeouts. Use `WorkManager`/`AlarmManager` (`setExactAndAllowWhileIdle`, capped at ~once/9 min during Doze)
  for periodic checks, and FCM push for anything that must reach the device promptly regardless of Doze state.
- **Foreground services require a declared type** (Android 10+) and, since POST_NOTIFICATIONS, a visible notification
  the user can see and (on Android 13+) may suppress at the permission level — foreground-service notifications are
  normally exempt from needing `POST_NOTIFICATIONS` to **start** the service, but the guidance notes "apps must..."
  continue to follow best practices around visibility; this nuance should be revalidated against
  `https://developer.android.com/develop/ui/compose/notifications/notification-permission` during implementation planning.
- Source: same as §3.1/§3.2.

**Release gate:** Any caregiver-facing "live SOS tracking" or "always know launcher is active" promise in the PRD
needs explicit reframing as "best-effort, network- and OS-power-state dependent" rather than a guaranteed real-time
channel, to avoid both a false safety promise to a vulnerable user population and conflicts with Android's background
execution model.

---

## 7. PIN recovery is not a security boundary (inferred guidance, not a sourced platform fact)

**No Google/Android/Play primary source was found (or searched for a specific claim) stating that app-level PIN gates
are not security boundaries** — this is a well-established mobile-security engineering principle, not a documented
platform policy, so it is presented here as an **inferred recommendation** rather than a citation-backed fact:
- An in-app "caregiver PIN" protecting Settings/Home-customization is enforced entirely in application memory/state
  and local storage; it is trivially bypassable by uninstalling/reinstalling the app, clearing app data via Android
  Settings, using `adb`, or via any other launcher/root access — none of which the app can prevent, because Android
  does not grant third-party apps a mechanism to block OS-level "Clear data"/uninstall actions on themselves
  (`android:allowBackup`/device policy controller (DPC)/Device Owner provisioning are the only platform-level ways to
  constrain this, and Device Owner mode requires enterprise-style provisioning, not a consumer Play Store install flow).
- **Recommendation for the PRD:** describe the caregiver PIN explicitly as a **usability/misuse deterrent for the
  grandfather accidentally changing settings**, not a security control against a determined second party with
  physical device access, and plan a PIN-recovery flow (e.g., caregiver email-based reset) that does not assume the
  PIN is the sole gate to data; sensitive actions (e.g., changing the SOS contact) should additionally be
  recoverable/auditable from a companion/caregiver surface if one exists, rather than being unrecoverable if the PIN
  is forgotten.
- **Gap:** this entire section is professional judgment/inference, explicitly not backed by a Google policy citation — flagged per the task's request to distinguish sourced facts from inference.

---

## 8. Licensing: offline safety and expiry behavior (RevenueCat)

### 8.1 Offline entitlement caching
RevenueCat's SDK caches a product-to-entitlement mapping and the customer's `CustomerInfo` on-device; `getCustomerInfo()`
is documented as "safe to call ... frequently," normally resolving from cache without a network call, and is "updated
and cached ... when the app becomes active."
- Source: RevenueCat Docs, *Getting Subscription Status* — https://www.revenuecat.com/docs/customers/customer-info (accessed 2026-11-11)
- Secondary corroboration on offline behavior under backend outage (paying customers keep access by computing
  entitlement from the store's own purchase record when RevenueCat's backend is unreachable) — https://www.revenuecat.com/docs/test-and-launch/debugging/caching (RevenueCat's own Caching doc, accessed 2026-11-11). **Note:** a non-RevenueCat-domain mirror
  describing the same offline-entitlements mechanism was also found during research (`revenuedot.app`); that page is
  **not an official RevenueCat property** and is excluded as a citation here — only the `revenuecat.com`-hosted docs
  above are treated as primary source.

**Release implication:** The 7-day trial → paid gating logic should read cached `CustomerInfo`/entitlement state for
immediate, offline-safe UI decisions (e.g., "is the user still in trial / still entitled") rather than blocking on a
network call at every app launch — but the PRD must **not assume indefinite offline grace**; exact RevenueCat/Play
Billing grace-period and account-hold durations are configured in Play Console per subscription (see §8.3) and are a
**store/developer configuration choice**, not a RevenueCat SDK guarantee, so "how long a lapsed renewal stays
entitled while offline" is a release-configuration decision to make explicitly, not an assumed default.

### 8.2 Lifetime (one-time) purchase support
RevenueCat's non-subscription purchase documentation states: Android (Google Play) supports **consumables and
non-renewing subscriptions** as purchase types; for **non-consumable** ("lifetime unlock") behavior on Android, the
documentation specifically warns: "Non-consumable support is supported in Android SDK version 7.11.0 and up. In
Android SDK version 7.10.1 and below all Android IAPs will be consumed upon purchase. To replicate the behavior of a
non-consumable IAP for Android users, you must ensure your user will not be offered the IAP after the initial
purchase" (otherwise the user could be offered — and could inadvertently re-buy — the same "lifetime" product).
RevenueCat recommends creating "a new non-consumable IAP ... add the product to your offering, and add the product to
your entitlement" to implement a Lifetime tier alongside monthly/yearly subscription packages in the same offering.
- Source: RevenueCat Docs, *Non-Subscription Purchases* — https://www.revenuecat.com/docs/platform-resources/non-subscriptions (accessed 2026-11-11)

**Release implication:** The "lifetime" tier must (a) use a RevenueCat SDK version ≥ 7.11.0 on Android for proper
non-consumable semantics, and (b) the app must explicitly suppress re-offering the lifetime product to a customer who
already owns the matching entitlement, or users could be shown (and could tap) a redundant purchase of a product they
already permanently own. This is a concrete engineering requirement that must be verified against the RevenueCat SDK
version actually integrated at build time — **not yet configured in this repository**, so this is a forward
requirement, not a current-state claim.

### 8.3 Google Play subscription grace period / account hold are developer-configured
Google Play Console lets developers configure, per subscription: a **grace period** (how long a user keeps
entitlement access while a renewal payment is retried) and **account hold** (further hold duration before the
subscription is treated as expired), with Play Console computing "60 days minus any grace period duration" as a
related default; developers can adjust or disable these.
- Source: Google Play Console Help, *Create and manage subscriptions* — https://support.google.com/googleplay/android-developer/answer/140504 (accessed 2026-11-11)

**Release implication:** The PRD's "seven-day trial → monthly/yearly/lifetime" flow should explicitly state that
grace-period/account-hold durations for the monthly/yearly plans are a **Play Console configuration decision to be
made at subscription-setup time**, not an assumption baked into app logic; the storyboard should show a
"payment problem" state distinct from "trial ended" and "subscription active," since Play's grace/hold mechanics
introduce a third state the launcher must handle gracefully (continued access vs. restricted access) depending on how
those settings are configured — this is a release-configuration decision gate, not yet made.

---

## 9. Free trial wording, auto-renew disclosure, and lifetime (one-time) purchase distinction

### 9.1 Free trial / subscription wording policy (Google Play)
Google Play's Subscriptions policy explicitly flags as **violations**:
- "SKU names that do not accurately convey the nature of the subscription, such as 'Free Trial' or 'Try Premium
  membership - 3 days for free,' **for a subscription with an auto-recurring charge**."
- "Monthly subscriptions that do not inform users they will be automatically renewed and charged every month."
- "Offer only shows the introductory price and users may not understand what they will automatically be charged at
  the end of the introductory period" (from an annotated violation example on the same page).
- Developers "must be transparent about ... offer terms, the cost of your subscription, the frequency of your
  billing cycle, the automatic renewal terms, whether a subscription is required to use the app."
- A related subscription-creation guidance page states promotional benefit text itself must not reference "free
  trial" or price if not all users are eligible for that offer (e.g., "Try 7 days free" as a *benefit label* is
  disallowed when eligibility varies) — distinct from, but related to, the trial-disclosure requirement above.
- Source: Google Play Console Help, *Subscriptions* policy — https://support.google.com/googleplay/android-developer/answer/9900533 (accessed 2026-11-11)
- Source: Google Play Console Help, *Create and manage subscriptions* — https://support.google.com/googleplay/android-developer/answer/140504 (accessed 2026-11-11)

**Release implication:** The onboarding/paywall storyboard must, at the point of trial start, **explicitly and
prominently disclose**: (a) that the trial auto-converts to a paid auto-renewing subscription at a stated price and
billing frequency unless cancelled, (b) the exact date/price of the first charge, and (c) a clear, same-screen path to
see full terms — not a secondary/buried screen. "Free trial" as a button label is acceptable; using it as if the
plan were a one-time, non-renewing freebie (without renewal disclosure) is a documented policy violation pattern.

### 9.2 Cancellation/management access requirement
Apps selling subscriptions "must ensure that your app(s) clearly disclose how a user can manage or cancel their
subscription" and must provide in-app access to an easy-to-use cancellation method (a link to Play's Subscription
Center and/or the developer's own cancellation flow satisfies this).
- Source: same Subscriptions policy page as above (accessed 2026-11-11).

**Release implication:** The storyboard must include a reachable "Manage/Cancel subscription" entry point (e.g., in
caregiver settings), not only a purchase flow.

### 9.3 Lifetime (one-time) purchase vs. subscription wording
Google's **one-time-product** billing lifecycle documentation treats one-time purchases as a materially different
product type from subscriptions (no renewal, acknowledgment/consumption semantics, and a **3-day acknowledgment
window** after which an unacknowledged purchase is auto-refunded and revoked by Google Play).
- Source: Android Developers / Play Billing, *One-time purchase lifecycle* — https://developer.android.google.cn/google/play/billing/lifecycle/one-time (accessed 2026-11-11)

**Release implication:** The "Lifetime" tier must be implemented and worded as a **one-time, non-renewing purchase**
distinct from the monthly/yearly auto-renewing subscriptions — and the app (or RevenueCat, per §8.2 configuration)
must acknowledge the purchase within 3 days or Google Play will auto-refund and revoke it, which is a concrete
backend/SDK-integration requirement, not just copy wording.

---

## 10. Privacy & consent: not collecting contact/SMS/location *values*

### 10.1 Google Play Data Safety disclosure model
Google Play's Data Safety section requires developers to disclose data **collection** and **sharing**, but explicitly
carves out two cases where access does **not** need to be disclosed as "collected":
1. Data the app "accesses only on your device and it is not sent off your device" (the stated example is precisely
   location data used only for on-device functionality without transmission to a server).
2. Data "sent off the device but only processed ephemerally" (held in memory, used only to service a real-time
   request, not retained).
- Source: Google Play Help, *Understand app privacy and security practices with Google Play's Data safety section* — https://support.google.com/googleplay/answer/11416267 (accessed 2026-11-11)
- Source: Google Play Console Help, *Provide information for Google Play's Data safety section* — https://support.google.com/googleplay/android-developer/answer/10787469 (accessed 2026-11-11)

**Release implication:** The PRD's privacy stance ("don't collect contact/SMS/location values") is achievable within
Google's own disclosure framework **only if implementation matches it precisely**: contact names/numbers used solely
on-device to populate the custom dialer/SMS UI, and location used solely on-device (e.g., to show a caregiver a
one-time SOS location without the app's own backend storing it, or transmitted but genuinely ephemeral/not retained),
can be declared as **not collected**. If any of these values are transmitted to and retained by an app backend or
third-party SDK server (including Firebase Analytics default event parameters, if configured to capture them), that
reclassifies them as "collected" and "shared," requiring disclosure. This is a concrete implementation constraint, not
just a policy-wording choice: the architecture must avoid sending SMS content, contact list values, or precise
location to any backend (including Firebase/RevenueCat infrastructure) to honor the "not collected" claim.

### 10.2 Firebase Crashlytics / Analytics data collection surface
Firebase's own "Prepare for Google Play's data disclosure requirements" guidance documents what Crashlytics and
Analytics collect automatically vs. by developer configuration:
- Crashlytics collects **developer-defined data**: "any custom keys, logs, and free-text user IDs that a developer
  attaches to crash reports" and "any developer-defined non-fatal events with custom stack traces" — i.e., Crashlytics
  itself does not automatically capture contacts/SMS/location; it only captures what the integrating app explicitly
  logs into it (crash keys, breadcrumbs).
- Crashlytics optionally uses Google Analytics for Firebase "breadcrumb" logs (user actions before a crash + crash
  counts) **if the Analytics SDK is also included**.
- Google Analytics for Firebase separately documents **automatic** collection including "masked IP address" (used to
  "derive coarse location data"), in-app purchase/subscription events (product ID, name, price), and app lifecycle
  events (screen views, sessions) — none of which are raw contact/SMS/precise-location values by default, but IP-derived
  coarse location is itself a disclosable data type under Play's framework.
- Source: Firebase Docs, *Prepare for Google Play's data disclosure requirements* (Android) — https://firebase.google.com/docs/android/play-data-disclosure (last updated 2026-10-23; accessed 2026-11-11)
- Source: Google Analytics Help, *Prepare for Google Play's data disclosure requirements* — https://support.google.com/analytics/answer/11582702 (accessed 2026-11-11)

**Release implication:** To honor "do not collect contact/SMS/location values": (a) never pass raw contact names/phone
numbers, SMS bodies, or precise coordinates into Crashlytics custom keys/logs or Firebase Analytics event parameters —
treat these as forbidden fields in any analytics/crash instrumentation code path; (b) expect Firebase Analytics'
default IP-derived "coarse location" collection to still require a Data Safety disclosure entry for approximate
location, even if the app never explicitly reads device GPS; (c) Crashlytics/Analytics inclusion itself (even
unconfigured) still requires completing Firebase's own Play Data Safety mapping tables referenced above as part of
store submission — this is a documentation/config task for release, not yet done in this repository.

### 10.3 No store-approval or integration-status claim
This document does not assert that the Claro Home Launcher has been submitted to, reviewed by, or approved on Google
Play, nor that a RevenueCat project, Firebase project, or any SDK has been configured in this codebase. All findings
above describe **platform rules the implementation must satisfy**, to be validated during actual store submission and
backend configuration, which have not occurred.

---

## 11a. BaldPhone precedent: historical signal, not a currently verified blanket prohibition

A competitor/precedent check surfaced **BaldPhone** (an open-source senior-accessibility launcher combining
launcher, dialer, SMS, and other system-level functions — directly comparable in shape to Claro Home Launcher) as a
cautionary case. The following is what is **directly verifiable** from BaldPhone's own GitHub repository, versus what
is **not** verifiable and should not be asserted as current policy:

**Verified from primary/first-party sources:**
1. In a 2020 GitHub feature request asking BaldPhone to publish on Google Play (`UriahShaulMandel/BaldPhone` issue
   #216), the original maintainer is quoted (via a linked Reddit thread, reproduced in the GitHub issue) explaining
   that BaldPhone "actually used to be on Google Play, but after the permission requirements thing (which means
   BaldPhone would've to be split to BaldLauncher, BaldPhone, BaldRecentCalls, BaldAlarms, and more ridicul[o]us
   SubApps, I decided to remove it from there and open source it completely." A commenter in the same thread notes a
   Play Store listing did exist at the time but was "a somewhat crippled version."
   - Source: GitHub, `UriahShaulMandel/BaldPhone` issue #216, "Release BaldPhone on Play Store" — https://github.com/UriahShaulMandel/BaldPhone/issues/216 (accessed 2026-11-11)
2. In a separate, later GitHub issue (#1245, opened 2024-04-13), a user reports: "Google has removed Baldphone from
   the Play Store." **No reason for this 2024 removal is stated in the issue thread itself** — commenters speculate
   about project abandonment and recommend F-Droid as an alternative, but none cite an official Google takedown
   notice or reason code.
   - Source: GitHub, `UriahShaulMandel/BaldPhone` issue #1245, "Baldphone removed from Google Play" — https://github.com/UriahShaulMandel/BaldPhone/issues/1245 (accessed 2026-11-11)
3. A current fork, BaldPhone Neo, states in its own README (undated, read 2026-11-11): "At this stage, BaldPhone Neo
   is not available on Google Play. The current Google Play policies restrict apps that request multiple
   system-level permissions (e.g., managing calls, SMS, and media) - which are essential for BaldPhone Neo's all-in-one
   accessibility experience. Publishing on Google Play would require splitting the app into several smaller ones or
   removing critical functionality." This is the **fork maintainer's own characterization/decision**, not a quoted
   Google policy document or rejection notice.
   - Source: GitHub, `DamianKuzmiak/BaldPhoneNeo` README — https://github.com/DamianKuzmiak/BaldPhoneNeo (accessed 2026-11-11)

**What this evidence does *not* establish:**
- None of the three sources above is an official Google Play rejection notice, policy citation, or Play Console
  communication reproduced verbatim — they are developer self-reports and a fork maintainer's planning rationale.
- The 2020 explanation and the 2024 removal are **two different events roughly four years apart**, under what may
  have been materially different Play Store policy text each time (Google's SMS/Call Log permission policy has
  itself been revised multiple times, including a further scheduled change effective 2027-01-27 per
  §2.1/§9 above) — they should not be treated as one continuous, still-current rejection reason.
- Nothing in the currently published Android/Play documentation reviewed in §1–§2 of this report (RoleManager,
  default-handler guide, SMS/Call Log permission policy) states a **blanket prohibition on one app holding
  HOME + DIALER + SMS roles simultaneously**. The current, dated policy documents instead describe **conditional
  requirements** (privacy policy, described core functionality, appropriate permission scoping, correct request
  ordering — §2.2) and a **narrow, named exception list** for SMS/Call Log access by non-default-handler apps — a
  materially different (and more specific/auditable) framework than "combined system apps are banned."

**Recommendation — treat as a release gate, not a blanket prohibition:**
Given the gap between this BaldPhone precedent (self-reported, dated, reason partly unconfirmed) and the currently
published Google Play policy text (conditional, requirement-based), the PRD should **not** assert "Google Play
prohibits combined launcher/dialer/SMS apps" as a verified fact. Instead, this should be encoded as an explicit
**pre-submission release gate**:
1. Before committing engineering effort to claiming all three default-handler roles (`ROLE_HOME`, `ROLE_DIALER`,
   `ROLE_SMS`) in the same package, re-verify the then-current Play Console *SMS or Call Log permission groups* and
   *Permissions policy* pages (§2.1) against the shipping feature set, since this policy area is explicitly flagged
   by Google itself as subject to change (a change is already scheduled for 2027-01-27 — §2.1).
2. Prefer the minimal-permission architecture from §2.4 (intent-delegation instead of claiming default-handler roles)
   for v1 wherever the UX permits, specifically **to reduce exposure to this category of risk** regardless of whether
   the BaldPhone precedent would recur under current policy.
3. If default-handler roles are ultimately required for a feature (e.g., a genuinely custom in-app SMS inbox), treat
   first submission as a **policy-compliance spike**: submit to Play Console's pre-launch review / closed-testing
   track and obtain actual reviewer feedback before building the full combined-role experience, rather than assuming
   approval or rejection either way from this precedent alone.
4. Document in the PRD that this is an **identified risk with a historical analogue, not a resolved question** —
   revisit before each submission, since Google's named exception list and permission-group policy text are both
   explicitly subject to revision (§2.1).

---

## 11b. Commercial model: free download, 7-day free premium trial, separate lifetime purchase, and preserving basic functions after expiry

This section addresses the confirmed commercial direction: **free download** of the app; a **7-day free trial of
premium features**; conversion to a **monthly or yearly auto-renewing subscription**; a **Lifetime tier sold as a
separate one-time purchase** (not a trial-eligible path); and a product recommendation to **preserve basic
calls/messages/SOS functionality after trial expiry** for users who do not convert.

### 11b.1 Wording implications of "free download + free trial of premium"
This structure is distinct from "subscribe now, first N days free" gating the entire app, and is **more directly
alignable** with Google's policy language than an all-features-behind-trial model, provided the paywall/trial screen
still meets the disclosure bar from §9.1: any point where the user is enrolled into an **auto-renewing** monthly/yearly
plan (even with a free initial period) must disclose the auto-renewal, post-trial price, and billing cadence
prominently on the same screen as the trial offer — the "free download" framing does not relax this requirement,
since the policy's trigger is "a subscription with an auto-recurring charge," which still applies once trial-to-paid
conversion is auto-renewing. Re-affirmed against: Google Play Console Help, *Subscriptions* — https://support.google.com/googleplay/android-developer/answer/9900533 (accessed 2026-11-11).

### 11b.2 Lifetime as a separate one-time purchase, not part of the trial funnel
Selling Lifetime as a standalone one-time purchase (rather than "convert your trial to lifetime") is consistent with
the One-time purchase lifecycle model already documented in §9.3/§8.2: it is a materially different Play Billing
product type from the monthly/yearly subscriptions, with its own 3-day acknowledgment window (after which Google Play
auto-refunds/revokes an unacknowledged purchase) and its own RevenueCat non-consumable-entitlement handling
requirement (SDK ≥ 7.11.0 on Android; must suppress re-offering the product once owned — §8.2). **No new platform
constraint was found specific to selling Lifetime "separately" from the trial** beyond what §8.2/§9.3 already
establish; the only requirement is that the storyboard/paywall clearly present Lifetime as a distinct, non-renewing
product alongside (not substituting for) the monthly/yearly offers, which Google's own offering/package model
(multiple packages in one offering) supports natively.

### 11b.3 Preserving basic calls/messages/SOS after trial expiry — platform support and a concrete policy tie-in
Preserving core telephony/SMS/SOS functionality for users who do not convert to paid is not only a reasonable product
choice but is **reinforced by an existing platform requirement already identified in §2.2**, if the app claims any
default-handler role:
- The Android default-handler guide states a default handler app "must be able to perform the functionality for
  which it's a default handler. For example, a default SMS handler must be able to send text messages" — with no
  carve-out for "only if the user is a paying subscriber."
  - Source: Android Developers, *Permissions used only in default handlers* — https://developer.android.com/guide/topics/permissions/default-handlers (accessed 2026-11-11, already cited in §2.2).
- **Interpretation:** if Claro Home Launcher claims `ROLE_DIALER` and/or `ROLE_SMS` (see §2.3/§2.4 on whether this is
  necessary at all for v1), then gating *basic* calling/SMS send behind the paywall would put the app in tension with
  this stated default-handler functionality requirement — a user whose trial lapsed would, in effect, have a device
  default dialer/SMS app that can no longer place calls or send texts, which does not "perform the functionality for
  which it's a default handler." This is a **concrete, citable reason**, not only a goodwill/UX argument, to keep
  basic calling, basic SMS send, and the SOS path free and unlimited, gating only premium/convenience features
  (e.g., photo speed dials, home customization, caregiver dashboards, multi-contact management) behind the
  trial/subscription.
- This reasoning does **not** extend to the Home/launcher role itself in the same documented way (no equivalent
  "must remain minimally functional" clause was found for `ROLE_HOME` specifically in the sources reviewed), but the
  same safety/trust argument applies as a product-safety recommendation: a grandfather-facing launcher that stops
  being usable as a home screen or loses access to emergency contacts after an unnoticed trial lapse is a foreseeable
  harm to a vulnerable user population, independent of Play policy.

**Release recommendation:** Encode "free forever: phone dialer (basic calling), SMS send/receive for configured
contacts, and the SOS tile" as an explicit, permanent (non-trial-gated) feature set in the PRD, both as sound product
design for this user base and because it measurably reduces the specific default-handler policy-compliance risk
identified above if dialer/SMS default-handler roles are claimed at all.

---

## 12. Summary table — release gates and open risks

| Area | Gate / Constraint | Status |
|---|---|---|
| HOME+DIALER+SMS combined roles | API-independent per role; OEM skin behavior unverified | Needs device QA |
| Emergency numbers | Cannot be silently auto-dialed by any third-party app; OS always hands off to preloaded dialer | Hard platform constraint — scope SOS to non-emergency contacts |
| SMS/Call Log permissions | Require default-handler role, requested *before* runtime permission; avoidable entirely if only using intents | Design decision: prefer intent-only to minimize scope |
| SOS delivery status | `sentIntent`/`deliveryIntent` available; delivery report is carrier-dependent, not guaranteed | Must design "Sent" as the reliable state, "Delivered" as best-effort |
| Camera/Gallery | Delegate via `ACTION_IMAGE_CAPTURE`/Photo Picker; avoids storage permissions | Photo Picker specifics need a direct-source follow-up fetch before build |
| Background execution | Doze/App Standby defer background work; alarms capped ~1/9min when idle; `POST_NOTIFICATIONS` can be denied | Use FCM + in-app status, not background-only assumptions |
| PIN recovery | Not a documented platform security boundary; inferred engineering judgment | State as usability gate, not security control, in PRD |
| Licensing offline safety | RevenueCat caches `CustomerInfo`/entitlement mapping for offline reads; grace/account-hold durations are Play Console config, not SDK defaults | Configuration decision needed at Play Console subscription setup |
| Lifetime purchase | Must use RevenueCat SDK ≥ 7.11.0 on Android for non-consumable semantics; must suppress re-offer after purchase; 3-day ack window or Google auto-refunds | Concrete engineering + config requirement |
| Trial/subscription wording | "Free trial" label banned if it hides auto-renewal; must disclose price/cadence/cancellation path prominently | Storyboard copy must be rewritten to match policy |
| Privacy — no contact/SMS/location collection | Achievable under Play's "on-device only" / "ephemeral" carve-outs, but only if analytics/crash code paths never log raw values; Analytics' default coarse-location-by-IP still needs disclosure | Concrete "forbidden fields" rule for instrumentation code |
| Combined HOME+DIALER+SMS Play rejection risk (BaldPhone precedent) | Historical, self-reported developer account (2020 explanation, separate undated 2024 removal) — not a reproduced Google policy citation; current published policy is conditional/requirement-based, not a blanket ban | Pre-submission release gate: re-verify live policy text; prefer intent-delegation (§2.4) to reduce exposure; treat first combined-role submission as a compliance spike |
| Free trial + separate Lifetime purchase + free-forever basics | Trial-to-auto-renew disclosure rules (§9.1) still apply regardless of "free download" framing; Lifetime remains a distinct one-time-purchase product (§8.2/§9.3); default-handler functionality requirement (§2.2) argues for keeping basic calling/SMS/SOS free if dialer/SMS roles are claimed | Product + policy-aligned recommendation: gate only premium/convenience features, not basic calls/messages/SOS |

---

## 13. Explicit non-claims

- This document does **not** claim Google Play Store listing/app review approval.
- This document does **not** claim that Google Play currently prohibits, or will reject, a combined
  HOME+DIALER+SMS launcher app. The BaldPhone precedent in §11a is presented strictly as a **historical,
  self-reported signal** (developer/fork-maintainer accounts, not reproduced Google rejection notices) to be used as
  a pre-submission release gate, not as a verified current policy conclusion.
- This document does **not** claim any RevenueCat offering/entitlement/product, Firebase project, or Crashlytics/Analytics
  instrumentation has been configured in this repository — the repository was empty prior to this research task.
  All RevenueCat/Firebase behaviors described above are **documented platform/SDK behavior to design against**, not
  confirmation of an existing integration.
- Where I could not locate or directly fetch a primary Google/Android/RevenueCat source for a specific sub-claim
  (noted inline in §5 and §3.3), this is flagged as a **gap requiring a follow-up fetch** before implementation,
  rather than presented as verified.
