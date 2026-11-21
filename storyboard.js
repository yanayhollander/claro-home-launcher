/* Claro Home: local, in-memory product storyboard. No device or network APIs. */
(() => {
  "use strict";
  const $ = id => document.getElementById(id);
  const phone = $("phone-content");
  const actionDock = $("phone-actions");
  const previewParams = new URLSearchParams(location.search);
  let classicMode = previewParams.get("version") === "classic";
  const classicLayouts = { classic: "A. Classic", wide: "B. Wide softkeys", lower: "C. Softkeys below", stacked: "D. Integrated pad" };
  const classicLayout = Object.hasOwn(classicLayouts, previewParams.get("layout")) ? previewParams.get("layout") : "classic";
  const device = document.querySelector(".phone");
  const classicDeck = $("classic-deck");
  let classicActions = [], classicOptionActions = [], classicOptionsOpen = false;
  let classicOptionsScroll = 0, classicOptionReturnKey = "";
  let classicOptionInput = null;
  let classicSelected = null, classicSelectionKey = "", classicSelectionScope = "";
  let classicScrollObserver;
  document.body.classList.toggle("classic-preview", classicMode);
  {
    document.body.dataset.classicLayout = classicLayout;
    $("classic-layout-controls").hidden = !classicMode;
    $("classic-layout").innerHTML = Object.entries(classicLayouts).map(([value, label]) => `<option value="${value}">${label}</option>`).join("");
    $("classic-layout").value = classicLayout;
    $("classic-layout").addEventListener("change", event => {
      const url = new URL(location.href);
      url.searchParams.set("layout", event.target.value);
      location.assign(url.href);
    });
    if (classicMode && previewParams.get("embedded") === "1") {
      document.body.classList.add("classic-embed");
      $("clean-preview").checked = true;
    }
  }
  $(classicMode ? "classic-version" : "touch-version").setAttribute("aria-current", "true");
  const esc = value => String(value ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const paths = {
    phone: "M8 3H4a1 1 0 0 0-1 1c0 9.4 7.6 17 17 17a1 1 0 0 0 1-1v-4l-5-2-2 2a14 14 0 0 1-6-6l2-2-2-5Z",
    messages: "M2 5h20v14H2V5Zm2 2 8 6 8-6h-3l-5 4-5-4H4Z",
    camera: "M3 7h5l2-3h4l2 3h5v13H3V7Zm9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z",
    gallery: "M3 3h18v18H3V3Zm2 15h14l-4-5-3 3-4-6-3 8ZM16 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z",
    sos: "M12 3 2 21h20L12 3Zm0 6v5m0 3h.01",
    apps: "M5 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm14 0a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM5 10a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm14 0a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM5 17a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm14 0a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z",
    microphone: "M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0V5Zm-3 6v1a6 6 0 0 0 12 0v-1m-6 7v4m-4 0h8",
    speaker: "M3 9h4l5-5v16l-5-5H3V9Zm13-2a7 7 0 0 1 0 10m3-13a11 11 0 0 1 0 16",
    end: "M3 14c5-5 13-5 18 0l-2 5-5-2v-3h-4v3l-5 2-2-5Z",
    backspace: "M9 5h12v14H9l-7-7 7-7Zm3 4 6 6m0-6-6 6",
    clock: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm0 4v5l3 2",
    settings: "m9 3-1 3-3 1-2 5 2 5 3 1 1 3h6l1-3 3-1 2-5-2-5-3-1-1-3H9Zm3 5a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z",
    contact: "M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM4 21v-3a8 8 0 0 1 16 0v3",
    "contact-add": "M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM2 21v-3a7 7 0 0 1 14 0v3M20 5v8m-4-4h8",
    edit: "M4 20l1-5L17 3l4 4L9 19l-5 1ZM14 6l4 4",
    delete: "M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7",
    check: "m4 12 5 5L20 6",
    "arrow-up": "M12 20V4m-7 7 7-7 7 7",
    "arrow-down": "M12 4v16m-7-7 7 7 7-7",
    "arrow-left": "M20 12H4m7-7-7 7 7 7",
    "arrow-right": "M4 12h16m-7-7 7 7-7 7",
    lock: "M6 10h12v11H6V10Zm3 0V6a3 3 0 0 1 6 0v4",
    sun: "M12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M5 19l2-2M17 7l2-2M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Z"
  };
  const sampleCallDuration = "00:24";
  const icon = name => `<svg class="icon ${name}-icon" width="24" height="24" viewBox="0 0 24 24" fill="${["phone", "messages", "camera", "gallery", "apps", "end"].includes(name) ? "currentColor" : "none"}" fill-rule="evenodd" stroke="currentColor" stroke-width="${["phone", "messages", "camera", "gallery", "apps", "end"].includes(name) ? ".4" : "1.7"}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name] || paths.apps}"/></svg>`;
  const definitions = [
    ["home", "Everyday use", "A calmer kind of home.", "Everything that matters, right where it belongs.", "HOME-01/02 · CORE-01", "Tap a person, explore the six tiles, then scroll to the extra apps."],
    ["dialer", "Everyday use", "A number. A familiar voice.", "A readable keypad for entering phone numbers.", "PHONE-01/02 · CALL-03", "Enter a number, then press Call."],
    ["contacts", "Everyday use", "The people who matter.", "Searchable names, clear numbers and familiar initials.", "CONTACT-01", "Search Mira or a number. Open a person to call or write."],
    ["contact-detail", "Everyday use", "One person, clear choices.", "Choose the number before calling or writing.", "CONTACT-02 · SETTINGS-02", "Try the second number, or open protected contact editing."],
    ["calling", "Phone & messages", "Keep the conversation simple.", "Large, labeled controls remain within reach.", "CALL-01/02", "Try speaker, mute and Home. An active demo call stays accessible."],
    ["incoming", "Phone & messages", "A familiar voice is calling.", "Answer and decline without learning a swipe.", "CALL-01", "Answer the fictional incoming call, or review an unknown caller."],
    ["call-ended", "Phone & messages", "An obvious next step.", "Review the connected call duration, then return home, call again or write a message. The sample duration matches the active call preview.", "CALL-02", "Call back or send a follow-up; no real phone is contacted."],
    ["missed-call", "Phone & messages", "See who called.", "A missed incoming call is not a failed outgoing call.", "CALL-02", "Call back or write a message. Private callers have no callback number."],
    ["call-unanswered", "Phone & messages", "They did not answer.", "An unanswered outgoing call has no connected duration.", "CALL-02", "Try calling again, write a message or return Home."],
    ["call-failed", "Phone & messages", "An honest, useful explanation.", "No service is different from no internet.", "CALL-02/03", "Change the failure reason and try the simulated native handoff."],
    ["messages", "Phone & messages", "Messages without the clutter.", "Big names, readable previews and visible unread messages.", "SMS-01/04", "Read Mira’s message, or write a new one."],
    ["conversation", "Phone & messages", "Room for every word.", "A simple conversation with honest delivery status.", "SMS-01/03/04", "Reply, review the recipient and send a demo message."],
    ["compose", "Phone & messages", "Say it in your own words.", "Direct Send, explicit recipients and automatically retained drafts.", "SMS-02", "Choose a contact or enter a number. Send directly; Back retains the draft."],
    ["reply", "Phone & messages", "Space to write a reply.", "Reading and writing have separate views in Classic.", "SMS-02/03", "Reply, correct with backspace, or return to the conversation without losing the draft."],
    ["message-failed", "Phone & messages", "Your words are still here.", "Recover without losing the draft or hiding duplicate risk.", "SMS-03", "Review the retained text before explicitly retrying."],
    ["camera", "Photos & apps", "Let the camera do its job.", "Opens the device camera immediately; Claro adds no capture or save screen.", "MEDIA-01", "The external-camera illustration stands in for Android. Return to the previous screen when finished."],
    ["gallery", "Photos & apps", "Little moments, easy to find.", "Newest device photos, represented here by original illustrations.", "MEDIA-02", "Open a picture. Classic Up/Down browses photos; Options contains Delete."],
    ["photo", "Photos & apps", "One memory at a time.", "Browse pictures and authorize device deletion deliberately.", "MEDIA-02/03", "Classic Up/Down browses illustrations. Options opens the simulated Android delete confirmation."],
    ["sos", "Help & safety", "Help from someone you trust.", "A family help call is not an emergency dispatch service.", "SOS-01/02", "Start a five-second demo countdown, then cancel it."],
    ["sos-countdown", "Help & safety", "Time to change your mind.", "Five seconds, a clear stop and no hidden side effects.", "SOS-02", "Cancel before zero. Leaving this screen also cancels the countdown."],
    ["sos-active", "Help & safety", "Your familiar call controls.", "SOS uses the same call screen, with status, speaker, mute and End.", "SOS-03", "Review dialing, ringing or connected states, then end the simulated call. No alert is automatically delivered."],
    ["sos-failed", "Help & safety", "Help must not be a promise.", "Useful recovery without claiming anyone has been notified.", "SOS-03", "Review the failure, then explicitly try again or return Home."],
    ["apps", "Photos & apps", "Only the apps you chose.", "Allowed apps only. Search and editing live in protected settings.", "APPS-01/02", "Open an allowed app or review the empty and unavailable states."],
    ["pin", "Caregiver", "A small pause for settings.", "Choose a date question or caregiver PIN during setup.", "PIN-01/02", "The date question uses today's local date in dd/MM/YYYY format. PIN mode uses the credentials you created in Setup."],
    ["settings", "Caregiver", "Thoughtfully set up. Easily kept.", "One protected place for the caregiver’s choices.", "SETTINGS-01/02/03", "Change display, favorites, home layout or readiness; explicitly lock when done."],
    ["home-edit", "Caregiver", "Apps & Menu.", "Choose allowed apps and which appear in Menu, separately from Home slots.", "APPS-01/02", "Search the installed-app samples. Allow an app, then optionally show it in Menu."],
    ["speed-dial", "Caregiver", "Home tiles.", "Add or rearrange Home tiles.", "SETTINGS-02", "Choose contacts, apps or built-in shortcuts in a full-screen picker. Changes apply immediately."],
    ["tile-picker", "Caregiver", "Choose what belongs here.", "Searchable contact numbers, installed apps and built-in shortcuts.", "SETTINGS-02", "Built-in shortcuts are unique; contacts and apps may repeat. Clear removes only this assignment."],
    ["menu-order", "Caregiver", "A familiar order.", "Menu order is saved separately from Home tiles.", "SETTINGS-01", "Move entries up or down. Clearing Home slots does not change Menu."],
    ["phone-style", "Caregiver", "The same phone, another style.", "Switch presentation without losing contacts, drafts or settings.", "SETTINGS-03", "Apply Touch or Classic. Switching waits during a call, SOS countdown or purchase."],
    ["call-audio", "Caregiver", "Audio that follows your phone.", "Automatic speaker and earpiece, with manual selection taking priority.", "CALL-01", "Maximum volume is off by default and requires warning confirmation. Sensor behavior is not simulated."],
    ["display", "Caregiver", "Comfort comes first.", "Language and display, with full-screen language and text-size pickers.", "A11Y-01/02 · SETTINGS-03", "Choose Standard, Large or Huge text and optional vibration. High contrast has been removed."],
    ["display-picker", "Caregiver", "One clear choice.", "Select a language or text size, then return to the display editor.", "I18N-01 · A11Y-01", "Selection updates the preview; Save applies it."],
    ["sos-settings", "Caregiver", "Agree on help before it is needed.", "Choose a trusted person and explain the limits together.", "SOS-01/04 · SETTINGS-02", "Choose a recipient, confirm their agreement, save and run a simulated test."],
    ["permissions", "Caregiver", "Ready means checked.", "Role grants and successful service are different things.", "SETUP-02/03/04", "Simulate a denial and repair. No OS permission is actually requested."],
    ["privacy", "Caregiver", "Your choice, at any time.", "Usage analytics and crash reports turn on after onboarding consent; either can be turned off here.", "PRIV-01/02 · DATA-01", "Toggle analytics or crash diagnostics. This demo contains no SDK or upload."],
    ["reset", "Caregiver", "A fresh start, not a surprise.", "Reset only this in-memory demo configuration.", "DATA-02", "Review exactly what changes. Cancel is always available."],
    ["welcome", "Getting started", "Big buttons. Less fuss.", "A warmer welcome, followed by a clear explanation of helping improve Claro.", "SETUP-01", "Set up Claro opens the eight-step journey. Continue on the next screen enables analytics and crash reports. These can be turned off later in Settings."],
    ["setup", "Getting started", "Make Claro yours.", "Permissions first, then Home shortcuts, VIP direct-call contacts and an easy SOS choice.", "SETUP-01/04 · PRIV-01", "Select any screen above to review, or follow setup in order. VIP selections save directly to Home. Review shortcuts never give consent; Android prompts are simulations."],
    ["access-settings", "Caregiver", "Settings access.", "Today's date or a six-digit caregiver PIN with a separate recovery secret.", "PIN-01/02", "A recovery secret needs at least 12 characters. Settings stay unlocked until explicitly locked or this page reloads."],
    ["plans", "Membership", "Free essentials. Optional extras.", "Seven-day trial only after an eligible store enrollment.", "BILL-01/02/03 · CORE-01/02", "Select monthly, yearly or separate lifetime. Actual prices are not approved."],
    ["purchase", "Membership", "Review, never assume.", "A store-review simulation, not a real checkout.", "BILL-02/03/04", "Explore pending, canceled and offline outcomes. Nothing can charge you."],
    ["membership", "Membership", "Membership, in plain language.", "No subscription state takes away everyday phone functions.", "BILL-04/05/06", "Try restore or simulated manage/cancel, then review expiry."],
    ["trial-ended", "Membership", "Still your familiar phone.", "Premium convenience ends. Core calling, SMS and SOS do not.", "CORE-01/02 · BILL-05", "Continue free; all core tiles and assignments remain."],
    ["pin-recovery", "Caregiver", "Recover access deliberately.", "Only PIN mode needs a separate recovery secret. Date mode has no secret to forget.", "PIN-02", "Use the recovery phrase created in Setup. In date mode this route returns to the date question, not a PIN reset."]
  ];
  const dialerOptions = {
    "dialer-contacts-first": { label: "1. Contacts first", initialTab: "contacts", title: "Start with a person.", subtitle: "Phone opens the contact list. The keypad and recent calls stay one tap away." },
    "dialer-keypad-first": { label: "2. Keypad first", initialTab: "keypad", title: "A familiar keypad. People within reach.", subtitle: "Keep number entry first, but promote Contacts to a large button at the top." },
    "dialer-two-choices": { label: "3. Two big choices", initialTab: "choices", title: "People or a number?", subtitle: "Two large, vertically stacked choices before opening contacts or the keypad." }
  };
  const registry = Object.fromEntries(definitions.map((d, index) => [d[0], { id: d[0], group: d[1], title: d[2], subtitle: d[3], refs: d[4], try: d[5], index }]));
  Object.entries(dialerOptions).forEach(([id, option]) => {
    registry[id] = { ...registry.dialer, ...option, id, try: "Compare this alternative without replacing the current dialer. Choose a person or enter a number; all calls remain simulated." };
  });
  registry["classic-menu"] = { ...registry.home, id: "classic-menu", title: "A familiar phone menu.", subtitle: "Menu order is independent of your Home tiles.", try: "Choose a feature. Missed-call and message badges reflect the same records as Home." };
  const protectedRoutes = new Set(["settings", "home-edit", "speed-dial", "tile-picker", "menu-order", "phone-style", "call-audio", "display", "display-picker", "language", "access-settings", "sos-settings", "permissions", "privacy", "reset", "plans", "purchase", "membership", "trial-ended"]);
  const installedApps = ["Weather", "Calendar", "Calculator", "Music", "Maps", "WhatsApp", "Facebook"];
  const shortcuts = ["phone", "messages", "camera", "gallery", "sos", "apps", "alarm"];
  const languages = { en: "🇬🇧 English", es: "🇪🇸 Español", he: "🇮🇱 עברית" };
  const deviceLanguage = [...navigator.languages, "en"].map(language => language.split("-")[0]).find(language => languages[language]);
  const initial = () => ({
    contacts: [
      { id: "mira", name: "Mira", numbers: ["202-555-0101", "202-555-0102"], color: "rose" },
      { id: "daniel", name: "Daniel", numbers: ["202-555-0103"], color: "blue" },
      { id: "leah", name: "Leah", numbers: ["202-555-0104"], color: "gold" },
      { id: "noah", name: "Noah", numbers: ["202-555-0105"], color: "" }
    ],
    keys: ["mira:0:202-555-0101", "daniel:0:202-555-0103"],
    sos: "mira:0:202-555-0101", sosConsent: true,
    allowed: ["Weather", "Calendar", "Calculator", "Music", "Maps"], menuApps: ["Weather", "Calendar"],
    slots: ["phone", "camera", "gallery", "sos", "apps"],
    menuOrder: ["contacts", "phone", "messages", "camera", "gallery", "sos", "apps", "app:Weather", "app:Calendar", "settings"],
    language: deviceLanguage, textSize: "Standard", large: false, contrast: false, haptics: true,
    easyAudio: true, maximumVolume: false, selectedStyle: "classic", pendingStyle: "", setupStep: 0, onboarding: false, pickerTarget: "home:0", pickerReturn: "speed-dial", displayPicker: "language",
    analytics: false, crashes: false, accessMode: "date", pin: "", recovery: "", configured: false,
    setupCompleted: false, setupReentry: false, onboardingPicker: "", onboardingQuery: "",
    readiness: { Home: "Not checked", Phone: "Not checked", Messages: "Not checked", Contacts: "Not checked", Notifications: "Not checked", Media: "Not checked" },
    handover: false, digits: "", recipient: "", body: "", drafts: {}, deletedThreads: [], selected: "mira", numberIndex: 0,
    target: { name: "Mira", number: "202-555-0101" }, messages: [], unread: true,
    missedCalls: [{ id: 1, name: "Mira", number: "202-555-0101", when: "Today · 09:20", unread: true }], selectedMissedId: 1,
    activeCall: false, callState: "Connected", muted: false, speaker: false, held: false, dialTab: "keypad", phoneTabs: {}, network: "available", sosArmed: false, classicTones: "",
    membership: "Free", plan: "monthly", photos: [{ id: 1, title: "Morning hills", owned: false }, { id: 2, title: "A quiet afternoon", owned: false }, { id: 3, title: "Garden light", owned: true }, { id: 4, title: "Weekend walk", owned: false }],
    photoIndex: 0, search: "", error: "", notice: "", attempts: 0, cooldown: 0, recoveryAttempts: 0, recoveryCooldown: 0,
    unlocked: false, intended: "settings", contactMode: "", editId: "", picker: "", external: "", confirm: "", setupVisits: {}, formDrafts: {}, preferenceDrafts: {}
  });
  let state = initial(), route = "", variant = "standard", suppressClick = null;
  let countdownTimer, holdTimer, holdButton, holdStart, countdown = 5, draft = null, accessModeDraft = null;
  let classicFocusVisible = false;
  let classicInput = null, classicModeIndex = 0, multiTap = null;
  const classicModes = ["English", "עברית", "Numbers"];
  const classicEnglish = { "1": ".,?!1", "2": "abc2", "3": "def3", "4": "ghi4", "5": "jkl5", "6": "mno6", "7": "pqrs7", "8": "tuv8", "9": "wxyz9", "0": " 0", "*": ".,?!*" };
  const classicHebrew = { "2": "אבג", "3": "דהו", "4": "זחט", "5": "יכךל", "6": "מםנן", "7": "סעפף", "8": "צץקר", "9": "שת" };
  const variants = {
    home: ["standard", "missed", "unread messages", "notifications cleared", "no SIM", "no signal", "airplane mode", "signal unknown", "unassigned", "active call", "huge text"],
    dialer: ["standard", "empty recents", "unassigned", "permission denied", "no signal"],
    ...Object.fromEntries(Object.keys(dialerOptions).map(id => [id, ["standard", "empty contacts", "empty recents", "unassigned", "permission denied", "no signal"]])),
    contacts: ["standard", "empty", "permission denied"],
    "contact-detail": ["standard", "save failure", "write denied"],
    calling: ["connected", "dialing", "ringing", "on hold", "Bluetooth", "call waiting", "no answer"],
    incoming: ["known", "unknown", "private", "missed"],
    "call-ended": ["ended", "missed", "declined", "canceled"],
    "missed-call": ["standard", "unknown", "private"],
    "call-unanswered": ["no answer"],
    "call-failed": ["no signal", "no SIM", "invalid number", "account unavailable", "permission denied", "unknown failure"],
    messages: ["standard", "empty", "permission denied", "MMS / group"],
    conversation: ["standard", "sending", "sent", "delivery unknown", "delivery confirmed", "MMS", "download error"],
    compose: ["standard", "invalid recipient", "offline"],
    "message-failed": ["known failure", "unknown outcome"],
    camera: ["standard", "canceled", "unavailable", "capture failure"],
    gallery: ["standard", "empty", "loading", "permission denied", "cloud unavailable"],
    photo: ["standard", "delete failure"],
    sos: ["standard", "unconfigured", "no signal", "no SIM"],
    "sos-countdown": ["standard", "canceled", "interrupted"],
    "sos-active": ["dialing", "ringing", "connected", "ended"],
    "sos-failed": ["no signal", "busy", "no answer", "unknown failure"],
    apps: ["standard", "empty", "missing app"],
    pin: ["standard", "incorrect", "cooldown", "locked session"],
    settings: ["standard", "session timeout"],
    "home-edit": ["standard", "extra tiles"], "speed-dial": ["standard", "unassigned"],
    display: ["standard", "huge text"], language: ["standard"],
    "sos-settings": ["standard", "unconfigured", "not agreed"],
    permissions: ["standard", "denied", "revoked", "unavailable", "permanent denial"],
    privacy: ["standard", "consent withdrawn"], reset: ["standard", "active call", "reset complete"],
    welcome: ["standard"], setup: ["standard", "incomplete", "complete"],
    plans: ["standard", "ineligible", "loading", "offline", "owned lifetime"],
    purchase: ["standard", "pending", "canceled", "error", "offline", "demo success"],
    membership: ["Free", "Trial demo", "Active demo", "Canceled — valid demo", "Grace demo", "Payment issue demo", "Account hold demo", "Expired demo", "Lifetime demo", "Offline — unverified"],
    "trial-ended": ["standard", "offline"],
    "pin-recovery": ["standard", "incorrect", "cooldown", "forgotten secret"]
  };
  variants["classic-menu"] = ["standard"];
  variants.settings = ["standard", "process restarted"];
  variants.setup = ["standard"];
  variants["home-edit"] = ["standard", "empty"];
  variants["tile-picker"] = variants["menu-order"] = variants["phone-style"] = variants["call-audio"] = variants["display-picker"] = variants["access-settings"] = ["standard"];
  variants.reply = ["standard", "send failure", "unknown outcome"];
  variants.compose = ["standard", "invalid recipient", "offline", "send failure", "unknown outcome"];
  variants.conversation.push("delete failure");
  variants.plans.unshift("unconfigured");
  variants.membership.unshift("Unavailable");
  variants.photo = ["standard", "delete failure"];
  variants.gallery = ["standard", "empty", "loading", "permission denied", "partial access"];
  const copy = {
    en: { home: "Home", back: "Back", phone: "Phone", messages: "Messages", camera: "Camera", gallery: "Gallery", sos: "SOS", apps: "Apps", people: "Your people", contacts: "Contacts", call: "Call", recent: "Recent", missed: "Missed", keypad: "Keypad", search: "Search name or number", newMessage: "Write a message", date: "Wednesday, 11 November", more: "More apps below", settings: "Settings", callPerson: "Call", type: "Enter a number", write: "Message", battery: "Battery", signal: "Mobile signal", ready: "Available", add: "Add person" },
    es: { home: "Inicio", back: "Atrás", phone: "Teléfono", messages: "Mensajes", camera: "Cámara", gallery: "Galería", sos: "SOS", apps: "Aplicaciones", people: "Tus personas", contacts: "Contactos", call: "Llamar", recent: "Recientes", missed: "Perdidas", keypad: "Teclado", search: "Buscar nombre o número", newMessage: "Escribir un mensaje", date: "Miércoles, 11 de noviembre", more: "Más aplicaciones abajo", settings: "Ajustes", callPerson: "Llamar", type: "Introduce un número", write: "Mensaje", battery: "Batería", signal: "Señal móvil", ready: "Disponible", add: "Añadir persona" },
    he: { home: "בית", back: "חזרה", phone: "טלפון", messages: "הודעות", camera: "מצלמה", gallery: "גלריה", sos: "עזרה", apps: "יישומים", people: "האנשים שלך", contacts: "אנשי קשר", call: "התקשר", recent: "אחרונות", missed: "שלא נענו", keypad: "חייגן", search: "חיפוש שם או מספר", newMessage: "כתיבת הודעה", date: "יום רביעי, 11 בנובמבר", more: "יישומים נוספים למטה", settings: "הגדרות", callPerson: "התקשר", type: "הזנת מספר", write: "הודעה", battery: "סוללה", signal: "קליטה סלולרית", ready: "זמין", add: "הוספת איש קשר" }
  };
  const t = key => copy[state.language][key] || key;
  const button = (text, action, cls = "secondary", attrs = "") => `<button type="button" class="${cls}" data-action="${action}" ${attrs}>${text}</button>`;
  const goButton = (text, dest, cls = "secondary") => button(text, "go", cls, `data-route="${dest}" ${/^Cancel/.test(text) && !text.includes("keep draft") ? 'data-discard="true"' : ""}`);
  const info = (text, cls = "") => `<div class="info-card ${cls}" role="${cls === "error" ? "alert" : "status"}">${text}</div>`;
  const field = (label, name, value = "", type = "text", attrs = "") => `<label><span class="field-label">${esc(label)}</span><input name="${name}" id="field-${name}" type="${type}" value="${esc(value)}" ${attrs}></label>`;
  const row = (title, subtitle, action, attrs = "", ico = "") => button(`${ico}<span class="row-copy">${esc(title)}${subtitle ? `<small>${esc(subtitle)}</small>` : ""}</span><span class="arrow" aria-hidden="true">→</span>`, action, "row-button", attrs);
  const avatar = (contact, large = false) => {
    const palettes = [["#dcebe5", "#bb7955", "#3c4d46", "#48836e"], ["#e0e9f4", "#e0ab86", "#54473e", "#557bb1"], ["#f3e7d5", "#c58d68", "#675042", "#bd813c"], ["#ebe3f1", "#e6b492", "#48414c", "#8a6da8"]];
    const index = Math.max(0, state.contacts.findIndex(c => c.id === contact?.id || c.name === contact?.name)) % palettes.length;
    const [background, skin, hair, shirt] = palettes[index];
    return `<span class="avatar ${contact?.color || ""} ${large ? "large" : ""}" aria-hidden="true">${contact?.name && contact.name !== "?" ? `<svg class="avatar-portrait" viewBox="0 0 56 56" width="56" height="56"><rect width="56" height="56" rx="8" fill="${background}"/><path d="M7 56c0-20 42-20 42 0" fill="${shirt}"/><path d="M23 35h10v10H23z" fill="${skin}"/><ellipse cx="28" cy="24" rx="13" ry="16" fill="${skin}"/><path d="M15 25V17C15 2 42 2 41 21l-5-9c-5 7-12 3-17 8v5z" fill="${hair}"/><path d="M22 25h1m10 0h1" stroke="${hair}" stroke-width="2.5" stroke-linecap="round"/><path d="M24 32q4 4 8 0" fill="none" stroke="${hair}" stroke-width="1.8" stroke-linecap="round"/></svg>` : icon("contact")}</span>`;
  };
  const resolve = value => {
    if (value === "manual-sos") return state.sosManual ? { ...state.sosManual, id: "manual-sos", numbers: [state.sosManual.number] } : null;
    const [id, index, assignedNumber] = (value || "").split(":");
    const contact = state.contacts.find(c => c.id === id);
    const number = contact?.numbers[Number(index) || 0];
    return contact && number && (!assignedNumber || assignedNumber === number) ? { ...contact, number } : null;
  };
  const selectedContact = () => state.contacts.find(c => c.id === state.selected) || state.contacts[0];
  const selectedMissedCall = () => state.missedCalls.find(call => call.id === state.selectedMissedId) || state.missedCalls[0];
  const notificationCounts = () => ({
    missed: state.readiness.Phone.startsWith("Denied") ? null : state.missedCalls.filter(call => call.unread).length,
    messages: state.readiness.Messages.startsWith("Denied") ? null : Number(state.unread)
  });
  const todayDate = () => {
    const now = new Date();
    return `${String(now.getDate()).padStart(2, "0")}/${String(now.getMonth() + 1).padStart(2, "0")}/${now.getFullYear()}`;
  };
  const recipientContact = () => state.contacts.find(c => c.numbers.some(number =>
    number.replace(/[ ()-]/g, "") === state.recipient.replace(/[ ()-]/g, "")));
  const validNumber = value => /^[+]?[\d ()-]{3,24}$/.test(value) && value.replace(/\D/g, "").length >= 3;
  const emergencyNumber = value => ["112", "911", "999", "000", "100", "101", "102"].includes(value.replace(/\D/g, ""));
  let reviewFeedback = "";
  function reviewOnly(text) { reviewFeedback = text; }
  const contactOptions = (value, includeEmpty = true) => `${includeEmpty ? '<option value="">Not assigned</option>' : ""}${state.contacts.map(c => c.numbers.map((n, i) => `<option value="${c.id}:${i}:${esc(n)}" ${value === `${c.id}:${i}:${n}` ? "selected" : ""}>${esc(c.name)} · \u2066${esc(n)}\u2069${i ? " · home" : " · mobile"}</option>`).join("")).join("")}`;
  function clearHold() {
    clearTimeout(holdTimer);
    if (holdButton) { holdButton.classList.remove("holding"); holdButton.removeAttribute("aria-description"); }
    holdButton = null;
  }
  function cleanup() { clearHold(); clearInterval(countdownTimer); countdownTimer = undefined; }
  function lock() { state.unlocked = false; accessModeDraft = null; }
  const legacyRoutes = { "call-confirm": "dialer", "message-confirm": "compose", language: "display" };
  const isProtected = id => protectedRoutes.has(id) && !state.onboarding;
  function navigate(id, replace = false) {
    id = legacyRoutes[id] || id;
    if (!registry[id]) id = "home";
    if (id === "setup" && !state.onboarding && route === "settings") {
      state.setupReentry = true; state.setupCompleted = true; state.onboarding = true;
    }
    if (id === "home" && state.onboarding && !state.setupReentry) {
      state.notice = "Finish setup before going Home."; render(); return;
    }
    if (isProtected(id) && !state.unlocked) { state.intended = id; state.pendingAction = ""; id = "pin"; }
    if (id === "camera" && route !== "camera") state.cameraReturn = route;
    if (id === "home") state.pendingAction = "";
    if (id === route && id === "home") return;
    if (id === "dialer" && state.activeCall) id = "calling";
    const hash = `#${id}`;
    if (location.hash === hash) { activate(id); return; }
    if (replace) history.replaceState(history.state || { claro: true, parent: null }, "", hash);
    else history.pushState({ claro: true, parent: route || null }, "", hash);
    activate(id);
  }
  function activate(id, fromHistory = false) {
    cleanup();
    if (legacyRoutes[id]) { id = legacyRoutes[id]; history.replaceState(history.state, "", `#${id}`); }
    if (!registry[id]) { id = "home"; history.replaceState(history.state, "", "#home"); }
    if (classicMode && dialerOptions[id]) { id = "dialer"; history.replaceState(history.state, "", "#dialer"); }
    if (id === "pin-recovery" && state.accessMode === "date") { id = "pin"; history.replaceState(history.state, "", "#pin"); }
    if (isProtected(id) && !state.unlocked) { state.intended = id; state.pendingAction = ""; id = "pin"; history.replaceState(history.state, "", "#pin"); }
    if (route !== id) {
      if (route === "compose") state.newDraft = { recipient: state.recipient, body: state.body };
      if (route === "reply") state.drafts[state.recipient] = state.body;
      classicInput = null; multiTap = null; classicFocusVisible = false; classicSelectionKey = "";
      if (route === "setup") accessModeDraft = null;
      if (draft && ["home-edit", "display", "language"].includes(route)) state.preferenceDrafts[route] = draft;
      if (route === "contact-detail") state.contactMode = "";
      variant = (variants[id] || ["standard"])[0]; draft = null; state.search = ""; state.external = ""; state.confirm = ""; state.error = ""; state.notice = "";
      if (id === "dialer" && !state.preparedNumber && !(fromHistory && route === "contacts")) { state.digits = ""; state.dialIdentity = null; }
      state.preparedNumber = false;
      if (id === "compose") {
        state.recipient = state.newDraft?.recipient || "";
        state.body = state.newDraft?.body || "";
      }
      if (id === "reply") {
        state.recipient = state.target.number;
        state.body = state.drafts[state.recipient] || "";
      }
      if (id === "phone-style") state.selectedStyle = classicMode ? "classic" : "touch";
      if (id === "sos-active") {
        const recipient = resolve(state.sos);
        setTarget(recipient, recipient?.number);
        state.callTarget = { ...state.target };
        state.callState = variant;
      }
      if (id === "setup" && !state.onboarding) state.onboarding = true;
      if (id === "camera") state.external = "Device camera";
    }
    route = id;
    if (["call-ended", "call-failed", "call-unanswered", "sos-failed"].includes(id)) { state.activeCall = false; state.held = false; }
    if (state.pendingStyle && !state.activeCall && !["sos-countdown", "purchase"].includes(id)) {
      classicMode = state.pendingStyle === "classic"; state.pendingStyle = "";
      const url = new URL(location.href); url.searchParams.set("version", classicMode ? "classic" : "touch"); history.replaceState(history.state, "", url);
    }
    if (id === "missed-call") {
      const missed = selectedMissedCall();
      if (!state.readiness.Phone.startsWith("Denied")) missed.unread = false;
    }
    if (["calling", "sos-active"].includes(id)) state.activeCall = true;
    if (id === "calling") { variant = state.callState.toLowerCase(); if (state.callTarget) state.target = { ...state.callTarget }; }
    if (id === "conversation") {
      if (state.conversationTarget) state.target = { ...state.conversationTarget };
      if (state.contacts.find(contact => contact.id === "mira")?.numbers.includes(state.target.number)) state.unread = false;
    }
    if (id === "display") draft = draft || state.preferenceDrafts[id] || { language: state.language, textSize: state.textSize, large: state.large, haptics: state.haptics };
    if (id === "display-picker") draft = state.preferenceDrafts.display || { language: state.language, textSize: state.textSize, haptics: state.haptics };
    state.setupVisits[id] = true;
    render();
    phone.scrollTop = 0; phone.focus({ preventScroll: true });
    if (route === "sos-countdown") startCountdown();
  }
  function back() {
    if (classicMode && classicOptionsOpen) { closeClassicOptions(); return; }
    if (classicMode && state.contactMode && classicTarget() && (classicTarget().selectionEnd ?? 0) > 0) { classicDelete(); return; }
    if (state.external && route === "camera") { actions["external-back"](); return; }
    if (state.confirm || state.external || state.contactMode) { state.confirm = ""; state.external = ""; state.contactMode = ""; state.error = ""; render(); return; }
    if (route === "setup") { if (state.onboardingPicker) { actions["setup-picker-done"](); } else if (state.setupStep === 0) navigate("welcome"); else { state.setupStep--; render(); } return; }
    if (state.onboarding && ["speed-dial", "sos-settings"].includes(route)) { navigate("setup"); return; }
    if (route === "reply") { navigate("conversation"); return; }
    if (route === "conversation") { navigate("messages"); return; }
    if (route === "tile-picker") { navigate(state.pickerReturn); return; }
    if (route === "display-picker") { navigate(state.onboarding ? "setup" : "display"); return; }
    if (classicMode && classicTarget()) {
      const input = classicTarget();
      if ((input.selectionEnd ?? input.value.length) > 0) { classicDelete(); return; }
    }
    if (dialerOptions[route] && state.phoneTabs[route] && state.phoneTabs[route] !== dialerOptions[route].initialTab) {
      state.phoneTabs[route] = dialerOptions[route].initialTab; state.search = ""; render(); return;
    }
    const safeParent = { calling: "home", "call-ended": "dialer", "call-failed": "dialer", conversation: "messages", compose: "messages", "message-failed": "compose", "contact-detail": "contacts", photo: "gallery", "sos-countdown": "sos", "sos-active": "sos", "sos-failed": "sos", "pin-recovery": "pin", purchase: "plans" };
    const previous = history.state?.claro && history.state.parent;
    if (["missed-call", "call-unanswered"].includes(route) && (!previous || ["calling", "incoming"].includes(previous))) { navigate("dialer", true); return; }
    if (previous && previous !== "sos-countdown") { cleanup(); history.back(); }
    else navigate(safeParent[route] || "home", true);
  }
  const screenActions = {
    home: [],
    dialer: [".dial-actions"],
    ...Object.fromEntries(Object.keys(dialerOptions).map(id => [id, ["dial", "emergency"]])),
    contacts: classicMode ? ["add-contact", "cancel-picker"] : ["cancel-picker"],
    "contact-detail": classicMode ? ["edit-contact", "delete-contact", "contact-call", "contact-message"] : ["contact-call", "contact-message"],
    calling: ["end-call"],
    incoming: ["answer", "decline"],
    "call-ended": ["go:home", "retry-call", "target-message"],
    "missed-call": ["retry-call", "target-message", "go:home"],
    "call-unanswered": ["retry-call", "target-message", "go:home"],
    "call-failed": ["retry-call", "native"],
    messages: ["go:compose"],
    conversation: ["reply", "go:messages"],
    compose: ["submit"],
    reply: ["submit"],
    "message-failed": ["retry-message", "go:messages"],
    camera: ["open-camera", "go:gallery"],
    gallery: ["finish-loading", "pick-photo", "go:camera"],
    photo: ["remove-photo", "go:gallery"],
    sos: ["go:sos-settings", "start-sos"],
    "sos-countdown": ["cancel-sos", "start-sos", "go:sos"],
    "sos-active": ["end-call"],
    "sos-failed": ["go:home", "start-sos"],
    apps: [],
    pin: ["submit", "go:pin-recovery"],
    settings: ["done-settings", "lock"],
    "home-edit": ["done-settings"],
    "speed-dial": ["done-settings"],
    "tile-picker": ["clear-slot", "cancel-picker"],
    "menu-order": ["done-settings"],
    "phone-style": ["apply-style", "go:settings"],
    "call-audio": ["done-settings"],
    "display-picker": ["close-display-picker"],
    "access-settings": ["submit", "go:settings"],
    display: ["save-display", "go:settings"],
    language: ["submit", "go:settings"],
    "sos-settings": ["submit", "go:settings"],
    permissions: ["go:setup"],
    privacy: ["submit", "go:settings"],
    reset: ["reset-review", "go:settings"],
    welcome: ["start-setup"],
    setup: [".consent-notice", "submit", "consent-continue", "setup-next", "finish-setup", "finish-onboarding-sos", "skip-onboarding-sos", "setup-picker-done"],
    plans: ["review-purchase", "go:home"],
    purchase: ["mock-store", "go:plans"],
    membership: ["go:plans", "go:home"],
    "trial-ended": ["go:home", "go:plans"],
    "pin-recovery": ["submit", "forgot-recovery", "go:pin"]
  };
  if (classicMode) screenActions["classic-menu"] = [];
  function mountScreenActions() {
    const device = phone.parentElement;
    const useClassicActions = classicMode && !state.onboarding && !["welcome", "setup"].includes(route);
    let keys = screenActions[route] || [];
    if (state.external) {
      keys = route === "camera" ? ["capture", "external-back"] :
        route === "purchase" && state.external === "Google Play" ? ["purchase-pending", "purchase-success", "purchase-cancel"] :
        state.confirm === "picker" ? ["add-picker-photo", "external-back"] : ["external-back"];
    } else if (route === "contact-detail" && state.contactMode) {
      keys = ["submit", "cancel-contact"];
    } else if (route === "conversation" && state.confirm === "delete-thread") {
      keys = ["confirm-delete-thread", "cancel-confirm"];
    } else if (classicMode && route === "contact-detail" && state.confirm === "delete-contact") {
      keys = ["cancel-confirm", "confirm-delete-contact", "go:sos-settings"];
    } else if (route === "photo" && state.confirm === "remove-photo") {
      keys = ["cancel-confirm", "confirm-remove-photo"];
    } else if (route === "sos-settings" && state.confirm === "sos-test") {
      keys = ["run-sos-test", "cancel-confirm"];
    } else if (route === "reset" && state.confirm === "reset") {
      keys = ["cancel-confirm", "confirm-reset"];
    } else if (route === "call-audio" && state.confirm === "maximum-volume") {
      keys = ["enable-maximum", "cancel-confirm"];
    } else if (route === "reset" && (state.activeCall || variant === "active call")) {
      keys = ["go:calling", "go:settings"];
    }
    let controls = keys.map(key => {
      const selector = key === "submit" ? 'button[type="submit"]' :
        key.startsWith(".") ? key :
        key.startsWith("go:") ? `button[data-action="go"][data-route="${key.slice(3)}"]` :
        `button[data-action="${key}"]`;
      return (useClassicActions ? phone : device).querySelector(selector);
    }).filter(Boolean);
    if (!controls.length && route !== "home") {
      controls = [...phone.querySelectorAll(".screen > button.primary, .screen > button.danger")];
    }
    if (useClassicActions) {
      mountClassicActions(controls);
      return;
    }
    for (const control of controls) {
      if (control.type !== "submit" || !control.form) continue;
      const form = control.form;
      form.id = `screen-form-${form.dataset.form}`;
      control.setAttribute("form", form.id);
    }
    actionDock.replaceChildren(...controls);
    actionDock.hidden = controls.length === 0;
    actionDock.dataset.screen = route;
    actionDock.dir = phone.dir;
    actionDock.lang = phone.lang;
  }
  function mountClassicActions(preferred) {
    phone.querySelectorAll(".dialpad").forEach(pad => pad.remove());
    const selectable = ".row-button, .speed-dial, .plan, .photo-grid button, .classic-home-links button, .tab-row button, .home-tile, .slot-button, .inline-action, .call-controls button:not(.call-option), .edit-app button, .picker-choice";
    const buttons = [...phone.querySelectorAll("button")].filter(control => !control.matches(selectable));
    const ordered = [...new Set([...preferred.flatMap(control => control.matches("button") ? [control] : [...control.querySelectorAll("button")]), ...buttons])];
    const confirming = ["delete-contact", "delete-thread", "remove-photo", "sos-test", "reset", "maximum-volume"].includes(state.confirm) || Boolean(state.contactMode);
    classicActions = ordered.filter(control => !control.matches(selectable) && (!confirming || preferred.includes(control)));
    for (const control of ordered) {
      if (control.matches(selectable)) continue;
      if (control.type === "submit" && control.form) {
        control.form.id = `screen-form-${control.form.dataset.form}`;
        control.setAttribute("form", control.form.id);
      }
      const permission = control.closest(".permission-card")?.querySelector("h3")?.textContent;
      const app = control.closest(".edit-app")?.querySelector("strong")?.textContent;
      if (permission || app) control.textContent = `${control.textContent} ${permission || app}`;
      control.classList.add("classic-context-key");
      if (control.textContent.trim().length > 20) control.classList.add("classic-long-key");
      control.remove();
    }
    classicActions = classicActions.filter(control =>
      !["end-call", "decline", "call-keypad"].includes(control.dataset.action) &&
      !(control.dataset.action === "go" && control.dataset.route === "home" && control.textContent.trim() === t("home")));
    if (state.external) {
      classicActions = classicActions.filter(control => [
        "external-back", "capture", "add-picker-photo", "purchase-pending",
        "purchase-success", "purchase-cancel", "cancel-membership",
        "restore-none", "restore-demo", "restore-failed"
      ].includes(control.dataset.action));
    }
    if (!state.external && route === "dialer") {
      const call = document.createElement("button");
      call.type = "button"; call.dataset.action = "classic-call"; call.className = "classic-context-key primary";
      call.innerHTML = `${icon("phone")}<span>Call</span>`;
      classicActions.unshift(call);
    }
    classicOptionsOpen = false;
    actionDock.replaceChildren();
    actionDock.hidden = true;
  }
  function render(preserve = false) {
    const focused = document.activeElement;
    const focusId = preserve && focused && phone.contains(focused) ? focused.id : "";
    const selection = focusId && "selectionStart" in focused ? [focused.selectionStart, focused.selectionEnd] : null;
    const scroll = phone.scrollTop;
    const meta = route === "setup" ? { ...registry[route], title: setupSteps[state.setupStep] } : registry[route];
    document.title = `Claro Home${classicMode ? " 2G" : ""} — ${route === "home" ? "Interactive storyboard" : route}`;
    $("section-name").textContent = meta.group;
    $("screen-title").textContent = meta.title; $("screen-subtitle").textContent = meta.subtitle;
    $("screen-count").textContent = `${String(meta.index + 1).padStart(2, "0")} / ${definitions.length}`;
    $("note-title").textContent = meta.title;
    $("note-description").textContent = meta.subtitle;
    $("try-instruction").textContent = meta.try;
    const onboardingReview = ["welcome", "setup"].includes(route);
    $("onboarding-flow").hidden = !onboardingReview;
    if (onboardingReview) {
      $("onboarding-flow-steps").innerHTML = ["Welcome", ...setupSteps, "Home"].map((label, index) =>
        button(`<span>${String(index + 1).padStart(2, "0")}</span>${esc(label)}`, "review-setup-step", "",
          `data-step="${index - 1}" ${index === setupSteps.length + 1 && !state.setupCompleted ? 'disabled title="Finish setup before going Home"' : ""} ${((route === "welcome" && index === 0) || (route === "setup" && index === state.setupStep + 1)) ? 'aria-current="step"' : ""}`)).join("");
    }
    $("journey-name").textContent = `${meta.group} · ${route} · ${variant}`;
    $("prototype-status").textContent = "Current app UX reference · sample data · changes reset on reload · no real calls, messages, purchases or telemetry.";
    const boundaries = ["camera", "apps", "permissions", "purchase"].includes(route) ? "Android / Play boundary: the external panel is an illustration, not a launched app or real system prompt." : "Every call, message, emergency action and membership result is simulated; no network or device action occurs.";
    const detailNote = ["pin", "pin-recovery", "setup", "access-settings"].includes(route)
      ? "Date access is an accidental-change barrier, not a security lock. PIN mode needs a six-digit PIN and separate 12+ character recovery secret. Three failures pause entry for 30 seconds. Unlock lasts until Lock settings or process restart; Done and Home do not lock."
      : ["home", "speed-dial", "tile-picker"].includes(route)
        ? "You can keep adding Home tiles. Defaults contain Phone, Camera, Gallery, SOS and Apps, without Messages. Contacts and apps may repeat; built-ins are unique. Native contact photos and launcher icons are represented here by original sample artwork."
        : "This storyboard follows the current native navigation and interaction decisions. Device permissions, carrier behavior, proximity/audio, notifications, app discovery, photo deletion and store transactions remain simulated.";
    $("screen-notes").innerHTML = [[meta.refs, "Native UX changes supersede historical PRD wording where noted in the README."], ["Honest boundaries", boundaries], ["Review note", detailNote], ...(classicMode ? [["Classic controls", "Up/Down visits every item in reading order, including across grid columns; OK selects. The amber focus ring appears after the first arrow press on each screen. Photo Up/Down browses images. Back deletes text before navigating, except Reply Back retains the draft. # changes letters/numbers; hold # opens normal keyboard entry. Calls and reading messages hide the number pad until needed. Historical alternate deck arrangements remain available for comparison."]] : []), ["Implementation context", "All sample data is in memory. Native Android uses actual contacts/photos, launcher icons, Telecom, CallLog, SMS and MediaStore. The browser never accesses them. Core navigation has English, Spanish and Hebrew/RTL previews; secondary prototype copy and multi-tap remain English, unlike the localized native app. Membership simulations are review-only; unconfigured native billing never invents plans or entitlements."], ...(reviewFeedback ? [["Latest review action", reviewFeedback]] : [])].map((n, i) => `<div class="note"><span class="note-number">${i + 1}</span><div><h3>${esc(n[0])}</h3><p>${esc(n[1])}</p></div></div>`).join("");
    $("state-controls").innerHTML = `<p>Preview states only. They do not reflect a real device.</p>${(variants[route] || ["standard"]).map(v => button(esc(v), "variant", "", `data-variant="${esc(v)}" aria-pressed="${v === variant}"`)).join("")}${button("Reset this screen preview", "variant", "", `data-variant="${esc(variants[route][0])}"`)}`;
    $("screen-nav").innerHTML = definitions.map((d, i) => {
      const group = i === 0 || d[1] !== definitions[i - 1][1] ? `<div class="nav-group">${d[1]}</div>` : "";
      const link = `<a class="screen-link ${route === d[0] ? "active" : ""}" href="#${d[0]}" ${route === d[0] ? 'aria-current="page"' : ""}><span class="nav-index">${String(i + 1).padStart(2, "0")}</span>${esc(d[0].split("-").map(s => s[0].toUpperCase() + s.slice(1)).join(" "))}</a>`;
      const options = d[0] === "dialer" && !classicMode ? `<div class="dialer-options" role="group" aria-label="Dialer design options"><span class="dialer-options-label">Options</span>${Object.entries(dialerOptions).map(([id, option]) => `<a class="screen-link dialer-option ${route === id ? "active" : ""}" href="#${id}" ${route === id ? 'aria-current="page"' : ""}>${esc(option.label)}</a>`).join("")}</div>` : "";
      return group + (options ? `<div class="dialer-nav-item">${link}${options}</div>` : link);
    }).join("");
    const isRTL = state.language === "he";
    const device = document.querySelector(".phone");
    const textSize = draft?.textSize || state.textSize;
    device.classList.toggle("large-text", textSize !== "Standard" || variant === "huge text");
    device.classList.toggle("huge-text", textSize === "Huge" || variant === "huge text");
    document.body.classList.toggle("onboarding-preview", state.onboarding || ["welcome", "setup"].includes(route));
    document.body.classList.toggle("first-launch-setup", state.onboarding && !state.setupReentry || route === "welcome" && !state.setupCompleted);
    device.classList.toggle("rtl-preview", isRTL);
    phone.dir = isRTL ? "rtl" : "ltr";
    phone.lang = state.language;
    $("phone-back").innerHTML = `${isRTL ? "→" : "←"} <span>${esc(t("back"))}</span>`;
    $("phone-home").innerHTML = `<span class="home-outline" aria-hidden="true"></span> ${esc(t("home"))}`;
    $("phone-back").setAttribute("aria-label", t("back")); $("phone-home").setAttribute("aria-label", t("home"));
    document.querySelector(".phone-system-nav").dir = isRTL ? "rtl" : "ltr";
    document.querySelector(".device-status").dir = "ltr";
    const network = state.network !== "available" ? state.network : ["no SIM", "no signal", "airplane mode", "signal unknown"].includes(variant) ? variant : "available";
    const status = document.querySelector(".device-status-right");
    status.textContent = `${network === "available" ? "▂▄▆█" : network} · 82% ▰`;
    status.setAttribute("aria-label", `${network}, battery 82 percent`);
    if (classicMode) document.querySelector(".device-status > span:first-child").textContent = route === "home" ? "Claro" : "9:41";
    const content = classicMode && route === "home" ? classicHome() : classicMode && (route === "dialer" || dialerOptions[route]) ? classicDialer() : screens[route]();
    actionDock.replaceChildren();
    phone.innerHTML = `<section class="screen ${route === "home" ? "home-screen" : ""}" data-screen="${route}" aria-label="${esc(route)}">${state.notice ? info(esc(state.notice), "success") : ""}${state.error ? info(esc(state.error), "error") : ""}${content}${state.external && !content.includes('class="external-panel"') ? noticeExternal() : ""}</section>`;
    if ((state.onboarding || route === "setup") && route !== "welcome") {
      const screen = phone.querySelector(".screen");
      const title = screen.querySelector("h2");
      if (title) {
        const header = title.parentElement.classList.contains("screen-title-row")
          ? title.parentElement : document.createElement("header");
        header.classList.add("onboarding-header");
        header.dir = "ltr";
        title.dir = isRTL ? "rtl" : "ltr";
        if (title.parentElement !== header) header.append(title);
        header.insertAdjacentHTML("afterbegin", button('<span aria-hidden="true">←</span>', "onboarding-back", "onboarding-back", `aria-label="${esc(t("back"))}"`));
        screen.prepend(header);
      }
      screen.querySelector('[data-action="close-display-picker"]')?.remove();
      screen.querySelector('[data-action="cancel-picker"]')?.remove();
    }
    phone.querySelectorAll("form[data-form]").forEach(form => {
      const saved = state.formDrafts[form.dataset.form];
      if (!saved) return;
      [...form.elements].forEach(el => {
        if (!el.name || !(el.name in saved)) return;
        if (el.type === "checkbox") el.checked = saved[el.name];
        else el.value = saved[el.name];
      });
    });
    mountScreenActions();
    const deleteDialog = phone.querySelector(".contact-delete-dialog");
    if (deleteDialog) {
      deleteDialog.addEventListener("cancel", event => {
        event.preventDefault();
        actions["cancel-confirm"]();
      });
      deleteDialog.showModal();
    }
    const callDock = $("call-controls-dock");
    callDock.replaceChildren();
    const callControls = phone.querySelector(".call-controls");
    if (callControls) callDock.append(callControls);
    callDock.hidden = !callControls;
    const returnBar = $("return-to-call");
    returnBar.hidden = !state.activeCall || ["calling", "incoming", "sos-active"].includes(route);
    returnBar.textContent = `☎ Return to call · ${(state.callTarget || state.target).name}`;
    classicDeck.hidden = !classicMode || state.onboarding || ["welcome", "setup"].includes(route);
    document.body.classList.toggle("classic-preview", classicMode);
    $("classic-layout-controls").hidden = !classicMode;
    if (classicMode && !classicDeck.hidden) {
      phone.querySelectorAll('input:not([type="checkbox"]):not([type="radio"]), textarea').forEach(input => input.setAttribute("inputmode", "none"));
      renderClassicDeck();
    }
    if (focusId) {
      const restored = $(focusId);
      if (restored) { restored.focus({ preventScroll: true }); if (selection && restored.setSelectionRange) { try { restored.setSelectionRange(...selection); } catch (_) { /* Numeric fields do not support selection. */ } } }
      phone.scrollTop = scroll;
    }
  }
  const heading = title => `<h2>${esc(title)}</h2>`;
  const membershipLabel = value => String(value).replace(/\s*demo\b/gi, "").replace(" · 7 days proposed", "").trim();
  const emergency = () => button("Emergency call", "emergency", "secondary emergency-action");
  const permissionWarning = feature => `${info(`${esc(feature)} access needed.`, "warning")}${button(`Open ${esc(feature)}`, "native", "primary")}${goButton("Settings", "permissions")}`;
  const noticeExternal = () => state.external ? `<div class="external-panel" role="region" aria-label="${esc(state.external)}">${heading(state.external)}${state.external.endsWith("· Android access")
    ? button("Choose Claro / Allow", "accept-permission", "primary inline-action") + button("Not now", "deny-permission", "secondary inline-action")
    : button("Back to Claro", "external-back", "primary")}</div>` : "";
  function recentCallRows(missedOnly = false) {
    return state.missedCalls.map(missed => {
      const contact = state.contacts.find(c => c.numbers.includes(missed.number));
      return row(missed.name, `${missed.unread ? "● " : ""}${missed.when} ↙`, "open-missed-call", `data-call-id="${missed.id}" aria-label="${esc(missed.name)}, missed incoming call, ${esc(missed.when)}"`, avatar(contact));
    }).join("") +
      (missedOnly ? "" : state.contacts.slice(1, 3).map((c, i) => row(c.name, `9:${21 + i} ${i ? "↙" : "↗"}`, "recent-call", `data-id="${c.id}" aria-label="${esc(c.name)}, ${i ? "incoming" : "outgoing"} call"`, avatar(c))).join(""));
  }
  function callOutcomeTarget() {
    if (route !== "missed-call") return state.target;
    if (variant === "unknown") return { name: "Unknown caller", number: "202-555-0199" };
    if (variant === "private") return { name: "Private number", number: "Withheld" };
    return selectedMissedCall();
  }
  function callOutcome(title, callLabel, missed = false) {
    if (missed && state.readiness.Phone.startsWith("Denied")) return heading(title) + permissionWarning("Phone");
    const target = callOutcomeTarget();
    const contact = state.contacts.find(c => c.numbers.includes(target.number));
    const canCall = validNumber(target.number);
    return heading(title) + avatar(contact, true) +
      `<h3 class="call-name">${esc(target.name)}</h3><p dir="ltr">${esc(canCall ? target.number : "Number withheld")}</p>` +
      (missed ? `<p class="helper">${esc(selectedMissedCall().when)}</p>` : "") +
      (canCall ? button(`${icon("phone")} ${callLabel}`, "retry-call", "primary") + button("Message", "target-message") : "") +
      goButton("Home", "home");
  }
  function classicHome() {
    return homeHeader() + homeTiles();
  }
  function classicDialer() {
    if (variant === "permission denied" || state.readiness.Phone.startsWith("Denied")) return heading(t("phone")) + permissionWarning("Phone");
    return titleAction(t("phone"), icon("contact"), "go", `data-route="contacts" aria-label="Contacts"`) +
      `<div class="dial-display" dir="ltr" aria-live="polite">${esc(state.digits || t("type"))}</div>` +
      dialerMatches() + (variant === "empty recents" ? info("No calls yet.") : recentCallRows());
  }
  function classicTarget() {
    if (classicOptionsOpen) return null;
    if (classicSelected?.isConnected && classicSelected.matches('select, input[type="checkbox"], input[type="radio"]')) return null;
    const editable = input => input && phone.contains(input) && !input.disabled && !input.readOnly && (input.matches('textarea, input[type="text"], input[type="tel"], input[type="password"], input[type="search"]'));
    if (editable(document.activeElement)) return document.activeElement;
    if (editable(classicSelected)) return classicSelected;
    if (editable(classicInput)) return classicInput;
    if (route === "compose" || route === "reply") return phone.querySelector('[name="body"]');
    return [...phone.querySelectorAll("input, textarea")].find(editable) || null;
  }
  function classicIsNumeric(input) {
    return input && (input.type === "tel" || ["date", "pin", "newPin", "confirmPin"].includes(input.name));
  }
  function classicFeedback(text) { $("classic-feedback").textContent = text; }
  function classicCandidates() {
    return [...phone.querySelectorAll("button, input, select, textarea"), ...$("call-controls-dock").querySelectorAll("button")].filter(el =>
      !el.disabled && el.type !== "hidden" && !el.closest("[hidden]") && el.getClientRects().length);
  }
  function classicCandidateKey(el) {
    return el.id || el.name || JSON.stringify([el.tagName, el.dataset, el.getAttribute("aria-label")]);
  }
  function selectClassicItem(el, reveal = false) {
    phone.querySelectorAll(".classic-selected").forEach(item => item.classList.remove("classic-selected"));
    classicSelected = el;
    classicSelectionKey = el ? classicCandidateKey(el) : "";
    if (!el) return;
    if (classicFocusVisible) el.classList.add("classic-selected");
    if (reveal) {
      el.focus({ preventScroll: true });
      const bounds = el.getBoundingClientRect(), display = phone.getBoundingClientRect();
      if (bounds.top < display.top) phone.scrollTop += bounds.top - display.top - 4;
      else if (bounds.bottom > display.bottom) phone.scrollTop += bounds.bottom - display.bottom + 4;
    }
  }
  function syncClassicSelection() {
    const scope = `${route}:${classicOptionsOpen ? "options" : "screen"}`;
    if (classicSelectionScope !== scope) { classicSelectionScope = scope; classicSelectionKey = ""; }
    const candidates = classicCandidates();
    selectClassicItem(candidates.find(el => classicCandidateKey(el) === classicSelectionKey) || null);
  }
  function classicNeighbor(direction) {
    const candidates = classicCandidates();
    if (!candidates.length) return null;
    if (!candidates.includes(classicSelected)) return candidates[0];
    const sign = direction === "up" ? -1 : 1;
    return candidates[candidates.indexOf(classicSelected) + sign] || null;
  }
  function updateClassicNavigation() {
    const selected = classicSelected?.isConnected ? classicSelected : null;
    for (const direction of ["up", "down"]) {
      let available;
      if (route === "photo" && !classicOptionsOpen && !state.confirm) {
        available = direction === "up" ? state.photoIndex > 0 : state.photoIndex < state.photos.length - 1;
      } else {
        available = Boolean(classicNeighbor(direction)) || (direction === "up" ? phone.scrollTop > 1 : phone.scrollHeight - phone.clientHeight - phone.scrollTop > 1);
      }
      $(`classic-nav-${direction}`).disabled = !available;
    }
    $("classic-ok").disabled = !selected;
  }
  function classicNavigate(el) {
    const direction = el.dataset.direction;
    const sign = direction === "up" ? -1 : 1;
    classicFocusVisible = true;
    multiTap = null;
    if (route === "photo" && !classicOptionsOpen && !state.confirm) {
      actions[sign < 0 ? "previous-photo" : "next-photo"]();
    } else {
      const neighbor = classicNeighbor(direction);
      if (neighbor) selectClassicItem(neighbor, true);
      else phone.scrollBy({ top: sign * Math.max(72, Math.floor(phone.clientHeight * 0.7)), behavior: "auto" });
    }
    updateClassicNavigation();
  }
  function classicOK() {
    const selected = classicSelected;
    if (!selected || selected.disabled) return;
    if (selected.matches("input:not([type=checkbox]):not([type=radio]), textarea, select")) selected.focus({ preventScroll: true });
    else selected.click();
  }
  function classicConfirmAction() {
    const eligible = classicActions.filter(control => !["classic-call", "contact-call", "place-call", "answer", "retry-call"].includes(control.dataset.action));
    return eligible.find(control => ["confirm-delete-contact", "confirm-delete-thread", "confirm-remove-photo", "confirm-reset", "enable-maximum", "cancel-sos", "start-sos", "reset-review"].includes(control.dataset.action)) ||
      eligible.find(control => control.type === "submit") ||
      eligible.find(control => control.classList.contains("primary")) || null;
  }
  function invokeClassicAction(control) {
    if (!control || control.disabled) return;
    if (control.type === "submit") {
      const form = $(control.getAttribute("form"));
      if (form) form.requestSubmit();
      else { state.error = "Form unavailable. Go back and try again."; render(); }
    } else {
      const action = actions[control.dataset.action];
      if (action) action(control);
      else { state.error = "Action unavailable. Go back and try again."; render(); }
    }
  }
  function closeClassicOptions() {
    classicOptionsOpen = false;
    classicOptionInput = null;
    $("classic-options")?.remove();
    phone.querySelector(".screen").hidden = false;
    classicSelectionScope = `${route}:screen`;
    classicSelectionKey = classicOptionReturnKey;
    renderClassicDeck();
    phone.scrollTop = classicOptionsScroll;
    updateClassicNavigation();
  }
  function openClassicOptions() {
    if (classicOptionsOpen) { closeClassicOptions(); return; }
    classicOptionInput = classicTarget();
    renderClassicSoftkeys();
    classicOptionsScroll = phone.scrollTop;
    classicOptionReturnKey = classicSelectionKey;
    classicOptionsOpen = true;
    phone.querySelector(".screen").hidden = true;
    phone.insertAdjacentHTML("beforeend", `<section id="classic-options" class="screen" aria-label="Options">${heading("Options")}${classicOptionActions.map((control, index) =>
      row(control.dataset.action === "delete-contact" ? "Delete contact" : control.getAttribute("aria-label") || control.textContent.trim(), "", "classic-option", `data-index="${index}" ${control.disabled ? "disabled" : ""}`)).join("")}</section>`);
    phone.scrollTop = 0;
    renderClassicDeck();
  }
  function renderClassicSoftkeys() {
    const input = classicOptionsOpen ? classicOptionInput : classicTarget();
    const confirmAction = classicConfirmAction(), confirm = $("classic-confirm");
    const call = $("classic-call"), end = $("classic-end");
    call.innerHTML = confirmAction && route !== "incoming"
      ? `<span>${esc(confirmAction.textContent.trim())}</span>`
      : `${icon("phone")}<span>${route === "incoming" ? "Answer" : route === "messages" ? "New" : route === "conversation" ? "Reply" : "Call"}</span>`;
    call.classList.toggle("blue-key", Boolean(confirmAction) && ["pin", "display", "home-edit", "speed-dial"].includes(route));
    call.disabled = Boolean(state.external) || classicOptionsOpen || Boolean(state.contactMode) || route === "sos-countdown" ||
      (!state.activeCall && ["missed-call", "call-unanswered"].includes(route) && !validNumber(callOutcomeTarget().number)) ||
      (!state.activeCall && route === "missed-call" && state.readiness.Phone.startsWith("Denied")) ||
      (state.activeCall && ["calling", "sos-active"].includes(route)) || (!confirmAction && Boolean(input) && !["dialer", "contacts"].includes(route));
    if (confirmAction) call.disabled = confirmAction.disabled || classicOptionsOpen;
    if (confirmAction?.classList.contains("danger")) call.disabled = true;
    end.innerHTML = `${icon("end")}<span>${esc(route === "incoming" ? "Decline" : state.activeCall ? "End" : t("home"))}</span>`;
    if (confirmAction?.classList.contains("danger")) end.textContent = confirmAction.textContent;
    $("classic-back").textContent = t("back");
    classicOptionActions = classicActions.filter(control => control !== confirmAction && !["classic-call", "contact-call", "place-call", "answer", "retry-call"].includes(control.dataset.action));
    const addContact = phone.querySelector('[data-action="add-contact"]');
    if (route === "contacts" && !state.picker && addContact) classicOptionActions.unshift(addContact);
    if (!state.external && !["delete-contact", "delete-thread", "remove-photo", "sos-test", "reset", "maximum-volume"].includes(state.confirm)) {
      for (const [label, destination] of [[t("contacts"), "contacts"], ["Menu", "classic-menu"]]) {
        if (route === destination || classicOptionActions.some(control => control.dataset.route === destination || (destination === "contacts" && control.dataset.action === "manage-contacts"))) continue;
        const control = document.createElement("button");
        control.type = "button"; control.dataset.action = "go"; control.dataset.route = destination; control.textContent = label;
        classicOptionActions.push(control);
      }
    }
    confirm.textContent = classicOptionsOpen ? "Select" : route === "home" ? "Menu" : "Options";
    confirm.disabled = classicOptionsOpen ? !classicSelected : route !== "home" && classicOptionActions.length === 0;
    confirm.classList.remove("primary", "danger");
    confirm.classList.toggle("classic-long-key", confirm.textContent.trim().length > 12);
    const screen = phone.querySelector(".screen");
    const shortcut = screen.querySelector(".classic-options-shortcut");
    shortcut?.remove();
    if (route === "settings" && !classicOptionsOpen) confirm.textContent = "Lock settings";
    if (route === "pin" && state.accessMode === "pin" && !classicOptionsOpen) confirm.textContent = "Forgot PIN?";
    if (state.contactMode) end.textContent = "Cancel";
    const cancel = classicActions.find(control => ["cancel-picker", "close-display-picker"].includes(control.dataset.action) || control.dataset.discard);
    if (cancel || state.confirm) $("classic-back").textContent = "Cancel";
  }
  function renderClassicDeck() {
    classicDeck.hidden = false;
    classicDeck.querySelector(".classic-number-keys").hidden = ["messages", "conversation"].includes(route) || (["calling", "sos-active"].includes(route) && state.confirm !== "call-keypad") || route === "incoming";
    syncClassicSelection();
    const blocked = Boolean(state.external) || classicOptionsOpen || route === "incoming" || route === "sos-countdown";
    const selectionOnly = classicSelected?.matches('select, input[type="checkbox"], input[type="radio"]');
    classicDeck.querySelectorAll('[data-action="classic-digit"]').forEach(key => { key.disabled = blocked || Boolean(selectionOnly); });
    const input = classicTarget();
    $("call-controls-dock").hidden = classicOptionsOpen || !$("call-controls-dock").querySelector(".call-controls");
    renderClassicSoftkeys();
    syncClassicSelection();
    const mode = $("classic-input-mode");
    mode.hidden = !input || classicIsNumeric(input);
    mode.textContent = mode.hidden ? "" : classicModes[classicModeIndex];
    classicDeck.querySelectorAll('[data-action="classic-digit"]').forEach(key => {
      const { digit } = key.dataset;
      const displayMode = input ? (classicIsNumeric(input) ? 2 : classicModeIndex) : 0;
      const englishLegend = /^[2-9]$/.test(digit) ? classicEnglish[digit].slice(0, -1).toUpperCase() :
        input ? ({ "*": ".,?!", "1": ".,?!", "0": "space" })[digit] || "" : "";
      const legend = digit === "#" && input ? (classicIsNumeric(input) ? "Hold: keyboard" : "Hold: language") :
        digit === "*" && classicCanEnterPlus() ? "Hold: +" :
        displayMode === 2 ? "" : (displayMode === 1 ? classicHebrew[digit] : null) || englishLegend;
      key.querySelector("small").textContent = legend;
      key.setAttribute("aria-label", [digit, legend].filter(Boolean).join(", "));
    });
    if (!state.activeCall) state.classicTones = "";
    $("classic-feedback").textContent = state.classicTones ? state.classicTones.slice(-12) : "";
    classicScrollObserver.disconnect();
    classicScrollObserver.observe(phone);
    if (phone.firstElementChild) classicScrollObserver.observe(phone.firstElementChild);
    requestAnimationFrame(updateClassicNavigation);
  }
  function classicEdit(input, text, replace = false) {
    const start = replace ? multiTap.start : input.selectionStart ?? input.value.length;
    const end = replace ? multiTap.end : input.selectionEnd ?? input.value.length;
    if (input.maxLength >= 0 && input.value.length - (end - start) + text.length > input.maxLength) { classicFeedback("Maximum length reached."); return false; }
    input.focus({ preventScroll: true });
    input.setRangeText(text, start, end, "end");
    if (input.name === "date") {
      const beforeCursor = input.value.slice(0, input.selectionStart).replace(/\D/g, "").length;
      const digits = input.value.replace(/\D/g, "").slice(0, 8);
      input.value = digits.slice(0, 2) + (digits.length >= 2 ? "/" : "") + digits.slice(2, 4) + (digits.length >= 4 ? "/" : "") + digits.slice(4);
      let cursor = 0, count = 0;
      while (cursor < input.value.length && count < beforeCursor) {
        if (/\d/.test(input.value[cursor])) count++;
        cursor++;
      }
      if (input.value[cursor] === "/") cursor++;
      input.setSelectionRange(cursor, cursor);
    }
    input.dispatchEvent(new Event("input", { bubbles: true }));
    classicInput = classicTarget();
    if (classicInput) {
      const fieldBounds = classicInput.getBoundingClientRect(), screenBounds = phone.getBoundingClientRect();
      if (fieldBounds.top < screenBounds.top || fieldBounds.bottom > screenBounds.bottom) phone.scrollTop += fieldBounds.top - screenBounds.top - 4;
    }
    updateClassicNavigation();
    return true;
  }
  function classicDigit(el) {
    if (suppressClick?.element === el) { suppressClick = null; return; }
    const digit = el.dataset.digit;
    if (state.activeCall && ["calling", "sos-active"].includes(route)) { state.classicTones = (state.classicTones + digit).slice(-24); renderClassicDeck(); return; }
    const input = classicTarget();
    if (input) {
      if (digit === "#" && !classicIsNumeric(input)) { classicModeIndex = (classicModeIndex + 1) % classicModes.length; multiTap = null; renderClassicDeck(); return; }
      if (["date", "pin", "newPin", "confirmPin"].includes(input.name) && !/^\d$/.test(digit)) { classicFeedback("Use number keys."); return; }
      const hebrew = classicModeIndex === 1 ? classicHebrew[digit] : null;
      const letters = classicIsNumeric(input) || classicModeIndex === 2 ? digit : hebrew ? hebrew + digit : classicEnglish[digit];
      const replace = letters.length > 1 && multiTap?.route === route && multiTap.id === input.id && multiTap.digit === digit && Date.now() - multiTap.time < 1000 && input.selectionStart === multiTap.end && input.selectionEnd === multiTap.end && input.value.slice(multiTap.start, multiTap.end) === multiTap.text;
      const index = replace ? (multiTap.index + 1) % letters.length : 0;
      const start = replace ? multiTap.start : input.selectionStart ?? input.value.length;
      const text = letters[index];
      if (classicEdit(input, text, replace)) multiTap = { route, id: input.id, digit, index, start, end: start + text.length, text, time: Date.now() };
      return;
    }
    classicDialDigit(digit);
  }
  function classicCanEnterPlus() {
    return !classicTarget() && !classicOptionsOpen && !state.activeCall && !state.external &&
      (route === "home" || (route === "dialer" && !state.digits));
  }
  function classicDialDigit(digit) {
    if (state.activeCall) { classicFeedback("Return to the call for tones."); return; }
    multiTap = null;
    if (route !== "dialer") { state.digits = digit; state.preparedNumber = true; state.dialIdentity = null; navigate("dialer"); }
    else { state.digits = (state.digits + digit).slice(0, 24); state.dialIdentity = null; state.error = ""; render(); }
  }
  function classicDelete() {
    if (state.activeCall && ["calling", "sos-active"].includes(route)) { classicFeedback("Tones cannot be deleted."); return; }
    multiTap = null;
    const input = classicTarget();
    if (!input) { state.digits = state.digits.slice(0, -1); render(); return; }
    let start = input.selectionStart ?? input.value.length;
    const end = input.selectionEnd ?? start;
    if (start === end && start > 0) { start--; if (input.name === "date" && input.value[start] === "/") start = Math.max(0, start - 1); }
    input.setSelectionRange(start, end);
    classicEdit(input, "");
  }
  function classicCall() {
    if (classicConfirmAction()) { invokeClassicAction(classicConfirmAction()); return; }
    if (route === "messages") { navigate("compose"); return; }
    if (route === "conversation") { actions.reply(); return; }
    if (route === "incoming") { actions.answer(); return; }
    if (state.activeCall) { navigate(route === "sos-active" ? "sos-active" : "calling"); return; }
    if (route === "contact-detail" && !state.contactMode) { actions["contact-call"](); return; }
    if (["call-ended", "call-failed", "missed-call", "call-unanswered"].includes(route)) { actions["retry-call"](); return; }
    if (route === "dialer" && state.digits) { actions.dial(); return; }
    if (classicSelected && ["contact", "phone-contact", "recent-call", "open-missed-call", "favorite"].includes(classicSelected.dataset.action)) {
      if (classicSelected.dataset.action === "contact") actions["phone-contact"](classicSelected);
      else actions[classicSelected.dataset.action](classicSelected);
      return;
    }
    navigate("dialer");
  }
  function classicEnd() {
    const destructive = classicConfirmAction();
    if (destructive?.classList.contains("danger")) { invokeClassicAction(destructive); return; }
    if (state.contactMode) { actions["cancel-contact"](); return; }
    if (route === "incoming") { actions.decline(); return; }
    if (state.activeCall) { actions["end-call"](); return; }
    state.digits = ""; classicInput = null; multiTap = null; navigate("home");
  }
  function dialerPreview() {
    const option = dialerOptions[route];
    const tab = state.phoneTabs[route] || option.initialTab;
    if (variant === "permission denied" || state.readiness.Phone.startsWith("Denied")) return heading(t("phone")) + permissionWarning("Phone") + emergency();
    const status = state.network !== "available" ? info("No mobile service.", "warning") : "";
    if (tab === "choices") return heading(t("phone")) + status +
      `<div class="phone-choices">${button(`${icon("contact")}<span>${esc(t("contacts"))}</span>`, "dial-tab", "secondary phone-choice", 'data-tab="contacts"')}${button(`${icon("phone")}<span>Dial a number</span>`, "dial-tab", "secondary phone-choice", 'data-tab="keypad"')}</div>` + emergency();
    const prominentContacts = route === "dialer-keypad-first" && tab === "keypad";
    const views = prominentContacts ? ["keypad", "recent"] : ["contacts", "keypad", "recent"];
    const navigation = `${prominentContacts ? button(`${icon("contact")} ${esc(t("contacts"))}`, "dial-tab", "primary contacts-shortcut", 'data-tab="contacts"') : ""}
      <div class="tab-row phone-view-tabs" role="group" aria-label="Phone views">${views.map(view => button(esc(t(view)), "dial-tab", view === tab ? "selected" : "", `data-tab="${view}" aria-pressed="${view === tab}"`)).join("")}</div>`;
    const header = heading(t("phone")) + navigation + status;
    if (tab === "contacts") {
      if (state.readiness.Contacts.startsWith("Denied")) return header + permissionWarning("Contacts") + emergency();
      const contacts = variant === "empty contacts" ? [] : state.contacts.filter(c => `${c.name} ${c.numbers.join(" ")}`.toLowerCase().includes(state.search.toLowerCase()));
      return header + field(t("search"), "contactSearch", state.search, "search", 'autocomplete="off" data-input="contact-search"') +
        contacts.map(c => row(c.name, "", "phone-contact", `data-id="${c.id}"`, avatar(c))).join("") +
        (!contacts.length ? info(state.search ? "No matches." : "No contacts yet.") : "") + emergency();
    }
    if (tab === "recent") return header +
      (variant === "empty recents" ? info("No calls yet.") : recentCallRows()) + emergency();
    return header +
      `<div class="dial-display-row"><div class="dial-display" dir="ltr" aria-live="polite">${esc(state.digits || t("type"))}</div>${button(icon("backspace"), "backspace", "secondary", `aria-label="Delete last digit" ${state.digits ? "" : "disabled"}`)}</div>
      <div class="dialpad" dir="ltr">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map((n, i) => {
        const label = ["", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""][i];
        return button(`${n}<small>${label}</small>`, "digit", "dial-key", `data-digit="${n}" aria-label="${n}"`);
      }).join("")}</div>` + button(`${icon("phone")} ${esc(t("call"))}`, "dial", "primary", state.digits ? "" : "disabled") + emergency();
  }
  const titleAction = (title, label, action, attrs = "") => `<div class="screen-title-row">${heading(title)}${button(label, action, "secondary inline-action title-action", attrs)}</div>`;
  const shortcutLabel = value => value === "alarm" ? "Alarm clock" : t(value);
  const slotContact = value => typeof value === "string" && value.startsWith("contact:") ? resolve(value.slice(8)) : null;
  const slotLabel = value => !value ? "Empty" : value.startsWith("contact:") ? slotContact(value)?.name || "Contact unavailable" : value.startsWith("app:") ? value.slice(4) : shortcutLabel(value);
  const appArtwork = name => `<span class="sample-app-icon" aria-hidden="true">${icon(({ Weather: "sun", Calendar: "clock", Music: "speaker", Maps: "arrow-up", WhatsApp: "messages", Facebook: "contact", Calculator: "apps" })[name] || "apps")}</span>`;
  function slotArtwork(value) {
    const contact = slotContact(value);
    if (contact) return avatar(contact);
    if (value?.startsWith("app:")) return appArtwork(value.slice(4));
    return icon(value === "alarm" ? "clock" : value || "apps");
  }
  function homeHeader() {
    const network = state.network;
    const date = new Date().toLocaleDateString(({ en: "en-GB", es: "es", he: "he" })[state.language], { weekday: "short", day: "numeric", month: "short" });
    if (classicMode) return `<header class="home-dashboard-header classic-dashboard-header"><time dir="ltr">9:41</time><p>${esc(date)}</p></header>`;
    return `<header class="home-dashboard-header"><div class="status-instruments"><strong>Claro</strong><div class="cell-signal ${network !== "available" ? "unavailable" : ""}" role="img" aria-label="${esc(t("signal"))}: ${esc(network)}">${Array.from({ length: 5 }, () => "<i></i>").join("")}</div><b aria-label="${esc(t("battery"))}: 82%">82%</b><div class="battery-meter" aria-hidden="true"><span class="battery-fill" style="width:82%"></span></div></div>${network !== "available" ? `<p>${esc(network)}</p>` : ""}<div class="home-date-time"><strong class="header-day">${esc(date)}</strong><time class="header-time" dir="ltr">9:41</time>${button(icon("settings"), "go", "gear-button inline-action", `data-route="settings" aria-label="${esc(t("settings"))}"`)}</div></header>`;
  }
  function homeTiles() {
    const counts = notificationCounts();
    return `<div class="home-slot-scroll"><div class="tile-grid home-slots">${state.slots.map((value, index) => {
      if (!value) return "";
      const count = value === "phone" ? counts.missed : value === "messages" ? counts.messages : 0;
      const badge = count === null || count > 0;
      const artwork = value === "sos" && !classicMode ? '<strong class="home-sos-label">SOS</strong>' : `${slotArtwork(value)}<span>${esc(slotLabel(value))}</span>`;
      return button(`${artwork}${badge ? `<span class="tile-badge">${count ?? "?"}</span>` : ""}`, "home-slot", `home-tile ${value}-tile`, `data-slot="${index}" aria-label="${esc(slotLabel(value))}${badge ? `, ${count ?? "unknown"} ${value === "phone" ? "missed calls" : "unread messages"}` : ""}"`);
    }).join("")}</div>${state.slots.every(value => !value) ? info("No Home tiles. Open Menu to use your phone.") : ""}${!classicMode ? goButton("Menu", "classic-menu", "secondary inline-action") : ""}</div>`;
  }
  function dialerMatches() {
    const digits = state.digits.replace(/\D/g, "");
    if (!digits || state.readiness.Contacts.startsWith("Denied")) return "";
    return state.contacts.flatMap(contact => contact.numbers.map(number => ({ contact, number })))
      .filter(({ number }) => number.replace(/\D/g, "").includes(digits)).slice(0, 5)
      .map(({ contact, number }) => row(contact.name, number, "number-match", `data-id="${contact.id}" data-number="${esc(number)}"`, avatar(contact))).join("");
  }
  function touchDialerResults() {
    if (state.dialIdentity) return row(state.dialIdentity.name, state.digits, "number-match", `data-id="${state.dialIdentity.id}" data-number="${esc(state.digits)}"`, avatar(state.dialIdentity));
    return /^\d+$/.test(state.digits) ? dialerMatches() : "";
  }
  function updateDialerNumber() {
    const display = phone.querySelector(".touch-dial-display");
    if (!display || phone.querySelector(".screen > .info-card.error") || state.external) {
      render();
      return;
    }
    display.textContent = state.digits || t("type");
    phone.querySelector(".touch-dial-results").innerHTML = touchDialerResults();
  }
  function homeVisibility() {
    return `<p>Choose shortcuts shown on Home. Hiding a tile does not disable the feature.</p>${["messages", "camera", "gallery", "apps", "app:WhatsApp", "app:Facebook"].map(value =>
      `<label class="check-label"><input type="checkbox" data-change="home-visibility" value="${value}" ${state.slots.includes(value) ? "checked" : ""}>Show ${esc(slotLabel(value))} on Home</label>`).join("")}`;
  }
  function peopleEditor() {
    const occupied = occupiedHomeSlots();
    const tiles = occupied.map((index, position) => {
      const value = state.slots[index];
      const label = slotLabel(value);
      return `<div class="edit-app">${row(label, "Edit or remove", "pick-slot", `data-target="home:${index}"`, slotArtwork(value))}<div class="button-row">${button("▲", "move-home-tile", "secondary inline-action", `data-index="${index}" data-delta="-1" aria-label="Move ${esc(label)} up" ${position === 0 ? "disabled" : ""}`)}${button("▼", "move-home-tile", "secondary inline-action", `data-index="${index}" data-delta="1" aria-label="Move ${esc(label)} down" ${position === occupied.length - 1 ? "disabled" : ""}`)}</div></div>`;
    }).join("");
    return heading("Home tiles") +
      row("Add tile", "Choose a contact, app, or shortcut", "pick-slot", `data-target="home:${state.slots.length}"`, icon("apps")) +
      "<h3>On your Home screen</h3>" + (tiles || info("No Home tiles yet. Tap Add tile to get started.")) +
      button("Done", "done-settings", "primary");
  }
  const occupiedHomeSlots = () => state.slots.flatMap((tile, index) => tile ? [index] : []);
  function tilePicker() {
    const home = state.pickerTarget.startsWith("home:");
    const index = Number(state.pickerTarget.split(":")[1]);
    const assigned = home ? state.slots[index] : state.keys[index];
    const query = state.search.toLowerCase();
    const contactRows = state.contacts.flatMap(contact => contact.numbers.map((number, index) =>
      `${contact.name} ${number}`.toLowerCase().includes(query) ? row(contact.name, number, "assign-slot", `data-value="contact:${contact.id}:${index}:${esc(number)}"`, avatar(contact)) : "")).join("");
    const appRows = home ? installedApps.filter(name => name.toLowerCase().includes(query)).map(name => row(name, "", "assign-slot", `data-value="app:${name}"`, appArtwork(name))).join("") : "";
    const shortcutRows = home ? shortcuts.filter(value => (value === assigned || !state.slots.includes(value)) && shortcutLabel(value).toLowerCase().includes(query)).map(value => row(shortcutLabel(value), "", "assign-slot", `data-value="${value}"`, icon(value === "alarm" ? "clock" : value))).join("") : "";
    return heading(home ? assigned ? "Replace tile" : "Add tile" : `Choose for Hold ${index + 1}`) +
      field("Search", "pickerSearch", state.search, "search", 'data-input="app-search"') +
      (contactRows + appRows + shortcutRows || info("No matches.")) + (assigned ? button(home ? "Remove from Home" : "Clear assignment", "clear-slot") : "") + button("Cancel", "cancel-picker");
  }
  function displayFields() {
    const values = draft || state;
    return row("Language", languages[values.language], "open-display-picker", 'data-picker="language"', icon("sun")) +
      row("Text size", values.textSize, "open-display-picker", 'data-picker="textSize"', icon("settings")) +
      `<label class="setting-toggle">Keypad & touch vibration<input type="checkbox" data-change="haptics" ${values.haptics ? "checked" : ""}></label>`;
  }
  function accessFields() {
    const accessMode = accessModeDraft || state.accessMode;
    return `<label><span class="field-label">Unlock with</span><select name="accessMode" data-change="access-mode"><option value="date" ${accessMode === "date" ? "selected" : ""}>Date question</option><option value="pin" ${accessMode === "pin" ? "selected" : ""}>Caregiver PIN</option></select></label>${accessMode === "date"
      ? info("Anyone who knows today's local date can unlock. This prevents accidental taps; it is not a security lock.")
      : `${field("New 6-digit PIN", "newPin", "", "password", 'inputmode="numeric" pattern="[0-9]{6}" minlength="6" required maxlength="6"')}${field("Confirm PIN", "confirmPin", "", "password", 'inputmode="numeric" required maxlength="6"')}${field("Recovery secret (12+ characters)", "recovery", "", "password", 'required minlength="12" maxlength="80" autocomplete="off"')}${info("Keep the PIN and recovery secret safe. Today's date cannot recover PIN mode.", "warning")}`}`;
  }
  const setupSteps = ["Help keep Claro reliable", "Choose phone style", "Language and display", "Permissions and roles", "Choose Home tiles", "Settings access", "Your VIP contacts", "SOS contact"];
  const defaultRolesReady = () => ["Home", "Phone", "Messages"].every(key => state.readiness[key].startsWith("Granted") || state.readiness[key] === "Unavailable");
  function onboardingContactChoices() {
    return state.contacts.flatMap(contact => contact.numbers.map((number, index) => ({
      contact, number, value: `${contact.id}:${index}:${number}`
    })));
  }
  function onboardingSuggestedContacts() {
    const recent = state.missedCalls.map(call => call.number);
    return [...state.contacts].sort((a, b) => {
      const rank = contact => {
        const index = recent.findIndex(number => contact.numbers.includes(number));
        return index < 0 ? Infinity : index;
      };
      return rank(a) - rank(b);
    }).slice(0, 3).map(contact => {
      const index = Math.max(0, contact.numbers.findIndex(number => recent.includes(number)));
      return { contact, number: contact.numbers[index], value: `${contact.id}:${index}:${contact.numbers[index]}` };
    });
  }
  function onboardingContactRow(choice, sos = false) {
    const selected = sos ? (state.sosDraft || state.sos) === choice.value : state.slots.includes(`contact:${choice.value}`);
    return row(`${choice.contact.name}${selected ? " ✓" : ""}`, choice.number,
      sos ? "select-onboarding-sos" : "toggle-onboarding-vip",
      `data-value="${esc(choice.value)}" aria-pressed="${selected}"`, avatar(choice.contact));
  }
  function onboardingPeopleChoices(sos = false) {
    if (!state.readiness.Contacts.startsWith("Granted")) {
      return info("Allow Contacts access to see your people. You can also add contacts later.") +
        button("Allow Contacts", "permission-request", "secondary inline-action", 'data-permission="Contacts"');
    }
    return `<p class="screen-kicker">Suggested contacts · recent calls first</p>` +
      onboardingSuggestedContacts().map(choice => onboardingContactRow(choice, sos)).join("");
  }
  function onboardingPickerScreen() {
    const sos = state.onboardingPicker === "sos";
    const query = state.onboardingQuery.trim().toLowerCase();
    const choices = onboardingContactChoices().filter(choice =>
      `${choice.contact.name} ${choice.number}`.toLowerCase().includes(query));
    return heading(sos ? "Choose SOS contact" : "Select VIP contacts") +
      (sos ? "<p>Choose one person.</p>" : info("Choose as many as you like. Changes save directly to Home.")) +
      field("Search contacts", "onboardingQuery", state.onboardingQuery, "search", 'data-input="onboarding-contact-search"') +
      (!state.readiness.Contacts.startsWith("Granted") ? onboardingPeopleChoices(sos) :
        choices.map(choice => onboardingContactRow(choice, sos)).join("") || info("No contacts match your search.")) +
      button("Done", "setup-picker-done", "primary");
  }
  function setupScreen() {
    if (state.onboardingPicker) return onboardingPickerScreen();
    const step = state.setupStep;
    let content = "";
    if (step === 0) content = `<div class="consent-symbol">${icon("lock")}</div><p>Share basic usage and crash reports to help us fix problems and improve Claro.</p>` +
      info("Your contacts, messages and photos stay private. Reports are never used for advertising.") +
      `<p class="consent-notice">By continuing, you agree to sharing. Turn it off anytime in Settings → Membership &amp; privacy.</p>` +
      button("Continue", "consent-continue", "primary");
    if (step === 1) content = styleChoices() + button("Continue", "setup-next", "primary");
    if (step === 2) content = displayFields() + button("Continue", "setup-next", "primary");
    if (step === 3) content = info("Set Claro as your default phone and allow Contacts to choose your VIPs and SOS person next.") +
      button("Set Claro as default", "request-defaults", "secondary inline-action") + permissionRows() +
      button("Continue", "setup-next", "primary", defaultRolesReady() ? "" : "disabled");
    if (step === 4) content = homeVisibility() + button("Continue", "setup-next", "primary");
    if (step === 5) content = `<form data-form="setup-access" class="stack">${accessFields()}<button type="submit" class="primary">Continue</button></form>`;
    if (step === 6) content = `<p>Who do you call most? Add your favourite people to Home for a direct call with one tap.</p>` +
      onboardingPeopleChoices() + button("Select contacts", "open-onboarding-vips", "secondary inline-action") +
      info(`On Home: ${state.slots.filter(value => slotContact(value)).length}. Changes save automatically.`) +
      button("Continue", "setup-next", "primary");
    if (step === 7) {
      const chosen = resolve(state.sosDraft || state.sos);
      content = `<p>Choose one person to call when you need help.</p>` + onboardingPeopleChoices(true) +
        button("Choose from contacts", "open-onboarding-sos", "secondary inline-action") +
        (chosen ? info(`${esc(chosen.name)} · ${esc(chosen.number)}`) +
          `<label class="check-label"><input type="checkbox" data-change="onboarding-sos-consent" ${state.sosConsent ? "checked" : ""}>They agreed to receive help calls.</label>` : "") +
        goButton("Enter a number instead", "sos-settings", "secondary inline-action") +
        info("Not an emergency service. Needs mobile service and an answer. No message or location sent.", "warning") +
        button("Finish setup", "finish-onboarding-sos", "primary", chosen && state.sosConsent ? "" : "disabled") +
        (state.sos ? "" : button("Skip SOS for now", "skip-onboarding-sos", "secondary inline-action"));
    }
    return heading(setupSteps[step]) + `<p class="screen-kicker">Setup step ${step + 1} of ${setupSteps.length}</p><div class="progress">${setupSteps.map((_, index) => `<span class="${index <= step ? "filled" : ""}"></span>`).join("")}</div>` +
      content;
  }
  function styleChoices() {
    return `<div class="button-row">${["classic", "touch"].map(style => button(`${style === "classic" ? "Classic Phone" : "Touch Home"}${state.selectedStyle === style ? " ✓" : ""}`, "select-style", "secondary picker-choice", `data-style="${style}" aria-pressed="${state.selectedStyle === style}"`)).join("")}</div>`;
  }
  function permissionRows() {
    return Object.entries(state.readiness).map(([name, value]) => `<div class="permission-card"><h3>${({ Home: "Default home", Phone: "Default phone", Messages: "Default SMS", Contacts: "Contacts", Notifications: "Notifications", Media: "Photos" })[name]}</h3><p>${esc(value)}</p>${button("Allow or repair", "permission-request", "secondary inline-action", `data-permission="${name}"`)}</div>`).join("");
  }
  function composeScreen(replying = false) {
    const contact = recipientContact();
    const recipientHeader = contact ? `<div class="message-recipient" role="group" aria-label="Recipient">${avatar(contact)}<div><strong>${esc(contact.name)}</strong><bdi dir="ltr">${esc(state.recipient)}</bdi></div></div>` : "";
    return heading(replying ? `Reply to ${state.target.name}` : "Write a message") + recipientHeader + `<form data-form="compose" class="stack">${replying ? (contact ? "" : `<p dir="ltr">${esc(state.recipient)}</p>`) : field("To", "recipient", state.recipient, "tel", 'data-input="recipient" dir="ltr" required maxlength="24"') + button(`${icon("contact")} Contacts`, "choose-recipient", "secondary inline-action")}
      <label><span class="field-label">Message</span><textarea id="field-body" name="body" data-input="body" required maxlength="2000" placeholder="Write here…">${esc(state.body)}</textarea></label>${button(icon("backspace"), "delete-body", "secondary inline-action", 'aria-label="Delete last character"')}
      ${variant === "offline" ? info("No internet. SMS needs mobile service.", "warning") : ""}<button class="primary" type="submit">Send</button></form>`;
  }
  function inboxRows() {
    const threads = new Map(state.contacts.filter(contact => ["mira", "daniel", "leah"].includes(contact.id)).map((contact, index) => [
      contact.numbers[0], { number: contact.numbers[0], name: contact.name, contact, body: ["I’ll come by after lunch.", "See you on Sunday.", "Thank you!"][index], time: sampleMessageTime(), sent: 0 }
    ]));
    state.messages.forEach((message, index) => {
      const contact = state.contacts.find(contact => contact.numbers.includes(message.number));
      threads.set(message.number, { number: message.number, name: contact?.name || message.number, contact, body: message.body, time: message.time, sent: index + 1 });
    });
    return [...threads.values()].filter(thread => (thread.sent || !state.deletedThreads.includes(thread.number)) && `${thread.name} ${thread.number}`.toLowerCase().includes(state.search.toLowerCase()))
      .sort((a, b) => b.sent - a.sent)
      .map(thread => row(thread.name, `${thread.contact?.id === "mira" && state.unread ? "● " : ""}${thread.body}\n${thread.time}${thread.sent ? " · Sent · delivery not confirmed" : ""}`, "open-conversation", `data-number="${esc(thread.number)}"`, avatar(thread.contact))).join("") || info("No matching messages.");
  }
  function sosSettingsScreen() {
    const selection = state.sosDraft || state.sos;
    const recipient = resolve(selection);
    return heading("SOS contact") + `<form data-form="sos-settings" class="stack">${button("Choose contact", "choose-sos", "secondary inline-action")}
      <label><span class="field-label">Person to call</span><select name="sos">${contactOptions(variant === "unconfigured" ? "" : selection)}${selection === "manual-sos" ? '<option value="manual-sos" selected>Entered phone number</option>' : ""}</select></label>
      ${field("Or enter a phone number", "manualNumber", selection === "manual-sos" ? recipient?.number : "", "tel", 'dir="ltr" maxlength="24"')}
      ${field("Name (optional)", "manualName", selection === "manual-sos" ? recipient?.name : "", "text", 'maxlength="60"')}
      <label class="check-label"><input type="checkbox" name="consent" ${state.sosConsent && !state.sosDraft && variant !== "not agreed" ? "checked" : ""}>They agreed to receive help calls.</label>
      ${info("Not an emergency service. Needs mobile service and an answer. No message or location sent.", "warning")}
      <button class="primary" type="submit">Save</button>${button("Test call", "sos-test")}${button("Done", "done-settings")}</form>` +
      (state.confirm === "sos-test" ? info("Tell your contact before testing.") + button("Start test", "run-sos-test", "primary") + button("Cancel", "cancel-confirm") : "");
  }
  const screens = {
    "classic-menu": () => heading("Menu") + state.menuOrder.map(value => {
      if (value.startsWith("app:")) return state.allowed.includes(value.slice(4)) && state.menuApps.includes(value.slice(4)) ? row(value.slice(4), "", "app", `data-app="${esc(value.slice(4))}"`, appArtwork(value.slice(4))) : "";
      const count = value === "phone" ? notificationCounts().missed : value === "messages" ? notificationCounts().messages : 0;
      return row(value === "contacts" ? t("contacts") : shortcutLabel(value), count === null ? "?" : count > 0 ? String(count) : "", "open-feature", `data-feature="${value}"`, icon(value === "contacts" ? "contact" : value));
    }).join(""),
    home: () => homeHeader() + homeTiles(),
    dialer: () => {
      const header = classicMode
        ? titleAction(t("phone"), icon("contact"), "go", `data-route="contacts" aria-label="${esc(t("contacts"))}"`)
        : heading(t("phone")) + goButton(`${icon("contact")} ${esc(t("contacts"))}`, "contacts", "secondary contacts-shortcut");
      if (variant === "permission denied" || state.readiness.Phone.startsWith("Denied")) return header + permissionWarning("Phone");
      const tab = state.dialTab;
      const tabs = `<div class="tab-row" role="group" aria-label="Phone views">${["keypad", "recent", "missed"].map(v => button(esc(t(v)), "dial-tab", v === tab ? "selected" : "", `data-tab="${v}" aria-pressed="${v === tab}"`)).join("")}</div>`;
      if (tab !== "keypad") return header + tabs + (variant === "empty recents" ? info("No calls yet.") : recentCallRows(tab === "missed"));
      return header + tabs + `<div class="dial-display touch-dial-display" dir="ltr" aria-live="polite">${esc(state.digits || t("type"))}</div>` + `
        <div class="dialpad" dir="ltr">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map((n, i) => button(`${n}<small>${["", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""][i]}</small>`, "digit", "dial-key", `data-digit="${n}" aria-label="${n}"`)).join("")}</div>
        <div class="touch-dial-results">${touchDialerResults()}</div>
        <div class="dial-actions">${button(icon("phone"), "dial", "primary", `aria-label="${esc(t("call"))}"`)}${button(icon("backspace"), "backspace", "secondary", 'aria-label="Delete last digit"')}</div>`;
    },
    ...Object.fromEntries(Object.keys(dialerOptions).map(id => [id, dialerPreview])),
    contacts: () => {
      if (variant === "permission denied" || state.readiness.Contacts.startsWith("Denied")) return heading(t("contacts")) + permissionWarning("Contacts");
      const list = variant === "empty" ? [] : state.contacts.filter(c => `${c.name} ${c.numbers.join(" ")}`.toLowerCase().includes(state.search.toLowerCase()));
      return titleAction(t("contacts"), classicMode ? `${icon("contact")} +` : icon("contact-add"), "add-contact", 'aria-label="Add contact"') + field(t("search"), "contactSearch", state.search, "search", 'autocomplete="off" data-input="contact-search"') +
        list.map(c => state.picker ? c.numbers.map((n, i) => row(c.name, n, "pick-contact", `data-id="${c.id}" data-number-index="${i}"`, avatar(c))).join("") : row(c.name, "", "contact", `data-id="${c.id}"`, avatar(c))).join("") + (!list.length ? info(state.search ? "No matches." : "No contacts yet.") : "") +
        (state.picker ? button("Cancel", "cancel-picker") : "");
    },
    "contact-detail": () => {
      if (state.readiness.Contacts.startsWith("Denied")) return heading(t("contacts")) + permissionWarning("Contacts");
      const c = selectedContact();
      if (state.contactMode) {
        const existing = state.contacts.find(p => p.id === state.editId);
        if (!state.unlocked) return info("Unlock caregiver settings before editing.") + goButton("Unlock", "pin");
        return heading(existing ? "Edit contact" : "Add contact") +
          `<form data-form="contact" class="stack">${field("Name", "name", state.contactDraft?.name ?? existing?.name ?? "", "text", 'required maxlength="60" autocomplete="off"')}${field("Mobile number", "number", state.contactDraft?.number ?? existing?.numbers[0] ?? "", "tel", 'required maxlength="24" dir="ltr"')}${field("Other number (optional)", "other", state.contactDraft?.other ?? existing?.numbers[1] ?? "", "tel", 'maxlength="24" dir="ltr"')}<button class="primary" type="submit">Save</button>${button("Cancel", "cancel-contact")}</form>`;
      }
      if (!c) return heading("No contact selected") + goButton("Contacts", "contacts");
      const sosLinked = resolve(state.sos)?.id === c.id;
      const confirmContent = info(`Delete ${esc(c.name)}? Favorites will be cleared. ${sosLinked ? "<strong>Choose another SOS contact first.</strong>" : ""}`, "warning") +
        button("Cancel", "cancel-confirm", "secondary", "autofocus") +
        (sosLinked ? goButton("Change SOS contact", "sos-settings") : button("Delete", "confirm-delete-contact", "danger"));
      const header = classicMode ? heading(c.name) : `<div class="screen-title-row">${heading(c.name)}${button(icon("edit"), "edit-contact", "secondary title-action", 'aria-label="Edit"')}${button(icon("delete"), "delete-contact", "secondary title-action", 'aria-label="Delete"')}</div>`;
      return header + avatar(c, true) + `<label><span class="field-label">Choose number</span><select data-change="contact-number" dir="ltr">${c.numbers.map((n, i) => `<option value="${i}" ${state.numberIndex === i ? "selected" : ""}>${esc(n)} · ${i ? "home" : "mobile"}</option>`).join("")}</select></label>` +
        button(`${icon("phone")} Call ${esc(c.name)}`, "contact-call", "primary") + button("Write a message", "contact-message") +
        (classicMode ? button("Edit", "edit-contact") + button("Delete", "delete-contact") : "") +
        (state.confirm !== "delete-contact" ? "" : classicMode ? confirmContent : `<dialog class="contact-delete-dialog" aria-label="Delete contact">${heading("Delete contact")}${state.error ? info(esc(state.error), "error") : ""}${confirmContent}</dialog>`);
    },
    calling: () => heading("Call") + `<div class="call-identity">${avatar({ name: state.target.name }, true)}<h3 class="call-name">${esc(state.target.name)}</h3><p dir="ltr">${esc(state.target.number)}</p><p class="call-status">${esc(variant === "connected" ? (state.held ? "On hold" : "Connected") : variant)}${["dialing", "ringing"].includes(variant) ? "" : ` · <bdi>${sampleCallDuration}</bdi>`}</p></div>` +
      `<div class="call-controls">${button(`${icon("speaker")} ${state.speaker ? "Earpiece" : "Speaker"}`, "speaker", "secondary", `aria-pressed="${state.speaker}"`)}${button(`${icon("microphone")} ${state.muted ? "Unmute" : "Mute"}`, "mute", "secondary", `aria-pressed="${state.muted}"`)}${button(state.held ? "Resume" : "Hold", "hold-call", `secondary ${classicMode && state.confirm === "call-keypad" ? "call-option" : ""}`, `aria-pressed="${state.held}"`)}${button(`${icon("apps")} ${state.confirm === "call-keypad" ? "Hide keypad" : "Keypad"}`, "call-keypad", "secondary")}</div>` +
      (state.confirm === "call-keypad" ? `<div class="dialpad" dir="ltr">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map(n => button(n, "dtmf", "dial-key", `data-digit="${n}"`)).join("")}</div><small dir="ltr">${esc(state.digits)}</small>` : "") +
      (variant === "Bluetooth" ? info("Bluetooth") : "") +
      (variant === "call waiting" ? info("Daniel is calling") + button("Hold & answer", "answer-waiting") + button("Decline", "decline-waiting") : "") +
      button(`${icon("end")} End`, "end-call", "danger call-end") + goButton(t("home"), "home"),
    incoming: () => heading("Incoming call") + avatar({ name: variant === "known" ? "Mira" : "?" }, true) + `<h3 class="call-name">${variant === "known" ? "Mira" : variant === "private" ? "Private number" : "Unknown caller"}</h3><p dir="ltr">${variant === "private" ? "Number withheld" : variant === "known" ? "202-555-0101" : "202-555-0199"}</p>${button(`${icon("phone")} Answer`, "answer", "primary")}${button(`${icon("end")} Decline`, "decline", "danger")}`,
    "call-ended": () => heading(`Call ${variant}`) + `<h3>${esc(state.target.name)}</h3><p dir="ltr">${esc(state.target.number)}</p>` +
      (variant === "ended" ? `<p class="call-duration" role="group" aria-label="Call duration">${icon("clock")}<bdi dir="ltr">${sampleCallDuration}</bdi></p>` : "") +
      button("Call again", "retry-call", "primary") + button("Message", "target-message") + goButton("Home", "home"),
    "missed-call": () => callOutcome("Missed call", "Call back", true),
    "call-unanswered": () => callOutcome("No answer", "Try again"),
    "call-failed": () => heading("Call not connected") + info(`${esc(variant)}. Check mobile service.`, "error") + `<p>${esc(state.target.name)} · <bdi>${esc(state.target.number)}</bdi></p>` + button("Try again", "retry-call", "primary") + button("Open Phone", "native") + goButton("Settings", "permissions") + noticeExternal(),
    messages: () => {
      if (variant === "permission denied" || state.readiness.Messages.startsWith("Denied")) return heading(t("messages")) + permissionWarning("Messages") + noticeExternal();
      return titleAction(t("messages"), "+", "new-message", 'aria-label="Write a new message"') + (classicMode ? "" : field(t("search"), "messageSearch", state.search, "search", 'data-input="message-search"')) +
        (variant === "empty" ? info("No messages yet.") : inboxRows()) +
        (variant === "MMS / group" ? row("Family", "3 people · attachment", "mms-thread", "", icon("messages")) : "");
    },
    conversation: () => state.readiness.Messages.startsWith("Denied") ? heading(t("messages")) + permissionWarning("Messages") : heading(state.target.name) +
      (state.contacts.some(contact => ["mira", "daniel", "leah"].includes(contact.id) && contact.numbers[0] === state.target.number) && !state.deletedThreads.includes(state.target.number) ? `<div class="bubble">I’ll come by after lunch.<small class="message-time">${sampleMessageTime()}</small></div><div class="bubble outgoing">That sounds lovely.<small class="message-time">${sampleMessageTime()} · Sent · delivery not confirmed</small></div>` : "") +
      state.messages.filter(m => m.number === state.target.number).map(m => `<div class="bubble outgoing">${esc(m.body)}<small class="message-time">${esc(m.time)} · Sent · delivery not confirmed</small></div>`).join("") +
      (["MMS", "download error"].includes(variant) ? info(variant === "MMS" ? "Attachment · 3 people" : "Download failed.", variant === "download error" ? "error" : "") + button("Open attachment", "native") : "") +
      (["sending", "sent", "delivery unknown", "delivery confirmed"].includes(variant) ? info(variant === "delivery confirmed" ? "Delivered" : variant === "sent" ? "Sent · delivery not confirmed" : esc(variant)) : "") +
      button("Write a reply", "reply", "primary") + button("Delete conversation", "delete-thread") +
      (state.confirm === "delete-thread" ? info(`Delete all SMS/MMS with ${esc(state.target.name)} and its reply draft? This cannot be undone.`, "warning") + button("Delete", "confirm-delete-thread", "danger") + button("Cancel", "cancel-confirm") : "") + noticeExternal(),
    compose: () => composeScreen(),
    reply: () => composeScreen(true),
    "message-failed": () => {
      const contact = recipientContact();
      const uncertain = variant === "unknown outcome";
      const confirmingRetry = state.confirm === "retry-message";
      const explanation = uncertain
        ? confirmingRetry ? "Sending again may deliver this message twice." : "This message may already have been sent."
        : state.readiness.Messages.startsWith("Denied") ? "Messages access is off."
        : state.network !== "available" ? "No mobile service. Try again when it returns." : "Please try again.";
      return heading(uncertain ? "Delivery uncertain" : "Message not sent") +
        `<div class="message-recipient" role="group" aria-label="Recipient">${avatar(contact)}<div><strong>${esc(contact?.name || "To")}</strong><bdi dir="ltr">${esc(state.recipient)}</bdi></div></div>` +
        info(explanation, uncertain ? "warning" : "error") +
        (state.body ? `<div class="bubble">${esc(state.body)}</div>` : "") +
        button(confirmingRetry ? "Send again" : "Try again", "retry-message", "primary") + goButton("Cancel", "messages");
    },
    camera: () => heading("Camera") + (variant === "unavailable" ? info("No camera app available.", "error") + button("Back", "external-back") : noticeExternal()),
    gallery: () => {
      if (variant === "permission denied" || state.readiness.Media.startsWith("Denied")) return heading("Gallery") + permissionWarning("Media") + noticeExternal();
      if (variant === "loading") return heading("Gallery") + info("Loading…") + button("Refresh", "finish-loading");
      return heading("Gallery") +
        (variant === "partial access" ? info("Only photos selected in Android are available. Review Photos access to choose more.", "warning") : "") +
        (variant === "empty" || !state.photos.length ? info("No pictures yet.") : `<div class="photo-grid">${state.photos.map((p, i) => button(`<span>${esc(p.title)}</span>`, "photo", "photo", `data-index="${i}" aria-label="View ${esc(p.title)}"`)).join("")}</div>`) +
        noticeExternal();
    },
    photo: () => {
      if (state.readiness.Media.startsWith("Denied")) return heading("Photo") + permissionWarning("Media");
      const p = state.photos[state.photoIndex] || state.photos[0];
      if (!p) return heading("No picture selected") + goButton("Gallery", "gallery");
      return heading(p.title) + `<div class="photo photo-view" role="img" aria-label="${esc(p.title)}"><span>${esc(p.title)}</span></div>` +
        (classicMode ? button("Delete picture", "remove-photo") : `<div class="button-row">${button("←", "previous-photo", "secondary inline-action", `aria-label="Previous picture" ${state.photoIndex === 0 ? "disabled" : ""}`)}${button("→", "next-photo", "secondary inline-action", `aria-label="Next picture" ${state.photoIndex === state.photos.length - 1 ? "disabled" : ""}`)}</div>` + button("Delete picture", "remove-photo")) +
        (state.confirm === "remove-photo" ? info("Delete this picture from your device? Android authorization is required.", "warning") + button("Cancel", "cancel-confirm") + button("Delete", "confirm-remove-photo", "danger") : "");
    },
    sos: () => {
      const recipient = variant === "unconfigured" ? null : resolve(state.sos);
      const blocked = !recipient || !state.sosConsent || ["no signal", "no SIM"].includes(variant) || state.network !== "available" || state.readiness.Phone.startsWith("Denied");
      return heading("Call for help") + '<div class="sos-symbol" aria-hidden="true">SOS</div>' +
        info(recipient ? `${avatar(recipient)}<strong>${esc(recipient.name)}</strong><br><bdi>${esc(recipient.number)}</bdi>` : "Choose a help contact in Settings.", blocked ? "warning" : "") +
        info("Not an emergency service. Calls your contact only. No message or location sent.", "warning") +
        (blocked ? info(["no signal", "no SIM"].includes(variant) || state.network !== "available" ? `${esc(state.network !== "available" ? state.network : variant)}. Cannot call.` : state.readiness.Phone.startsWith("Denied") ? "Phone access needed." : "Choose a contact who has agreed.", "error") : "") +
        (state.activeCall ? goButton("Return to call", "calling", "primary") : !recipient ? goButton("Set up SOS", "sos-settings", "primary") : button("Call in 5 seconds", "start-sos", "danger", blocked ? 'disabled aria-disabled="true"' : "")) + goButton("Settings", "sos-settings") + noticeExternal();
    },
    "sos-countdown": () => heading("Calling in…") + `<div class="countdown" id="countdown-value" role="timer" aria-live="polite">${countdown}</div><p>${esc(resolve(state.sos)?.name || "No contact")}</p>` +
      (variant === "canceled" || variant === "interrupted" ? info("Canceled. No call started.", "success") + button("Try again", "start-sos", "danger") + goButton("SOS", "sos") : button("STOP", "cancel-sos", "danger", 'aria-label="Stop help call"')) +
      '<small>Back or Home cancels the call.</small>',
    "sos-active": () => variant === "ended" ? screens["call-ended"]() : screens.calling(),
    "sos-failed": () => heading("Help call failed") + info(`${esc(variant)}. No one reached. No alert or location delivered.`, "error") + button("Try again", "start-sos", "danger") + goButton("Home", "home") + noticeExternal(),
    apps: () => {
      const list = variant === "empty" ? [] : state.allowed;
      return heading(t("apps")) + (list.map(a => row(a, "", "app", `data-app="${a}"`, appArtwork(a))).join("") || info("No apps chosen yet. Ask your caregiver to add apps in Settings → Apps & Menu.")) + noticeExternal();
    },
    pin: () => {
      const remaining = Math.max(0, Math.ceil((state.cooldown - Date.now()) / 1000));
      const dateMode = state.accessMode === "date";
      return heading(dateMode ? "Today's date?" : "Settings access") + `<div class="pin-emblem">${icon("lock")}</div>` +
        (remaining ? info(`Wait ${remaining} seconds.`, "warning") + button("Try again", "refresh") : "") +
        `<form data-form="pin" class="stack">${dateMode
          ? field("dd/MM/YYYY", "date", "", "text", 'dir="ltr" placeholder="dd/MM/YYYY" pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}" minlength="10" maxlength="10" required autocomplete="off" spellcheck="false"')
          : field("6-digit PIN", "pin", "", "password", 'inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" required autocomplete="off"')}<button type="submit" class="primary" ${remaining ? "disabled" : ""}>Unlock</button></form>` +
        (dateMode ? "" : goButton("Forgot PIN?", "pin-recovery"));
    },
    settings: () => heading("Settings") + `<div class="settings-list stack">${[
      ["Call audio", "Automatic speaker and earpiece", "call-audio"],
      ["Phone style", classicMode ? "Classic Phone" : "Touch Home", "phone-style"],
      ["Apps & Menu", "Allowed apps and Menu visibility", "home-edit"],
      ["Menu order", "Separate from Home tiles", "menu-order"],
      ["Home tiles", `Home tiles configured: ${state.slots.filter(Boolean).length}`, "speed-dial"],
      ["Language & display", `${languages[state.language]} · ${state.textSize}`, "display"],
      ["SOS contact", "", "sos-settings"],
      ["Permissions", "", "permissions"],
      ["Membership & privacy", "Core features free · your diagnostics choices", "membership"],
      ["Settings access", state.accessMode === "date" ? "Today's date question" : "Caregiver PIN", "access-settings"],
      ["Reset Claro", "", "reset"]
    ].map(a => row(a[0], a[1], "go", `data-route="${a[2]}"`, icon(a[2] === "speed-dial" ? "contact" : a[2] === "sos-settings" ? "sos" : "settings"))).join("")}${button("Done", "done-settings", "primary")}${button("Lock settings", "lock")}</div>`,
    "home-edit": () => {
      const apps = variant === "empty" ? [] : installedApps.filter(name => name.toLowerCase().includes(state.search.toLowerCase()));
      return heading("Apps & Menu") + info("Hiding apps is a convenience, not a device security lock. Home tiles are configured separately.") +
        field("Search", "appSearch", state.search, "search", 'data-input="app-search"') +
        (apps.map(name => `<div class="edit-app">${appArtwork(name)}<strong>${name}</strong><label class="check-label"><input type="checkbox" data-change="allow-app" value="${name}" ${state.allowed.includes(name) ? "checked" : ""}>Allow app</label><label class="check-label"><input type="checkbox" data-change="menu-app" value="${name}" ${state.menuApps.includes(name) ? "checked" : ""} ${state.allowed.includes(name) ? "" : "disabled"}>Show in Menu</label></div>`).join("") || info("No eligible apps match.")) +
        button("Done", "done-settings", "primary");
    },
    "speed-dial": peopleEditor,
    "tile-picker": tilePicker,
    "menu-order": () => heading("Menu order") + state.menuOrder.filter(value => !value.startsWith("app:") || state.menuApps.includes(value.slice(4))).map((value, visibleIndex, entries) => {
      const index = state.menuOrder.indexOf(value);
      return `<div class="edit-app"><strong>${esc(value === "contacts" ? t("contacts") : slotLabel(value))}</strong><div class="button-row">${button("Move up", "move-menu", "secondary inline-action", `data-index="${index}" data-delta="-1" ${visibleIndex === 0 ? "disabled" : ""}`)}${button("Move down", "move-menu", "secondary inline-action", `data-index="${index}" data-delta="1" ${visibleIndex === entries.length - 1 ? "disabled" : ""}`)}</div></div>`;
    }).join("") + button("Done", "done-settings", "primary"),
    "phone-style": () => heading("Phone style") + info("Switching changes only presentation. Contacts, messages and settings stay in place.") + styleChoices() +
      (state.pendingStyle ? info("The style change is waiting for the current activity to finish.", "warning") : "") +
      button("Apply", "apply-style", "primary") + goButton("Cancel", "settings"),
    "call-audio": () => heading("Call audio") + info("Use the speaker when the phone is away, and the earpiece nearby. Bluetooth and headphones are unchanged. Manual audio selection takes priority for that call.") +
      `<label class="setting-toggle">Automatic speaker and earpiece<input type="checkbox" data-change="easy-audio" ${state.easyAudio ? "checked" : ""}></label>` +
      (state.easyAudio ? `<label class="setting-toggle">Maximum speaker volume<input type="checkbox" data-change="maximum-volume" ${state.maximumVolume ? "checked" : ""}></label>` : "") +
      info("Without a far proximity reading, Claro uses the earpiece. The sensor is not hearing protection.", "warning") +
      (state.confirm === "maximum-volume" ? info("Maximum volume can be very loud. Keep the loudspeaker away from your ear. Your previous volume is restored on earpiece or call end.", "warning") + button("Enable maximum speaker volume", "enable-maximum", "danger") + button("Cancel", "cancel-confirm") : "") + button("Done", "done-settings", "primary"),
    display: () => heading("Language and display") + displayFields() +
      button("Save", "save-display", "primary") + goButton("Cancel", "settings"),
    "display-picker": () => heading(state.displayPicker === "language" ? "Language" : "Text size") +
      (state.displayPicker === "language" ? Object.entries(languages) : ["Standard", "Large", "Huge"].map(size => [size, size])).map(([value, label]) => row(label, draft?.[state.displayPicker] === value ? "✓ Selected" : "", "select-display", `data-value="${value}"`)).join("") + button("Cancel", "close-display-picker"),
    "access-settings": () => heading("Settings access") + `<form data-form="setup-access" class="stack">${accessFields()}<button type="submit" class="primary">Save access method</button></form>` + goButton("Cancel", "settings"),
    "sos-settings": sosSettingsScreen,
    permissions: () => heading("Permissions and roles") + info("Allowed means access was granted, not that a real call, SMS or SOS test succeeded.") + permissionRows() + noticeExternal(),
    privacy: () => heading("Privacy") + `<form data-form="privacy" class="stack"><label class="setting-toggle">Usage analytics<input name="analytics" type="checkbox" ${state.analytics ? "checked" : ""}></label><label class="setting-toggle">Crash reports<input name="crashes" type="checkbox" ${state.crashes ? "checked" : ""}></label><p>Optional. Change your choices anytime.</p><button class="primary" type="submit">Save</button>${button("Turn both off", "withdraw-consent")}${goButton("Cancel", "settings")}</form>`,
    reset: () => heading("Reset Claro?") + info("Clears local preferences, Home tiles, speed dials, drafts, SOS, access credentials and diagnostics consent. Android contacts, call log, messages, photos and store purchases stay unchanged.", "warning") +
      (state.activeCall || variant === "active call" ? info("End your call first.", "error") + goButton("Return to call", "calling") : state.confirm === "reset" ? info("Reset all Claro changes?") + button("Cancel", "cancel-confirm", "primary") + button("Reset", "confirm-reset", "danger") : button("Continue", "reset-review", "danger")) + goButton("Cancel", "settings"),
    welcome: () => heading("Welcome to Claro") + `<img class="welcome-symbol welcome-logo" src="marketing/store/assets/logo-2048.png" alt=""><h3>Big buttons. Less fuss.</h3><p>Keep your people and everyday apps close, with clear calls, easy messages and a home that feels familiar.</p>${info("Let’s make Claro feel like your phone. Choose a style, adjust the text and add what matters — on your own or with someone you trust.")}${button("Set up Claro", "start-setup", "primary")}`,
    setup: setupScreen,
    plans: () => variant === "unconfigured" ? heading("Membership") + info("Purchases unavailable. No store products are configured. Calls, messages, SOS and Home stay available.", "warning") + goButton("Back to Settings", "settings") : heading("Membership") + info("Calls, messages, SOS and your home stay free.") +
      (variant === "loading" ? info("Loading plans…") : "") +
      (variant === "offline" ? info("Store offline. No current price or purchase available. Free phone functions still work.", "warning") : "") +
      (variant === "ineligible" ? info("Trial eligibility is not confirmed. Store may offer no free trial; review its exact terms.", "warning") : "") +
      (variant === "owned lifetime" ? info("Lifetime membership") : "") +
      ["monthly", "yearly", "lifetime"].map(p => button(`<strong>${p === "monthly" ? "Monthly" : p === "yearly" ? "Yearly" : "Lifetime"}</strong><small>Price to be confirmed</small><small>${p === "lifetime" ? "One payment · no renewal · no trial" : "7 days free if eligible · automatically renews " + (p === "monthly" ? "monthly" : "yearly")}</small>`, "select-plan", `plan ${state.plan === p ? "selected" : ""}`, `data-plan="${p}" aria-pressed="${state.plan === p}"`)).join("") +
      button("Review plan", "review-purchase", "primary") + goButton("Continue free", "home") + button("Restore purchases", "restore") + noticeExternal(),
    purchase: () => heading(state.plan === "lifetime" ? "Lifetime" : state.plan === "yearly" ? "Yearly plan" : "Monthly plan") +
      info("Price to be confirmed. Purchase unavailable until price and terms are confirmed.", "warning") +
      `<p>${state.plan === "lifetime" ? "One payment. No renewal or subscription trial." : `7 days free if eligible and you enroll. Then automatically renews ${state.plan === "yearly" ? "yearly" : "monthly"} at the price shown in Google Play. Installing does not start a trial. Cancel in Google Play before its stated deadline to avoid renewal.`}</p>` +
      (variant !== "standard" ? info(({ pending: "Pending. Access starts after approval.", canceled: "Canceled. No purchase made.", error: "Purchase failed.", offline: "Connect to try again.", "demo success": "Plan selected." })[variant] || "", variant === "error" || variant === "offline" ? "error" : "") : "") +
      button("Google Play", "mock-store", "primary") + goButton("Cancel", "plans") +
      (state.external ? noticeExternal() + button("Await approval", "purchase-pending") + button("Done", "purchase-success") + button("Cancel", "purchase-cancel") : ""),
    membership: () => variant === "Unavailable" ? heading("Membership & privacy") + info("Purchases unavailable. No configured store products or verified membership. Core phone functions stay available.", "warning") + goButton("Optional diagnostics", "privacy") + goButton("Done", "settings", "primary") : heading("Membership") + info(`<strong>${esc(membershipLabel(variant === "Free" ? state.membership : variant))}</strong>`) +
      `<p>Calls, messages, SOS, favorites and home settings stay free.</p>` +
      goButton("Plans", "plans", "primary") + button("Restore purchases", "restore") + button("Manage in Google Play", "manage-membership") + goButton("Trial ended", "trial-ended") + goButton("Home", "home") +
      noticeExternal() + (state.external ? button("Cancel renewal", "cancel-membership") + button("No purchases found", "restore-none") + button("Restore lifetime", "restore-demo") + button("Try later", "restore-failed") : ""),
    "trial-ended": () => heading("Trial ended") + info("Your calls, messages, SOS and home stay free. Your settings are unchanged.") + goButton("Continue free", "home", "primary") + goButton("Plans", "plans") + button("Restore purchases", "restore") + noticeExternal(),
    "pin-recovery": () => heading("Reset PIN") +
      (variant === "forgotten secret" ? info("Without the PIN or phrase, reset Claro in Android app settings. Claro settings and drafts will be lost. Device contacts and messages stay.", "warning") : "") +
      `<form data-form="recovery" class="stack">${field("Recovery secret", "recovery", "", "password", 'required maxlength="80" autocomplete="off"')}${field("New 6-digit PIN", "newPin", "", "password", 'required inputmode="numeric" pattern="[0-9]{6}" maxlength="6"')}${field("Confirm new PIN", "confirmPin", "", "password", 'required inputmode="numeric" maxlength="6"')}<button class="primary" type="submit">Reset PIN</button></form>${variant === "forgotten secret" ? "" : button("Forgot secret?", "forgot-recovery")}${goButton("Cancel", "pin")}`
  };
  const sampleMessageTime = () => new Date(2026, 10, 17, 9, 20).toLocaleString(({ en: "en-GB", es: "es", he: "he" })[state.language], { dateStyle: "short", timeStyle: "short" });
  function setTarget(contact, number) {
    state.target = { name: contact?.name || number || "Unknown person", number: number || contact?.numbers[0] || "" };
  }
  function setIncomingTarget() {
    if (variant === "known") setTarget(state.contacts.find(c => c.id === "mira"), "202-555-0101");
    else state.target = { name: variant === "private" ? "Private number" : "Unknown caller", number: variant === "private" ? "Withheld" : "202-555-0199" };
  }
  function callContact(contact, number) {
    setTarget(contact, number); placeCall();
  }
  function placeCall() {
    if (state.activeCall) { navigate("calling"); state.notice = "End your current call first."; render(); return; }
    if (["no signal", "no SIM", "permission denied"].includes(variant) || state.network !== "available" || state.readiness.Phone.startsWith("Denied") || !validNumber(state.target.number)) {
      const reason = !validNumber(state.target.number) ? "invalid number" : state.readiness.Phone.startsWith("Denied") ? "permission denied" : state.network !== "available" ? state.network : variant;
      navigate("call-failed"); variant = reason; render(); return;
    }
    if (emergencyNumber(state.target.number)) { state.external = "Emergency dialer"; render(); return; }
    state.callTarget = { ...state.target };
    state.activeCall = true; state.callState = "dialing"; state.muted = false; state.speaker = false; state.held = false;
    navigate("calling"); variant = "dialing";
    render();
  }
  function startSOS() {
    if (!resolve(state.sos) || !state.sosConsent || (route === "sos" && variant === "unconfigured") || state.network !== "available" || state.readiness.Phone.startsWith("Denied")) {
      state.error = "SOS cannot start: check recipient agreement and mobile service. No one was notified."; render(); return;
    }
    if (state.activeCall) { state.error = "End your current call before SOS."; render(); return; }
    countdown = 5; state.sosArmed = true;
    if (route === "sos-countdown") { variant = "standard"; state.error = ""; state.notice = ""; }
    navigate("sos-countdown");
  }
  function startCountdown() {
    clearInterval(countdownTimer);
    if (variant === "canceled" || variant === "interrupted") return;
    if (!state.sosArmed) { variant = "canceled"; state.notice = "No call started."; render(); return; }
    state.sosArmed = false;
    if (!resolve(state.sos) || !state.sosConsent || state.network !== "available" || state.readiness.Phone.startsWith("Denied") || state.activeCall) { variant = "canceled"; state.error = "Cannot call. Check SOS contact, mobile service and Phone access."; render(); return; }
    countdown = 5;
    const timerText = $("countdown-value"); if (timerText) timerText.textContent = "5";
    countdownTimer = setInterval(() => {
      countdown -= 1;
      const target = $("countdown-value"); if (target) target.textContent = String(countdown);
      if (countdown <= 0) {
        clearInterval(countdownTimer);
        const contact = resolve(state.sos);
        setTarget(contact, contact?.number);
        placeCall();
      }
    }, 1000);
  }
  function protectedAction(destination, action) {
    if (!state.unlocked) { state.intended = destination; state.pendingAction = action; navigate("pin"); return false; }
    return true;
  }
  function beginContactEdit(add = false) {
    if (!protectedAction("contact-detail", add ? "add-contact" : "edit-contact")) return;
    const nextId = add ? "" : selectedContact()?.id || "";
    if (state.editId !== nextId) { state.contactDraft = null; delete state.formDrafts.contact; }
    state.editId = nextId; state.contactMode = "edit"; navigate("contact-detail"); state.contactMode = "edit"; render();
  }
  function applyPhoneStyle(style) {
    classicMode = style === "classic";
    state.pendingStyle = "";
    document.body.classList.toggle("classic-preview", classicMode);
    const url = new URL(location.href);
    url.searchParams.set("version", style);
    history.replaceState(history.state, "", url);
    $("touch-version").removeAttribute("aria-current"); $("classic-version").removeAttribute("aria-current");
    $(classicMode ? "classic-version" : "touch-version").setAttribute("aria-current", "true");
    render();
  }
  function setHomeVisibility(value, visible) {
    if (!visible) { state.slots = state.slots.map(slot => slot === value ? null : slot); return; }
    if (state.slots.includes(value)) return;
    state.slots.push(value);
    if (value.startsWith("app:") && !state.allowed.includes(value.slice(4))) state.allowed.push(value.slice(4));
  }
  function saveDisplay() {
    const values = draft || state;
    state.language = values.language; state.textSize = values.textSize; state.haptics = values.haptics;
    state.large = values.textSize !== "Standard"; state.contrast = false;
    delete state.preferenceDrafts.display; delete state.preferenceDrafts.language; draft = null;
  }
  function permissionPrompt(name) {
    state.permission = name;
    state.external = `${name} · Android access`;
    render();
  }
  function advanceRoleQueue() {
    const name = state.roleQueue.shift();
    if (name) permissionPrompt(name);
    else { state.external = ""; state.notice = "Default-app choices recorded. Service tests are separate."; render(); }
  }
  const actions = {
    "classic-digit": classicDigit,
    "classic-delete": classicDelete,
    "classic-call": classicCall,
    "classic-end": classicEnd,
    "classic-back": back,
    "classic-confirm": () => {
      if (classicOptionsOpen) classicOK();
      else if (route === "home") navigate("classic-menu");
      else if (route === "settings") actions.lock();
      else if (route === "pin" && state.accessMode === "pin") navigate("pin-recovery");
      else openClassicOptions();
    },
    "classic-options": openClassicOptions,
    "classic-option": el => {
      const control = classicOptionActions[Number(el.dataset.index)];
      closeClassicOptions();
      invokeClassicAction(control);
    },
    "classic-nav": classicNavigate,
    "classic-ok": classicOK,
    go: el => {
      if (el.dataset.discard) { delete state.preferenceDrafts[route]; draft = null; const form = phone.querySelector("form[data-form]"); if (form) delete state.formDrafts[form.dataset.form]; }
      if (el.dataset.route === "missed-call") state.selectedMissedId = (state.missedCalls.find(call => call.unread) || state.missedCalls[0]).id;
      navigate(el.dataset.route);
    },
    back,
    lock: () => { lock(); navigate("classic-menu"); },
    "done-settings": () => navigate(state.onboarding ? "setup" : route === "settings" ? "classic-menu" : "settings"),
    "return-to-call": () => navigate("calling"),
    "open-feature": el => {
      const feature = el.dataset.feature;
      if (feature === "alarm") { state.external = "Alarm clock"; render(); return; }
      if (feature === "phone") {
        state.dialTab = notificationCounts().missed > 0 ? "missed" : "keypad";
        navigate("dialer"); return;
      }
      navigate(feature);
    },
    "home-slot": el => {
      const value = state.slots[Number(el.dataset.slot)];
      const contact = slotContact(value);
      if (contact) { callContact(contact, contact.number); return; }
      if (value?.startsWith("contact:")) { state.error = "Contact assignment is unavailable. Choose it again in Settings."; render(); return; }
      if (value?.startsWith("app:")) { actions.app({ dataset: { app: value.slice(4) } }); return; }
      if (value) actions["open-feature"]({ dataset: { feature: value } });
    },
    "pick-slot": el => { state.pickerTarget = el.dataset.target; state.pickerReturn = "speed-dial"; navigate("tile-picker"); },
    "move-home-tile": el => {
      const slots = occupiedHomeSlots();
      const index = Number(el.dataset.index);
      const position = slots.indexOf(index);
      if (position < 0) return;
      const next = slots[position + Number(el.dataset.delta)];
      if (next === undefined) return;
      [state.slots[index], state.slots[next]] = [state.slots[next], state.slots[index]];
      const scroll = phone.scrollTop;
      render();
      phone.scrollTop = scroll;
    },
    "assign-slot": el => {
      const value = el.dataset.value;
      const [kind, index] = state.pickerTarget.split(":");
      if (kind === "home") {
        if (shortcuts.includes(value) && state.slots.some((slot, position) => slot === value && position !== Number(index))) {
          state.error = "This shortcut is already on Home. Clear its existing tile first."; render(); return;
        }
        state.slots[Number(index)] = value;
        if (value.startsWith("app:") && !state.allowed.includes(value.slice(4))) state.allowed.push(value.slice(4));
      } else state.keys[Number(index)] = value.slice(8);
      navigate(state.pickerReturn);
    },
    "clear-slot": () => {
      const [kind, index] = state.pickerTarget.split(":");
      if (kind === "home") state.slots[Number(index)] = null;
      else state.keys[Number(index)] = "";
      navigate(state.pickerReturn);
    },
    "move-menu": el => {
      const index = Number(el.dataset.index);
      const visible = state.menuOrder.filter(value => !value.startsWith("app:") || state.menuApps.includes(value.slice(4)));
      const next = state.menuOrder.indexOf(visible[visible.indexOf(state.menuOrder[index]) + Number(el.dataset.delta)]);
      if (next >= 0 && next < state.menuOrder.length) [state.menuOrder[index], state.menuOrder[next]] = [state.menuOrder[next], state.menuOrder[index]];
      render(true);
    },
    "select-style": el => { state.selectedStyle = el.dataset.style; render(); },
    "apply-style": () => {
      if (state.activeCall || state.sosArmed || state.external) {
        state.pendingStyle = state.selectedStyle; render(); return;
      }
      applyPhoneStyle(state.selectedStyle); navigate("settings");
    },
    "open-display-picker": el => {
      if (state.onboarding) {
        state.displayPicker = el.dataset.picker; state.inlineDisplayPicker = true;
        state.preferenceDrafts.display = draft || { language: state.language, textSize: state.textSize, haptics: state.haptics };
      } else state.displayPicker = el.dataset.picker;
      navigate("display-picker");
    },
    "select-display": el => {
      draft[state.displayPicker] = el.dataset.value;
      state.preferenceDrafts.display = draft;
      if (state.onboarding) { saveDisplay(); state.inlineDisplayPicker = false; navigate("setup"); }
      else navigate("display");
    },
    "close-display-picker": () => { state.inlineDisplayPicker = false; navigate(state.onboarding ? "setup" : "display"); },
    "enable-maximum": () => { state.maximumVolume = true; state.confirm = ""; render(); },
    "start-setup": () => {
      state.onboarding = true; state.setupReentry = state.setupCompleted; state.setupStep = 0; state.selectedStyle = "classic";
      state.onboardingPicker = ""; state.onboardingQuery = "";
      if (!state.setupReentry) { state.sos = ""; state.sosConsent = false; state.sosDraft = ""; }
      navigate("setup");
    },
    "review-setup-step": el => {
      const step = Number(el.dataset.step);
      if (step < 0) { navigate("welcome"); return; }
      if (step >= setupSteps.length) {
        if (!state.setupCompleted) { state.notice = "Finish setup first. Home is not available during first launch."; render(); return; }
        state.onboarding = false; navigate("home"); return;
      }
      state.setupStep = step; state.setupReentry = state.setupCompleted; state.onboarding = true; state.inlineDisplayPicker = false;
      state.onboardingPicker = "";
      state.error = ""; state.notice = ""; draft = null; navigate("setup");
    },
    "consent-continue": () => {
      state.analytics = true; state.crashes = true; delete state.formDrafts.privacy;
      actions["setup-next"]();
    },
    "open-onboarding-vips": () => { state.onboardingPicker = "vips"; state.onboardingQuery = ""; render(); phone.scrollTop = 0; },
    "open-onboarding-sos": () => { state.onboardingPicker = "sos"; state.onboardingQuery = ""; render(); phone.scrollTop = 0; },
    "setup-picker-done": () => { state.onboardingPicker = ""; state.onboardingQuery = ""; render(); phone.scrollTop = 0; },
    "toggle-onboarding-vip": el => {
      const value = `contact:${el.dataset.value}`;
      state.slots = state.slots.includes(value) ? state.slots.filter(slot => slot !== value) : [...state.slots, value];
      render(true);
    },
    "select-onboarding-sos": el => {
      state.sosDraft = el.dataset.value; state.sosConsent = false; state.onboardingPicker = ""; state.onboardingQuery = ""; render(); phone.scrollTop = 0;
    },
    "finish-onboarding-sos": () => {
      const selected = state.sosDraft || state.sos, recipient = resolve(selected);
      if (!recipient || !validNumber(recipient.number) || emergencyNumber(recipient.number) || !state.sosConsent) {
        state.error = "Choose a valid non-emergency contact and confirm their agreement."; render(); return;
      }
      state.sos = selected; state.sosDraft = ""; actions["finish-setup"]();
    },
    "skip-onboarding-sos": () => { state.sosDraft = ""; actions["finish-setup"](); },
    "onboarding-back": () => {
      if (route === "contacts" && state.picker) actions["cancel-picker"]();
      else if (route === "display-picker") actions["close-display-picker"]();
      else back();
    },
    "setup-next": () => {
      if (state.setupStep === 3 && !defaultRolesReady()) { state.error = "Confirm all supported default-app roles first."; render(); return; }
      if (state.setupStep === 1) applyPhoneStyle(state.selectedStyle);
      if (state.setupStep === 2 && draft) saveDisplay();
      state.setupStep = Math.min(setupSteps.length - 1, state.setupStep + 1); state.error = ""; state.notice = ""; render(); phone.scrollTop = 0;
      if (state.setupStep === 3) actions["request-defaults"]();
    },
    "finish-setup": () => {
      if (!defaultRolesReady()) { state.setupStep = 3; state.error = "Confirm all supported default-app roles first."; render(); return; }
      state.configured = true; state.setupCompleted = true; state.onboarding = false; lock(); navigate("home");
    },
    "request-defaults": () => { state.roleQueue = ["Home", "Phone", "Messages"].filter(name => !state.readiness[name].startsWith("Granted") && state.readiness[name] !== "Unavailable"); advanceRoleQueue(); },
    "permission-request": el => { state.roleQueue = []; permissionPrompt(el.dataset.permission); },
    "accept-permission": () => {
      state.readiness[state.permission] = "Granted · not service-tested";
      state.external = ""; state.notice = ""; state.error = "";
      if (state.roleQueue?.length) advanceRoleQueue();
      else render();
    },
    "deny-permission": () => {
      state.readiness[state.permission] = "Denied · repair needed"; state.roleQueue = []; state.external = "";
      state.notice = "Request canceled. No defaults were changed for this step."; render();
    },
    "number-match": el => {
      state.digits = el.dataset.number; state.dialIdentity = state.contacts.find(contact => contact.id === el.dataset.id); updateDialerNumber();
    },
    "new-message": () => navigate("compose"),
    "delete-body": () => {
      const input = $("field-body");
      if (input) { classicInput = input; classicDelete(); }
    },
    "delete-thread": () => { state.confirm = "delete-thread"; render(); },
    "confirm-delete-thread": () => {
      if (state.readiness.Messages.startsWith("Denied") || variant === "delete failure") { state.error = "Could not delete the conversation. Messages and draft were kept."; render(); return; }
      const number = state.target.number;
      state.deletedThreads.push(number); delete state.drafts[number];
      state.messages = state.messages.filter(message => message.number !== number);
      if (state.contacts.find(contact => contact.id === "mira")?.numbers.includes(number)) state.unread = false;
      navigate("messages");
    },
    refresh: () => render(),
    variant: el => {
      if (route === "incoming" && el.dataset.variant === "missed") {
        setIncomingTarget();
        const id = Math.max(...state.missedCalls.map(call => call.id)) + 1;
        state.missedCalls.unshift({ id, ...state.target, when: `Today · ${new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}`, unread: true });
        state.selectedMissedId = id;
        state.activeCall = false; state.held = false;
        navigate("home"); return;
      }
      if (route === "calling" && el.dataset.variant === "no answer") { navigate("call-unanswered"); return; }
      if (route === "call-ended" && el.dataset.variant === "missed") { navigate("missed-call"); return; }
      cleanup(); variant = el.dataset.variant; state.error = ""; state.notice = ""; state.confirm = ""; state.external = "";
      if (["calling", "sos-active"].includes(route)) state.callState = variant;
      if (route === "camera" && variant !== "unavailable") state.external = "Device camera";
      if (route === "home" && variant === "missed") state.missedCalls[0].unread = true;
      if (route === "home" && variant === "unread messages") state.unread = true;
      if (route === "home" && variant === "notifications cleared") { state.missedCalls.forEach(call => { call.unread = false; }); state.unread = false; }
      if (dialerOptions[route]) {
        if (variant === "standard") state.phoneTabs[route] = dialerOptions[route].initialTab;
        if (variant === "empty contacts") state.phoneTabs[route] = "contacts";
        if (variant === "empty recents") state.phoneTabs[route] = "recent";
        if (variant === "unassigned") state.phoneTabs[route] = "keypad";
      }
      if (["home", "dialer", "sos", "call-confirm"].includes(route) || dialerOptions[route]) {
        if (["no SIM", "no signal", "airplane mode", "signal unknown"].includes(variant)) state.network = variant;
        else if (variant === "standard") state.network = "available";
      }
      if (route === "settings" && variant === "process restarted") { state.intended = "settings"; lock(); navigate("pin"); state.notice = "Settings locked."; }
      if (route === "pin" && variant === "incorrect") state.error = state.accessMode === "date" ? "That isn't today's date. Use dd/MM/YYYY." : "Incorrect PIN.";
      if (route === "pin" && variant === "locked session") { lock(); state.notice = "Settings locked."; }
      if (route === "pin" && variant === "cooldown") state.cooldown = Date.now() + 30000;
      if (route === "pin-recovery" && variant === "cooldown") state.recoveryCooldown = Date.now() + 30000;
      if (route === "pin-recovery" && variant === "incorrect") state.error = "Recovery phrase did not match.";
      if (route === "privacy" && variant === "consent withdrawn") { state.analytics = false; state.crashes = false; delete state.formDrafts.privacy; state.notice = "Both turned off."; }
      if (route === "display" && variant === "huge text") draft.textSize = "Huge";
      if (route === "home" && variant === "active call") state.activeCall = true;
      if (route === "sos-active" && variant === "ended") state.activeCall = false;
      if (route === "compose" && variant === "invalid recipient") state.recipient = "not-a-number";
      if (route === "reset" && variant === "reset complete") reviewOnly("Reset preview does not reset session memory; final confirmation performs the local reset.");
      if (route === "setup" && variant === "complete") reviewOnly("Completion preview does not verify native service or change the handover checklist.");
      if (route === "sos-countdown" && variant === "standard") state.sosArmed = true;
      render();
      if (route === "sos-countdown") startCountdown();
    },
    unassigned: () => { state.notice = "Add a person in Settings."; render(); },
    "dial-tab": el => {
      clearHold();
      if (dialerOptions[route]) state.phoneTabs[route] = el.dataset.tab;
      else state.dialTab = el.dataset.tab;
      state.search = ""; render(); phone.scrollTop = 0;
    },
    "phone-contact": el => {
      const contact = state.contacts.find(c => c.id === el.dataset.id);
      if (!contact) { state.error = "Contact is unavailable."; render(); return; }
      if (contact.numbers.length === 1) callContact(contact, contact.numbers[0]);
      else { state.selected = contact.id; state.numberIndex = 0; navigate("contact-detail"); }
    },
    digit: el => { if (suppressClick?.element === el) { suppressClick = null; return; } state.digits = (state.digits + el.dataset.digit).slice(0, 24); state.dialIdentity = null; state.error = ""; updateDialerNumber(); },
    backspace: () => { state.digits = state.digits.slice(0, -1); state.dialIdentity = null; updateDialerNumber(); },
    dial: () => {
      if (!validNumber(state.digits)) { state.error = "Enter a valid number."; render(); return; }
      if (emergencyNumber(state.digits)) { actions.emergency(); return; }
      callContact(state.dialIdentity, state.digits);
    },
    "recent-call": el => {
      const c = state.contacts.find(p => p.id === el.dataset.id);
      state.preparedNumber = true; state.digits = c.numbers[0]; state.dialIdentity = c;
      if (route === "dialer") { state.dialTab = "keypad"; render(); }
      else navigate("dialer");
    },
    contact: el => { state.selected = el.dataset.id; state.numberIndex = 0; navigate("contact-detail"); },
    "contact-call": () => { const c = selectedContact(); if (c) callContact(c, c.numbers[state.numberIndex] || c.numbers[0]); },
    "contact-message": () => { const c = selectedContact(); if (c) { setTarget(c, c.numbers[state.numberIndex] || c.numbers[0]); state.recipient = state.target.number; navigate("compose"); } },
    "add-contact": () => beginContactEdit(true),
    "edit-contact": () => beginContactEdit(false),
    "delete-contact": () => {
      if (!protectedAction("contact-detail", "delete-contact")) return;
      state.confirm = "delete-contact"; render();
    },
    "cancel-contact": () => { state.contactMode = ""; state.contactDraft = null; delete state.formDrafts.contact; state.error = ""; render(); },
    "confirm-delete-contact": () => {
      if (route !== "contact-detail" || state.confirm !== "delete-contact") return;
      if (!state.unlocked) { navigate("pin"); return; }
      const c = selectedContact();
      if (!c || resolve(state.sos)?.id === c.id) { state.error = "Replace the SOS recipient before deleting this contact."; render(); return; }
      if (variant === "write denied" || variant === "save failure") { state.error = "Could not delete contact. Nothing changed."; render(); return; }
      state.contacts = state.contacts.filter(p => p.id !== c.id);
      state.keys = state.keys.map(v => v.startsWith(`${c.id}:`) ? "" : v);
      state.slots = state.slots.map(value => value?.startsWith(`contact:${c.id}:`) ? null : value);
      navigate("contacts"); state.notice = "Contact deleted. Favorites cleared."; render();
    },
    "cancel-confirm": () => { state.confirm = ""; state.error = ""; render(); },
    "place-call": () => placeCall(),
    speaker: () => { state.speaker = !state.speaker; render(); },
    mute: () => { state.muted = !state.muted; render(); },
    "hold-call": () => { state.held = !state.held; render(); },
    "call-keypad": () => { state.confirm = state.confirm === "call-keypad" ? "" : "call-keypad"; state.digits = ""; render(); },
    dtmf: el => { state.digits = (state.digits + el.dataset.digit).slice(-20); render(); },
    "answer-waiting": () => { setTarget(state.contacts.find(c => c.id === "daniel"), "202-555-0103"); variant = "connected"; state.held = false; state.notice = "Answered. First call on hold."; render(); },
    "decline-waiting": () => { variant = "connected"; state.notice = "Call declined."; render(); },
    "end-call": () => { const canceled = ["dialing", "ringing"].includes(variant); state.activeCall = false; state.held = false; navigate("call-ended"); if (canceled) { variant = "canceled"; render(); } },
    answer: () => { setIncomingTarget(); state.callTarget = { ...state.target }; state.activeCall = true; state.callState = "Connected"; navigate("calling"); },
    decline: () => {
      setIncomingTarget();
      state.activeCall = false; navigate("call-ended"); variant = "declined"; render();
    },
    "retry-call": () => {
      if (state.activeCall) { navigate("calling"); state.notice = "End your current call first."; render(); return; }
      const target = callOutcomeTarget();
      if (!validNumber(target.number)) { state.error = "No number available to call."; render(); return; }
      callContact(target, target.number);
    },
    "target-message": () => {
      const target = callOutcomeTarget();
      if (!validNumber(target.number)) { state.error = "No number available to message."; render(); return; }
      state.recipient = target.number; navigate("compose");
    },
    "open-missed-call": el => {
      const missed = state.missedCalls.find(call => call.id === Number(el.dataset.callId));
      if (!missed) { state.error = "Call is unavailable."; render(); return; }
      state.selectedMissedId = missed.id; navigate("missed-call");
    },
    "open-conversation": el => {
      const number = el.dataset.number;
      const contact = state.contacts.find(contact => contact.numbers.includes(number));
      setTarget(contact, number); state.conversationTarget = { ...state.target }; navigate("conversation");
    },
    "mms-thread": () => { state.target = { name: "Family group", number: "202-555-0101" }; state.conversationTarget = { ...state.target }; navigate("conversation"); variant = "MMS"; render(); },
    reply: () => navigate("reply"),
    "choose-recipient": () => { state.picker = "message"; navigate("contacts"); },
    "choose-sos": () => { state.picker = "sos"; navigate("contacts"); },
    "pick-contact": el => {
      const c = state.contacts.find(p => p.id === el.dataset.id); state.selected = c.id; state.numberIndex = 0;
      const index = Number(el.dataset.numberIndex) || 0;
      if (state.picker === "sos") {
        state.sosDraft = `${c.id}:${index}:${c.numbers[index]}`; state.picker = ""; delete state.formDrafts["sos-settings"]; navigate("sos-settings"); return;
      }
      state.newDraft = { recipient: c.numbers[index], body: state.body }; setTarget(c, c.numbers[index]); state.picker = ""; navigate("compose");
      state.notice = "Recipient selected."; render();
    },
    "cancel-picker": () => { if (route === "tile-picker") { navigate(state.pickerReturn); return; } const destination = state.picker === "sos" ? "sos-settings" : "compose"; state.picker = ""; navigate(destination); },
    "cancel-compose": () => navigate("messages"),
    "send-message": () => {
      if (!validNumber(state.recipient) || emergencyNumber(state.recipient) || !state.body.trim()) { navigate("compose"); state.error = "Choose a contact and enter a message."; render(); return; }
      setTarget(recipientContact(), state.recipient);
      if (variant === "send failure" || variant === "unknown outcome" || state.readiness.Messages.startsWith("Denied") || state.network !== "available") { const unknown = variant === "unknown outcome"; navigate("message-failed"); variant = unknown ? "unknown outcome" : "known failure"; render(); return; }
      state.conversationTarget = { ...state.target };
      state.messages.push({ number: state.recipient, body: state.body, time: new Date().toLocaleString(({ en: "en-GB", es: "es", he: "he" })[state.language]) });
      delete state.drafts[state.recipient]; state.body = ""; state.newDraft = null;
      navigate("conversation"); state.newDraft = null; state.notice = "Sent · delivery not confirmed."; render();
    },
    "retry-message": () => {
      if (variant === "unknown outcome" && state.confirm !== "retry-message") {
        state.confirm = "retry-message"; render(); return;
      }
      variant = "standard"; state.confirm = "";
      actions["send-message"]();
    },
    "open-camera": () => { state.external = "Camera"; render(); },
    capture: () => {
      if (variant === "capture failure" || variant === "unavailable") { state.error = "Picture not saved. Try again."; render(); return; }
      state.photos.push({ id: Date.now(), title: "New picture", owned: true }); navigate("gallery"); state.notice = "Picture saved."; render();
    },
    photo: el => { state.photoIndex = Number(el.dataset.index); navigate("photo"); },
    "previous-photo": () => { if (state.confirm || state.photoIndex <= 0) return; state.photoIndex--; render(); },
    "next-photo": () => { if (state.confirm || state.photoIndex >= state.photos.length - 1) return; state.photoIndex++; render(); },
    "remove-photo": () => { state.confirm = "remove-photo"; render(); },
    "confirm-remove-photo": () => {
      if (variant === "delete failure") { state.error = "Could not delete. Picture kept."; render(); return; }
      state.photos.splice(state.photoIndex, 1); state.photoIndex = Math.max(0, Math.min(state.photoIndex, state.photos.length - 1));
      navigate("gallery"); state.notice = "Picture removed."; render();
    },
    "pick-photo": () => {
      state.external = "Photos"; state.confirm = "picker"; render();
      phone.querySelector(".screen").insertAdjacentHTML("beforeend", button("Choose picture", "add-picker-photo", "primary"));
      if (classicMode) { mountClassicActions(classicActions); renderClassicDeck(); }
      else mountScreenActions();
    },
    "add-picker-photo": () => { state.photos.push({ id: Date.now(), title: "Landscape", owned: false }); state.external = ""; variant = "standard"; state.notice = "Picture added."; render(); },
    "finish-loading": () => { variant = "standard"; render(); },
    "start-sos": startSOS,
    "cancel-sos": () => { clearInterval(countdownTimer); variant = "canceled"; state.notice = "Countdown stopped. No help call started."; render(); },
    emergency: () => { state.external = "Emergency dialer"; render(); },
    native: () => { state.external = ["messages", "conversation", "message-failed", "compose"].includes(route) ? "Messages" : ["gallery", "photo"].includes(route) ? "Photos" : "Phone"; render(); },
    "external-back": () => {
      state.external = ""; state.confirm = ""; state.notice = "";
      if (route === "camera") { navigate(state.cameraReturn || "home"); return; }
      render();
    },
    app: el => {
      const name = el.dataset.app;
      if (!state.allowed.includes(name)) { state.error = "This app is not allowed. Ask a caregiver to review the app list."; render(); return; }
      if (variant === "missing app") { state.error = `${name} unavailable.`; render(); return; }
      state.external = name;
      render();
    },
    "manage-contacts": () => navigate("contacts"),
    "forgot-recovery": () => { variant = "forgotten secret"; state.error = ""; render(); },
    "save-display": () => { saveDisplay(); navigate("settings"); state.notice = "Saved."; render(); },
    "sos-test": () => { state.confirm = "sos-test"; render(); },
    "run-sos-test": startSOS,
    "permission-grant": el => { state.readiness[el.dataset.permission] = "Granted · not service-tested"; variant = "standard"; state.notice = "Allowed."; render(); },
    "permission-deny": el => { state.readiness[el.dataset.permission] = "Denied · repair needed"; variant = "standard"; state.notice = "Not allowed."; render(); },
    "permission-repair": el => { state.external = `${el.dataset.permission} settings`; render(); },
    "withdraw-consent": () => { state.analytics = false; state.crashes = false; delete state.formDrafts.privacy; state.notice = "Both turned off."; render(); },
    "reset-review": () => { state.confirm = "reset"; render(); },
    "confirm-reset": () => {
      if (state.activeCall) { state.error = "End your call first."; render(); return; }
      const { contacts, missedCalls, messages, photos, deletedThreads, unread } = state;
      state = { ...initial(), contacts, missedCalls, messages, photos, deletedThreads, unread, sos: "", sosConsent: false, keys: ["", ""] };
      draft = null; navigate("welcome");
    },
    "select-plan": el => { state.plan = el.dataset.plan; render(); },
    "review-purchase": () => {
      if (variant === "offline" || variant === "loading") { state.error = "Store unavailable. Try later or continue free."; render(); return; }
      navigate("purchase");
    },
    "mock-store": () => {
      if (variant === "offline" || variant === "error") { state.error = "Store unavailable. No purchase made."; render(); return; }
      reviewOnly("Google Play outcomes are simulated. No actual product, price, enrollment, payment or entitlement is verified.");
      state.external = "Google Play"; render();
    },
    "purchase-pending": () => { state.external = ""; variant = "pending"; state.notice = "Awaiting approval. No access granted yet."; render(); },
    "purchase-success": () => { state.membership = state.plan === "lifetime" ? "Lifetime demo" : "Trial demo · 7 days proposed"; reviewOnly("Mock purchase success selected. No verified purchase, real trial, renewal or payment exists."); navigate("membership"); state.notice = "Plan selected."; render(); },
    "purchase-cancel": () => { state.external = ""; variant = "canceled"; state.notice = "Canceled. No purchase made."; render(); },
    restore: () => {
      state.external = "Google Play";
      render();
      if (route !== "membership") {
        phone.querySelector(".screen").insertAdjacentHTML("beforeend", `${button("No purchases found", "restore-none")}${button("Restore lifetime", "restore-demo")}${button("Try later", "restore-failed")}`);
        if (classicMode) {
          const previous = classicActions;
          mountClassicActions(previous);
          renderClassicDeck();
        }
      }
    },
    "manage-membership": () => { state.external = "Google Play"; render(); },
    "cancel-membership": () => { state.membership = "Canceled — valid demo"; state.external = ""; state.notice = "Renewal canceled. Access lasts through your paid period."; render(); },
    "restore-none": () => { state.external = ""; state.notice = "No purchases found. Check your Google Play account."; render(); },
    "restore-demo": () => { state.membership = "Lifetime demo"; state.external = ""; state.notice = "Lifetime restored. No renewal."; reviewOnly("Mock lifetime restore only. No store account or entitlement is verified."); render(); },
    "restore-failed": () => { state.external = ""; state.error = "Restore unavailable. Try later. Calls stay free."; render(); }
  };
  const forms = {
    pin: data => {
      if (Date.now() < state.cooldown) { state.error = "Wait for the pause to finish, then try again."; render(); return; }
      const dateMode = state.accessMode === "date";
      const correct = dateMode ? data.get("date") === todayDate() : Boolean(state.pin) && data.get("pin") === state.pin;
      if (!correct) {
        state.attempts += 1; state.error = `${dateMode ? "That isn't today's date. Use dd/MM/YYYY." : "Incorrect PIN."} ${3 - state.attempts} attempt(s) before a 30-second pause.`;
        if (state.attempts >= 3) { state.cooldown = Date.now() + 30000; state.attempts = 0; state.error = "Three failed attempts. Wait 30 seconds."; }
        render(); return;
      }
      state.unlocked = true; state.configured = true; state.attempts = 0; state.cooldown = 0;
      const destination = state.intended || "settings", pending = state.pendingAction;
      state.pendingAction = ""; navigate(destination, true);
      if (pending === "edit-contact" || pending === "add-contact") beginContactEdit(pending === "add-contact");
      else if (pending === "delete-contact") { state.confirm = "delete-contact"; render(); }
    },
    recovery: data => {
      if (state.accessMode !== "pin") { navigate("pin"); state.notice = "No PIN to reset. Enter today's date."; render(); return; }
      if (Date.now() < state.recoveryCooldown) { state.error = "Recovery paused for 30 seconds after three incorrect phrases."; render(); return; }
      if (data.get("recovery") !== state.recovery) {
        state.recoveryAttempts += 1;
        if (state.recoveryAttempts >= 3) { state.recoveryCooldown = Date.now() + 30000; state.recoveryAttempts = 0; }
        state.error = "Recovery phrase did not match. No PIN changed."; render(); return;
      }
      const pin = data.get("newPin");
      if (!/^\d{6}$/.test(pin) || pin !== data.get("confirmPin")) { state.error = "Enter matching new 6-digit PINs. Nothing changed."; render(); return; }
      state.pin = pin; state.attempts = 0; state.cooldown = 0; state.recoveryAttempts = 0; state.recoveryCooldown = 0; lock();
      navigate("pin"); state.notice = "PIN reset. Use your new PIN."; render();
    },
    "setup-access": data => {
      if (state.configured && !state.unlocked && !state.onboarding) { state.intended = "access-settings"; navigate("pin"); return; }
      const accessMode = data.get("accessMode");
      if (!["date", "pin"].includes(accessMode)) { state.error = "Choose a date question or caregiver PIN."; render(); return; }
      let pin = "", recovery = "";
      if (accessMode === "pin") {
        pin = String(data.get("newPin") || ""); recovery = String(data.get("recovery") || "").trim();
        if (!/^\d{6}$/.test(pin) || pin !== data.get("confirmPin") || recovery.length < 12 || recovery === pin) { state.error = "Use matching 6-digit PINs and a separate recovery secret of at least 12 characters."; render(); return; }
      }
      state.accessMode = accessMode; state.pin = pin; state.recovery = recovery; state.configured = true; state.unlocked = true;
      state.attempts = 0; state.cooldown = 0; state.recoveryAttempts = 0; state.recoveryCooldown = 0;
      accessModeDraft = null; state.error = ""; state.notice = "Settings access saved.";
      if (state.onboarding) { state.setupStep = 6; navigate("setup"); }
      else navigate("settings");
      render();
    },
    contact: data => {
      if (!state.unlocked) { state.intended = "contact-detail"; navigate("pin"); return; }
      const name = String(data.get("name")).trim(), number = String(data.get("number")).trim(), other = String(data.get("other")).trim();
      state.contactDraft = { name, number, other };
      if (!name || !validNumber(number) || (other && !validNumber(other))) { state.error = "Enter a name and valid numbers. Nothing saved."; render(true); return; }
      if (variant === "save failure" || variant === "write denied" || state.readiness.Contacts.startsWith("Denied")) { state.error = "Could not save. Check Contacts access."; render(true); return; }
      const existing = state.contacts.find(c => c.id === state.editId);
      const nextNumbers = [number, ...(other ? [other] : [])];
      if (existing && [state.sos].some(v => {
        const [id, index] = String(v).split(":");
        return id === existing.id && existing.numbers[Number(index)] !== nextNumbers[Number(index)];
      })) {
        state.error = "Number used by SOS. Choose another SOS recipient before changing it."; render(true); return;
      }
      const contact = { id: existing?.id || `person${Date.now()}`, name, numbers: [number, ...(other ? [other] : []), ...(existing?.numbers.slice(2) || [])], color: existing?.color || "" };
      if (existing) state.contacts[state.contacts.indexOf(existing)] = contact; else state.contacts.push(contact);
      state.selected = contact.id; state.numberIndex = 0; state.contactMode = ""; state.contactDraft = null; delete state.formDrafts.contact; state.error = ""; state.notice = "Contact saved."; render();
    },
    compose: data => {
      state.recipient = route === "reply" ? state.target.number : String(data.get("recipient") || "").trim(); state.body = String(data.get("body"));
      if (!validNumber(state.recipient) || emergencyNumber(state.recipient) || !state.body.trim()) { state.error = "Choose a valid non-emergency recipient and enter a message. Draft retained."; render(true); return; }
      const c = state.contacts.find(c => c.numbers.includes(state.recipient)); setTarget(c, state.recipient);
      actions["send-message"]();
    },
    "sos-settings": data => {
      const manualNumber = String(data.get("manualNumber") || "").trim();
      const value = manualNumber ? "manual-sos" : data.get("sos"), agreed = data.has("consent");
      const recipient = manualNumber ? { number: manualNumber, name: String(data.get("manualName") || "").trim() || manualNumber } : resolve(value);
      if (!recipient || !validNumber(recipient.number) || emergencyNumber(recipient.number) || !agreed) { state.error = "Choose a valid non-emergency number and confirm the recipient's agreement. Nothing was saved."; render(); return; }
      if (manualNumber) state.sosManual = recipient;
      state.sos = value; state.sosDraft = ""; state.sosConsent = true; delete state.formDrafts["sos-settings"]; state.error = ""; state.notice = "Saved. Test with your contact."; variant = "standard"; render();
    },
    privacy: data => { state.analytics = data.has("analytics"); state.crashes = data.has("crashes"); delete state.formDrafts.privacy; state.notice = "Saved."; render(); }
  };
  function rememberForm(el) {
    const form = el.closest("form[data-form]");
    if (!form || !["contact", "speed-dial", "sos-settings", "privacy"].includes(form.dataset.form)) return;
    state.formDrafts[form.dataset.form] = Object.fromEntries([...form.elements].filter(input => input.name).map(input => [input.name, input.type === "checkbox" ? input.checked : input.value]));
  }
  document.addEventListener("click", event => {
    const anchor = event.target.closest('a[href^="#"]');
    if (anchor && (registry[anchor.hash.slice(1)] || legacyRoutes[anchor.hash.slice(1)])) { event.preventDefault(); navigate(anchor.hash.slice(1)); return; }
    const el = event.target.closest("[data-action]");
    if (!el || el.disabled) return;
    const action = actions[el.dataset.action];
    if (action) action(el);
  });
  phone.addEventListener("submit", event => {
    const form = event.target.closest("[data-form]"); if (!form) return;
    event.preventDefault();
    forms[form.dataset.form]?.(new FormData(form));
  });
  phone.addEventListener("input", event => {
    const el = event.target; rememberForm(el);
    switch (el.dataset.input) {
      case "contact-search": state.search = el.value; render(true); break;
      case "message-search": state.search = el.value; render(true); break;
      case "app-search": state.search = el.value; render(true); break;
      case "recipient": state.recipient = el.value; render(true); break;
      case "body": state.body = el.value; break;
      case "onboarding-contact-search": state.onboardingQuery = el.value; render(true); break;
    }
    if (classicMode) updateClassicNavigation();
  });
  phone.addEventListener("focusin", event => {
    if (!classicMode) return;
    if (classicCandidates().includes(event.target)) selectClassicItem(event.target);
    if (event.target.matches("input, textarea")) classicInput = event.target;
    multiTap = null; renderClassicDeck();
  });
  phone.addEventListener("change", event => {
    const el = event.target; rememberForm(el);
    switch (el.dataset.change) {
      case "access-mode": accessModeDraft = el.value; state.error = ""; render(); break;
      case "contact-number": state.numberIndex = Number(el.value); break;
      case "allow-app": {
        const value = el.value;
        state.allowed = el.checked ? [...new Set([...state.allowed, value])] : state.allowed.filter(a => a !== value);
        if (!el.checked) state.menuApps = state.menuApps.filter(a => a !== value);
        render(true); break;
      }
      case "menu-app": {
        const value = el.value;
        state.menuApps = el.checked ? [...new Set([...state.menuApps, value])] : state.menuApps.filter(a => a !== value);
        if (el.checked && !state.menuOrder.includes(`app:${value}`)) state.menuOrder.push(`app:${value}`);
        render(true); break;
      }
      case "home-visibility": state.error = ""; setHomeVisibility(el.value, el.checked); render(true); break;
      case "onboarding-sos-consent": state.sosConsent = el.checked; render(true); break;
      case "haptics": if (draft) draft.haptics = el.checked; else state.haptics = el.checked; break;
      case "easy-audio": state.easyAudio = el.checked; if (!el.checked) state.maximumVolume = false; render(); break;
      case "maximum-volume": if (el.checked) state.confirm = "maximum-volume"; else state.maximumVolume = false; render(); break;
      case "handover": state.handover = el.checked; render(); break;
    }
  });
  device.addEventListener("pointerdown", event => {
    if (classicMode && event.target.closest(".classic-deck")) event.preventDefault();
    const key = event.target.closest('[data-action="classic-digit"]:not(:disabled)');
    if (!key || event.button !== 0) return;
    const entersPlus = key.dataset.digit === "*" && classicCanEnterPlus();
    if (!entersPlus && (key.dataset.digit !== "#" || !classicTarget())) return;
    clearHold(); suppressClick = null; holdButton = key; holdStart = { x: event.clientX, y: event.clientY, id: event.pointerId };
    try { key.setPointerCapture(event.pointerId); } catch (_) { /* Capture may be unavailable for an interrupted pointer. */ }
    key.classList.add("holding"); key.setAttribute("aria-description", "Hold in progress. Release or move to cancel.");
    holdTimer = setTimeout(() => {
      const input = classicTarget();
      if (holdButton !== key || (entersPlus ? !classicCanEnterPlus() : !input)) return;
      suppressClick = { element: key }; clearHold();
      if (entersPlus) {
        classicDialDigit("+");
      } else if (classicIsNumeric(input)) {
        input.inputMode = "tel"; input.focus(); classicFeedback("Keyboard entry enabled.");
      } else {
        classicModeIndex = classicModeIndex === 1 ? 0 : 1;
        multiTap = null;
        renderClassicDeck();
      }
    }, 800);
  });
  device.addEventListener("pointermove", event => {
    if (holdButton && holdStart && Math.hypot(event.clientX - holdStart.x, event.clientY - holdStart.y) > 10) { suppressClick = { element: holdButton }; clearHold(); }
  });
  device.addEventListener("pointerup", clearHold);
  device.addEventListener("pointercancel", () => { if (holdButton) suppressClick = { element: holdButton }; clearHold(); });
  device.addEventListener("lostpointercapture", clearHold);
  phone.addEventListener("scroll", () => { if (holdButton) suppressClick = { element: holdButton }; clearHold(); }, { passive: true });
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && phone.querySelector(".contact-delete-dialog[open]")) return;
    if (event.key === "Escape") { clearHold(); if ($("clean-preview") && !document.body.classList.contains("classic-embed")) $("clean-preview").checked = false; if (state.external || state.confirm || state.contactMode) back(); }
  });
  $("phone-back").addEventListener("click", back);
  $("phone-home").addEventListener("click", () => {
    if (state.onboarding && !state.setupReentry) return;
    state.onboarding = false; navigate("home");
  });
  $("previous-screen").addEventListener("click", () => navigate(definitions[(registry[route].index - 1 + definitions.length) % definitions.length][0]));
  $("next-screen").addEventListener("click", () => navigate(definitions[(registry[route].index + 1) % definitions.length][0]));
  window.addEventListener("popstate", () => activate(location.hash.slice(1), true));
  window.addEventListener("hashchange", () => { const id = location.hash.slice(1); if (id !== route) activate(id, true); });
  document.addEventListener("visibilitychange", () => {
    clearHold();
    if (document.hidden && route === "sos-countdown") { clearInterval(countdownTimer); variant = "interrupted"; render(); }
  });
  window.addEventListener("pagehide", cleanup);
  {
    classicDeck.innerHTML = `<div id="classic-controls" class="classic-context-keys" role="group" aria-label="Fixed phone controls">
        ${button("Confirm", "classic-confirm", "classic-context-key", 'id="classic-confirm"')}
        ${button(esc(t("back")), "classic-back", "classic-context-key", 'id="classic-back"')}
        ${button("", "classic-call", "classic-context-key primary", 'id="classic-call"')}
        ${button("", "classic-end", "classic-context-key classic-end", 'id="classic-end"')}
        <div class="classic-dpad" role="group" aria-label="Up, OK and Down navigation">
          ${["up", "down"].map(direction => button(icon(`arrow-${direction}`), "classic-nav", "classic-nav-key", `id="classic-nav-${direction}" data-direction="${direction}" aria-label="Navigate ${direction}" aria-controls="phone-content"`)).join("")}
          ${button("OK", "classic-ok", "classic-nav-key", 'id="classic-ok" aria-label="Open or select highlighted item" aria-controls="phone-content"')}
        </div>
      </div>
      <div class="classic-mode-status"><output id="classic-feedback" role="status" aria-live="polite"></output><span id="classic-input-mode" hidden></span></div>
      <div class="classic-number-keys" dir="ltr" role="group" aria-label="Number keypad">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map(digit => button(`<strong>${digit}</strong><small></small>`, "classic-digit", "classic-number", `data-digit="${digit}" aria-label="${digit}"`)).join("")}</div>`;
    classicScrollObserver = new ResizeObserver(updateClassicNavigation);
    phone.addEventListener("scroll", updateClassicNavigation, { passive: true });
    document.addEventListener("selectionchange", () => {
      if (phone.contains(document.activeElement)) updateClassicNavigation();
    });
  }
  const entry = location.hash.slice(1);
  const initialRoute = legacyRoutes[entry] || entry;
  history.replaceState({ claro: true, parent: null }, "", registry[initialRoute] ? `#${initialRoute}` : "#home");
  activate(registry[initialRoute] ? initialRoute : "home");
})();
