# Claro Home Launcher — Product Requirements Document

**Status:** Proposed baseline for owner review; planning only  
**Version:** 1.1 · **Date:** 11 November 2026  
**Audience:** Product owner, designer/storyboard author, Android engineering, accessibility and release QA

## 1. Product intent and decision authority

Claro helps a grandfather moving from a 2G feature phone use an Android phone confidently: recognize family, make and answer calls, read and send messages, take and view photos, and ask a trusted person for help. The senior gets a predictable everyday surface; a capable senior can manage setup/preferences independently, or a caregiver can manage them on their behalf.

The product is an original, icon- and photo-led experience with dense, high-contrast tiles, **not a visual clone of BIG Launcher**. Large targets alone are insufficient: incoming calls, messaging, errors, permission loss and subscription expiry must remain understandable.

This document defines the required target and its validation plan. It does **not** authorize Android implementation. Native development starts only after owner review and approval of the next phase. The HTML storyboard is a simulation, not a working launcher, calling service or billing integration.

### 1.1 Confirmed direction

- Six initial home tiles: **Phone, Messages, Camera, Gallery, SOS, Apps**; additional tiles scroll.
- Large text and image/icon tiles; four photo speed-dial squares, visible together rather than hidden in a carousel; configurable direct-call long presses on dialer keys **1** and **2**.
- Claro-owned simplified **dialer, contacts and messaging**, including incoming and active calling UI in the integrated target.
- Allowlisted app menu, customizable additional home tiles and protected preferences. During setup, choose a **today's-date question (dd/MM/YYYY)** with no remembered secret, or a **caregiver PIN** with separate recovery. The date question is only an accidental-change barrier, not a secure caregiver lock.
- Free download; a **seven-day free premium trial** through explicit eligible Play subscription enrollment; monthly/yearly subscriptions afterward; lifetime sold separately as a one-time purchase.
- Language response preserved verbatim: **“english, spanish and hebrew and right to left support planned”**. Plan English, Spanish, Hebrew and RTL; the storyboard is English with representative language previews.
- RevenueCat for purchase/entitlement integration; Firebase Analytics and Crashlytics for privacy-reviewed measurement and reliability.

No commercial prices, Android minimum, store approval, SDK configuration or completed usability testing are asserted here.

### 1.2 Sources and interpretation

Read alongside [competitor research](competitor-research.md) and [platform feasibility](platform-feasibility.md), researched on 11 November 2026. Competitor reviews are qualitative signals, not population statistics. Historical packages, publisher-curated reviews, aggregator estimates and missing review evidence retain the limitations documented there.

Design implications: visible status and missed events; large answer/end-call controls; searchable contact assignment even with thousands of contacts; integrated everyday flows; free, selectable settings-access protection; and explicit maintenance/reliability investment.

**Product decisions take precedence over research recommendations:** the intent-only route is an early validation phase, not the final messaging scope. Photo speed dials, basic customization and safety configuration are not removed after expiry. No caregiver companion app, backend alerting service or remote dashboard is approved.

**Technical clarification:** do not interpret the research's `MANAGE_OWN_CALLS`/`ConnectionService` discussion as a blanket requirement for replacing the SIM-call UI. Claro's default-phone target uses `InCallService`, Telecom and the dialer role; self-managed/VoIP connection ownership is a separate architecture, not this product's requirement. Emergency routing and full handler obligations must be verified against current primary documentation during the spike. [P1–P4]

## 2. People, problems and proposed outcomes

| Person | Primary needs | Success looks like |
|---|---|---|
| Senior, primary user | Recognizable people, readable words, obvious actions, recoverable mistakes; limited smartphone vocabulary | Calls family, answers a call, reads/replies to SMS and returns Home without coaching |
| Caregiver, setup user | Assign contacts, protect preferences, check readiness, understand purchases | Sets up a safe phone, verifies calls/SMS/SOS and can recover configuration access locally |
| Trusted SOS recipient | Understand that this is a family help call, not monitored dispatch | Receives an ordinary call when the mobile network permits; expectations were agreed beforehand |

We do not assume every senior has poor vision, impaired dexterity or the same language preference. Text size, contrast and language must be adjustable without infantilizing the user. Do not equate answering a date question with a medical/cognitive assessment or a guarantee that someone can safely manage every setting.

### 2.1 Proposed success measures, not observed results

These are initial research/release targets to validate with the owner, not evidence-backed market claims:

| Measure | Proposed acceptance target and method |
|---|---|
| Senior task completion | At least 80% of participants complete each critical call/read/reply/return-home task without facilitator intervention after one guided introduction |
| Senior/caregiver setup | At least 90% complete role setup, four favorites, the chosen settings-access method and a consented test call in 15 minutes without technical assistance |
| Safety comprehension | Every pilot caregiver can explain trusted-contact SOS versus emergency services and can cancel the countdown |
| Commercial comprehension | Every pilot caregiver can identify the first charge, recurrence, cancellation route and separate lifetime option before enrollment |
| Accessibility | No release-blocking TalkBack, RTL, text clipping or primary-target violations in the agreed device matrix |
| Reliability | Zero unresolved severity-1 safety/data-loss/call-access defects; proposed beta crash-free sessions ≥99.5% where consented diagnostics provide a meaningful sample |

Recruit a small formative sample first (proposed 5–8 seniors and 5–8 caregivers, including relevant language/accessibility needs), then broaden device and usability coverage. Always report sample size and limitations. Task observation and consented local QA are needed where telemetry is declined; absence of telemetry is not success.

## 3. Scope, phases and free/premium boundary

### 3.1 Feasibility split

| Phase | Purpose | Completion means | Does not mean |
|---|---|---|---|
| **A — launcher/intent prototype** | Validate hierarchy, vocabulary, touch targets, caregiver setup and safe external handoffs | Clickable HTML storyboard; after approval, a bounded device spike may test HOME role and native call/SMS/camera intents | A finished simplified phone or messaging product; intent-based native SMS is not acceptable as final scope |
| **B — integrated easy phone, required target** | Own simplified dialer/contacts, incoming/active calls and full default-SMS duties | HOME + DIALER + SMS roles and permissions, working Telecom `InCallService`, real inbox/send/receive/provider behavior, policy and device gates passed | Guaranteed approval on every OEM or by Google Play |

If B cannot pass policy or device gates, stop for an explicit owner scope/distribution decision. Do not silently ship A as the promised integrated product or split into companion apps without approval. Independent Android roles are not evidence of a universal combined-role prohibition or a universal OEM compatibility guarantee. BaldPhone's 2020 explanation and separately reported 2024 removal are historical developer/community reports, not a current Google rejection notice. [P1–P4, P12]

### 3.2 Integrated MVP

Home/status/favorites; default phone and contacts; default SMS obligations including safe MMS handling; trusted-contact SOS; external camera and simplified local gallery; allowlisted apps; local settings with date-question or caregiver-PIN access; PIN-only recovery; accessibility/languages; safe free/premium membership; opt-in diagnostics proposal; release/privacy readiness.

### 3.3 Entitlement invariant

**REQ-CORE-01 — No safety or everyday-function paywall.**  
Acceptance: home/navigation, four speed dials, key 1/2 assignments, basic phone/contacts/SMS, SOS/emergency handoff, camera/gallery/apps, essential display/language changes, both settings-access modes and PIN recovery, role repair and safety configuration work without a purchase and after expiry. Existing favorites, layout, permissions and SOS recipients are never erased, disabled or obscured by entitlement changes. No paywall covers an incoming call, call controls, message, SOS or emergency action.

