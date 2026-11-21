# Claro Home Launcher

Product planning and an interactive HTML storyboard for an Android launcher built
around confidence, familiarity and large, recognizable icons and photo controls.

The repository contains both a native Android app and a dependency-free browser
storyboard. **The storyboard uses fictional data and simulated outcomes.** It
never contacts anybody, requests Android permissions or processes a payment.
Its screens now follow the current native app flows, rather than the original
historical PRD alone.

## Explore

Live storyboard: <https://yanayhollander.github.io/claro-home-launcher/>.
GitHub Pages publishes the root of `main`. The `.nojekyll` file keeps the site
static and preserves the linked Markdown documents at their original paths.

Open `index.html` directly in a modern browser. No installation or build is needed.
For a local HTTP preview, run this from the repository root:

```sh
python3 -m http.server 4173 --bind 127.0.0.1
```

Then open <http://127.0.0.1:4173>. Stop the server with Ctrl+C.

Use the screen navigator to review individual screens or follow the buttons on the
phone for connected journeys. The side panel explains the intent behind each
screen. Review controls expose alternate and failure states.

Under **02 Dialer**, three indented historical alternatives preview **Contacts first**,
**Keypad first** (with a prominent Contacts button), and **Two big choices**.
These are interactive alternatives, not an approved replacement: the original
Dialer. Call actions now start the simulated call directly, without confirmation;
multiple-number contacts still require an explicit number choice. Long-press
shortcuts remain available. Historical `#call-confirm` and `#message-confirm`
links resolve to the dialer and message editor, not obsolete confirmation screens.

### Current app flows

- **Home:** Add as many tiles as you need for contacts, installed apps or built-in
  shortcuts, including Alarm clock. Defaults contain Phone, Camera, Gallery,
  SOS and Apps, without Messages. Built-ins are unique; contacts/apps may repeat.
  Clock/status stay fixed while tiles scroll. Both styles group signal and
  battery percentage at the top right. Home tiles offers
  an Add tile action, an editable list with up/down controls to reorder tiles,
  and searchable pickers.
- **Setup:** a friendly welcome, then Help keep Claro reliable, Phone style (Classic preselected),
  Language and display, Permissions and roles, Choose Home tiles, Settings access,
  Your VIP contacts, and SOS contact. A top-left Back arrow on each setup screen
  and picker revisits the previous screen; Welcome has no Back button.
  The extra Back/Home bar is hidden until first-launch setup is complete.
  Continue on the privacy step enables usage analytics and crash reports;
  there is no skip action. Collection stays off before Continue,
  advertising stays off, and existing installations keep their saved choices.
  VIP setup offers three suggestions (recent calls first among loaded contacts)
  and a searchable multi-picker; selections save directly as Home call tiles.
  SOS has a similar single-contact picker with explicit recipient agreement.
  The storyboard's full-flow strip opens every onboarding screen for review.
  Finish requires all supported Home/Phone/SMS roles; the sequential Android
  prompts are illustrated, and declining one stops the sequence.
  No plans, trial or handover checklist is included in setup.
- **Calls:** direct dialing, number-to-contact matches and history rows with
  direction indicators. Touch shows only configured speed-dial shortcuts, with
  a large labeled Contacts button above the keypad/history tabs. Neither dialer nor SOS has an emergency-services button;
  Android emergency calling remains separate. After the SOS countdown, the normal
  call screen shows live status and the same End, Speaker and Mute controls. Contact matches
  appear below the keypad so they don't move the number buttons while typing.
  Touch Contacts uses app-bar icons for Add, Edit, and Delete. Deleting a
  contact requires a separate confirmation in a dialog.
  In the Android app, both Touch and Classic can add or replace a photo when
  creating or editing a contact. The photo is written to Android Contacts on Save.
  Non-missed history fills the dialer instead of showing
  Ended. New calls start at Dialing; external review controls simulate connection
  and outcomes. A fixed Return to call action remains available across screens.
  Speaker/Mute/Hold controls are docked; Classic hides the number pad until Keypad.
