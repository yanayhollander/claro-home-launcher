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

Repeated navigation to the current screen with identical route arguments is a
no-op, rather than a single-top replacement that can remount and flicker.
Menu, Phone, Messages, apps, media and settings share this guard; different route
arguments still open a separate screen, and contextual Options menus still toggle.

Compact Android widgets are persisted as `WIDGET` Home slots with a host ID and
provider component, independently of app allowlisting. The protected slot picker
discovers Home-capable installed providers and disables widgets that cannot fit a
single tile, respecting each provider's permitted resize axes. `HomeWidgetSetup`
owns the `AppWidgetHost` lifecycle, Android binding approval, provider configuration,
pending-state restoration and removal of unused host IDs. A slot is committed only
after binding/configuration succeeds; cancellation leaves its previous value
untouched. Both Home styles use native `AppWidgetHostView` content, without an
outer shortcut click handler. Missing or oversized providers show an explicit
warning. Widget discovery uses an `APPWIDGET_UPDATE` visibility query, not broad
package access.

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
  **Open keyboard** shows the software keyboard. Editing preserves additional phone
  numbers and their labels rather than dropping numbers outside the two fields.
- Classic Home and both Phone dialers treat 1 and 2 as ordinary keys, without
  favorite names or direct-call shortcuts.
- Android Contacts supplies current photos for dialer matches, call history and
  screens, message threads and recipient headers, and contact tiles in both layouts.
  Visible avatars refresh when the Contacts provider reports a change.
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
The dedicated Emergency services shortcut opens a separate native dialer and
appears only when opening a configured SOS from its Home tile. Dialing an
emergency number manually still hands off to the system dialer.
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
scrolling tile region in both styles. Each NavHost destination lays out its
content and bottom controls together. Classic Home, Menu and Dialer use the
same deck component, docked below their content. Classic Messages and other
Phone views render their own controls instead. During Back, an outgoing
screen never gets the incoming screen's controls.
Back from Contacts opened in Phone returns to the dialer without clearing
the number. Contacts opened directly from Menu keeps its normal exit behavior.
On Classic Home or an empty Classic dialer, hold `*` to start an international
number with `+`. Tapping `*` or `#` enters that symbol; `#` only changes typing
mode in a text field.
These native setup decisions supersede the original PRD setup/billing copy.
The HTML storyboard follows these flows with in-memory sample data and
illustrated Android boundaries; it is not a native integration environment.

After Language and display, Choose Home tiles offers Messages, Camera, Gallery
and Apps switches. Choices use the existing persisted Home slots and affect
Home only: Menu access, feature behavior, notifications and permission/role
setup remain unchanged. Existing tile visibility supplies the initial choices,
so a fresh setup still starts without Messages on Home. Adding an enabled tile
appends it without replacing an existing tile.
Home tiles settings lists configured tiles with an Add tile action
and up/down controls that save the order for both Home styles.
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
text field is focused. Tap **Open keyboard** to open it for the focused field. Moving to another field starts with
the system keyboard suppressed again. Touch mode and onboarding (which has no
Classic keypad) keep normal keyboard entry.
Focused text fields using the global Classic deck (including app search and
the Home tile picker) use the same multi-tap letters as Contacts and Messages.
Repeated presses cycle a key's letters. Hold # to switch between English and Hebrew,
or tap # to cycle English, Hebrew, and numbers without changing the app language.
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

Home is a configurable grid without a fixed tile limit (`HomeSnapshot.homeSlots`)
persisted by `HomeSlotStore` (`claro_home_slots`; the legacy `claro_favorites`
list is migrated). Each slot is optional and may hold a contact (direct call),
an allowed installed app, or a built-in shortcut (Phone, Messages, Camera,
Gallery, SOS, Apps, Alarm clock via `AlarmClock.ACTION_SHOW_ALARMS`); built-in
shortcuts are unique, contacts/apps may repeat. Defaults seed Phone, Camera,
Gallery, SOS and Apps (no Messages). Slots are edited only in the protected
"Home tiles" settings screen. `HomeSnapshot.tiles` is the Classic
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