**REQ-CORE-02 — Premium affects caregiver conveniences only.**  
Proposed premium package: reusable layout presets, additional caregiver-only appearance presets and on-device configuration export/import excluding secrets, messages and contact values. Final package value and export privacy design require owner approval; free basic reorder/add/remove and accessibility settings remain available. Acceptance: expiry may prevent creating/applying a new premium preset or export, but retains the currently applied safe configuration and all senior actions. Trial expiration is shown in caregiver membership, never as a blocking senior renewal demand.

### 3.4 Postlaunch candidates and exclusions

Postlaunch, subject to approval: additional caregiver convenience tools, richer optional message templates, broader media types, optional location sharing with explicit consent and failure semantics. Location is not needed for the initial trusted-contact call.

Out of MVP: monitored emergency response, automatic dispatch, medical/fall detection, background live tracking, automatic SOS escalation, bulk SOS SMS, remote administration/companion app, cloud contact/SMS backup, RCS replacement, VoIP service, custom camera/keyboard, device-owner kiosk enforcement, ads, social feeds and third-party app UI enlargement. Excluding RCS or rich authoring does not excuse losing MMS or other default-handler duties.

## 4. Experience and accessibility contract

**REQ-UX-01 — Recognition before reading.**  
Acceptance: use original Claro artwork: large recognizable icons and portraits, white/charcoal high-contrast neutral surfaces and saturated meaningful accents, not muted beige/sage cards or copied competitor artwork. The six default tiles form a tightly packed **2-column × 3-row** grid of big, edge-to-edge rectangular light tiles with dark separators, **2–4dp gutters** and minimal internal padding. Use a green handset (Phone), blue envelope (Messages), orange camera (Camera), blue photo (Gallery), red SOS symbol and violet apps grid (Apps). Keep short, preferably one-word visible labels wherever recognition needs them; every control has an accessible name. Color supplements shape, text and state cues, never replaces them. Portrait/name fallbacks work without downloaded assets; do not hide labels with invisible text.

Senior-facing copy is minimal and production-ready: no developer, prototype or implementation explanations inside the phone. Safety limits, errors, action outcomes and trial/renewal/price disclosures remain visible in concise language; minimal copy must not hide critical instructions.

**REQ-A11Y-01 — Touch and type.**  
Acceptance: primary actions and tiles have targets **≥64dp**; secondary controls have **48dp minimum**, with separation that avoids adjacent destructive actions. Start at **24sp body** and **32sp key action labels**, tuned through device/user testing; supporting text must also pass readability review. Respect system font/display scaling and provide a huge-text setting. Test at least 200% font scale and the largest supported display size: reflow/scroll rather than truncate critical names, numbers, recipient choices, prices, errors or controls. Expandable names and wrapped labels remain available.

**REQ-A11Y-02 — Contrast and nonvisual operation.**  
Acceptance: target WCAG AA contrast (4.5:1 normal text, 3:1 large text and meaningful control boundaries), with an optional high-contrast palette. TalkBack announces each control, state, unread count, photo contact and logical heading; focus order matches reading order, dialogs contain focus and dismiss back to the invoking control. Controls have selected/disabled semantics and do not rely on unlabeled imagery. Validate hardware/assistive activation and switch-navigation feasibility in the accessibility pass.

**REQ-UX-02 — Predictable exits and feedback.**  
Acceptance: ordinary Claro screens show a consistent Back or Home exit appropriate to the task. Main screen actions occupy a fixed bottom dock immediately above Back/Home, outside the independently scrolling content: Add person, Call/Cancel, Answer/Decline, Send/Cancel, Save/Cancel and equivalent actions stay reachable without scrolling. Action buttons are full-width and stacked vertically, never split side by side: Call above Cancel, Answer above Decline, and Save above Cancel. Preserve the same vertical order in RTL; longer copy or huge text wraps without clipping and preserves minimum targets. Contextual controls such as keypad digits, search, contact rows, plan choices and per-item editing remain with their content. Docked form submissions retain validation, keyboard submission and entered values. System Back behaves consistently; leaving a call screen does not hang up, and Home exposes a clear return-to-call action. Unsaved edits/drafts prompt or preserve state. Destructive actions use explicit confirmation with the affected item named; cancellation is visually easy. Important feedback persists in-page, not solely a short toast. No essential function requires a hidden gesture; long-press speed dial has visible equivalent contact call actions.

**REQ-I18N-01 — Planned language support.**  
Acceptance: English, Spanish and Hebrew resources are planned for MVP release and professionally reviewed before release; no machine-only safety or billing copy. Hebrew mirrors navigation/layout where appropriate, preserves logical screen-reader order and keeps phone numbers, keypad sequences and codes **LTR using bidi isolation**. Do not blindly mirror photos or universal media/call symbols. Dates/time use selected locale and device 12/24-hour settings. Spanish expansion and mixed Hebrew/Latin names must not clip. Changing language keeps assignments and provides an obvious reversible preview.

**REQ-UX-03 — Home capacity is a reference, not a squeeze constraint.**  
Acceptance: at **360×800dp**, standard typography, a compact neutral day/date/time and status header, all four square photo favorites and all six default tiles fit the initial home viewport without scrolling. Verify fit rather than shrinking the **≥64dp** primary targets or hiding labels. Four favorites form a visible 2×2 group with recognizable portraits and minimal names, not a carousel or demo-initials notes. Additional tiles scroll. At large type, enlarged display settings or restrictive insets, prioritize readability and use vertical scroll with a visible continuation cue; never truncate critical content to preserve the reference layout.

**REQ-UX-04 — Clean phone and separate review shell.**  
Acceptance: the phone renders only the intended product UI, including necessary safety, error and commercial copy. Requirement IDs, scenario explanations, technical/platform limitations, implementation notes, simulated-state labels and fictional-data/no-real-device-action disclosures belong solely in the external review/annotation panel and README, not in the phone screen. Product-required caregiver **Test** mode remains clearly labeled; it is not a storyboard disclaimer.

The review shell provides a **Phone only** toggle outside the phone for clean screenshots: hide navigation, annotations and all review chrome without changing the product screen or state. **Escape** returns to the review shell; explain this exit beside the toggle before entering, not as an overlay inside the captured phone. Review controls are accessible and keyboard-operable. Captures contain neither review notes nor a mock/prototype banner and are unambiguous future implementation references. Review mode retains the fictional-data/no-real-actions disclosure and current scenario/boundary annotations.

## 5. Onboarding, readiness and fallback

**REQ-SETUP-01 — Caregiver-guided first use.**  
Acceptance: Welcome explains “simpler home, phone and messages,” external camera boundaries and trusted-contact SOS limits. Offer use-free/skip-premium at equal clarity. Setup can be completed independently or with a caregiver and covers language/display preview, date-question versus caregiver-PIN choice (credentials/recovery only for PIN), four favorite contacts, key 1/2 assignments, SOS recipient agreement, app allowlist, role/permission readiness and a handover checklist. Setup is resumable after interruption; missing optional setup is shown honestly, not marked complete.

**REQ-SETUP-02 — Request capabilities in context.**  
Acceptance: check role availability with `RoleManager`; request HOME, DIALER and SMS individually with plain-language explanation. Request the applicable default-handler role **before** its restricted runtime SMS/call-log permissions. Request contacts read/write only for implemented contact features; media via picker/owned content first; notifications when their benefit is explained; no location permission in MVP. Denial, “don't ask again,” role unavailable and later revocation have explicit repair guidance and safe navigation. System prompts are shown as external/system UI in the storyboard. [P1–P5]