- **Messages:** direct Send without review, date/time labels, retained new-message
  and per-recipient reply drafts, and explicit duplicate-risk confirmation for
  uncertain retries. Touch compose shows a recognized contact's photo, name and
  number, matching Classic; changing to a different number clears the old identity.
  Classic inbox/conversations have no number pad or inbox
  search; Reply opens a separate writing view. Back from Reply retains the draft.
  Options contains confirmed conversation deletion.
- **Settings:** session-long unlock, explicit Lock settings, Apps & Menu
  allowlisting/search, separate Menu order, Home slots, phone-style switching
  without losing in-memory data, and automatic call-audio settings with a
  maximum-volume warning. Language/text-size rows open full-screen pickers;
  Standard/Large/Huge and vibration replace the old contrast toggle.
  In the native app, the bottom of Settings shows the shared Firebase,
  Crashlytics and Mixpanel user ID above the installed app version. Tap the ID
  three times quickly to copy it. Viewing Settings never creates an ID or
  enables telemetry; the ID is unavailable when analytics and crash reporting
  are both off.
- **Media:** Camera opens its external illustration immediately and returns to
  the preceding screen. Gallery represents newest device photos without an
  import step. Classic Up/Down browses pictures, and Options opens confirmed
  device deletion. The browser never opens a real camera or deletes real files.
- **Membership:** default screens show unavailable/unconfigured store products.
  Historical purchase scenarios remain explicit external review states only;
  they do not represent verified plans, purchases or entitlements.

App discovery, contact photos, launcher icons, native notifications, sensor/audio
switching, haptics, store verification and Android permissions cannot run in this
static page. Original sample artwork stands in for device photos/icons. Core
navigation has English, Spanish and Hebrew/RTL previews; secondary copy and
multi-tap remain English, unlike the localized Android app. All configuration
and drafts reset when the page reloads. Changing style **inside Settings** keeps
this in-memory state; the external version/comparison links reload the preview.

### Reusable native demo kit

For future requests to install Claro with demo assets, reuse the saved
[demo kit and setup instructions](marketing/store/demo/README.md). It contains
fictional contacts, AI-generated portraits, an importable vCard and the Home tile
recipe. Native capture sources, store artwork and clean screenshots are also
preserved in `marketing/store/`; do not recreate the portraits or export a
device/account backup. Installing an APK alone does not import the demo data.

### Native phone-style previews

The onboarding and Settings style cards use native example captures, not a second
hand-drawn implementation of Home. Whenever either home layout changes, refresh
both `classic-home.png` and `touch-home.png` in `marketing/store/captures/` using
fictional, privacy-reviewed data, then run `python3 scripts/render-style-previews.py`.
The generated Android resources and source hashes must be updated together.

For store artwork, `python3 scripts/render-play-screenshots.py` regenerates
watermarked review compositions. Use `--clean` to export the five watermark-free
PNG candidates into `marketing/store/screenshots/`, with source/output hashes.
Clean exports do not constitute publication approval. Review
`marketing/store/screenshot-templates/store-candidates-review.png` before upload;
never upload the review boards or watermarked previews as store screenshots.

### Native Home widgets (Android only)

Unlock Settings, open **Home tiles**, choose a slot, then tap
**Choose widget**. Pick a compact widget from an installed app and complete
Android's approval and any setup supplied by that app. The existing tile stays
unchanged if setup is cancelled or fails.

Widgets occupy one tile in either phone style; widgets whose minimum size does
not fit are disabled in the picker. **Claro clock** is included as a compact live
time/date example with no account, permission or network requirement beyond
Android's widget-host approval. Changing phone style can make a widget too
large, in which case Home shows a warning instead of clipping its controls.
Widget buttons belong to their provider and can perform real actions, including
opening a gate. PalGate is available only if installed and exposing a compatible
Android widget. Removing or replacing a widget releases its Android widget ID.
The browser storyboard does not host Android widgets.

### Missed and unanswered calls

`#missed-call` shows who called and when, with Call back, Message and Home.
Open it from Phone's missed/recent history, the missed Home Phone badge, or the
classic Menu. Private callers show no callback/message action without a number.
`#call-unanswered` distinguishes an outgoing call with no answer and offers
Try again, Message and Home. Neither outcome shows a connected-call duration.

