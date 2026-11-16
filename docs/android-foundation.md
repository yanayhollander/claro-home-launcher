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
Incoming SMS notifications show the sender and expandable message preview when
unlocked, with a generic public version for lock-screen privacy. Each thread has
its own notification intent; tapping it refreshes and opens the matching Claro
conversation, even from cold start or an unrelated inbox search. Opening the
thread clears that notification. Inbox SMS rows are Received, never Sent or
delivery-unconfirmed; outgoing messages retain honest carrier delivery status.
Conversation bubbles and inbox previews show a localized calendar date and
time. Newly received SMS store arrival time separately from the sender's
timestamp; the latter remains available for duplicate-broadcast reconciliation.
Messages with missing timestamps show an explicit unavailable label, not 1970
or the current time. SMS character/segment counters and the repeated carrier-
charge disclaimer are omitted from message screens.

## Phone and contacts implementation

- `feature:communication` owns the shared call/contact state machine, capability
  contracts, persisted speed-dial references and call outcomes.
- Home and Menu share real missed-call/unread-message badge projections.
  Becoming the default launcher never creates a new-item count. Opening Phone
  from Home/Menu does not mark a missed call reviewed automatically: Classic
  puts unreviewed missed calls first, and Touch opens its Missed section.
  Individual missed-call details and message conversations mark their items
  reviewed/read; the corresponding badges then decrease or disappear.
- Telecom callbacks are authoritative for dialing, ringing, connection, hold,
  audio routes, duration and disconnect outcomes. Submitting a call to Telecom
  never fabricates a connected state.
- Call actions dial immediately without a confirmation screen, including Home
  contacts, entered numbers and callbacks. Starting another call clears the
  previous disconnected session while waiting for Telecom. Opening a non-missed
  history entry prepares its number in the dialer rather than showing "Ended";
  the ended screen is reserved for an actual Telecom disconnect.
- End before submission cancels the pending request. End while waiting for a
  Telecom call ID is applied to the matching outgoing call as soon as it arrives,
  never to an unrelated incoming call. A pending call remains reachable from
  Return to call. Ordered, buffered Telecom events retain disconnect outcomes;
  disconnecting one call restores controls for any surviving active, held or
  incoming call instead of showing its ended screen over the remaining call.
- Emergency numbers hand off through the system dialer. Claro never silently
  selects a SIM/account or claims emergency completion.
- Android Contacts Provider remains the source of truth. Lists are paged,
  multiple numbers require explicit selection, and saved assignment snapshots
  become unresolved when a contact or selected number changes.
- Contact photos appear in Contacts, contact pickers, Home tiles and caller
  details in both styles. Photos are decoded off the main thread at bounded
  avatar sizes, with the existing generated portrait for absent or unreadable
  photos. Incoming caller photos are resolved through the Contacts provider.
- Calling qualification checks Phone-state access separately from account
  availability. Missing access requests Phone permission, not SIM/account repair.
- Touch and Classic presentations consume the same immutable communication
  state. Classic keeps its integrated control deck and permanent keypad.
- Classic Contacts keeps contact-detail Edit/Delete actions in Options only,
  without duplicate Edit/Delete buttons in the detail view.
  Detail and Options content start at the top rather than inheriting the
  contact list's scroll position. Writes require caregiver settings unlock;
  Add contact is a fixed person-plus icon in the Contacts title bar rather
  than a large list button, and uses the same protected action as Options.
  read-only contact actions stay disabled and deletion requires confirmation.
  Linked SOS contacts cannot be deleted until replaced. The Classic editor uses
  multi-tap text, numeric phone fields, Back to delete, green Save and red Cancel;
  long # still opens the software keyboard. Editing preserves additional phone
  numbers and their labels rather than dropping numbers outside the two fields.
- Classic Home displays saved Hold 1/2 names and routes long presses through
  the same speed-dial coordinator as Phone. Ordinary taps still enter digits;
  missing or unresolved assignments show the existing setup/repair guidance,
  never dial a different contact.