**REQ-SETUP-03 — Degraded is usable, not falsely ready.**  
Acceptance: readiness distinguishes Home/Phone/Messages/Contacts/Notifications/Media and shows exact missing capability. With DIALER denied, offer native dialer handoff and label native incoming UI; with SMS denied, offer native composer/inbox handoff labeled as a **temporary fallback**, not Claro messaging. Role loss disables restricted operations immediately and never fabricates history/unread state. If HOME is not granted, the app still opens for setup with instructions. Unsupported telephony hardware cannot be marked phone-ready.

**REQ-SETUP-04 — Return and handover.**  
Acceptance: after OS settings, camera, purchase dialogs, role requests, interruption, reboot or process recreation, recover to the appropriate screen using current system state. Caregiver checks actual outgoing/incoming calls, SMS send/receive, selected SIM, audible ringtone within OS rules, home return and a consented SOS test. Readiness reports observations separately from untested capabilities; a permission grant is not proof of successful service.

## 6. Everyday functional requirements

### 6.1 Home and navigation

**REQ-HOME-01 — Required content.**  
Acceptance: show day/date/time, a compact **horizontal battery bar** with percentage/charging state, **stepped cellular signal bars** and the six core labeled tiles in the confirmed order initially. Keep the header neutral and compact so tiles and portraits dominate. Signal must distinguish available/no SIM/no service/airplane mode/unknown with accessible names and non-color cues, using actual accessible OS data; do not invent bars or require unjustified sensitive permission just to draw them. No internet is not the same as no mobile calling/SMS service. Missed-call and unread-message badges reflect authoritative permitted data; when unavailable, show unknown/readiness rather than a false zero.

**REQ-HOME-02 — Four familiar people.**  
Acceptance: four square photo slots show recognizable portraits and short visible names; use a recognizable person silhouette plus name when a photo is unavailable, not demo-initials annotations. Tapping an assigned person opens a large Call confirmation; unassigned slots explain “Ask your caregiver to add a person” with a protected setup route. No carousel or long-press-only assignment. Changed/deleted contacts or revoked photo access get a clear unresolved state; never call a different number because a provider ID was reused.

### 6.2 Phone, contacts and call lifecycle

**REQ-PHONE-01 — Simplified dialer.**  
Acceptance: large numeric keypad, readable entered number, backspace, Call, Contacts and recent/missed calls; empty/invalid number cannot silently initiate a call. Ordinary calls show name/number confirmation only, with no SIM label or selector. All call entry points, including speed dial and trusted-person SOS, use the phone's default calling account. Repeated taps cannot create duplicate calls. In integrated B, use Telecom/default-phone capabilities, not a simulated timer as evidence of connection. [P2]

**REQ-PHONE-02 — Keys 1 and 2.**  
Acceptance: caregiver independently assigns each to one explicitly selected non-emergency number; key labels and a visible status area say “Hold 1: [name]”/“Not set.” Proposed hold threshold is 1 second, with progress/haptic feedback and one call on successful hold; tune in usability testing. These two configured holds intentionally direct-call without ordinary confirmation. Short taps type digits; canceling a hold does not dial. Unset, invalid, no-service and permission-lost states give clear feedback, never a hidden voicemail call. Visible contact Call remains an equivalent accessible route. Prevent unsafe overlap with emergency-number handling.

**REQ-CONTACT-01 — Browse and search.**  
Acceptance: Claro contacts show large names/photos/numbers, search by name or number, a clear empty/no-results state and contact detail. Support multiple numbers and require explicit number choice for call/message/favorite assignment. Validate assignment/search usability with at least 2,000 fixture contacts without loading the complete list into the UI at once. Unknown callers remain identifiable by number or “Private number,” never guessed identity.

**REQ-CONTACT-02 — Add, edit and delete.**  
Acceptance: ordinary browsing/call/message are senior-accessible; Add/Edit/Delete use the selected settings-access method and are reachable from detail and settings. Create/edit name, number/type and optional photo with save/cancel and validation. Proposed source of truth is Android Contacts Provider for persisted contacts, with Room storing stable references and selected-number snapshots for reconciliation, not a separate unsynchronized address book. Read/write denial leaves existing view/fallback honest. Deletion confirmation identifies the contact, warns about linked speed dial/SOS, and requires repair/replacement of the active SOS assignment before deleting it. System/account-source write restrictions show failure without claiming save/delete. Refresh linked assignments explicitly after changes; never silently redirect safety calls.

**REQ-CALL-01 — Incoming and active calls.**  
Acceptance: B provides large labeled **Answer** and **Decline** buttons without mandatory swipe, caller identity/unknown state and accessible ringing feedback under OS volume/DND rules. Active UI shows actual call state, duration after connection, Mute/Speaker/Keypad/End, current audio route and supported hold/call-waiting actions. Controls reflect Telecom capability, including Bluetooth changes; unsupported actions are not faked. Do not assume every Telecom call is SIM-based. Incoming UI/notification behavior on locked screen, current OS full-screen-intent rules, background restrictions and OEM fallback must pass the spike. Unavoidable external/system call UI is identified in the storyboard's external annotation panel, not by an implementation note inside the phone.

**REQ-CALL-02 — Outcomes and interruption.**  
Acceptance: distinguish dialing/ringing/connected/on hold/ended/missed/declined/canceled/failed, using authoritative callbacks. Call ended shows the final connected-call duration beside a clock icon in large LTR digits (`mm:ss`, or `h:mm:ss` for calls of an hour or more), followed by Home, Call again and Message. Measure duration from connection to disconnection using Telecom timing, not time spent dialing/ringing or browsing the screen. Missed, declined, canceled and otherwise unconnected calls do not show a connected duration; missed calls appear in permitted history/home badge with a callback action. Failure states include no SIM/service, role/permission loss, invalid number, unavailable account and network failure with Retry, native fallback or Home as appropriate. Do not equate ringing with answered or generic disconnect with a known reason. Returning Home does not terminate the active call. Call history access remains permission- and role-scoped.

**REQ-CALL-03 — Dual SIM and emergency calls.**  
Acceptance: voice calls and SMS use their respective OS default accounts without a Claro SIM selector or SIM label. If no usable default is configured or Android requires a choice, defer to the system's account-selection/setup flow; never silently pick another SIM or change device-wide defaults. Preserve an SMS draft if default-account setup is needed before sending. Emergency-number recognition/routing uses platform-supported mechanisms; use Telecom's documented default-dialer call route and accept preloaded emergency UI, or a native emergency-dialer handoff in fallback. No PIN, entitlement or configured SOS recipient is needed to reach the native emergency path; network/OS availability is never promised. [P2, P6]

### 6.3 Required integrated messaging

**REQ-SMS-01 — Own inbox and conversation.**  
Acceptance: B includes Claro inbox, unread state, contact/number search or accessible filtering, empty/no-results states, conversation history with incoming/outgoing timestamps, readable content and Reply/New message. Read state updates only when appropriate content is viewed. Drafts survive ordinary navigation/process recreation. Incoming unknown numbers remain visible; an app allowlist must not silently filter SMS or calls. Reconcile system provider state and avoid duplicate rows after repeated broadcasts/restart.

