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

## Scope boundary

The storyboard is deliberately dependency-free HTML, CSS and JavaScript. It does
not initialize RevenueCat or Firebase, ship API credentials, collect analytics,
load third-party fonts or connect to a backend. The native app is a separate
implementation phase after review of this plan and storyboard.