Use the incoming screen's external **missed** preview control (after choosing
known, unknown or private), or the active-call screen's **no answer** control.
Both screens are also available directly in the sidebar and comparison selector.
These are simulated outcomes, not an actual ring timeout or carrier callback.

Home and Menu share missed-call and unread-message badges. Opening Phone does
not mark a missed call reviewed; it prioritizes missed entries. Opening the inbox alone
does not mark messages read. Reading a missed call clears only that call's count,
and opening another person's conversation does not clear Mira's unread message.
Permission denial shows `?` instead of a guessed count. An unanswered outgoing
call does not increase the incoming missed-call count. Simulating a missed
incoming call returns Home with a new indication and retains earlier call records.
Home preview controls can show missed calls, unread messages or cleared alerts.

### Classic Phone controls

The **Touch Home / Classic Phone** switch above the storyboard navigation keeps
both presentations available. Open
<http://127.0.0.1:4173/?version=classic#home> for Classic.

Classic keeps a 12-key number pad, four large outer keys, and a connected
**Up / wide OK / Down** pad, with no left/right arrows. The current arrangement
has Back/Menu above Call/Home. Primary Unlock/Save/Done/Send actions use the
green-key position; Unlock, display Save and Apps Done use blue. Destructive
confirmations use the red key. Reading messages and collapsed calls hide the
number pad.

The right softkey opens Menu/Options and selects an option while that list is
open. Back deletes selected/preceding text before navigating, except Reply Back
retains the draft and returns to reading. Reply also has a backspace control.
Dialer Options has no Delete character or Clear text command; use Back to delete digits.
Contact Edit/Delete and photo/conversation Delete live in Options; destructive
actions require confirmation.

Open <http://127.0.0.1:4173/classic-buttons.html> to compare the base arrangement
and three alternatives side by side:

| Version | Arrangement | Tradeoff |
|---|---|---|
| A. Classic | Softkeys and call keys flank the connected pad | Most display space |
| B. Wide softkeys | Wide Back/Confirm row above the pad and call keys | Larger softkeys |
| C. Softkeys below | Wide Back/Confirm row below the pad and call keys | Softkeys closest to the numbers |
| D. Integrated pad | Wide corner keys with navigation fitted between them | Wider outer keys; only 32px taller than A |

Use the common screen selector to reset all four to the same scene. Otherwise,
each frame is independently interactive and keeps its own in-memory state.
The comparison scales 386×808 previews to fit four columns; full-size links and
the storyboard's Button arrangement selector are provided for touch-target review.
Individual layouts use `?version=classic&layout=classic`, `layout=wide`,
`layout=lower`, or `layout=stacked`, followed by the desired screen hash.
The existing `layout=stacked` URL now opens replacement D, the integrated pad.

Active calls show the caller photo, name and number with secondary status and
duration. Controls stay fixed below the scrollable details. The storyboard uses
a fixed connected sample duration, not a live timer.

Up/Down visits every selectable item in reading order, including across grid
columns, and scrolls it into view. The amber focus ring appears only after the
first arrow press on each screen. In photo detail, Up/Down browses pictures,
except during deletion confirmation. Lists and fields remain tappable.
Menu retains all built-in features independently of Home visibility.

Typing from Home starts fresh number entry, not a call. Green calls directly.
In both dialers, 1 and 2 are ordinary digits and cannot start a call.
Focused text fields use multi-tap: hold # to switch between English and Hebrew,
or tap # to cycle English, Hebrew, and numbers.
0 inserts a space in either text language, and phone/PIN/date fields stay numeric.
Date separators are inserted automatically. In the Android app, **Open keyboard**
shows the system keyboard for the focused field; another field suppresses it again.

Green answers incoming calls; red declines/ends a call or returns Home while
retaining SMS drafts. On the active-call screen the numbers simulate tones;
they never silently start a second call. Numbers are disabled during an incoming
call, SOS countdown or simulated external handoff. Real Android/third-party apps
cannot retain Claro's keypad; that platform boundary remains unchanged.

### Settings access

Setup offers **Date question** or **Caregiver PIN**. The initial preview uses the
date question: enter today's local date as **dd/MM/YYYY**. It has no PIN or
recovery phrase to forget and is only an accidental-change barrier, not a security
lock or an assessment of someone's abilities.