**REQ-SMS-02 — Compose, recipient and confirmation.**  
Acceptance: choose/search a contact and one number or enter a validated number; show name and full destination before send. Large editor supports multiline text, system keyboard and available OS dictation without promising a custom accessible keyboard. Confirmation shows recipient and content only, with Edit/Cancel/Send; use the phone's default SMS account without a SIM label or selector. Warn if length/Unicode encoding causes multiple SMS segments and possible carrier charges; Hebrew and Spanish text round-trip correctly. Disable duplicate send activation while a submission is pending.

**REQ-SMS-03 — Observable send outcomes.**  
Acceptance: distinguish draft, sending/pending, sent, delivered only if supported and confirmed, and failed/unknown outcome. Sent is not proof someone read or received it. The failure screen groups the recipient's photo/fallback, name and number together, using the failed SMS destination rather than the last call's participant, and retains the message preview. Explain known causes (no SIM/service, denied role/permission, carrier error) briefly. Show only full-width **Try again** then **Cancel** in the bottom dock, without separate Edit or ambiguous Check messages actions. A known-failure retry attempts the retained message directly, without another edit/review detour; Cancel returns to messages without deleting the draft. For an unknown outcome, warn that it may already have been sent; Try again first requests explicit duplicate-risk confirmation with **Send again**, not an automatic resend. Offline internet alone does not block carrier SMS; no cellular service cannot be “fixed” by a premium purchase. Never silently retry a user message indefinitely. [P4, P6]

**REQ-SMS-04 — Full default-handler responsibilities.**  
Acceptance: before claiming SMS role, implement and validate SMS deliver, MMS WAP-push handling, `SENDTO` for supported SMS/MMS schemes, respond-via-message service, provider writes, multipart reassembly, notifications and role-loss behavior. Incoming MMS/media/group messages and download failures must remain accessible and preserved, with standards-compliant retrieval/persistence and honest carrier-data requirements; do not swallow them because the everyday design emphasizes SMS. Support safe display/opening and required handler responses; advanced rich-message/group authoring is a scoped UX decision only after the spike establishes compliance. No RCS promise. A native composer shortcut is not completion of this requirement. [P3–P4]

### 6.4 SOS: help from a trusted person

**REQ-SOS-01 — Meaning and configuration.**  
Acceptance: describe SOS as **“Call [trusted person] for help”**, not a monitored service. MVP calls one caregiver-approved, non-emergency recipient; no SMS, location or automatic recipient escalation is implied. Setup requires an explicit selected number, recipient agreement checklist and visible test mode. Emergency numbers cannot be saved as trusted-contact assignments; the native emergency route remains separate. Final approved recipient(s) and local copy are owner decisions.

**REQ-SOS-02 — Unset, armed and countdown.**  
Acceptance: unset SOS says it is not configured and offers caregiver setup plus the emergency-dialer route, never a success message. Armed SOS shows the configured person before action. Activation starts a clearly announced **5-second cancellable countdown**, with a large Stop/Cancel target and visible emergency-dialer alternative. Cancellation produces no call. Countdown is based on a persisted elapsed-time deadline, not UI animation frames. If interrupted/recreated before placing the call, reconcile state and require explicit resumption rather than surprising the user with a delayed background call; no scheduled automatic retry.

**REQ-SOS-03 — Calling, failure and emergency handoff.**  
Acceptance: countdown completion places one ordinary call and shows dialing/ringing/connected only from actual call state. “Help sent,” “someone is coming” or automatic dispatch claims are forbidden. Busy/no answer/declined/no signal/airplane mode/role loss/call failure are distinct when known, otherwise “Call did not connect” with Retry, Home and native emergency handoff. Ending or returning from a call shows its actual outcome, not a resolved emergency. All action paths remain free and independent of internet/backend availability; cellular calls still require functioning device/network service.

**REQ-SOS-04 — Safe testing.**  
Acceptance: test mode is caregiver-protected, clearly labeled **Test**, explains whether a real consented test call will occur and never contacts emergency services. Confirm recipient and allow cancel before any real test. For the HTML storyboard, “No call placed” is a simulation disclosure in the external review panel, not inside the phone; this does not remove the native caregiver test-mode warning or confirmation. A failed test cannot be marked ready.

### 6.5 Camera and gallery

**REQ-MEDIA-01 — Honest camera handoff.**  
Acceptance: Camera explains and opens the installed system camera through a resolvable capture intent; show external UI boundary and how to return. No camera installed, cancellation, URI failure and permission/storage failure have recovery states. Captures owned by Claro use scoped storage and safe content URIs; do not claim Claro enlarges native camera controls. A custom camera is outside MVP.

**REQ-MEDIA-02 — Simplified gallery.**  
Acceptance: provide a large-thumbnail Claro list and photo detail for Claro captures and explicitly selected/imported local images, plus clear empty, loading, missing-access and unavailable/cloud-only states. This is not a claim of automatic access to every device photo. “Add photos” uses system Photo Picker with document-picker fallback, persistent URI permission where supported and clear restoration on revoked grants. Photo detail has visible Back/Home and accessible next/previous controls; gestures are optional.

**REQ-MEDIA-03 — Deletion means what it says.**  
Acceptance: a large confirmation previews/names the photo. For an imported reference, label **Remove from Claro** and state the original is retained; for an app-owned capture, label **Delete photo** and use required OS authorization before deleting. Cancel leaves it intact; rejection/failure keeps the item with an explanation. Never imply a picker selection grants arbitrary storage-delete access. [P5]

### 6.6 Apps

**REQ-APPS-01 — Allowlisted application menu.**  
Acceptance: senior menu has Favorites and All allowed apps, large labels/icons, search, empty/no-results and app-missing/disabled/error states. “All” means **all allowed**, not a way around caregiver selection. Caregiver picker can discover eligible launchable apps within package-visibility rules, search them and add/remove allowlist/favorites/additional home tiles. Removed/uninstalled apps cannot leave dead tiles without explanation.

**REQ-APPS-02 — External boundary and limits.**  
Acceptance: opening an app hands off to its own UI, which Claro cannot simplify. Caregiver gets short return-Home instructions. App-menu hiding is a usability choice, not a device-wide access-control/kiosk guarantee; OS settings, notifications and other entry points can remain accessible.

## 7. Caregiver preferences and local data

**REQ-PIN-01 — Protected preferences, free forever.**  
Acceptance: first setup offers **Date question** or **Caregiver PIN**, with short explanations and no assumption that a senior needs a caregiver. Date mode asks for today's date in **dd/MM/YYYY**, with a visible format and LTR entry even in Hebrew/RTL. Validate the exact zero-padded local calendar date at submission, including month/year rollover, leap days and a page left open across midnight; no internet is needed. Date mode needs neither PIN nor recovery phrase. It only deters accidental changes: anyone who knows or can find today's date can unlock; it is not a cognitive assessment or secure access control.

PIN mode uses a caregiver-chosen PIN, confirmed at setup (native proposal: six digits; storyboard currently accepts 4–6), plus PIN-specific recovery under PIN-02. Correct entry through the selected method unlocks the same local session. Incorrect entry gives neutral feedback; never display the correct date as an answer/hint on the challenge screen or expose PIN digits. Protected screens include the access-method choice itself, home/app changes, speed-dial assignment, contact mutations, SOS config/test, membership purchase actions, privacy/reset and display/language. Essential call/message/SOS/emergency actions never need either unlock. Store/payment confirmations still apply; the date question is not purchase authorization. Lock on explicit exit, screen lock and a proposed two-minute inactivity timeout, without losing unsaved drafts or interrupting a call. After initial setup, changing modes or credentials requires unlocking through the **current** method; Cancel, failed validation and timeout leave that method unchanged.