- Dialer digits show up to five matching saved contact numbers after a short
  debounce. Existing matches narrow immediately and recent-query previews
  restore immediately on Back; every query is revalidated in the background.
  The bounded preview cache lasts only while editing a number. A small inline
  progress indicator replaces the loading card, and stable contact-row keys
  preserve avatars/focus when results change. Provider lookups run off-main
  and ignore phone-number formatting.
  Classic dialer Contacts is a fixed, labeled-accessibility title-bar icon,
  not a row at the end of the recent-call list; Options keeps its shortcut.
  Selecting a match fills the full number and preserves its name/photo until
  the digits are edited; Call uses that identity. Recent calls selected for
  dialing also retain the name/photo. Classic Contacts options contain contact
  management/repair actions, not text deletion or emergency calling; Back still
  deletes search text.
- Active caller photo, name and number are pinned above the content in both
  styles. Status/duration are secondary. Classic calls collapse the number pad
  by default; Keypad shows a compact pad for DTMF and Hide keypad restores space.
  Speaker/Mute/End remain available; Hold/Swap stay in Options while expanded.
  Call controls retain readable labels alongside handset/speaker, microphone,
  keypad and hold/resume/swap icons; muted microphones show a crossed-out icon.
  Classic icons fit inline without increasing the pinned controls' height.
- Settings → Call audio controls automatic speaker/earpiece switching, enabled
  by default. It requires a far proximity reading before using the speaker;
  near/unknown/missing sensor data uses the earpiece. Headsets/Bluetooth are
  unchanged and manual audio selection suspends automation for that call.
  Maximum speaker volume is a separate, initially disabled option requiring
  warning confirmation; it applies only on the speaker after connection.
  The previous volume is restored on earpiece/headset/end unless the user
  adjusted it manually. Proximity is not reliable hearing protection and still
  needs physical-device validation.
- Incoming calls use a high-priority full-screen notification request with
  visible Answer and Decline actions. Actual lock-screen presentation remains
  subject to Android/OEM full-screen-intent policy.
- Dialing, connected and held calls have an ongoing notification with Return
  to call and Hang up controls; tapping it opens the current call, not a fresh
  dialer. This uses a standard call-category notification with an explicit
  Hang up action, not CallStyle, because the bound InCallService is not a
  foreground service and Android rejects that CallStyle notification.
  Notification visibility still depends on Android permissions and
  channel settings. A fixed Return to call bar appears across Claro screens
  whenever the active call controls are not already visible, including while
  browsing contacts. Opening Phone during a call also restores its controls.

Physical-device validation is still required for carrier connection/disconnect
causes, no-service and dual-SIM account selection, Bluetooth and proximity
behavior, call waiting/conference capability reporting, ringtone/DND behavior,
locked-screen incoming calls, emergency handoff, and OEM dialer-role repair.

The safety feature routes a configured trusted-contact
SOS through the shared Telecom call gateway. If the required role or permission
is absent, the request fails into a repair state instead of simulating success.
Emergency access is a separate native dialer handoff and is never gated by Claro
configuration or entitlement.
SOS setup opens a searchable contact/number picker or accepts a specific phone
number with an optional name. Saving requires a valid non-emergency number and
recipient agreement; invalid input leaves the existing configuration unchanged.
The recipient, selected contact identity and photo are stored privately across
restarts, and linked SOS contacts are protected from contact deletion.
An unconfigured SOS screen offers Set up SOS, not a disabled countdown button.
Configured screens show the recipient and a cancellable five-second call action;
active calls offer Return to call rather than another call request. Telecom
updates drive SOS call status, and a finished call requires an explicit new
countdown to call again. Emergency services opens an exported system emergency
dialer, never an empty normal dial intent that loops back into Claro. No emergency
number is preselected, and launch failures are shown explicitly.