For PIN mode, create a 6-digit PIN and a separate 12+ character recovery secret.
There are no preassigned demo credentials. The date question cannot recover or
bypass a caregiver PIN. After setup, changing access methods requires the current
unlock method. Done and Home leave settings unlocked; **Lock settings** or a
page reload locks them. Three incorrect attempts pause entry for 30 seconds.
All choices remain in memory and reset on page reload.

## Visual review contract

The phone shows only the intended product UI, not developer/prototype notes or
demo disclaimers. Keep requirement IDs, implementation and system-boundary notes,
scenario labels and the fictional-data/no-real-actions disclosure in the external
review/annotation panel and this README. Phone screenshots are future
implementation references; concise safety, error and action-outcome text and legal
trial/renewal/price disclosures remain visible.

- Use original artwork, large recognizable icons and portraits, white/charcoal
  neutral surfaces and saturated green/blue/orange/violet/red accents, not muted
  beige/sage cards or copied competitor artwork.
- Home uses a configurable three-column grid with dark separators, 2–4dp gutters
  and minimal padding. Home tiles scroll below fixed status/clock.
  Use recognizable symbols, contact portraits and short labels.
- Keep day/date/time, a horizontal battery bar and stepped signal bars compact
  and neutral. Every control has an accessible name; visible labels and non-color
  cues remain where needed. Preserve ≥64dp primary targets, AA contrast and
  readable large-text reflow rather than squeezing or hiding critical content.
- The review shell must provide a **Phone only** toggle outside the phone, hiding
  navigation, annotations and review chrome for clean captures without changing
  screen state. Explain beside the toggle that **Escape** restores review; do not
  add an exit hint or prototype banner inside the phone capture.

These are presentation requirements, not claims of native functionality.
Native English, Spanish and Hebrew/RTL are implemented. The browser's limited
localization and device-only boundaries are described above.

## Product documents

- [Full PRD](docs/PRD.md): scope, behavior, commercial assumptions, privacy,
  acceptance criteria and native implementation phases.
- [Competitor research](docs/competitor-research.md): source-backed observations,
  accessible review evidence and differentiation.
- [Android feasibility](docs/platform-feasibility.md): Android roles, Google Play
  restrictions, safety limitations and integration requirements.

The proposed product direction is not final commercial or safety approval.
In particular, trial terms and pricing require confirmation, and Android phone,
SMS and emergency behavior require real-device validation before release.

## Native Android foundation

The modular Kotlin/Jetpack Compose app implements persisted Touch/Classic
presentation, onboarding, protected settings, Home customization, Telecom calls,
contacts, SMS/MMS, gallery, allowed apps and SOS integration. Native production
debug builds do not seed storyboard people or invented entitlements.
RevenueCat, Mixpanel, Firebase Analytics, Crashlytics and Meta App Events adapters
are configuration-gated. Onboarding Continue opts into usage analytics and crash
reporting; no collection starts before consent. Both remain independently
adjustable in Membership & privacy, and advertising measurement stays default-off.
Every custom analytics event is mirrored to Mixpanel and Firebase Analytics.
Detailed screen names, numeric Home tile counts and privacy-safe Home tile taps
support navigation and usage analysis. Mode, text size and tile counts are also
Mixpanel super/profile properties and Firebase default parameters/user properties;
contact names, numbers, messages and app identities are excluded.
Meta receives only approved activation and
conversion events, never calls, SMS, contacts or SOS activity; it also requires an
explicit build-time enablement after consent/queue review. Unconfigured
billing is explicitly unavailable rather than supplying fake plans.

See [Android foundation](docs/android-foundation.md) for the module graph,
dependency policy, and [local test and Internal Testing commands](docs/android-foundation.md#local-verification).
Android checks run locally on request, not on commits, pushes, or pull requests.

## Scope boundary

The storyboard is deliberately dependency-free HTML, CSS and JavaScript. It does
not initialize RevenueCat or Firebase, ship API credentials, collect analytics,
load third-party fonts or connect to a backend. Native vendor adapters require
environment-injected public configuration and never affect the storyboard.