**REQ-PIN-02 — Local recovery and throttling.**  
Acceptance: **only PIN mode** configures a separate high-entropy recovery secret locally, with instructions to store it safely elsewhere. Store neither PIN nor recovery secret in plaintext: use salted password hashing/key derivation with parameters reviewed during engineering, plus OS-backed protection where suitable. After five incorrect unlock attempts through either mode, apply a visible persisted cooldown (proposed 30 seconds, doubling to a 30-minute cap); throttle PIN recovery too and survive process restart without relying only on wall-clock time. Successful PIN recovery verifies the secret and sets/confirms a new PIN; there is **no universal/master bypass**, date-question fallback for PIN mode, emailed default code or new companion service. Forgotten PIN and secret offer honest OS-level reset/reconfiguration guidance with data-loss warning, not a bypass that preserves access. Date mode has no Forgot PIN/reset-PIN flow; direct recovery links return to its date question. An authenticated switch from PIN to date mode removes obsolete PIN/recovery material. The PIN deters accidental changes; it is not resistant to uninstall, clear-data, rooted access or device-owner control.

**REQ-SETTINGS-01 — Home editing.**  
Acceptance: reorder with large Move up/down controls, optionally drag; add allowed app shortcuts and remove additional tiles with preview/save/cancel. Phone, Messages, Camera, Gallery, SOS and Apps are retained; in particular core safety tiles cannot be removed or replaced. Four favorite slots remain available. Preview large-text scrolling before save. Failed saves retain the last valid layout.

**REQ-SETTINGS-02 — People and safety.**  
Acceptance: a searchable speed-dial picker supports four photos/names, key 1/2 assignment and explicit number selection; allow replacing/clearing ordinary favorites with confirmation. SOS configuration, contact editing and role readiness are directly reachable. Changing an SOS number requires confirmation and readiness/test review; it cannot silently inherit a different contact number after sync.

**REQ-SETTINGS-03 — Display, language and status.**  
Acceptance: edit readable text size, contrast, language and permitted appearance preferences with immediate preview and undo/cancel. Keep basic accessibility controls free. A readiness summary links to actual missing role/permission repair, respecting OS grant dialogs and ringtone/volume limits rather than promising to override DND.

**REQ-DATA-01 — Local-first storage.**  
Acceptance: keep layout, allowlist, selected references, selected settings-access mode, PIN/recovery verifiers when applicable, and preferences locally; use Contacts Provider and Telephony provider as appropriate, Room for structured app-owned state and DataStore for preferences. Restart, migration and entitlement changes must not silently downgrade PIN mode to date mode. Define migrations, app backup exclusions and deletion semantics before implementation; never back up PIN/recovery material into a plain configuration export. No Claro account is required for basic use; store account may be needed for purchase/restore.

**REQ-DATA-02 — Privacy and reset are distinct.**  
Acceptance: Privacy shows analytics/crash consent, local data categories, platform/SDK processing and policy access. Reset confirmation lists precisely what will be removed: Claro preferences, favorites, selected image references, PIN/recovery and app-owned drafts/cache; explains what remains in Android contacts/SMS/shared photos, and that uninstall/reset does **not** cancel a Play subscription. Require caregiver authentication and a final explicit destructive confirmation. Reset does not delete provider contacts/messages/photos in bulk or silently discard active-call state; defer until safe. On completion, clear the documented local scope and return Welcome while emergency/native fallback remains reachable. Cloud telemetry deletion requests, if applicable, follow published provider/support procedures; a local reset must not claim to erase already transmitted diagnostics.

## 8. Commercial requirements

**REQ-BILL-01 — Free install is not enrollment.**  
Acceptance: installing/opening/setup does not start a subscription, trial or charge. “Continue free” remains available. The seven-day premium trial starts only after an eligible caregiver explicitly selects a monthly/yearly offer and confirms Google Play enrollment. Ineligible accounts see truthful store-returned alternatives; never promise a second trial. The storyboard's external review panel and README disclose that the simulation cannot enroll or charge; do not repeat that implementation disclaimer inside the phone.

**REQ-BILL-02 — Informed enrollment.**  
Acceptance: plans and confirmation show localized store price/currency, seven-day eligibility/terms, selected monthly/yearly cadence, actual first-charge date, automatic-renewal terms, cancellation instructions, terms/privacy links and core-free guarantee. Until products/prices are approved, storyboard uses **“Price to be confirmed”** and **“First charge: shown by Google Play at enrollment”**, not invented amounts/dates or a functional purchase promise. Premium explains benefits to caregivers, not that the senior's phone will stop. [P7]

**REQ-BILL-03 — Lifetime is separate.**  
Acceptance: label lifetime **one-time purchase, no renewal** with its own store price and terms; it has no automatic trial conversion. Hide redundant lifetime purchase for confirmed owners. Buying lifetime during an active subscription warns that the existing subscription must be managed/canceled separately unless verified store behavior proves otherwise; never imply an automatic cancellation. Verify SDK non-consumable support, purchase acknowledgment and refunds/revocations before release. [P8–P9]

**REQ-BILL-04 — Honest transaction lifecycle.**  
Acceptance: distinguish loading plans, available, purchasing, canceled, pending, verified success, failed, store unavailable/offline and restored/no purchases. Pending is not entitled success. Grant premium from verified RevenueCat/store entitlement, reconcile on return/foreground and make repeated callbacks idempotent. Include Restore purchases and store/account mismatch guidance without exposing account details. No RevenueCat product/entitlement/offering is assumed configured. [P8]

**REQ-BILL-05 — Membership, cancellation and expiry.**  
Acceptance: caregiver membership shows free/trial/active/canceled-but-valid/grace/payment problem/account hold/expired/lifetime/offline-unverified as supported by authoritative state, with known dates and a Play Manage/Cancel route. Reminders belong in caregiver membership and optional consented reminders; disclosures at enrollment remain primary because notifications/timers may be delayed or denied. Cancellation/expiry cannot disable core functions, delete configuration or switch default handlers. Trial-ended copy offers Continue free, Plans and Restore without disrupting the senior.

**REQ-BILL-06 — Offline does not become a safety gate.**  
Acceptance: use cached entitlement state without blocking launch; mark stale/unknown membership honestly and refresh when possible. A proposed non-destructive premium editing grace for uncertain cache state requires owner approval; do not assume indefinite offline entitlement or hard-code Play grace/account-hold durations. Core functionality is entirely independent of entitlement/network checks. New purchases/restore may require internet and show Retry/Back. [P8–P9]

## 9. Privacy, analytics and reliability

**REQ-PRIV-01 — Opt-in diagnostics by default, proposed design.**  
Acceptance: Firebase Analytics and Crashlytics collection start disabled, including pre-consent automatic events and queued uploads; offer separate understandable optional toggles without affecting free functions or purchases. Declining produces no analytics/crash upload. Withdrawal prevents future collection and handles locally queued data per documented SDK behavior. Verify startup/automatic breadcrumbs, instance identifiers, retention, IP-derived location and SDK defaults with network inspection before any “private”/Data Safety claim. Purchases still necessarily involve Google Play/RevenueCat processing disclosed separately; diagnostics consent does not claim to disable that processing. [P10]

