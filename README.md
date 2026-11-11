# Claro Home Launcher

Product planning and an interactive HTML storyboard for an Android launcher built
around confidence, familiarity and large, recognizable icons and photo controls.

**This is a design prototype, not an Android application.** Calls, SMS, emergency
actions, permissions, subscriptions and device readings use fictional demo data.
It never contacts anybody, requests Android permissions or processes a payment.

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

Under **02 Dialer**, three indented options preview **Contacts first**,
**Keypad first** (with a prominent Contacts button), and **Two big choices**.
These are interactive alternatives, not an approved replacement: the original
Dialer and the Home Phone tile remain unchanged. Single-number contacts in these
options open call confirmation directly; multiple-number contacts still require
an explicit number choice. Keypad long-press shortcuts remain available.

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

Home shows persistent missed-call and unread-message counts: large tappable
notification shortcuts beside the clock in 2G mode, and Phone/Messages tile badges
in touch mode. They open the relevant call or inbox; opening the inbox alone
does not mark messages read. Reading a missed call clears only that call's count,
and opening another person's conversation does not clear Mira's unread message.
Permission denial shows `?` instead of a guessed count. An unanswered outgoing
call does not increase the incoming missed-call count. Simulating a missed
incoming call returns Home with a new indication and retains earlier call records.
Home preview controls can show missed calls, unread messages or cleared alerts.

### Separate 2G phone version

The **Original / 2G phone** switch above the storyboard navigation keeps both
designs available. Open <http://127.0.0.1:4173/?version=classic#home> for the
classic concept; the original URL and its dialer alternatives are unchanged.
Switching versions reloads the in-memory prototype rather than sharing drafts.

The 2G concept keeps a 12-key number pad, four large outer keys, and one connected
four-way pad with center OK. Back always stays on the left and the primary
Confirm/Unlock/Save/Send softkey on the right. Green Call/Answer stays left and red
Home/End/Decline stays right. Their positions do not move between screens within
an arrangement. There are no separate permanent Delete or Options keys.

The right softkey opens Menu/Options when there is no primary action, and selects
an option while that list is open. When a screen has both a primary action and
secondary choices, an Options navigation row in the display opens the latter.
Text deletion and clearing are included in Options alongside secondary actions
such as Edit, Delete contact, Cancel and navigation shortcuts. Text deletion never
deletes a contact/photo; destructive choices still require separate confirmation.

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

The classic active-call screen shows status and duration near the top without
requiring an initial scroll, followed by a centered caller photo, name and number.
Longer caller details scroll above the fixed phone controls.
The storyboard uses a fixed sample duration, not a live call timer.

The four arrows highlight contacts, menu items and fields; OK opens/selects the
highlighted item. Left/right move the text cursor or change a selected dropdown;
up/down move between fields. Photo-view left/right show the previous/next photo.
When no selectable item remains above/below, up/down scroll the display with
overlap. The selected item scrolls into view. Lists, photos, selection controls
and input fields also remain tappable, and swiping still scrolls the display.
Home prioritizes the clock, four people, Messages and SOS. Menu contains Phone,
Contacts, Messages, Camera, Gallery, SOS, Apps and protected Settings instead of
repeating the original six-tile home. The same contact assignments and access
rules are used within each preview session.

Typing from Home starts number entry, not a call. Green opens call confirmation,
then confirms it. Confirmation has no duplicate Call or Cancel buttons in the
display: use the green Call softkey to proceed, or Back/Home to leave.
Long-pressing 1/2 for 0.8 seconds calls the assigned person
directly only from idle Home/Phone. Text/date/PIN entry cannot trigger speed dial.
Focused fields use the fixed keys: English ABC multi-tap for text, # to switch
ABC/123, 0 for space, and number entry for phone/PIN/date fields. The mode indicator
appears only for text entry, labeled Letters or Numbers, never as an idle "123".
Date separators are inserted automatically. This is a basic English text-entry preview, not T9
prediction or a completed Spanish/Hebrew keyboard.

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

For PIN mode, create a 4–6 digit PIN and a separate recovery phrase in Setup.
There are no preassigned demo credentials. The date question cannot recover or
bypass a caregiver PIN. After setup, changing access methods requires the current
unlock method. Returning Home locks settings; three incorrect attempts pause
entry for 30 seconds. All choices remain in memory and reset on page reload.

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
- At the 360×800dp reference view, fit six big rectangular light tiles in a tight
  2×3 grid with dark separators, 2–4dp gutters and minimal padding, plus four visible
  photo contacts with short names. Use handset/envelope/camera/photo/SOS/apps
  symbols and short recognition labels; no demo-initials notes.
- Keep day/date/time, a horizontal battery bar and stepped signal bars compact
  and neutral. Every control has an accessible name; visible labels and non-color
  cues remain where needed. Preserve ≥64dp primary targets, AA contrast and
  readable large-text reflow rather than squeezing or hiding critical content.
- The review shell must provide a **Phone only** toggle outside the phone, hiding
  navigation, annotations and review chrome for clean captures without changing
  screen state. Explain beside the toggle that **Escape** restores review; do not
  add an exit hint or prototype banner inside the phone capture.

These are presentation requirements, not claims of native functionality.
English, Spanish and Hebrew with RTL remain planned; native scope and commercial
decisions are unchanged.

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

The repository also contains a modular Kotlin and Jetpack Compose application
scaffold. It preserves the static storyboard and establishes replaceable
data/platform/vendor boundaries, persisted Touch/Classic visual mode, typed
navigation, startup recovery, deterministic fake data, and accessible placeholder
homes without implementing production phone, SMS, billing, or telemetry flows.

See [Android foundation](docs/android-foundation.md) for the module graph,
dependency policy, and local build commands.

## Scope boundary

The storyboard is deliberately dependency-free HTML, CSS and JavaScript. It does
not initialize RevenueCat or Firebase, ship API credentials, collect analytics,
load third-party fonts or connect to a backend. The native app is a separate
implementation phase after review of this plan and storyboard.