Camera opens the device's own camera app immediately
(`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`, so flash, modes and settings are the
camera app's; Claro adds no save/discard step) and returns to the previous
screen when the user comes back. The Gallery lists the newest 300 images on the
device from `MediaStore` (explicit date-added/ID descending order, `READ_MEDIA_IMAGES`, with
`READ_MEDIA_VISUAL_USER_SELECTED` for partial access on Android 14+ and
`READ_EXTERNAL_STORAGE` up to API 32); there is no "add picture" import step.
Gallery observes MediaStore changes so late camera saves refresh automatically.
When a new photo arrives in the grid, it returns to the newest items at the top;
an open photo detail keeps its selection.
This deliberately overrides the original Claro-only gallery wording of
REQ-MEDIA-01/02 at the product owner's request. Deleting a photo still uses
Android's delete authorization flow. The Apps screen and Home/menu tiles show
each app's real launcher icon (`ClaroAppIcon`, falling back to a generic glyph),
launch only caregiver-allowlisted launcher activities and do not claim to
resize, lock, or control third-party apps.

Messaging UX rules: drafts persist automatically, so Compose has no
Cancel/Keep-draft buttons (system/product Back is the exit and Classic
Back deletes the last typed character first); Classic Options no longer repeat
Delete-character/Cancel; the failed-send screen keeps its PRD Cancel. A
successful send clears the draft and recipient query, and leaving Messages
resets the inbox search. Call history rows show only the time plus a trailing
incoming/outgoing/missed direction icon and read the system `CallLog`.
Classic Messages prioritizes reading: the inbox has no search field or number
pad, and its title bar has an accessible New message compose icon. New is also
available on the green key and in Options; there is no duplicate list button.
Conversations initially show messages and timestamps, without a reply field or
keypad. Reply opens a dedicated writing view with the keypad. Back leaves Reply
without deleting its draft, then returns from the conversation to the inbox;
Android Back follows the same navigation. Red Home remains an explicit exit.
The Reply field has its own backspace button for correcting text.
Touch conversation Back also returns to its inbox.
Classic conversation Options includes Delete conversation. Confirmation names
the recipient and warns that all SMS/MMS in that thread and its reply draft
are removed permanently. Only the current thread is targeted, Android must
allow Claro to write SMS, and deletion is verified before clearing its draft
or returning to the inbox. Failed/denied requests retain the conversation and
show an error; notifications and unread badges update after successful removal.
Onboarding offers Classic first (left) and preselected.
New messages and replies use a direct Send action (שליחה in Hebrew), without a
review/confirmation screen. The send action validates the recipient, body and
SMS readiness before submitting, and repeated taps cannot submit twice.
Unknown-outcome retries still require explicit duplicate-risk confirmation.
Reopening the dialer starts with an empty number and no pending call target in
both phone styles. Typing from Classic Home starts a fresh number, and external
`tel:` intents replace the previous entry instead of appending to it.

Native onboarding has one welcome action ("Set up Claro") and six setup steps,
ending with Permissions and roles → Finish setup → Home. Subscription offers,
trial messaging and the internal handover checklist are not part of setup;
membership stays available later in protected Settings. Both system Back and
the on-screen Back action return to the previous setup step. Permission rows
and their action buttons open the corresponding Android request.

New setup starts with the first supported device language (English, Spanish or
Hebrew, falling back to English). Language and display lets the user keep it or
choose another; saved choices survive restarts. Existing configured language
choices are preserved, and resetting Claro uses the device language again.
App navigation switches screens immediately, with no enter, exit or back fades.
Pressing Home on the current Home screen is a no-op, preserving its navigation
entry and UI state instead of rebuilding it. From Menu or another screen, Home
pops back to the existing matching Home entry, preserving its saved UI state;
only first setup or a style change without a matching entry creates a new Home.
Android system Home intents also return to Claro Home instead of leaving the
user on the last dialer/menu screen; incomplete setup remains in onboarding.
Home status (battery/signal), clock and date are fixed above an independently
scrolling tile region in both styles. Classic Home and Menu share one root-level
deck with the number-entry dialer. It stays mounted in the same position while
only NavHost content changes, including when a digit opens Phone from Home.
Home passes tile scrolling, digit entry and Hold 1/2 assignments into that deck;
the screen does not draw a second copy. No lifecycle delay removes/reinserts
the keypad when opening Menu or starting number entry. Dialer Options, Clear,
backspace, Call and Hold 1/2 use the same persistent deck with dialer callbacks.
These native setup decisions supersede the original PRD setup/billing copy;
the HTML storyboard remains a separate historical design reference.

After Language and display, Choose Home tiles offers Messages, Camera, Gallery
and Apps switches. Choices use the existing persisted Home slots and affect
Home only: Menu access, feature behavior, notifications and permission/role
setup remain unchanged. Existing tile visibility supplies the initial choices,
so a fresh setup still starts without Messages on Home. Adding a tile to a full
12-slot Home shows an explicit error and never replaces another tile.
The same switches are available in Home tiles and speed dial settings.
Installed WhatsApp/WhatsApp Business and Facebook/Facebook Lite also appear
as optional Home switches with their real app icons. Enabling creates a saved
Home slot and allows the app; hiding removes only its Home shortcut. App
visibility policy survives restarts, and a full Home never replaces a tile.
Setup has seven steps; its order is separate from persisted enum values so
Back navigation, progress and existing completed setups stay compatible.
Entering Permissions and roles starts sequential native Home, Phone and SMS
default-app requests, with Phone/SMS permission requests after role approval.
Cancelling a default-app prompt stops the sequence; Set Claro as default retries
without silently changing any Android defaults. Finish setup is disabled until
Android confirms all available roles belong to Claro, and completion verifies
them again before persisting or navigating Home. Unavailable roles on devices
without the relevant support do not block setup.

Classic screens with Claro's keypad do not open the Android keyboard when a
text field is focused. Long-press # to open it for the focused field; short #
and Hold 1/2 retain their existing actions. Moving to another field starts with
the system keyboard suppressed again. Touch mode and onboarding (which has no
Classic keypad) keep normal keyboard entry.
Focused text fields using the global Classic deck (including app search and
the Home tile picker) use the same multi-tap letters as Contacts and Messages.
Repeated presses cycle a key's letters; short # switches letters/numbers.
Number and phone fields keep numeric entry. Keypad edits respect the cursor
and selected text without opening Android's keyboard.
The global Back key deletes selected text or the preceding Unicode code point
while a field can be edited; otherwise it navigates back. Explicit screen Back
actions and the dedicated Contacts/Messages editing decks retain their behavior.

In-call controls are pinned below the scrollable caller details in both Touch
and Classic mode, so Speaker, Mute and Hold remain visible without scrolling;
Classic keeps End on its fixed red key and Touch keeps its fixed End call dock.
During a Classic call, expanding the keypad moves Hold/Swap to Options to
preserve space for the caller identity and primary controls.
Ended-call headings use a high-contrast red on the dark header.

The Home signal indicator reads Android's service state and signal level for
the default data subscription (falling back to the default subscription), using
the existing Phone permission. Missing access stays explicitly unknown rather
than displaying invented bars or "no service". Clock, battery and signal refresh
every five seconds while the app is resumed, stopping when it is backgrounded.
The Android status bar uses a black background and dark system icons to make it
less prominent than Claro's own status display. This is visual de-emphasis, not
kiosk mode: Android still owns the notification shade and swipe gesture.

Home is a configurable 12-slot grid (`HomeSnapshot.homeSlots`, `HOME_SLOT_COUNT`)
persisted by `HomeSlotStore` (`claro_home_slots`; the legacy `claro_favorites`
list is migrated). Each slot is optional and may hold a contact (direct call),
an allowed installed app, or a built-in shortcut (Phone, Messages, Camera,
Gallery, SOS, Apps, Alarm clock via `AlarmClock.ACTION_SHOW_ALARMS`); built-in
shortcuts are unique, contacts/apps may repeat. Defaults seed Phone, Camera,
Gallery, SOS and Apps (no Messages). Slots are edited only in the protected
"Home tiles & speed dial" settings screen. `HomeSnapshot.tiles` is the Classic
Menu list; the `isOnHome` app flag now means "Show in Menu". The Apps screen
lists allowed apps only (no search or edit entry); app allow-listing, Menu
visibility and search live in protected settings. The setup counter
`favoriteSlotsAssigned` follows the number of configured slots.
Installed-app discovery queries `ACTION_MAIN` with `CATEGORY_LAUNCHER` without
requiring `CATEGORY_DEFAULT`, so normal launcher apps can appear in the Home
tile picker. Disabled apps and Claro itself remain excluded.

The Alarm shortcut declares `com.android.alarm.permission.SET_ALARM`, required
by Samsung Clock for `ACTION_SHOW_ALARMS`. Missing handlers or a device refusing
the handoff show an error in Claro instead of crashing. The shortcut opens the
alarm list; it does not create or change an alarm.

The Menu order follows `HomeSnapshot.menuOrder` (protected Settings → "Menu
order", stored separately from slots so slot clears keep it); unknown or missing
keys fall back to the default order (Contacts, core tiles, apps, Settings).
Choosing a contact/app/shortcut for a Home tile or speed-dial key opens a
full-screen picker with search. The Apps editor's notice and search scroll with
the list. Focus is made visible by `ClaroFocusIndication` (amber ring, set in
`ClaroTheme`) plus `Modifier.claroFocusRing()` for Material buttons; the Classic
deck keeps Compose in keyboard input mode (a touch-mode listener flips it back) so
taps on Up/Down move through previous/next items in reading order, including
across grid columns, without resetting an existing focus. The ring stays hidden
(`ClaroFocusVisibility`) until the first arrow/D-pad press and hides again on the next screen.
The shared Classic control pad has Up, a full-width OK button and Down; it has
no left/right arrow buttons.
In the photo viewer, Down advances to the next photo and Up returns to the
previous one. Photo navigation is suspended while confirming deletion.

Settings unlock is session-long: once the Date/PIN gate is answered, protected
settings stay unlocked until the user taps "Lock settings" or the process is
recreated. This intentionally deviates from the PRD's two-minute inactivity
and screen-lock proposal at the user's request; "Done" leaves settings
without locking.

In Classic mode, settings/unlock/editor screens hand their action dock to the
hardware-style deck instead of drawing on-screen buttons
(`ClaroScreenScaffold(dockInSoftkeys = true)`, `ClaroSoftkeyHost` in
`core/design`): the primary button becomes the green key (Unlock, Done, Apply,
Save), a secondary button becomes the Menu key (for example Lock settings or
Forgot PIN), Cancel becomes the Back key relabelled "Cancel", and redundant
"back" buttons are dropped. Red destructive buttons (Reset Claro) stay on
screen. Touch mode, onboarding and the SOS senior screen keep their normal docks.
Unlock, Save (Language and display) and the Apps & Menu Done render a blue green-key
(`softkeyColor`). The Gallery photo detail has no on-screen Delete button: its
Options softkey opens the delete confirmation (Red Delete, Cancel on Back).

Language and display shows Language (with country flag) and Text size as rows that
open full-screen pickers (select-and-return, Back/Cancel closes), so more languages
can be added without crowding the screen. The High contrast option was removed
from the UI; saves always persist `highContrast = false`.

Protected settings are the only route to phone-style changes, allowed-app/Home
customization, contact creation/editing/deletion, SOS recipient configuration
and caregiver test calls, local Claro reset, membership, and diagnostics. Senior
SOS never exposes recipient inputs or test controls; tapping `Settings` from
SOS, or `Add person`/`Edit`/`Delete` from Contacts routes
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