**REQ-PRIV-02 — No sensitive user content in telemetry.**  
Acceptance: never send contact names/photos/numbers, dialed numbers, SMS/MMS bodies/recipients, call records, PIN/recovery values, exact location, media URIs or free-text user input to Firebase, RevenueCat attributes or developer logs. No advertising identifiers or contact/SMS upload. Use an allowlist of coarse event names and enumerated outcomes, sanitize exceptions and audit crash breadcrumbs/custom keys. Inspect actual payloads; do not equate anonymized device IDs with “no personal data.” Actual SDK identifiers/transaction data and retention are disclosed accurately.

**REQ-OBS-01 — Minimal proposed measurement.**  
Acceptance: consented events may include `setup_step_result` (step/outcome enum), `role_readiness` (capability/status enum), `task_flow_result` (generic task/outcome only), `membership_screen_view` and `accessibility_preset_selected`. Never attach contact, call, conversation, installed-app identity or content context. Prefer synthetic/device-lab testing for sensitive call/SOS outcomes. Any automatic purchase events or RevenueCat/Firebase linking require explicit privacy review; no cross-SDK identity link is assumed.

**REQ-REL-01 — Offline and lifecycle resilience.**  
Acceptance: home, contacts already available on-device, dialer, local SMS/history/drafts, configured SOS, owned/available photos, app menu and both settings-access methods work without internet. Calls/SMS still depend on radio/SIM/carrier; cloud-only photos cannot be promised offline. Role checks and local error recovery do not require a backend. Reboot/process death, storage pressure, provider changes and interrupted writes do not corrupt assignments or generate unintended calls/messages. No always-on service or FCM/companion infrastructure is introduced for safety promises. [P6, P11]

## 10. Screen registry and storyboard coverage contract

The implementation artifact will be `index.html` plus `storyboard.js`, with a **sidebar navigation**, **phone panel** and external **annotation panel**. The phone is the exact intended product UI, not a presentation slide. The annotation panel alone carries requirement IDs, scenario/state, external/system boundaries, accessibility and implementation notes, prototype limitations and fictional-data/no-real-actions disclosures. The external **Phone only** toggle and Escape return follow UX-04. This revision specifies presentation only; native requirements, commercial decisions and planned English/Spanish/Hebrew support are unchanged.

**Registry rules:** the following simple hash IDs are stable and canonical. Variants can be in-place states/controls on one route; they do not all need separate pages. Each listed variant must be reachable through the product flow or external review controls and named in the annotation panel, not merely described there. No hash duplicates. Browser Back/Home must remain coherent. Use fictional sample portraits/people/messages, no real PII. The storyboard must never actually dial, send, purchase, request roles or delete device data. Label the simulation and its scenarios outside the phone; inside, show concise production-ready success/failure states with all necessary safety and commercial disclosures.

| Hash ID | Screen and required in-place variants | Requirement coverage |
|---|---|---|
| `home` | Compact neutral day/date/time, horizontal battery and stepped signal bars; six light rectangular tiles in 2×3 grid, 2–4dp gutters; four visible portraits/names; extra-tile scroll; huge text; missed/unread; no SIM/no signal/unknown; active-call return | HOME-01/02, UX-01/03/04, A11Y-01/02, CORE-01 |
| `dialer` | Empty/entered/invalid number, recent/missed list and empty; key 1/2 assigned/unset/hold progress; default calling account, no SIM selector | PHONE-01/02, CALL-02/03 |
| `contacts` | List/search/no results/empty/permission denied; recipient/assignment picker mode | CONTACT-01, SETUP-03 |
| `contact-detail` | Detail/multiple numbers; protected add/edit/delete forms; delete confirmation/save failure/linked SOS warning | CONTACT-02, SETTINGS-02 |
| `call-confirm` | Person/number review only; default calling account, no SIM label/selector or emergency-call button; cancel/place; accessible equivalent to direct hold | PHONE-01/02 |
| `calling` | Dialing/ringing/connected/hold, active controls/DTMF/audio routes/call waiting; return Home | CALL-01/02 |
| `incoming` | Known/unknown/private caller; Answer/Decline; lock-screen/system-boundary annotation | CALL-01 |
| `call-ended` | Ended/canceled/declined/missed; callback/message/Home | CALL-02 |
| `call-failed` | No service/SIM, invalid, account unavailable, permission/role lost, unknown failure; Retry/native handoff | CALL-02/03 |
| `messages` | Inbox/unread/search or filter/empty/no results; role denied; MMS/group summary | SMS-01/04 |
| `conversation` | Incoming/outgoing/readable history, draft/reply, sending/sent/delivery-unknown/confirmed delivery; MMS/media/download error | SMS-01/03/04 |
| `compose` | Contact/manual recipient selection, multiple numbers, empty/invalid/body/draft, Unicode multipart warning; default SMS account, no SIM selector | SMS-02 |
| `message-confirm` | Recipient/content confirmation only; full-width bottom actions stacked Send, Edit, Cancel in that order; pending/sent; no SIM label | SMS-02/03 |
| `message-failed` | Recipient portrait/name/number grouped together; retained text; Try again then Cancel; unknown result requires explicit Send again duplicate-risk confirmation | SMS-03 |
| `camera` | External camera handoff/return/canceled/unavailable/capture failure | MEDIA-01 |
| `gallery` | Local list/empty/loading/add with picker/missing access/cloud unavailable | MEDIA-02 |
| `photo` | Detail/next/previous, Remove reference vs Delete owned capture confirmation, failure | MEDIA-02/03 |
| `sos` | Unset/armed; trusted person and emergency-service disclaimer; emergency handoff | SOS-01/02 |
| `sos-countdown` | Five-second countdown, Stop/Cancel, interruption-safe resume, canceled | SOS-02 |
| `sos-active` | Trusted-contact dialing/ringing/connected/ended; explicit non-dispatch wording | SOS-03 |
| `sos-failed` | No service/busy/no answer/unknown failure, Retry and native emergency handoff | SOS-03 |
| `apps` | Favorites/all allowed/search/empty/no results/missing app/external handoff | APPS-01/02 |
| `pin` | Selected date-question or PIN gate; dd/MM/YYYY local-date validation; correct/incorrect/locked-session/cooldown; unlock and cancel; Forgot PIN only in PIN mode | PIN-01/02 |
| `settings` | Caregiver hub, contact/app management modes, readiness/membership entry, session timeout | SETTINGS-01/02/03, PIN-01 |
| `home-edit` | Reorder/add/remove additional tiles, protected core tiles, preview/save/cancel | SETTINGS-01 |
| `speed-dial` | Four-slot photo/name assignment, contact search/number/photo picker, key 1/2 assign/clear | SETTINGS-02, PHONE-02 |
| `display` | Text sizes/huge reflow/contrast/preview/revert | A11Y-01/02, SETTINGS-03 |
| `language` | English/Spanish/Hebrew selection, representative Spanish expansion/Hebrew RTL + LTR numbers | I18N-01 |
| `sos-settings` | Approved recipient/configuration warning, readiness, caregiver Test confirmation/result; simulated-versus-real-call explanation in external review panel | SOS-01/04, SETTINGS-02, UX-04 |
| `permissions` | Roles/permissions readiness, denied/revoked/unavailable/permanent denial/system settings fallback | SETUP-02/03/04 |
| `privacy` | Opt-in/off consent, data categories/SDK disclosure, withdrawal, local-data explanation | PRIV-01/02, DATA-01/02 |
| `reset` | Protected local-reset scope/cancel/final confirmation/deferred during call/result | DATA-02 |
| `welcome` | Product introduction, independent or caregiver-assisted setup, language, Continue free | SETUP-01, BILL-01 |
| `setup` | Date-question/PIN choice, PIN-only credential/recovery setup, current-method authorization for changes; resumable favorites/SOS/roles/handover checklist | SETUP-01/04, PIN-01/02 |
| `plans` | Monthly/yearly eligible trial/ineligible, separate lifetime/owned, store unavailable, free continuation | BILL-01/02/03 |
| `purchase` | Visible trial/price/renewal disclosures; external Play confirmation, pending/canceled/error/verified success; simulation/boundary notes outside phone | BILL-02/03/04, UX-04 |
| `membership` | Free/trial/active/canceled-valid/grace/payment issue/hold/expired/lifetime/offline; restore/manage outcomes | BILL-04/05/06 |
| `trial-ended` | Expired premium convenience, retained core configuration, Continue free/Plans/Restore | CORE-01/02, BILL-05 |
| `pin-recovery` | PIN mode: secret entry/incorrect/cooldown/new PIN/success, forgotten-secret reset guidance; date mode: return to date question without a reset flow | PIN-02 |