The membership/privacy screen links to the public
[privacy policy](https://yanayhollander.github.io/claro-public/privacy/),
[terms of use](https://yanayhollander.github.io/claro-public/terms/) and
[support page](https://yanayhollander.github.io/claro-public/support/).
Privacy and terms are also accessible from purchase review. These pages are
hosted separately in `yanayhollander/claro-public`; publishing them does not
enable telemetry or replace the documented SDK/privacy release review.

The app is safe to build with no commercial or telemetry configuration. Empty
values keep RevenueCat unavailable and analytics/crash collection disabled:

Mobile telemetry client values can also be stored in the gitignored,
owner-readable `telemetry.local.properties` at the repository root. Prefix keys
with `staging.` or `production.`, for example
`staging.CLARO_FIREBASE_APPLICATION_ID`. Debug builds select staging and release
builds select production. Environment variables override local values;
`CLARO_TELEMETRY_ENVIRONMENT` or `-Pclaro.telemetryEnvironment=staging` can select an
explicit environment. RevenueCat and Meta retain their separate environment-only
configuration; this local file must never contain admin credentials.

The registered Firebase projects are `claro-home-launcher` (production) and
`claro-home-launcher-staging`. Both register `dev.hollander.claro`. Staging also
registers `dev.hollander.claro.localtest`; `-Pclaro.localInstrumentation=true`
selects its `staging.CLARO_FIREBASE_LOCALTEST_APPLICATION_ID` and
`staging.CLARO_FIREBASE_LOCALTEST_API_KEY`. A local-test build rejects production
telemetry. Firebase app registration alone does not enable/link a GA4 property
or prove event/report delivery.

Mixpanel uses one US-region project named `Claro` for all environments. Set
`staging.CLARO_MIXPANEL_TOKEN` and `production.CLARO_MIXPANEL_TOKEN` to that
project's same public mobile token, with `https://api.mixpanel.com` as both
endpoints. Application events include the build-selected `environment` property.
Production reports must explicitly filter `environment=production`; staging
reports must filter `environment=staging`. Do not include events missing that
property. These filters separate reporting, not storage, access, or project-wide
quotas; native SDK-generated events may not have application event context.

```sh
export CLARO_REVENUECAT_PUBLIC_KEY='public_sdk_key'
export CLARO_MIXPANEL_TOKEN='public_project_token'
export CLARO_FIREBASE_APPLICATION_ID='firebase_application_id'
export CLARO_FIREBASE_API_KEY='firebase_public_api_key'
export CLARO_FIREBASE_PROJECT_ID='firebase_project_id'
export CLARO_FIREBASE_SENDER_ID='firebase_sender_id'
export CLARO_TELEMETRY_ENVIRONMENT='staging' # development, staging, production
# Choose the endpoint matching the Mixpanel project's region:
export CLARO_MIXPANEL_SERVER_URL='https://api-eu.mixpanel.com'
# Optional Meta App Events (public Android client configuration only):
export CLARO_META_APP_ID='meta_app_id'
export CLARO_META_CLIENT_TOKEN='meta_client_token'
# Leave unset until SDK consent/queue behavior and policy have been reviewed:
# export CLARO_META_ENABLED='true'
```

Only public Android client values are supported. Do not add RevenueCat secret
keys, Meta app secrets/server access tokens, service credentials or
`google-services.json`. App-level Firebase resources are generated from these
environment values, while a shared bootstrap initializes Firebase programmatically.
Firebase and Meta initialization providers are removed so they cannot initialize
ahead of the application consent controller. Meta uses the minimal `facebook-core`
artifact, not Facebook Login, Audience Network, or sharing.

Separate persisted consent toggles control usage analytics (Mixpanel/Firebase),
crash reports, and advertising measurement (Meta). All default to false, including
the new marketing choice on existing installations. Withdraw/reset turns all three
off. Consent is applied at application startup, including service-only launches;
features never wait for event delivery. SDK failures are logged locally by vendor
and exception class without exception text or user content.

Mixpanel, Firebase Analytics and Crashlytics use the same persisted random
installation UUID, assigned through each SDK's native identity API only when its
own consent is enabled. It is not a person/account ID, contains no contact or
hardware identifiers, and does not identify a person across devices or reinstalls.
Meta and RevenueCat keep separate identities. The shared ID is removed atomically
with saved consent when both usage analytics and crash reporting are off, so a
later grant gets a new ID; this does not delete records already sent to providers.
Failure to persist an ID disables new analytics/diagnostics collection rather
than sending mismatched IDs or interrupting core app features.

Normal unconfigured builds still work. For a build that must have telemetry
configuration, use `-Pclaro.requireTelemetry=true`; its pre-build check requires
Firebase and Mixpanel for the selected environment, plus Meta credentials if Meta
is enabled. The standalone check defaults to staging; explicitly select
production when checking release configuration. It does not build, run tests,
or upload events:

```sh
./gradlew :app:verifyTelemetryConfiguration
./gradlew :app:verifyTelemetryConfiguration -Pclaro.telemetryEnvironment=production
```

Register the actual `applicationId` from `app/build.gradle.kts` and its `.localtest`
variant in the appropriate nonproduction/production vendor apps. Never point
developer/test purchases at production Meta reporting. Firebase projects and Meta
apps/datasets remain separate by environment. Mixpanel intentionally shares one
project, using the explicit environment filters described above rather than
claiming project-level isolation.

### Event routing and semantics

The shared typed API is `core/domain/.../Telemetry.kt`. Feature modules depend on
`ProductAnalytics` / `CrashReporter`, never on a vendor SDK. Serialization/routing
is centralized in `TelemetryEnvelope.kt` and `ConsentAwareObservability`.

| Events | Primary destination | Additional Meta event, with marketing consent |
|---|---|---|
| `session_started` | Mixpanel | Activate App plus the SDK's supported install publication |
| `setup_step_result`, `setup_completed` | Mixpanel | Complete Tutorial for completed setup only |
| `screen_view`, `visual_mode_selected`, `accessibility_preset_selected` | Mixpanel | None |
| `home_changed`, widget/call/SMS/SOS/external-action task outcomes | Mixpanel | None |
| `membership_screen_view` | Mixpanel | View Content |
| `checkout_started` | Mixpanel | Initiate Checkout |
| `conversion` trial/subscription/purchase | Mixpanel | Start Trial / Subscribe / Purchase |
| `role_readiness`, startup recovery task outcome | Firebase Analytics | None |
| Unexpected technical failures, safe breadcrumbs | Crashlytics | None |

Foreground sessions begin on first foreground or after 30 minutes in the
background; activity recreation and repeated foreground callbacks do not create
another session. Session emission is deduplicated per destination, so newly
granted marketing consent does not duplicate Mixpanel activation. Screen views
come from navigation entries rather than recomposition. Persisted setup/display
changes are observed without replaying old preferences on process start.
Diagnostics are offered after onboarding, so initial setup coverage is limited
to events that actually occur after consent; no historical funnel is backfilled.

Call placement means submitted to Telecom, not connected. SMS submission, carrier
sent, and delivery confirmation are separate outcomes. SOS telemetry contains
only coarse outcomes and a test-mode boolean, never a destination or a claim that
help arrived. Role snapshots are deduplicated until the state changes. Widgets
complete only after their slot is persisted; cancellation/unavailability are
separate outcomes. No arbitrary strings, contact/phone/message/media content,
PIN/recovery values or provider identifiers are accepted by the public event API.
SMS delivery callbacks are observed once at application scope, independently of
the messaging screen, with a bounded 512-callback process-local deduplication
window. Reopening a screen does not replay delivery telemetry.

Meta receives no visual mode, text size, readiness, screen arguments, or
call/SMS/SOS events. It has no Complete Registration event because Claro has no
registration flow. Automatic Meta events, metadata, product-improvement events
and advertising-ID collection are disabled; the merged AD_ID permission is
removed along with the SDK-added AdServices advertising-ID, attribution,
custom-audience and topics permissions. Mixpanel's typed initialization options
disable automatic events, autocapture and feature flags and set the correct
regional endpoint before SDK construction. IP geolocation is off; its SDK still
emits its documented `$opt_in` consent event. Firebase may emit its own automatic
events after analytics consent. SDK/device/network identifiers still require
disclosure; advertising-ID absence does not make these integrations anonymous.

### Verified billing conversions

The purchase callback matches a purchased Google transaction/order against
RevenueCat's active matching entitlement and transaction data. Only a new
verified callback can produce acquisition/revenue events; refresh/restore never
does. Monetary values come from the matching RevenueCat transaction's structured
charged price, converted from micros using `BigDecimal`, not displayed strings
or a subscription's advertised future renewal price. Missing/mismatched evidence
is logged and the conversion is omitted, without affecting billing.

Trial activation emits Start Trial only. A paid subscription emits Subscribe for
acquisition and Purchase for its actual positive charge; count only Purchase as
revenue, not both as two orders. A lifetime charge emits Purchase only. Hashed
transaction markers are persisted locally before emission to prefer a missed
conversion over duplicated revenue after process death. Raw order IDs/tokens
never leave the billing mapper. Markers are not erased by diagnostics/local Claro
reset, preventing an old purchase from becoming a new conversion.

Sandbox transactions are excluded when `CLARO_TELEMETRY_ENVIRONMENT=production`.
Client reporting does not reliably cover renewals, refunds, trial-to-paid charges,
or purchases completed while the app is absent. Do not also enable automatic IAP,
RevenueCat-to-Meta partner reporting or a server integration for the same events
without a supported and verified deduplication design.

### Release gates and known SDK limits

Crashlytics automatic uploads remain disabled. A process started with persisted
crash consent explicitly sends its previous pending reports; granting consent
inside a process deletes earlier pending reports instead of backfilling them.
Withdrawal stops wrapper recording and requests deletion of unsent reports.
Firebase may still store automatic crashes locally while uploads are disabled,
and requests already in flight cannot be recalled. The application wrapper strips
handled exception messages/causes/suppressed data; automatically captured fatal
crashes and ANRs bypass it and need a separate sensitive-data audit.
Repeated handled failures with the same operation, exception type and top frame
are recorded at most once per foreground session.

Meta remains unavailable unless `CLARO_META_ENABLED=true` as well as valid public
configuration and user marketing consent. This flag is a release gate, not a
claim that consent/network behavior has been verified. Its SDK has no supported
public API that guarantees erasing all manually queued events or fully shutting
down initialized SDK work. Withdrawal closes the application event gate and
disables the applicable settings, but cannot promise to cancel already queued
uploads. Keep Meta disabled until the approved privacy policy and real-device
review accept this limitation or a supported stronger design is available.
Keep codeless/suggested-event features off in Meta's dashboard and include
remote SDK feature behavior in that review; manual event gates are not proof
that an initialized SDK has stopped all internal work.

Before enabling production collection: inspect real SDK traffic for fresh install,
all eight consent combinations, restart, offline/reconnect, withdrawal and
re-consent; verify the merged manifest and readable release crash reports; inspect
Meta Test Events/Events Manager, Firebase DebugView and Mixpanel receipt with
synthetic data. Wrapper inspection and fake-vendor tests do not prove SDK delivery
or absence of background requests. Tests run only on request.

### Basic report definitions

These are definitions for saved console reports, not a claim that dashboards or
data ingestion have already been configured. In the shared Claro Mixpanel project,
use separate production and staging boards, with matching `environment` filters on
every saved query and the board itself. Keep active-installation, funnel, retention,
and amount calculations within one environment, not just visually broken down
after aggregation. Use a consistent reporting time zone and describe users as consenting
installations rather than people. Do not register the shared UUID as a custom
event dimension; use the SDK-native user-ID fields.

| Report | Destination | Definition |
|---|---|---|
| Daily and 28-day active installations | Mixpanel | Unique IDs with `session_started`, daily trend over 28 days; optional `visual_mode` breakdown. |
| Core feature outcomes | Mixpanel | `task_flow_result` counts grouped by `task` and `outcome`; keep started, submitted, sent and verified outcomes distinct instead of claiming call connections or assistance. |
| Membership funnel | Mixpanel | Completed `membership_screen_view` to `checkout_started` to `conversion`; break verified results into trial, subscription and purchase instead of counting all three as payments. |
| Client-observed charged amounts | Mixpanel | Sum numeric `amount` on `conversion` where `kind=purchase`, grouped by `currency`; never sum different currencies or count Subscribe as a second payment. Excludes unobserved renewals, refunds and trial-to-paid charges. |
| Readiness and startup health | Firebase Analytics / GA4 | `role_readiness` grouped by `capability` and `outcome`; `task_flow_result` filtered to `task=startup_recovery`, grouped by outcome. Register the corresponding event-scoped dimensions plus `environment` before expecting historical custom-dimension reports. |
| Reliability | Crashlytics | Built-in crash-free users, fatal/nonfatal issues and ANRs, segmented by app version over 7/28 days. Coverage and latency depend on diagnostics consent and the explicit next-start upload policy. |

Use the shared installation UUID to correlate permitted Mixpanel activity with
Crashlytics diagnostics and Firebase native user IDs; it is not a replacement for
an authenticated cross-device account identity. GA4 custom-dimension registration
is not retroactive, and aggregated reports can lag DebugView/realtime receipt.
Do not treat setup events as a complete first-install funnel: consent may only
be granted after setup, and the app does not backfill earlier activity.

In the vendor consoles, configure data retention/deletion, access roles, Meta
Business Portfolio/ad account and optimization eligibility, dashboard definitions,
and alert owners. Start with new/regressed fatal, ANR and crash-velocity alerts.
Monitor consented sessions/retention, task success vs uncertain outcomes, purchase
funnel and purchase-only revenue; use the PRD's provisional 99.5% crash-free
sessions beta target only with a meaningful consented sample. These delayed tools
are not emergency monitoring. Update privacy policy and Play Data Safety based on
verified SDK behavior before release. No project provisioning, dashboard setup or
live event ingestion is implied by a successful local build.

## Toolchain and dependency policy

Versions are pinned in `gradle/libs.versions.toml`; dynamic and preview versions
are prohibited. The baseline uses JDK 17 bytecode, minSdk 29, and compile/target
SDK 37. Dependabot proposes grouped weekly updates, but upgrades must be checked
against official Android, Kotlin, Firebase, Mixpanel, and RevenueCat release
notes before merging.

Run the Gradle wrapper locally with JDK 17. To refresh dependency checksums after an approved
upgrade, run:

```sh
./gradlew --write-verification-metadata sha256 :app:assembleDebug
```

Review the generated `gradle/verification-metadata.xml` diff and commit it only
after confirming repositories and artifacts. Do not copy SDKs, Gradle caches,
emulator data, or build directories into repository or session artifact storage.

## Local verification

Tests run only when requested or when preparing a build for Play Internal Testing.
There is no Android test workflow on GitHub Actions. Set `ANDROID_HOME` (or
`ANDROID_SDK_ROOT`) to an SDK containing Android 37 and platform-tools. For device
tests, use `adb devices` to find an already-booted emulator or designated test
phone and pass its serial with `--device` (or set `ANDROID_SERIAL`). Do not target
a phone someone relies on for calls or messages.

```sh
scripts/android-local jvm
scripts/android-local jvm :app '*IntegrationContractsTest'
scripts/android-local lint
scripts/android-local ui --device emulator-5554
scripts/android-local ui --device emulator-5554 \
  --class 'com.clarohome.app.StoryboardRegressionTest#touchHomeScrollsTilesWithoutMovingStatusHeader'
scripts/android-local all --device emulator-5554
```

`jvm` and `lint` need no device. `all` checks the debug build, runs the JVM suites
and lint across modules, and runs unfiltered connected debug instrumentation on
the selected device. The runner uses `dev.hollander.claro.localtest` for
instrumentation so Gradle does not install over or remove the regular Claro app.
It does not wipe the device or automatically change Android default apps. The
reports are under each module's `build/reports/`, including
`app/build/reports/androidTests/connected/debug/` for the current Android UI suite.
Some real-activity tests are conditional on role, notification, or initial state;
review skipped cases and verify those scenarios on a dedicated test device.
When screenshot comparisons, UI Automator, or performance suites are added,
expose their local commands here too.

### Play Internal Testing

Release builds enable AGP 9.5 app optimization: R8 code shrinking, optimization,
obfuscation and resource shrinking, with the platform's default optimized keep
rules. Debug builds remain unminified. Dependencies supply their consumer keep
rules; add narrowly scoped application rules under `app/src/release/keepRules/`
only when an actual reflective entry point requires them.

The release AAB includes the generated R8 mapping for Play deobfuscation. Native
debug symbol level is `FULL`; AGP packages symbols when the input libraries
contain them. Already-stripped third-party native libraries may still trigger a
Play symbol warning and require matching symbols from their publisher. Preserve
the mapping and available native symbols alongside the exact signed candidate;
never reuse diagnostic files from a different build.

Automatic Crashlytics mapping upload is disabled: this app injects Firebase
resource values directly instead of applying the Google Services plugin required
by that upload task. This does not disable crash reporting or Play's bundled
mapping, but Firebase deobfuscation requires a separate matching-map upload.

Inspect all Play tracks and artifacts before choosing a version code greater
than every previously used code; a local version choice does not reserve it.
Use the existing private Play upload keystore from outside the repository and
Copilot session storage. Never replace an established upload identity.
`jarsigner` prompts locally for passwords. Run:

```sh
export CLARO_UPLOAD_KEYSTORE=/path/to/upload-key.jks
export CLARO_UPLOAD_KEY_ALIAS=your-upload-alias
scripts/android-local internal --device emulator-5554
```

This runs the full local validation before building and signing the release AAB.
It stops on a failed check or an unavailable test device. To prepare a bundle
without running tests or interacting with a device, use:

```sh
export CLARO_VERSION_CODE=2 # Example only: inspect the live store first.
export CLARO_VERSION_NAME=0.1.0
scripts/android-local bundle
```

The version overrides map to Gradle properties `claro.versionCode` and
`claro.versionName`; omitting them preserves the app defaults. For unattended
local signing, inject **both** `CLARO_UPLOAD_STORE_PASSWORD` and
`CLARO_UPLOAD_KEY_PASSWORD` from the approved password manager into the process
environment. Do not put their values in command arguments, chat, logs, Git, or
GitHub secrets for this local workflow. Supplying only one password fails.

Only upload the AAB
path printed by the successful invocation to **Play Console → Internal testing**;
do not upload a prior or unsigned Gradle output. The script does not upload or
publish anything. Do not edit the checkout during preparation; if the candidate
changes, rerun the gate. The debug device suite is not a substitute for checking
the signed build and the physical-device scenarios below.

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