Requirement IDs in the table abbreviate the `REQ-` prefix only. **UX-01/04 and A11Y-01/02 apply to every row**, including dialogs, alternate states and caregiver screens. The representative language preview is not a claim of professionally approved translation or a fully localized storyboard.

**Dialer design alternatives, pending selection:** the storyboard nests three
interactive sub-options under **02 Dialer**: `dialer-contacts-first`,
`dialer-keypad-first`, and `dialer-two-choices`. These do not replace the canonical
`dialer`, change the 39-screen numbering, or change the Home Phone destination.
They compare a contacts-first list, a keypad with a large top Contacts button,
and two large stacked Contacts/Dial a number choices. Single-number contact
selection goes directly to call confirmation; multiple numbers require explicit
selection. Recent includes missed calls instead of a separate Missed tab.
Keypad long-press shortcuts and separate, secondary emergency access remain.
Review notes and option labels stay outside the phone.

### 10.1 Required connected walkthroughs

1. **First handover:** Welcome → Setup → choose date question or caregiver PIN/PIN recovery → contacts/favorites → SOS config/test → Permissions → Home; show a denied-role branch.
2. **Call a familiar person:** Home → confirmation → Calling → ended → Home; alternate direct hold 1/2, failure and unknown incoming call.
3. **Read and reply:** Home badge → Messages → Conversation → Compose/recipient → confirmation → sent; alternate failed/unknown outcome → explicit retry.
4. **Ask for help:** armed SOS → five-second countdown → canceled, then a separate simulated attempt → calling → failure/emergency handoff.
5. **See a photo/use an app:** Camera external handoff → Gallery → Photo/delete confirmation; Apps favorites/all allowed/search/external handoff.
6. **Caregiver changes:** PIN incorrect/cooldown/recovery → Settings → reorder/add/remove → speed dial/contact edit → huge text/language/RTL → readiness/privacy/reset cancellation.
7. **Membership without disruption:** Plans → eligible seven-day offer → purchase pending/success → Membership/manage → expired → Continue free → working Home/Phone/Messages/SOS; separate lifetime and offline/restore branches.

## 11. Proposed engineering plan — after review only

### Phase 0 — approve product and prototype

Owner reviews this PRD, screen coverage, original visual direction and premium/free boundary. Build only the English HTML storyboard and representative language variants; conduct formative tests. Approval explicitly selects the bounded native spike next, not an implicit full implementation.

### Phase 1 — policy and physical-device spike

On owner-approved test hardware, verify combined HOME/DIALER/SMS role eligibility, request ordering, loss/regrant, manifest/handler components, incoming/locked-screen behavior, Telecom capability handling, SMS/MMS/provider lifecycle, emergency handoff, dual SIM and camera/picker behavior. Audit current target SDK/Play rules, restricted permissions, package visibility, media access and full-screen intent requirements.

Recommend **Android 10+ (API 29+)** as an initial planning baseline because of `RoleManager`; final minimum/target SDK require device inventory and current Play target audit. Test API 29 baseline and representative current versions, including Android 13+ notification differences. Use existing suitable devices/emulators rather than assuming simulator results prove carrier behavior.

**Gate:** documented evidence against B requirements and actual applicable Play permission declarations/reviewer feedback where obtainable. A closed-test upload is not an approval guarantee. If blocked, return options to owner; no unapproved companion architecture. Resolve SMS/MMS scope and OEM viability before expanding feature implementation.

### Phase 2 — foundations and free integrated core

Proposed **Kotlin + Jetpack Compose** with state-driven screens, unidirectional state flow and testable domain operations. Separate modules/boundaries:

| Boundary | Responsibility |
|---|---|
| `app` / navigation / design system | Composition root, accessibility, localization, protected routes |
| `launcher` | HOME role experience, app allowlist, tile/favorite presentation |
| `telephony` | RoleManager/Telecom adapters, `InCallService`, call state, audio routes, emergency handoff |
| `contacts` | Contacts Provider access, search/edit, stable assignment reconciliation |
| `messaging` | Default-SMS components, SMS/MMS transports/provider reconciliation, inbox/drafts/status |
| `safety` | Trusted-contact SOS state machine and test mode; no backend dispatch |
| `media` | Camera/picker adapters, owned captures, selected-photo list/delete semantics |
| `caregiver` / local data | Date-question/PIN mode, PIN-only recovery, rate limits, Room/DataStore, preferences/reset/migrations |
| `billing` | RevenueCat/Play adapters and cached entitlement; never a dependency of core call/SMS/SOS eligibility |
| `observability` | Firebase consent gate, event allowlist/redaction; no content coupling |

These are logical boundaries; exact Gradle module count is an engineering decision. Domain state machines must not call billing/network to permit safety actions. Keep platform adapters replaceable for deterministic tests; use real system providers rather than duplicate histories with conflicting truths.

### Phase 3 — caregiver enhancements and services

Add approved premium conveniences, RevenueCat offerings/entitlement mapping and separate non-consumable lifetime product; configure store trial eligibility/disclosures, acknowledgment, grace/hold handling and restore tests. Integrate Firebase only behind verified consent gates and privacy-safe schemas. No existing SDK/project configuration is claimed. Secrets and server credentials are not embedded in client source.

### Phase 4 — verification, controlled pilot and release

Execute the QA plan below, finalize professional Spanish/Hebrew copy, validate store disclosures and staged rollout/rollback. Supply caregiver setup/recovery guidance and a maintained support contact. Release requires owner sign-off, not just completion of screen artwork.

## 12. Test, QA and release plan — not test execution

No Android tests or platform integration validation have been executed by writing this document.

| Layer | Planned verification |
|---|---|
| Unit/state machines | SOS cancellation/interruption/no duplicate call; key-hold threshold; call/SMS outcome transitions; provider deduplication; local-date format/leap-day/midnight rollover, mode-change authorization/no date bypass for PIN, unlock/recovery throttling and restart; layout invariants; entitlement isolation |
| Integration | Role grant/deny/revoke/regrant, runtime permissions, Contacts/Telephony provider behavior, multipart/Unicode SMS, MMS handling/download failure, process death/migrations, capture/picker grant loss and delete authorization |
| Accessibility/localization | Target dimensions/contrast; TalkBack focus/announcements; 200%+ supported type and display scale; small/large screens/insets; Hebrew RTL and LTR numbers; Spanish expansion; keyboard/focus behavior |
| Storyboard visual review | At 360×800dp, all six tiles and four portraits/names fit at standard type; 2–4dp tile gutters, ≥64dp primary targets, measured AA contrast, compact status bars and visible recognition labels; no developer/demo notes inside any phone state; Phone only hides review chrome and Escape restores review without losing state; safety/outcome and legal disclosures remain readable |
| Physical telephony | Outgoing/incoming/locked-screen/missed/failed/call waiting, proximity/audio/Bluetooth, no SIM/no service/airplane, single/dual SIM/default changes, busy/no answer, SMS carrier delivery unknown, MMS data unavailable |
| Billing sandbox | Eligible/ineligible seven-day offer, monthly/yearly/lifetime, pending/canceled/restore/account mismatch, renewal/cancel/grace/hold/expiry/refund, offline cached status and owned-lifetime suppression; core remains usable in every state |
| Privacy | Inspect cold-start/pre-consent/post-withdrawal traffic and logs; synthetic sensitive strings never appear in Firebase/RevenueCat payloads; actual SDK identifiers/disclosures match implementation |
| Resilience | No internet, no cellular service, Doze/background restriction, denied notifications, reboot, time changes, storage pressure, app missing, external intent cancellation and system UI return |
| Usability | Observe actual seniors and caregivers performing §2 tasks and §10 walkthroughs; record errors without sensitive content; revise language/layout before release |

Choose representative stock Android and major target-market OEM devices, including the grandfather's actual phone if available. Final matrix follows owner device inventory; do not promise every manufacturer.

**Release gates:** approved native scope; passed policy/device spike; full handler obligations; no unresolved critical call/SMS/SOS/data-loss defects; no paywall regressions; accessibility/language sign-off; audited consent/logging; verified purchase configuration; privacy policy and accurate Play Data Safety/permission declarations; tested migrations/recovery; documented support and staged rollout/rollback that does not strand users with a broken default handler. Emergency tests use approved platform/lab procedures, never unsolicited calls to live emergency services.

## 13. Owner decisions and risks still open

| Decision/risk | Recommendation or next evidence | Gate |
|---|---|---|
| Actual monthly/yearly/lifetime prices, currencies and regional offers | Owner decides; retrieve localized prices from Play/RevenueCat, not hardcoded copy | Before commercial storyboard finalization/store setup |
| Final premium caregiver value | Approve concrete convenience package; never move everyday/safety essentials behind premium | Before purchase implementation |
| Minimum Android, target devices and target SDK | Android 10+ recommendation pending inventory/current policy audit | Before native spike scope |
| Combined-role Play/OEM viability | Prove applicable duties and obtain real policy/device evidence; no blanket ban/approval assumption | Before full native commitment |
| SMS/MMS implementation and carriers | Validate complete default-handler contract and scoped rich-message UX; no silent native-SMS substitution | Before integrated release |
| Approved SOS recipient(s), wording and test protocol | Start with one agreed trusted person; no emergency number or monitoring claim | Before handover |
| Photo style | Owner selects real consented family photos versus neutral illustrated placeholders; no unlicensed asset reuse | Before visual/storyboard sign-off |
| Languages and professional reviewers | English/Spanish/Hebrew + RTL planned; approve reviewed safety/billing copy | Before localized release |
| PIN/recovery parameters | Review six-digit proposal, secret format, hashing and cooldown against usability/threat model | Before persistence implementation |
| Diagnostics, retention and data policy | Approve default-off proposal and actual SDK disclosures/retention | Before SDK activation |
| Billing grace/offline uncertainty and cancellation support | Explicit Play configuration and conservative premium-only handling; core unaffected | Before billing release |
| Gallery/library expectations | Explain owned + selected local images; broad library access requires a new justified permission/product decision | Before media implementation |

## 14. Primary-source references

Supporting research contains access dates, detailed evidence and limitations. The following material platform points should be rechecked at implementation/submission time; links are not claims of approval or configuration.

- **P1 — Roles:** [Android `RoleManager`](https://developer.android.com/reference/android/app/role/RoleManager) — independent role availability/request APIs.
- **P2 — Calling:** [Default phone application](https://developer.android.com/develop/connectivity/telecom/dialer-app), [`InCallService`](https://developer.android.com/reference/android/telecom/InCallService), [`TelecomManager`](https://developer.android.com/reference/android/telecom/TelecomManager) and [`Intent`](https://developer.android.com/reference/android/content/Intent) — default-call UI, emergency/preloaded dialer boundary and external handoffs; distinguish self-managed calling examples from default-phone duties.
- **P3 — Restricted access:** [Default-handler permissions](https://developer.android.com/guide/topics/permissions/default-handlers) and [Play SMS/Call Log policy](https://support.google.com/googleplay/android-developer/answer/10208820) — matching functionality, role-first ordering and permitted use. Re-audit scheduled policy changes before shipping.
- **P4 — SMS duties:** [`Telephony`: creating an SMS app](https://developer.android.com/reference/android/provider/Telephony) and [`SmsManager`](https://developer.android.com/reference/android/telephony/SmsManager) — default-handler components/provider responsibilities and carrier-dependent delivery callbacks.
- **P5 — Media:** [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [shared media storage](https://developer.android.com/training/data-storage/shared/media) and [`FileProvider`](https://developer.android.com/reference/androidx/core/content/FileProvider) — user-selected access, ownership/delete rules and safe content URIs. Photo Picker guidance was directly read for this PRD; full media/device behavior remains a spike obligation.
- **P6 — Device capabilities:** [`SubscriptionManager`](https://developer.android.com/reference/android/telephony/SubscriptionManager), [`TelecomManager`](https://developer.android.com/reference/android/telecom/TelecomManager) and [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) — multi-SIM capability/permission boundaries and denied-notification behavior.
- **P7 — Subscription disclosure:** [Play Subscriptions policy](https://support.google.com/googleplay/android-developer/answer/9900533) — transparent recurrence, trial terms and cancellation access.
- **P8 — RevenueCat:** [CustomerInfo](https://www.revenuecat.com/docs/customers/customer-info), [caching](https://www.revenuecat.com/docs/test-and-launch/debugging/caching) and [non-subscription purchases](https://www.revenuecat.com/docs/platform-resources/non-subscriptions) — cached entitlement, restore and non-consumable semantics; verify supported SDK version at integration.
- **P9 — Store lifecycle:** [Manage subscriptions](https://support.google.com/googleplay/android-developer/answer/140504) and [one-time purchase lifecycle](https://developer.android.com/google/play/billing/lifecycle/one-time) — configurable grace/hold and purchase acknowledgment.
- **P10 — Disclosure/consent:** [Firebase Android data disclosure](https://firebase.google.com/docs/android/play-data-disclosure), [Analytics collection controls](https://firebase.google.com/docs/analytics/configure-data-collection), [Crashlytics opt-in reporting](https://firebase.google.com/docs/crashlytics/customize-crash-reports?platform=android#enable-reporting), [Play Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469) — defaults, collection controls and actual-data disclosure.
- **P11 — Lifecycle limits:** [Power-management resource limits](https://developer.android.com/topic/performance/power/power-details) and [Doze/App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) — no guaranteed always-on background timers or delivery.
- **P12 — Historical precedent:** [BaldPhone #216](https://github.com/UriahShaulMandel/BaldPhone/issues/216) and [#1245](https://github.com/UriahShaulMandel/BaldPhone/issues/1245) — dated developer/community evidence, not a blanket current Android/Play restriction.
