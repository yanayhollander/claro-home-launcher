/* Claro Home: local, in-memory product storyboard. No device or network APIs. */
(() => {
  "use strict";
  const $ = id => document.getElementById(id);
  const phone = $("phone-content");
  const actionDock = $("phone-actions");
  const previewParams = new URLSearchParams(location.search);
  const classicMode = previewParams.get("version") === "classic";
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
  if (classicMode) {
    document.body.dataset.classicLayout = classicLayout;
    $("classic-layout-controls").hidden = false;
    $("classic-layout").innerHTML = Object.entries(classicLayouts).map(([value, label]) => `<option value="${value}">${label}</option>`).join("");
    $("classic-layout").value = classicLayout;
    $("classic-layout").addEventListener("change", event => {
      const url = new URL(location.href);
      url.searchParams.set("layout", event.target.value);
      location.assign(url.href);
    });
    if (previewParams.get("embedded") === "1") {
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
    ["dialer", "Everyday use", "A number. A familiar voice.", "A readable keypad with two deliberately direct shortcuts.", "PHONE-01/02 · CALL-03", "Tap digits, or hold 1 or 2 for 0.8 seconds. Short taps only type."],
    ["contacts", "Everyday use", "The people who matter.", "Searchable names, clear numbers and familiar initials.", "CONTACT-01", "Search Mira or a number. Open a person to call or write."],
    ["contact-detail", "Everyday use", "One person, clear choices.", "Choose the number before calling or writing.", "CONTACT-02 · SETTINGS-02", "Try the second number, or open protected contact editing."],
    ["call-confirm", "Phone & messages", "A moment to be sure.", "The right person and number, using the phone’s default calling account.", "PHONE-01 · CALL-03", "Review the number, then start a simulated call."],
    ["calling", "Phone & messages", "Keep the conversation simple.", "Large, labeled controls remain within reach.", "CALL-01/02", "Try speaker, mute and Home. An active demo call stays accessible."],
    ["incoming", "Phone & messages", "A familiar voice is calling.", "Answer and decline without learning a swipe.", "CALL-01", "Answer the fictional incoming call, or review an unknown caller."],
    ["call-ended", "Phone & messages", "An obvious next step.", "Review the connected call duration, then return home, call again or write a message. The sample duration matches the active call preview.", "CALL-02", "Call back or send a follow-up; no real phone is contacted."],
    ["missed-call", "Phone & messages", "See who called.", "A missed incoming call is not a failed outgoing call.", "CALL-02", "Call back or write a message. Private callers have no callback number."],
    ["call-unanswered", "Phone & messages", "They did not answer.", "An unanswered outgoing call has no connected duration.", "CALL-02", "Try calling again, write a message or return Home."],
    ["call-failed", "Phone & messages", "An honest, useful explanation.", "No service is different from no internet.", "CALL-02/03", "Change the failure reason and try the simulated native handoff."],
    ["messages", "Phone & messages", "Messages without the clutter.", "Big names, readable previews and visible unread messages.", "SMS-01/04", "Read Mira’s message, or write a new one."],
    ["conversation", "Phone & messages", "Room for every word.", "A simple conversation with honest delivery status.", "SMS-01/03/04", "Reply, review the recipient and send a demo message."],
    ["compose", "Phone & messages", "Say it in your own words.", "A large writing area and an explicit recipient.", "SMS-02", "Choose a contact or type a demo number; text stays here when you go back."],
    ["message-confirm", "Phone & messages", "Read it once before sending.", "No accidental send and no hidden recipient.", "SMS-02/03", "Edit the draft or confirm a simulated send."],
    ["message-failed", "Phone & messages", "Your words are still here.", "Recover without losing the draft or hiding duplicate risk.", "SMS-03", "Review the retained text before explicitly retrying."],
    ["camera", "Photos & apps", "Let the camera do its job.", "A clearly labeled boundary with the Android camera.", "MEDIA-01", "Open the mock external camera, take a demo picture and return."],
    ["gallery", "Photos & apps", "Little moments, easy to find.", "Local illustration placeholders, not real photographs.", "MEDIA-02", "View a landscape or add a simulated picker reference."],
    ["photo", "Photos & apps", "One memory at a time.", "Clear navigation and a deliberate remove or delete decision.", "MEDIA-02/03", "Move through the demo illustrations and review removal confirmation."],
    ["sos", "Help & safety", "Help from someone you trust.", "A family help call is not an emergency dispatch service.", "SOS-01/02", "Start a five-second demo countdown, then cancel it."],
    ["sos-countdown", "Help & safety", "Time to change your mind.", "Five seconds, a clear stop and no hidden side effects.", "SOS-02", "Cancel before zero. Leaving this screen also cancels the countdown."],
    ["sos-active", "Help & safety", "Calling your trusted person.", "Call progress never means someone has received your request.", "SOS-03", "Review connected or unanswered states. No alert is automatically delivered."],
    ["sos-failed", "Help & safety", "Help must not be a promise.", "Useful recovery without claiming anyone has been notified.", "SOS-03", "Try again or open the simulated system emergency screen."],
    ["apps", "Photos & apps", "Only the apps you chose.", "A calm, searchable allowlist with explicit external boundaries.", "APPS-01/02", "Search an allowed app and explore its mock handoff."],
    ["pin", "Caregiver", "A small pause for settings.", "Choose a date question or caregiver PIN during setup.", "PIN-01/02", "The date question uses today's local date in dd/MM/YYYY format. PIN mode uses the credentials you created in Setup."],
    ["settings", "Caregiver", "Thoughtfully set up. Easily kept.", "One protected place for the caregiver’s choices.", "SETTINGS-01/02/03", "Change display, favorites, home layout or readiness; explicitly lock when done."],
    ["home-edit", "Caregiver", "Make home feel like home.", "The six core tiles always stay. Extra apps are optional.", "SETTINGS-01 · CORE-02", "Reorder an extra app, add another, then Save or Cancel."],
    ["speed-dial", "Caregiver", "Familiar people, carefully assigned.", "Four visible favorites and independently assigned keys 1 and 2.", "SETTINGS-02 · PHONE-02", "Assign a person and exact number to each slot. Initials are placeholders."],
    ["display", "Caregiver", "Comfort comes first.", "Bigger type may scroll. Essential actions never disappear.", "A11Y-01/02 · SETTINGS-03", "Preview larger text and contrast, then save or revert."],
    ["language", "Caregiver", "A familiar language.", "Representative English, Spanish and Hebrew previews.", "I18N-01", "Choose Hebrew, save, then return Home to see RTL and LTR numbers."],
    ["sos-settings", "Caregiver", "Agree on help before it is needed.", "Choose a trusted person and explain the limits together.", "SOS-01/04 · SETTINGS-02", "Choose a recipient, confirm their agreement, save and run a simulated test."],
    ["permissions", "Caregiver", "Ready means checked.", "Role grants and successful service are different things.", "SETUP-02/03/04", "Simulate a denial and repair. No OS permission is actually requested."],
    ["privacy", "Caregiver", "Private by design, not by promise.", "Two optional diagnostics choices, both off by default.", "PRIV-01/02 · DATA-01", "Toggle analytics or crash diagnostics. This demo contains no SDK or upload."],
    ["reset", "Caregiver", "A fresh start, not a surprise.", "Reset only this in-memory demo configuration.", "DATA-02", "Review exactly what changes. Cancel is always available."],
    ["welcome", "Getting started", "A little more independence.", "A simpler home, phone and messages, with a caregiver nearby.", "SETUP-01 · BILL-01", "Begin the checklist or use the free demo immediately."],
    ["setup", "Getting started", "A careful first handover.", "A resumable checklist, with missing steps shown honestly.", "SETUP-01/04", "Choose a date question or caregiver PIN, then follow favorites, safety and roles."],
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
  if (classicMode) registry["classic-menu"] = { ...registry.home, id: "classic-menu", title: "A familiar phone menu.", subtitle: "All core features remain available above the fixed keypad.", try: "Choose a feature. Contacts, Back, Menu and the number keys stay in the same place." };
  const protectedRoutes = new Set(["settings", "home-edit", "speed-dial", "display", "language", "sos-settings", "permissions", "privacy", "reset", "plans", "purchase", "membership", "trial-ended"]);
  const initial = () => ({
    contacts: [
      { id: "mira", name: "Mira", numbers: ["202-555-0101", "202-555-0102"], color: "rose" },
      { id: "daniel", name: "Daniel", numbers: ["202-555-0103"], color: "blue" },
      { id: "leah", name: "Leah", numbers: ["202-555-0104"], color: "gold" },
      { id: "noah", name: "Noah", numbers: ["202-555-0105"], color: "" }
    ],
    favorites: ["mira:0", "daniel:0", "leah:0", "noah:0"], keys: ["mira:0", "daniel:0"],
    sos: "mira:0", sosConsent: true, extra: ["Weather", "Calendar", "Calculator", "Music"],
    allowed: ["Weather", "Calendar", "Calculator", "Music", "Maps"], language: "en", large: false, contrast: false,
    analytics: false, crashes: false, accessMode: "date", pin: "", recovery: "", configured: false,
    readiness: { Home: "Not checked", Phone: "Not checked", Messages: "Not checked", Contacts: "Not checked", Notifications: "Not checked", Media: "Not checked" },
    handover: false, digits: "", recipient: "202-555-0101", body: "", selected: "mira", numberIndex: 0,
    target: { name: "Mira", number: "202-555-0101" }, messages: [], unread: true,
    missedCalls: [{ id: 1, name: "Mira", number: "202-555-0101", when: "Today · 09:20", unread: true }], selectedMissedId: 1,
    activeCall: false, callState: "Connected", muted: false, speaker: false, held: false, dialTab: "keypad", phoneTabs: {}, network: "available", sosArmed: false, classicTones: "",
    membership: "Free", plan: "monthly", photos: [{ id: 1, title: "Morning hills", owned: false }, { id: 2, title: "A quiet afternoon", owned: false }, { id: 3, title: "Garden light", owned: true }, { id: 4, title: "Weekend walk", owned: false }],
    photoIndex: 0, search: "", appFilter: "all", error: "", notice: "", attempts: 0, cooldown: 0, recoveryAttempts: 0, recoveryCooldown: 0,
    unlocked: false, intended: "settings", contactMode: "", editId: "", picker: "", external: "", confirm: "", setupVisits: {}, formDrafts: {}, preferenceDrafts: {}
  });
  let state = initial(), route = "", variant = "standard", suppressClick = null;
  let countdownTimer, holdTimer, holdButton, holdStart, inactivityTimer, countdown = 5, draft = null, accessModeDraft = null;
  let classicInput = null, classicNumeric = false, multiTap = null;
  const variants = {
    home: ["standard", "missed", "unread messages", "notifications cleared", "no SIM", "no signal", "airplane mode", "signal unknown", "unassigned", "active call", "huge text"],
    dialer: ["standard", "empty recents", "unassigned", "permission denied", "no signal"],
    ...Object.fromEntries(Object.keys(dialerOptions).map(id => [id, ["standard", "empty contacts", "empty recents", "unassigned", "permission denied", "no signal"]])),
    contacts: ["standard", "empty", "permission denied"],
    "contact-detail": ["standard", "save failure", "write denied"],
    "call-confirm": ["standard", "no SIM"],
    calling: ["connected", "dialing", "ringing", "on hold", "Bluetooth", "call waiting", "no answer"],
    incoming: ["known", "unknown", "private", "missed"],
    "call-ended": ["ended", "missed", "declined", "canceled"],
    "missed-call": ["standard", "unknown", "private"],
    "call-unanswered": ["no answer"],
    "call-failed": ["no signal", "no SIM", "invalid number", "account unavailable", "permission denied", "unknown failure"],
    messages: ["standard", "empty", "permission denied", "MMS / group"],
    conversation: ["standard", "sending", "sent", "delivery unknown", "delivery confirmed", "MMS", "download error"],
    compose: ["standard", "invalid recipient", "offline"],
    "message-confirm": ["standard", "pending", "send failure", "unknown outcome"],
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
  if (classicMode) variants["classic-menu"] = ["standard"];
  const copy = {
    en: { home: "Home", back: "Back", phone: "Phone", messages: "Messages", camera: "Camera", gallery: "Gallery", sos: "SOS", apps: "Apps", people: "Your people", contacts: "Contacts", call: "Call", recent: "Recent", missed: "Missed", keypad: "Keypad", search: "Search name or number", newMessage: "Write a message", date: "Wednesday, 11 November", more: "More apps below", settings: "Settings", callPerson: "Call", type: "Enter a number", write: "Message", battery: "Battery", signal: "Mobile signal", ready: "Available", add: "Add person" },
    es: { home: "Inicio", back: "Atrás", phone: "Teléfono", messages: "Mensajes", camera: "Cámara", gallery: "Galería", sos: "SOS", apps: "Aplicaciones", people: "Tus personas", contacts: "Contactos", call: "Llamar", recent: "Recientes", missed: "Perdidas", keypad: "Teclado", search: "Buscar nombre o número", newMessage: "Escribir un mensaje", date: "Miércoles, 11 de noviembre", more: "Más aplicaciones abajo", settings: "Ajustes", callPerson: "Llamar", type: "Introduce un número", write: "Mensaje", battery: "Batería", signal: "Señal móvil", ready: "Disponible", add: "Añadir persona" },
    he: { home: "בית", back: "חזרה", phone: "טלפון", messages: "הודעות", camera: "מצלמה", gallery: "גלריה", sos: "עזרה", apps: "יישומים", people: "האנשים שלך", contacts: "אנשי קשר", call: "התקשר", recent: "אחרונות", missed: "שלא נענו", keypad: "לוח מקשים", search: "חיפוש שם או מספר", newMessage: "כתיבת הודעה", date: "יום רביעי, 11 בנובמבר", more: "יישומים נוספים למטה", settings: "הגדרות", callPerson: "התקשר", type: "הזנת מספר", write: "הודעה", battery: "סוללה", signal: "קליטה סלולרית", ready: "זמין", add: "הוספת איש קשר" }
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
    const [id, index] = (value || "").split(":");
    const contact = state.contacts.find(c => c.id === id);
    const number = contact?.numbers[Number(index) || 0];
    return contact && number ? { ...contact, number } : null;
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
  const contactOptions = (value, includeEmpty = true) => `${includeEmpty ? '<option value="">Not assigned</option>' : ""}${state.contacts.map(c => c.numbers.map((n, i) => `<option value="${c.id}:${i}" ${value === `${c.id}:${i}` ? "selected" : ""}>${esc(c.name)} · \u2066${esc(n)}\u2069${i ? " · home" : " · mobile"}</option>`).join("")).join("")}`;
  const appOptions = () => ["Weather", "Calendar", "Calculator", "Music", "Maps"].map(a => `<option>${a}</option>`).join("");
  function clearHold() {
    clearTimeout(holdTimer);
    if (holdButton) { holdButton.classList.remove("holding"); holdButton.removeAttribute("aria-description"); }
    holdButton = null;
  }
  function cleanup() { clearHold(); clearInterval(countdownTimer); countdownTimer = undefined; }
  function touchSession() {
    if (!state.unlocked) return;
    clearTimeout(inactivityTimer);
    inactivityTimer = setTimeout(() => {
      state.unlocked = false;
      accessModeDraft = null;
      if (protectedRoutes.has(route) || state.contactMode || route === "setup") { state.intended = route; state.contactMode = ""; navigate("pin"); state.notice = "Settings locked."; render(); }
    }, 120000);
  }
  function lock() { state.unlocked = false; accessModeDraft = null; clearTimeout(inactivityTimer); }
  function navigate(id, replace = false) {
    if (!registry[id]) id = "home";
    if (protectedRoutes.has(id) && !state.unlocked) { state.intended = id; state.pendingAction = ""; id = "pin"; }
    if (id === "home") { lock(); state.pendingAction = ""; }
    const hash = `#${id}`;
    if (location.hash === hash) { activate(id); return; }
    if (replace) history.replaceState(history.state || { claro: true, parent: null }, "", hash);
    else history.pushState({ claro: true, parent: route || null }, "", hash);
    activate(id);
  }
  function activate(id, fromHistory = false) {
    cleanup();
    if (!registry[id]) { id = "home"; history.replaceState(history.state, "", "#home"); }
    if (classicMode && dialerOptions[id]) { id = "dialer"; history.replaceState(history.state, "", "#dialer"); }
    if (id === "pin-recovery" && state.accessMode === "date") { id = "pin"; history.replaceState(history.state, "", "#pin"); }
    if (protectedRoutes.has(id) && !state.unlocked) { state.intended = id; state.pendingAction = ""; id = "pin"; history.replaceState(history.state, "", "#pin"); }
    if (id === "home") lock();
    if (route !== id) {
      classicInput = null; multiTap = null;
      if (route === "setup") accessModeDraft = null;
      if (draft && ["home-edit", "display", "language"].includes(route)) state.preferenceDrafts[route] = draft;
      if (route === "contact-detail") state.contactMode = "";
      variant = (variants[id] || ["standard"])[0]; draft = null; state.search = ""; state.external = ""; state.confirm = ""; state.error = ""; state.notice = "";
    }
    route = id;
    if (["call-ended", "call-failed", "call-unanswered", "sos-failed"].includes(id)) { state.activeCall = false; state.held = false; }
    if (id === "missed-call") {
      const missed = selectedMissedCall();
      if (!state.readiness.Phone.startsWith("Denied")) missed.unread = false;
    }
    if (["calling", "sos-active"].includes(id)) state.activeCall = true;
    if (id === "home-edit") draft = draft || state.preferenceDrafts[id] || { extra: [...state.extra], allowed: [...state.allowed] };
    if (id === "display") draft = draft || state.preferenceDrafts[id] || { large: state.large, contrast: state.contrast };
    if (id === "language") draft = draft || state.preferenceDrafts[id] || { language: state.language };
    state.setupVisits[id] = true;
    render();
    phone.scrollTop = 0; phone.focus({ preventScroll: true }); touchSession();
    if (route === "sos-countdown") startCountdown();
  }
  function back() {
    if (classicMode && classicOptionsOpen) { closeClassicOptions(); return; }
    if (state.confirm || state.external || state.contactMode) { state.confirm = ""; state.external = ""; state.contactMode = ""; state.error = ""; render(); return; }
    if (dialerOptions[route] && state.phoneTabs[route] && state.phoneTabs[route] !== dialerOptions[route].initialTab) {
      state.phoneTabs[route] = dialerOptions[route].initialTab; state.search = ""; render(); return;
    }
    const safeParent = { calling: "dialer", "call-confirm": "dialer", "call-ended": "dialer", "call-failed": "dialer", conversation: "messages", compose: "messages", "message-confirm": "compose", "message-failed": "compose", "contact-detail": "contacts", photo: "gallery", "sos-countdown": "sos", "sos-active": "sos", "sos-failed": "sos", "pin-recovery": "pin", purchase: "plans" };
    const previous = history.state?.claro && history.state.parent;
    if (["missed-call", "call-unanswered"].includes(route) && (!previous || ["calling", "incoming"].includes(previous))) { navigate("dialer", true); return; }
    if (previous && previous !== "sos-countdown") { cleanup(); history.back(); }
    else navigate(safeParent[route] || "home", true);
  }
  const screenActions = {
    home: [],
    dialer: [".dial-actions"],
    ...Object.fromEntries(Object.keys(dialerOptions).map(id => [id, ["dial", "emergency"]])),
    contacts: ["add-contact", "cancel-picker"],
    "contact-detail": ["edit-contact", "delete-contact", "contact-call", "contact-message"],
    "call-confirm": ["place-call", "back"],
    calling: ["end-call"],
    incoming: ["answer", "decline"],
    "call-ended": ["go:home", "retry-call", "target-message"],
    "missed-call": ["retry-call", "target-message", "go:home"],
    "call-unanswered": ["retry-call", "target-message", "go:home"],
    "call-failed": ["retry-call", "native"],
    messages: ["go:compose"],
    conversation: ["reply", "go:messages"],
    compose: ["submit", "cancel-compose"],
    "message-confirm": ["send-message", "go:compose", "go:messages"],
    "message-failed": ["retry-message", "go:messages"],
    camera: ["open-camera", "go:gallery"],
    gallery: ["finish-loading", "pick-photo", "go:camera"],
    photo: ["remove-photo", "go:gallery"],
    sos: ["go:sos-settings", "start-sos", "emergency"],
    "sos-countdown": ["cancel-sos", "start-sos", "go:sos"],
    "sos-active": ["end-sos", "emergency"],
    "sos-failed": ["go:home", "start-sos", "emergency"],
    apps: ["go:home-edit"],
    pin: ["submit", "go:pin-recovery", "go:home"],
    settings: ["manage-contacts", "lock"],
    "home-edit": ["save-home", "go:settings"],
    "speed-dial": ["submit", "go:settings"],
    display: ["save-display", "go:settings"],
    language: ["submit", "go:settings"],
    "sos-settings": ["submit", "go:settings"],
    permissions: ["go:setup"],
    privacy: ["submit", "go:settings"],
    reset: ["reset-review", "go:settings"],
    welcome: ["go:setup", "go:home"],
    setup: ["submit", "go:home"],
    plans: ["review-purchase", "go:home"],
    purchase: ["mock-store", "go:plans"],
    membership: ["go:plans", "go:home"],
    "trial-ended": ["go:home", "go:plans"],
    "pin-recovery": ["submit", "forgot-recovery", "go:pin"]
  };
  if (classicMode) screenActions["classic-menu"] = [];
  function mountScreenActions() {
    const device = phone.parentElement;
    let keys = screenActions[route];
    if (state.external) {
      keys = route === "camera" ? ["capture", "external-back"] :
        route === "purchase" && state.external === "Google Play" ? ["purchase-pending", "purchase-success", "purchase-cancel"] :
        state.confirm === "picker" ? ["add-picker-photo", "external-back"] : ["external-back"];
    } else if (route === "contact-detail" && state.contactMode) {
      keys = ["submit", "cancel-contact"];
    } else if (route === "contact-detail" && state.confirm === "delete-contact") {
      keys = ["cancel-confirm", "confirm-delete-contact", "go:sos-settings"];
    } else if (route === "photo" && state.confirm === "remove-photo") {
      keys = ["cancel-confirm", "confirm-remove-photo"];
    } else if (route === "sos-settings" && state.confirm === "sos-test") {
      keys = ["run-sos-test", "cancel-confirm"];
    } else if (route === "reset" && state.confirm === "reset") {
      keys = ["cancel-confirm", "confirm-reset"];
    } else if (route === "reset" && (state.activeCall || variant === "active call")) {
      keys = ["go:calling", "go:settings"];
    } else if (route === "setup" && !phone.querySelector('button[type="submit"]')) {
      keys = ["go:plans", "go:home"];
    }
    let controls = keys.map(key => {
      const selector = key === "submit" ? 'button[type="submit"]' :
        key.startsWith(".") ? key :
        key.startsWith("go:") ? `button[data-action="go"][data-route="${key.slice(3)}"]` :
        `button[data-action="${key}"]`;
      return (classicMode ? phone : device).querySelector(selector);
    }).filter(Boolean);
    if (!controls.length && route !== "home") {
      controls = [...phone.querySelectorAll(".screen > button.primary, .screen > button.danger")];
    }
    if (classicMode) {
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
    const selectable = ".row-button, .speed-dial, .plan, .photo-grid button, .classic-home-links button, .tab-row button";
    const buttons = [...phone.querySelectorAll("button")].filter(control => !control.matches(selectable));
    const ordered = [...new Set([...preferred.flatMap(control => control.matches("button") ? [control] : [...control.querySelectorAll("button")]), ...buttons])];
    const confirming = ["delete-contact", "remove-photo", "sos-test", "reset"].includes(state.confirm) || Boolean(state.contactMode);
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
      !["end-call", "end-sos", "decline", "call-keypad"].includes(control.dataset.action) &&
      !(control.dataset.action === "go" && control.dataset.route === "home" && control.textContent.trim() === t("home")));
    if (state.external) {
      classicActions = classicActions.filter(control => [
        "external-back", "capture", "add-picker-photo", "purchase-pending",
        "purchase-success", "purchase-cancel", "cancel-membership",
        "restore-none", "restore-demo", "restore-failed"
      ].includes(control.dataset.action));
    }
    if (!state.external && ["dialer", "call-confirm"].includes(route)) {
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
    const meta = registry[route];
    document.title = `Claro Home${classicMode ? " 2G" : ""} — ${route === "home" ? "Interactive storyboard" : route}`;
    $("section-name").textContent = meta.group;
    $("screen-title").textContent = meta.title; $("screen-subtitle").textContent = meta.subtitle;
    $("screen-count").textContent = `${String(meta.index + 1).padStart(2, "0")} / ${definitions.length}`;
    $("note-title").textContent = meta.title;
    $("note-description").textContent = meta.subtitle;
    $("try-instruction").textContent = meta.try;
    $("journey-name").textContent = `${meta.group} · ${route} · ${variant}`;
    $("prototype-status").textContent = "Local storyboard only · session changes reset on reload · no native implementation, telemetry or real actions.";
    const boundaries = ["camera", "apps", "permissions", "purchase"].includes(route) ? "Android / Play boundary: the external panel is an illustration, not a launched app or real system prompt." : "Every call, message, emergency action and membership result is simulated; no network or device action occurs.";
    const detailNote = route === "pin" || route === "pin-recovery" || route === "setup" ? "Date mode is an accidental-change barrier, not a security or cognitive assessment: anyone who knows today's local date can pass. It needs no PIN or recovery phrase. PIN mode requires credentials created in Setup; the date question cannot recover or bypass that PIN. Changing modes requires the current unlock method. Three failed attempts pause input for 30 seconds; inactivity locks after two minutes." : route === "home" || route === "speed-dial" ? "Original illustrated portraits stand in for contact photos. Four people stay visible. Additional apps scroll; the six core tiles and free features cannot be removed." : route === "plans" || route === "purchase" || route === "membership" ? "No prices are approved. Price to be confirmed. Seven days free only through an eligible explicit store enrollment; lifetime is separate and never renews." : "Large accessible targets and in-page outcomes. Native accessibility, provider behavior and OS lifecycle still require approved device validation.";
    $("screen-notes").innerHTML = [[meta.refs, "Product requirement references for this screen; review named states below."], ["Honest boundaries", boundaries], ["Review note", classicMode ? "Separate 2G concept: four outer keys and one connected navigation pad. Back stays left; Confirm stays right. The right softkey opens Options when no primary action exists; otherwise an Options navigation row gives access to secondary actions, including text deletion. Call and Home retain their positions within each layout. Compare four arrangements using the sidebar link. Four arrows highlight items/fields; OK selects. Left/right edit the cursor or dropdown value and browse photos; up/down move between items or scroll. Touch remains available. Hold 1/2 for speed dial only from Home/Phone, never while entering text or during SOS. Text fields support English ABC multi-tap; # switches letters/numbers, 0 inserts a space. Native third-party/system screens cannot retain Claro's keypad. This is not T9 prediction or fully localized text input." : detailNote], ["Implementation context", "All data and outcomes are in-memory simulations. Portraits and gallery landscapes are original illustrations, not contact photos. HOME, DIALER and SMS roles must be requested separately; contact/media access must be scoped. Native Telecom, lock-screen, accessibility, lifecycle, permission recovery, carrier delivery and device handover require validation. Optional analytics/crash collection needs separate consent, no sensitive values may be logged, and billing needs its own privacy disclosure. Only core navigation is translated; secondary screens remain English draft. Prices and products are unapproved; a real checkout must disclose currency, full charge, first billing date, trial eligibility, renewal, cancellation deadline and Play management before consent."], ...(reviewFeedback ? [["Latest review action", reviewFeedback]] : [])].map((n, i) => `<div class="note"><span class="note-number">${i + 1}</span><div><h3>${esc(n[0])}</h3><p>${esc(n[1])}</p></div></div>`).join("");
    $("state-controls").innerHTML = `<p>Preview states only. They do not reflect a real device.</p>${(variants[route] || ["standard"]).map(v => button(esc(v), "variant", "", `data-variant="${esc(v)}" aria-pressed="${v === variant}"`)).join("")}${button("Reset this screen preview", "variant", "", `data-variant="${esc(variants[route][0])}"`)}`;
    $("screen-nav").innerHTML = definitions.map((d, i) => {
      const group = i === 0 || d[1] !== definitions[i - 1][1] ? `<div class="nav-group">${d[1]}</div>` : "";
      const link = `<a class="screen-link ${route === d[0] ? "active" : ""}" href="#${d[0]}" ${route === d[0] ? 'aria-current="page"' : ""}><span class="nav-index">${String(i + 1).padStart(2, "0")}</span>${esc(d[0].split("-").map(s => s[0].toUpperCase() + s.slice(1)).join(" "))}</a>`;
      const options = d[0] === "dialer" && !classicMode ? `<div class="dialer-options" role="group" aria-label="Dialer design options"><span class="dialer-options-label">Options</span>${Object.entries(dialerOptions).map(([id, option]) => `<a class="screen-link dialer-option ${route === id ? "active" : ""}" href="#${id}" ${route === id ? 'aria-current="page"' : ""}>${esc(option.label)}</a>`).join("")}</div>` : "";
      return group + (options ? `<div class="dialer-nav-item">${link}${options}</div>` : link);
    }).join("");
    const isRTL = state.language === "he";
    const device = document.querySelector(".phone");
    device.classList.toggle("large-text", (draft?.large ?? state.large) || variant === "huge text");
    device.classList.toggle("high-contrast", draft?.contrast ?? state.contrast);
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
    if (classicMode) {
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
  const noticeExternal = () => state.external ? `<div class="external-panel" role="region" aria-label="${esc(state.external)}">${heading(state.external)}${button("Back to Claro", "external-back", "primary")}</div>` : "";
  function recentCallRows(missedOnly = false) {
    return state.missedCalls.map(missed => {
      const contact = state.contacts.find(c => c.numbers.includes(missed.number));
      return row(missed.name, `${missed.unread ? "● " : ""}${t("missed")} · ${missed.when}`, "open-missed-call", `data-call-id="${missed.id}"`, avatar(contact));
    }).join("") +
      (missedOnly ? "" : state.contacts.slice(1, 3).map((c, i) => row(c.name, `${t("recent")} · 9:${21 + i}`, "recent-call", `data-id="${c.id}"`, avatar(c))).join(""));
  }
  function homeNotifications() {
    const counts = notificationCounts();
    const alerts = [
      { count: counts.missed, label: t("missed"), description: "missed calls", symbol: "phone", destination: counts.missed === null ? "dialer" : "missed-call" },
      { count: counts.messages, label: t("messages"), description: "unread messages", symbol: "messages", destination: "messages" }
    ].filter(alert => alert.count === null || alert.count > 0);
    if (!alerts.length) return "";
    return `<div class="home-notifications" role="group" aria-label="Notifications">${alerts.map(alert =>
      button(`${icon(alert.symbol)}<span class="alert-label">${esc(alert.label)}</span><strong class="notification-count" aria-hidden="true">${alert.count ?? "?"}</strong>`, "go", `row-button home-notification ${alert.symbol}-notification`,
        `data-route="${alert.destination}" aria-label="${esc(alert.count === null ? `${alert.description}: access needed, count unknown` : `${alert.count} ${alert.description}`)}"`)
    ).join("")}</div>`;
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
    return `<div class="classic-home-header"><div class="classic-clock"><time dir="ltr">9:41</time><p>${esc(new Date().toLocaleDateString(({ en: "en-GB", es: "es", he: "he" })[state.language], { weekday: "short", day: "numeric", month: "short" }))}</p></div>${homeNotifications()}</div>
      <div class="speed-dials">${state.favorites.map((value, i) => { const contact = variant === "unassigned" && i === 3 ? null : resolve(value); return button(`${avatar(contact)}<span>${esc(contact?.name || "+")}</span>`, contact ? "favorite" : "unassigned", "speed-dial", `data-slot="${i}" aria-label="${esc(contact ? `Call ${contact.name}` : t("add"))}"`); }).join("")}</div>
      <div class="classic-home-links">${goButton(`${icon("messages")} ${esc(t("messages"))}`, "messages")}${goButton(`${icon("sos")} SOS`, "sos", "danger")}</div>`;
  }
  function classicDialer() {
    if (variant === "permission denied" || state.readiness.Phone.startsWith("Denied")) return heading(t("phone")) + permissionWarning("Phone") + emergency();
    return heading(t("phone")) + `<div class="dial-display" dir="ltr" aria-live="polite">${esc(state.digits || t("type"))}</div>` +
      goButton(`${icon("contact")} ${esc(t("contacts"))}`, "contacts") +
      recentCallRows() + emergency();
  }
  function classicTarget() {
    if (classicOptionsOpen) return null;
    if (classicSelected?.isConnected && classicSelected.matches('select, input[type="checkbox"], input[type="radio"]')) return null;
    const editable = input => input && phone.contains(input) && !input.disabled && !input.readOnly && (input.matches('textarea, input[type="text"], input[type="tel"], input[type="password"], input[type="search"]'));
    if (editable(document.activeElement)) return document.activeElement;
    if (editable(classicSelected)) return classicSelected;
    if (editable(classicInput)) return classicInput;
    if (route === "compose") return phone.querySelector('[name="body"]');
    return [...phone.querySelectorAll("input, textarea")].find(editable) || null;
  }
  function classicIsNumeric(input) {
    return input && (input.type === "tel" || ["date", "pin", "newPin", "confirmPin"].includes(input.name));
  }
  function classicFeedback(text) { $("classic-feedback").textContent = text; }
  function classicCandidates() {
    return [...phone.querySelectorAll("button, input, select, textarea")].filter(el =>
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
    el.classList.add("classic-selected");
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
    selectClassicItem(candidates.find(el => classicCandidateKey(el) === classicSelectionKey) || candidates[0] || null);
  }
  function classicNeighbor(direction) {
    const candidates = classicCandidates();
    if (!candidates.length) return null;
    if (!candidates.includes(classicSelected)) return candidates[0];
    const horizontal = ["left", "right"].includes(direction);
    const sign = ["up", "left"].includes(direction) ? -1 : 1;
    const bounds = classicSelected.getBoundingClientRect();
    const x = bounds.left + bounds.width / 2, y = bounds.top + bounds.height / 2;
    const neighbors = candidates.filter(el => el !== classicSelected).map(el => {
      const rect = el.getBoundingClientRect();
      const dx = rect.left + rect.width / 2 - x, dy = rect.top + rect.height / 2 - y;
      return { el, major: (horizontal ? dx : dy) * sign, minor: Math.abs(horizontal ? dy : dx), sameRow: Math.min(rect.bottom, bounds.bottom) - Math.max(rect.top, bounds.top) > 8 };
    }).filter(item => item.major > 8 && (!horizontal || item.sameRow)).sort((a, b) => a.major + a.minor * 3 - b.major - b.minor * 3);
    if (neighbors.length) return neighbors[0].el;
    if (horizontal && candidates.some(el => {
      if (el === classicSelected) return false;
      const rect = el.getBoundingClientRect();
      return Math.min(rect.bottom, bounds.bottom) - Math.max(rect.top, bounds.top) > 8;
    })) return null;
    return horizontal ? candidates[candidates.indexOf(classicSelected) + sign] || null : null;
  }
  function updateClassicNavigation() {
    const selected = classicSelected?.isConnected ? classicSelected : null;
    for (const direction of ["up", "down", "left", "right"]) {
      const horizontal = ["left", "right"].includes(direction);
      let available;
      if (horizontal && selected?.matches('input:not([type="checkbox"]):not([type="radio"]), textarea')) {
        available = direction === "left" ? selected.selectionEnd > 0 : selected.selectionStart < selected.value.length;
      } else if (horizontal && selected?.matches("select")) {
        available = direction === "left" ? selected.selectedIndex > 0 : selected.selectedIndex < selected.options.length - 1;
      } else if (horizontal && route === "photo" && !classicOptionsOpen) {
        available = state.photos.length > 1;
      } else {
        available = Boolean(classicNeighbor(direction)) || (!horizontal && (direction === "up" ? phone.scrollTop > 1 : phone.scrollHeight - phone.clientHeight - phone.scrollTop > 1));
      }
      $(`classic-nav-${direction}`).disabled = !available;
    }
    $("classic-ok").disabled = !selected;
  }
  function classicNavigate(el) {
    const direction = el.dataset.direction;
    const horizontal = ["left", "right"].includes(direction);
    const sign = ["up", "left"].includes(direction) ? -1 : 1;
    const selected = classicSelected;
    multiTap = null;
    if (horizontal && selected?.matches('input:not([type="checkbox"]):not([type="radio"]), textarea')) {
      const start = selected.selectionStart ?? 0, end = selected.selectionEnd ?? start;
      const cursor = start !== end ? sign < 0 ? start : end : Math.max(0, Math.min(selected.value.length, start + sign));
      selected.focus({ preventScroll: true }); selected.setSelectionRange(cursor, cursor);
    } else if (horizontal && selected?.matches("select")) {
      const index = selected.selectedIndex + sign;
      if (index >= 0 && index < selected.options.length && !selected.options[index].disabled) {
        selected.selectedIndex = index;
        selected.dispatchEvent(new Event("change", { bubbles: true }));
      }
    } else if (horizontal && route === "photo" && !classicOptionsOpen) {
      actions[sign < 0 ? "previous-photo" : "next-photo"]();
    } else {
      const neighbor = classicNeighbor(direction);
      if (neighbor) selectClassicItem(neighbor, true);
      else if (!horizontal) phone.scrollBy({ top: sign * Math.max(72, Math.floor(phone.clientHeight * 0.7)), behavior: "auto" });
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
    return eligible.find(control => ["confirm-delete-contact", "confirm-remove-photo", "confirm-reset", "cancel-sos", "start-sos", "reset-review"].includes(control.dataset.action)) ||
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
    call.innerHTML = `${icon("phone")}<span>${route === "incoming" ? "Answer" : "Call"}</span>`;
    call.disabled = Boolean(state.external) || classicOptionsOpen || Boolean(state.contactMode) || route === "sos-countdown" ||
      (!state.activeCall && ["missed-call", "call-unanswered"].includes(route) && !validNumber(callOutcomeTarget().number)) ||
      (!state.activeCall && route === "missed-call" && state.readiness.Phone.startsWith("Denied")) ||
      (state.activeCall && ["calling", "sos-active"].includes(route)) || (Boolean(input) && !["dialer", "contacts"].includes(route));
    end.innerHTML = `${icon("end")}<span>${esc(route === "incoming" ? "Decline" : state.activeCall ? "End" : t("home"))}</span>`;
    $("classic-back").textContent = t("back");
    classicOptionActions = classicActions.filter(control => control !== confirmAction && !["classic-call", "contact-call", "place-call", "answer", "retry-call"].includes(control.dataset.action));
    if (!state.external && route !== "incoming" && route !== "sos-countdown" && !(state.activeCall && ["calling", "sos-active"].includes(route)) && (input || (route === "dialer" && state.digits))) {
      const remove = document.createElement("button");
      remove.type = "button"; remove.dataset.action = "classic-delete"; remove.textContent = "Delete character";
      remove.disabled = input ? !input.value : !state.digits;
      classicOptionActions.unshift(remove);
      const clear = document.createElement("button");
      clear.type = "button"; clear.dataset.action = "classic-clear"; clear.textContent = "Clear text"; clear.disabled = remove.disabled;
      classicOptionActions.push(clear);
    }
    if (!state.external && !["delete-contact", "remove-photo", "sos-test", "reset"].includes(state.confirm)) {
      for (const [label, destination] of [[t("contacts"), "contacts"], ["Menu", "classic-menu"]]) {
        if (route === destination || classicOptionActions.some(control => control.dataset.route === destination || (destination === "contacts" && control.dataset.action === "manage-contacts"))) continue;
        const control = document.createElement("button");
        control.type = "button"; control.dataset.action = "go"; control.dataset.route = destination; control.textContent = label;
        classicOptionActions.push(control);
      }
    }
    confirm.textContent = classicOptionsOpen ? "Select" : confirmAction ? confirmAction.textContent.trim() : route === "home" ? "Menu" : "Options";
    confirm.disabled = classicOptionsOpen ? !classicSelected : confirmAction ? confirmAction.disabled : classicOptionActions.length === 0;
    confirm.classList.toggle("primary", !classicOptionsOpen && Boolean(confirmAction) && !confirmAction.classList.contains("danger"));
    confirm.classList.toggle("danger", !classicOptionsOpen && Boolean(confirmAction?.classList.contains("danger")));
    confirm.classList.toggle("classic-long-key", confirm.textContent.trim().length > 12);
    const screen = phone.querySelector(".screen");
    const shortcut = screen.querySelector(".classic-options-shortcut");
    if (confirmAction && classicOptionActions.length) {
      if (!shortcut) {
        screen.insertAdjacentHTML("beforeend", row("Options", "", "classic-options", 'aria-label="More options"'));
        screen.lastElementChild.classList.add("classic-options-shortcut");
      }
    } else shortcut?.remove();
  }
  function renderClassicDeck() {
    classicDeck.hidden = false;
    syncClassicSelection();
    const blocked = Boolean(state.external) || classicOptionsOpen || route === "incoming" || route === "sos-countdown";
    const selectionOnly = classicSelected?.matches('select, input[type="checkbox"], input[type="radio"]');
    classicDeck.querySelectorAll('[data-action="classic-digit"]').forEach(key => { key.disabled = blocked || Boolean(selectionOnly); });
    const input = classicTarget();
    renderClassicSoftkeys();
    syncClassicSelection();
    const mode = $("classic-input-mode");
    mode.hidden = !input || classicIsNumeric(input);
    mode.textContent = mode.hidden ? "" : classicNumeric ? "Numbers" : "Letters";
    classicDeck.querySelectorAll("[data-speed-label]").forEach(label => {
      const contact = resolve(state.keys[Number(label.dataset.speedLabel)]);
      label.textContent = ["home", "dialer"].includes(route) && !input ? contact?.name || "" : label.dataset.speedLabel === "0" ? ".,?!" : "ABC";
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
      if (digit === "#" && !classicIsNumeric(input)) { classicNumeric = !classicNumeric; multiTap = null; renderClassicDeck(); return; }
      if (["date", "pin", "newPin", "confirmPin"].includes(input.name) && !/^\d$/.test(digit)) { classicFeedback("Use number keys."); return; }
      const alphabet = { "1": ".,?!1", "2": "abc2", "3": "def3", "4": "ghi4", "5": "jkl5", "6": "mno6", "7": "pqrs7", "8": "tuv8", "9": "wxyz9", "0": " 0", "*": ".,?!*" };
      const letters = classicIsNumeric(input) || classicNumeric ? digit : alphabet[digit];
      const replace = letters.length > 1 && multiTap?.route === route && multiTap.id === input.id && multiTap.digit === digit && Date.now() - multiTap.time < 1000 && input.selectionStart === multiTap.end && input.selectionEnd === multiTap.end && input.value.slice(multiTap.start, multiTap.end) === multiTap.text;
      const index = replace ? (multiTap.index + 1) % letters.length : 0;
      const start = replace ? multiTap.start : input.selectionStart ?? input.value.length;
      const text = letters[index];
      if (classicEdit(input, text, replace)) multiTap = { route, id: input.id, digit, index, start, end: start + text.length, text, time: Date.now() };
      return;
    }
    if (state.activeCall) { classicFeedback("Return to the call for tones."); return; }
    multiTap = null;
    if (route !== "dialer") { state.digits = digit; navigate("dialer"); }
    else { state.digits = (state.digits + digit).slice(0, 24); state.error = ""; render(); }
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
    if (route === "incoming") { actions.answer(); return; }
    if (state.activeCall) { navigate(route === "sos-active" ? "sos-active" : "calling"); return; }
    if (route === "call-confirm") { actions["place-call"](); return; }
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
    if (route === "incoming") { actions.decline(); return; }
    if (state.activeCall) { actions[route === "sos-active" ? "end-sos" : "end-call"](); return; }
    state.digits = ""; classicInput = null; multiTap = null; navigate("home");
  }
  function classicSpeedDialAllowed() {
    return classicMode && !classicOptionsOpen && !classicTarget() && !state.activeCall && !state.external && ["home", "dialer"].includes(route);
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
    const keys = variant === "unassigned" ? ["", ""] : state.keys;
    return header +
      `<div class="dial-display-row"><div class="dial-display" dir="ltr" aria-live="polite">${esc(state.digits || t("type"))}</div>${button(icon("backspace"), "backspace", "secondary", `aria-label="Delete last digit" ${state.digits ? "" : "disabled"}`)}</div>
      <div class="dialpad" dir="ltr">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map((n, i) => {
        const contact = n === "1" || n === "2" ? resolve(keys[Number(n) - 1]) : null;
        const label = contact?.name || ["", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""][i];
        return button(`${n}<small>${esc(label)}</small>`, "digit", "dial-key", `data-digit="${n}" aria-label="${n}${contact ? `. Hold to call ${esc(contact.name)}` : ""}"`);
      }).join("")}</div>` + button(`${icon("phone")} ${esc(t("call"))}`, "dial", "primary", state.digits ? "" : "disabled") + emergency();
  }
  const screens = {
    "classic-menu": () => heading("Menu") + [
      [t("contacts"), "contacts", "contact"], [t("phone"), "dialer", "phone"], [t("messages"), "messages", "messages"],
      ["Missed calls", "missed-call", "phone"],
      [t("camera"), "camera", "camera"], [t("gallery"), "gallery", "gallery"], ["SOS", "sos", "sos"], [t("apps"), "apps", "apps"], [t("settings"), "settings", "settings"]
    ].map(([label, destination, symbol]) => row(label, "", "go", `data-route="${destination}"`, icon(symbol))).join(""),
    home: () => {
      const noSignal = state.network !== "available";
      const counts = notificationCounts();
      const dates = { en: ["WED 11", "NOV 2026"], es: ["MIÉ 11", "NOV 2026"], he: ["יום ד׳ 11", "נוב׳ 2026"] };
      return `<header class="home-dashboard-header"><div class="status-instruments"><div class="battery-meter" role="img" aria-label="${esc(t("battery"))} 82 percent"><span class="battery-fill" style="width:82%"></span></div><div class="cell-signal ${noSignal ? "unavailable" : ""}" role="img" aria-label="${esc(t("signal"))}: ${esc(noSignal ? state.network : t("ready"))}">${Array.from({ length: 5 }, () => "<i></i>").join("")}${noSignal ? '<span aria-hidden="true">×</span>' : ""}</div>${noSignal ? `<span class="network-unavailable">${esc(({ "no SIM": "No SIM", "no signal": "No signal", "airplane mode": "Airplane mode", "signal unknown": "Signal unknown" })[state.network] || state.network)}</span>` : ""}</div><div class="home-date-time"><div><strong class="header-day">${dates[state.language][0]}</strong><span class="header-month">${dates[state.language][1]}</span></div><time class="header-time" dir="ltr">9:41</time>${button(icon("settings"), "go", "gear-button", `data-route="settings" aria-label="${esc(t("settings"))}"`)}</div></header>
        ${(state.activeCall || variant === "active call") ? goButton(`${icon("phone")} ${esc(t("call"))}`, "calling", "primary compact") : ""}
        <div class="speed-dials">${state.favorites.map((v, i) => { const c = variant === "unassigned" && i === 3 ? null : resolve(v); return button(`${avatar(c)}<span>${esc(c?.name.split(/\s+/)[0] || "+")}</span>`, c ? "favorite" : "unassigned", "speed-dial", `data-slot="${i}" aria-label="${esc(c ? `${t("callPerson")} ${c.name}` : t("add"))}"`); }).join("")}</div>
        <div class="tile-grid core-tiles">${["phone", "messages", "camera", "gallery", "sos", "apps"].map((name, i) => {
          const count = name === "phone" ? counts.missed : name === "messages" ? counts.messages : 0;
          const hasBadge = count === null || count > 0;
          const countLabel = count === null ? "Access needed, count unknown" : `${count} ${name === "phone" ? "missed calls" : "unread messages"}`;
          return button(`${name === "sos" ? '<span class="sos-word" aria-hidden="true">SOS</span>' : icon(name)}${hasBadge ? `<span class="tile-badge" aria-hidden="true">${count ?? "?"}</span>` : ""}`, "go", `home-tile ${name}-tile`, `data-route="${i === 0 ? counts.missed > 0 ? "missed-call" : "dialer" : name}" aria-label="${esc(t(name))}${hasBadge ? `, ${esc(countLabel)}` : ""}" title="${esc(t(name))}"`);
        }).join("")}</div>
        <p class="scroll-cue" role="img" aria-label="${esc(t("more"))}"><span aria-hidden="true">⌄</span></p><div class="tile-grid extra-tiles">${state.extra.map(a => button(`${icon("apps")}<span>${esc(a)}</span>`, "app", "home-tile", `data-app="${esc(a)}"`)).join("")}</div>${noticeExternal()}`;
    },
    dialer: () => {
      if (variant === "permission denied" || state.readiness.Phone.startsWith("Denied")) return heading(t("phone")) + permissionWarning("Phone") + emergency();
      const tab = state.dialTab;
      const tabs = `<div class="tab-row" role="group" aria-label="Phone views">${["keypad", "recent", "missed"].map(v => button(esc(t(v)), "dial-tab", v === tab ? "selected" : "", `data-tab="${v}" aria-pressed="${v === tab}"`)).join("")}</div>`;
      const keys = variant === "unassigned" ? ["", ""] : state.keys;
      if (tab !== "keypad") return heading(t("phone")) + tabs + (variant === "empty recents" ? info("No calls yet.") : recentCallRows(tab === "missed")) + emergency();
      return heading(t("phone")) + tabs + `<div class="dial-display" dir="ltr" aria-live="polite">${esc(state.digits || t("type"))}</div><div class="hold-hints">${keys.map((v, i) => { const c = resolve(v); return button(`<bdi>${i + 1}</bdi> · ${esc(c?.name || "—")}`, "key-call", "secondary compact", `data-key="${i}" aria-label="${esc(t("call"))} ${esc(c?.name || "Not assigned")}"`); }).join("")}</div>
        <div class="dialpad" dir="ltr">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map((n, i) => button(`${n}<small>${["", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""][i]}</small>`, "digit", "dial-key", `data-digit="${n}" aria-label="${n}${n === "1" || n === "2" ? ". Hold for speed dial" : ""}"`)).join("")}</div>
        <div class="dial-actions">${button(icon("phone"), "dial", "primary", `aria-label="${esc(t("call"))}"`)}${button(icon("backspace"), "backspace", "secondary", 'aria-label="Delete last digit"')}</div>${goButton(`${icon("contact")} ${esc(t("contacts"))}`, "contacts")}${emergency()}`;
    },
    ...Object.fromEntries(Object.keys(dialerOptions).map(id => [id, dialerPreview])),
    contacts: () => {
      if (variant === "permission denied" || state.readiness.Contacts.startsWith("Denied")) return heading(t("contacts")) + permissionWarning("Contacts");
      const list = variant === "empty" ? [] : state.contacts.filter(c => `${c.name} ${c.numbers.join(" ")}`.toLowerCase().includes(state.search.toLowerCase()));
      return heading(t("contacts")) + field(t("search"), "contactSearch", state.search, "search", 'autocomplete="off" data-input="contact-search"') +
        list.map(c => state.picker ? c.numbers.map((n, i) => row(c.name, n, "pick-contact", `data-id="${c.id}" data-number-index="${i}"`, avatar(c))).join("") : row(c.name, "", "contact", `data-id="${c.id}"`, avatar(c))).join("") + (!list.length ? info(state.search ? "No matches." : "No contacts yet.") : "") +
        button(`${icon("contact")} ${esc(t("add"))}`, "add-contact") + (state.picker ? button("Cancel", "cancel-picker") : "");
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
      return heading(c.name) + avatar(c, true) + `<label><span class="field-label">Choose number</span><select data-change="contact-number" dir="ltr">${c.numbers.map((n, i) => `<option value="${i}" ${state.numberIndex === i ? "selected" : ""}>${esc(n)} · ${i ? "home" : "mobile"}</option>`).join("")}</select></label>` +
        button(`${icon("phone")} Call ${esc(c.name)}`, "contact-call", "primary") + button("Write a message", "contact-message") +
        button("Edit", "edit-contact") + button("Delete", "delete-contact") +
        (state.confirm === "delete-contact" ? info(`Delete ${esc(c.name)}? Favorites will be cleared. ${resolve(state.sos)?.id === c.id ? "<strong>Choose another SOS contact first.</strong>" : ""}`, "warning") + button("Cancel", "cancel-confirm") + (resolve(state.sos)?.id !== c.id ? button("Delete", "confirm-delete-contact", "danger") : goButton("Change SOS contact", "sos-settings")) : "");
    },
    "call-confirm": () => heading("Call?") + avatar({ name: state.target.name }, true) + `<h3 class="call-name">${esc(state.target.name)}</h3><p dir="ltr">${esc(state.target.number)}</p>` +
      (variant === "no SIM" ? info("No SIM. Calling unavailable.", "warning") : "") +
      (classicMode ? "" : button(`${icon("phone")} Call`, "place-call", "primary") + button("Cancel", "back")),
    calling: () => heading("Call") + avatar({ name: state.target.name }, true) + `<h3 class="call-name">${esc(state.target.name)}</h3><p dir="ltr">${esc(state.target.number)}</p><p class="call-status">${esc(variant === "connected" ? (state.held ? "On hold" : state.callState) : variant)}${["dialing", "ringing"].includes(variant) ? "" : ` · <bdi>${sampleCallDuration}</bdi>`}</p>` +
      `<div class="call-controls">${button(`${icon("speaker")} Speaker`, "speaker", "secondary", `aria-pressed="${state.speaker}"`)}${button(`${icon("microphone")} Mute`, "mute", "secondary", `aria-pressed="${state.muted}"`)}${button(state.held ? "Resume" : "Hold", "hold-call", "secondary", `aria-pressed="${state.held}"`)}${button(`${icon("apps")} Keypad`, "call-keypad")}</div>` +
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
    "call-failed": () => heading("Call not connected") + info(`${esc(variant)}. Check mobile service.`, "error") + `<p>${esc(state.target.name)} · <bdi>${esc(state.target.number)}</bdi></p>` + button("Try again", "retry-call", "primary") + button("Open Phone", "native") + goButton("Settings", "permissions") + emergency() + noticeExternal(),
    messages: () => {
      if (variant === "permission denied" || state.readiness.Messages.startsWith("Denied")) return heading(t("messages")) + permissionWarning("Messages") + noticeExternal();
      return heading(t("messages")) + field(t("search"), "messageSearch", state.search, "search", 'data-input="message-search"') + goButton(esc(t("newMessage")), "compose", "primary") +
        (variant === "empty" ? info("No messages yet.") : state.contacts.filter(c => c.name.toLowerCase().includes(state.search.toLowerCase())).slice(0, 3).map((c, i) => row(c.name, i === 0 ? `${state.unread ? "● " : ""}I’ll come by after lunch.` : i === 1 ? "See you on Sunday." : "Thank you!", "open-conversation", `data-id="${c.id}"`, avatar(c))).join("") || info("No matching messages.")) +
        (variant === "MMS / group" ? row("Family", "3 people · attachment", "mms-thread", "", icon("messages")) : "");
    },
    conversation: () => state.readiness.Messages.startsWith("Denied") ? heading(t("messages")) + permissionWarning("Messages") : heading(state.target.name) + `<span class="message-time">Today</span><div class="bubble">I’ll come by after lunch.</div><div class="bubble outgoing">That sounds lovely.</div>${state.messages.filter(m => m.number === state.target.number).map(m => `<div class="bubble outgoing">${esc(m.body)}</div><span class="message-time">Sent · delivery not confirmed</span>`).join("")}` +
      (["MMS", "download error"].includes(variant) ? info(variant === "MMS" ? "Attachment · 3 people" : "Download failed.", variant === "download error" ? "error" : "") + button("Open attachment", "native") : "") +
      (["sending", "sent", "delivery unknown", "delivery confirmed"].includes(variant) ? info(variant === "delivery confirmed" ? "Delivered" : variant === "sent" ? "Sent · delivery not confirmed" : esc(variant)) : "") +
      button("Write a reply", "reply", "primary") + goButton("All messages", "messages") + noticeExternal(),
    compose: () => heading("Write a message") + `<form data-form="compose" class="stack">${field("To", "recipient", state.recipient, "tel", 'data-input="recipient" dir="ltr" required maxlength="24"')}${button(`${icon("contact")} Contacts`, "choose-recipient")}<label><span class="field-label">Message</span><textarea id="field-body" name="body" data-input="body" required maxlength="2000" placeholder="Write here…">${esc(state.body)}</textarea></label><small id="message-length">${messageLength()}</small>${variant === "offline" ? info("No internet. SMS needs mobile service.", "warning") : ""}<button class="primary" type="submit">Review</button>${button("Cancel · keep draft", "cancel-compose")}</form>`,
    "message-confirm": () => heading("Send message?") + `<p><strong>${esc(state.target.name)}</strong><br><bdi>${esc(state.recipient)}</bdi></p><div class="bubble outgoing">${esc(state.body || "Your draft is empty.")}</div>` +
      (variant === "pending" ? info("Sending… Please wait.", "warning") : "") +
      button(variant === "pending" ? "Check status" : "Send", "send-message", "primary") + goButton("Edit", "compose") + goButton("Cancel · keep draft", "messages"),
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
    camera: () => heading("Camera") +
      (variant !== "standard" ? info("No picture saved.", "warning") : "") +
      (state.external ? `${noticeExternal()}${button(`${icon("camera")} Take picture`, "capture", "primary")}` : button(`${icon("camera")} Open camera`, "open-camera", "primary")) + goButton("Gallery", "gallery"),
    gallery: () => {
      if (variant === "permission denied" || state.readiness.Media.startsWith("Denied")) return heading("Gallery") + permissionWarning("Media") + noticeExternal();
      if (variant === "loading") return heading("Gallery") + info("Loading…") + button("Refresh", "finish-loading");
      return heading("Gallery") +
        (variant === "cloud unavailable" ? info("Connect to view cloud photos.", "warning") : "") +
        (variant === "empty" || !state.photos.length ? info("No pictures yet.") : `<div class="photo-grid">${state.photos.map((p, i) => button(`<span>${esc(p.title)}</span>`, "photo", "photo", `data-index="${i}" aria-label="View ${esc(p.title)}"`)).join("")}</div>`) +
        button("Add picture", "pick-photo") + goButton(`${icon("camera")} Camera`, "camera") + noticeExternal();
    },
    photo: () => {
      if (state.readiness.Media.startsWith("Denied")) return heading("Photo") + permissionWarning("Media");
      const p = state.photos[state.photoIndex] || state.photos[0];
      if (!p) return heading("No picture selected") + goButton("Gallery", "gallery");
      return heading(p.title) + `<div class="photo photo-view" role="img" aria-label="${esc(p.title)}"><span>${esc(p.title)}</span></div><div class="button-row">${button("←", "previous-photo", "secondary", 'aria-label="Previous picture"')}${button("→", "next-photo", "secondary", 'aria-label="Next picture"')}</div>` +
        button(p.owned ? "Delete" : "Remove", "remove-photo") +
        (state.confirm === "remove-photo" ? info(p.owned ? "Delete this picture?" : "Remove from Claro? The original stays on your device.", "warning") + button("Cancel", "cancel-confirm") + button(p.owned ? "Delete" : "Remove", "confirm-remove-photo", "danger") : "") + goButton("Gallery", "gallery");
    },
    sos: () => {
      const recipient = variant === "unconfigured" ? null : resolve(state.sos);
      const blocked = !recipient || !state.sosConsent || ["no signal", "no SIM"].includes(variant) || state.network !== "available" || state.readiness.Phone.startsWith("Denied");
      return heading("Call for help") + '<div class="sos-symbol" aria-hidden="true">SOS</div>' +
        info(recipient ? `${avatar(recipient)}<strong>${esc(recipient.name)}</strong><br><bdi>${esc(recipient.number)}</bdi>` : "Choose a help contact in Settings.", blocked ? "warning" : "") +
        info("Not an emergency service. Calls your contact only. No message or location sent.", "warning") +
        (blocked ? info(["no signal", "no SIM"].includes(variant) || state.network !== "available" ? `${esc(state.network !== "available" ? state.network : variant)}. Cannot call.` : state.readiness.Phone.startsWith("Denied") ? "Phone access needed." : "Choose a contact who has agreed.", "error") : "") +
        button("Call in 5 seconds", "start-sos", "danger", blocked ? 'disabled aria-disabled="true"' : "") + emergency() + goButton("Settings", "sos-settings") + noticeExternal();
    },
    "sos-countdown": () => heading("Calling in…") + `<div class="countdown" id="countdown-value" role="timer" aria-live="polite">${countdown}</div><p>${esc(resolve(state.sos)?.name || "No contact")}</p>` +
      (variant === "canceled" || variant === "interrupted" ? info("Canceled. No call started.", "success") + button("Try again", "start-sos", "danger") + goButton("SOS", "sos") : button("STOP", "cancel-sos", "danger", 'aria-label="Stop help call"')) +
      '<small>Back or Home cancels the call.</small>',
    "sos-active": () => heading("Help call") + avatar(resolve(state.sos), true) + `<h3>${esc(resolve(state.sos)?.name || "No contact")}</h3>` + info(`${esc(variant)}. Help is not confirmed.`) +
      info("Not an emergency service.", "warning") + button(`${icon("end")} End`, "end-sos", "danger") + goButton("No answer", "sos-failed") + emergency() + noticeExternal(),
    "sos-failed": () => heading("Help call failed") + info(`${esc(variant)}. No one reached. No alert or location delivered. In danger? Use Emergency call.`, "error") + button("Try again", "start-sos", "danger") + emergency() + goButton("Home", "home") + noticeExternal(),
    apps: () => {
      const list = variant === "empty" ? [] : state.allowed.filter(a => (state.appFilter === "all" || state.extra.includes(a)) && a.toLowerCase().includes(state.search.toLowerCase()));
      return heading("Apps") + field("Search", "appSearch", state.search, "search", 'data-input="app-search"') +
        `<div class="tab-row">${button("All allowed", "app-filter", state.appFilter === "all" ? "selected" : "", `aria-pressed="${state.appFilter === "all"}"`)}${button("On home", "app-favorites", state.appFilter === "home" ? "selected" : "", `aria-pressed="${state.appFilter === "home"}"`)}</div>` +
        (list.map(a => row(a, "", "app", `data-app="${a}"`, icon("apps"))).join("") || info("No apps found.")) + goButton("Edit apps", "home-edit") + noticeExternal();
    },
    pin: () => {
      const remaining = Math.max(0, Math.ceil((state.cooldown - Date.now()) / 1000));
      const dateMode = state.accessMode === "date";
      return heading(dateMode ? "Today's date?" : "Settings access") + `<div class="pin-emblem">${icon("lock")}</div>` +
        (remaining ? info(`Wait ${remaining} seconds.`, "warning") + button("Try again", "refresh") : "") +
        `<form data-form="pin" class="stack">${dateMode
          ? field("dd/MM/YYYY", "date", "", "text", 'dir="ltr" placeholder="dd/MM/YYYY" pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}" minlength="10" maxlength="10" required autocomplete="off" spellcheck="false"')
          : field("PIN", "pin", "", "password", 'inputmode="numeric" pattern="[0-9]{4,6}" minlength="4" maxlength="6" required autocomplete="off"')}<button type="submit" class="primary" ${remaining ? "disabled" : ""}>Unlock</button></form>` +
        (dateMode ? "" : goButton("Forgot PIN?", "pin-recovery")) + goButton("Cancel", "home");
    },
    settings: () => heading("Settings") + `<div class="settings-list stack">${[
      ["Home & apps", "", "home-edit"],
      ["People & speed dial", "", "speed-dial"],
      ["Display", "Text size and contrast", "display"],
      ["Language", "English · Español · עברית", "language"],
      ["SOS contact", "", "sos-settings"],
      ["Permissions", "", "permissions"],
      ["Privacy", "Optional diagnostics off by default", "privacy"],
      ["Membership", state.membership, "membership"],
      ["Settings access", "", "setup"],
      ["Reset Claro", "", "reset"]
    ].map(a => row(a[0], a[2] === "membership" ? membershipLabel(a[1]) : "", "go", `data-route="${a[2]}"`, icon(a[2] === "speed-dial" ? "contact" : a[2] === "sos-settings" ? "sos" : "settings"))).join("")}${button("Contacts", "manage-contacts")}${button("Done · lock", "lock", "primary")}</div>`,
    "home-edit": () => {
      const extras = draft?.extra || state.extra, allowed = draft?.allowed || state.allowed;
      return heading("Home & apps") +
        extras.map((a, i) => `<div class="edit-app"><strong>${esc(a)}</strong><div class="button-row">${button("↑", "move-app", "secondary compact", `data-index="${i}" data-delta="-1" aria-label="Move ${a} up" ${i === 0 ? "disabled" : ""}`)}${button("↓", "move-app", "secondary compact", `data-index="${i}" data-delta="1" aria-label="Move ${a} down" ${i === extras.length - 1 ? "disabled" : ""}`)}${button("Remove", "remove-app", "secondary compact", `data-index="${i}"`)}</div></div>`).join("") +
        `<label><span class="field-label">Add allowed app to home</span><select id="extra-app">${allowed.map(a => `<option>${esc(a)}</option>`).join("")}</select></label>${button("Add tile", "add-app")}` +
        `<h3>App allowlist</h3>${["Weather", "Calendar", "Calculator", "Music", "Maps"].map(a => `<label class="check-label"><input type="checkbox" data-change="allow-app" value="${a}" ${allowed.includes(a) ? "checked" : ""}>${a}</label>`).join("")}` +
        button("Save", "save-home", "primary") + goButton("Cancel", "settings");
    },
    "speed-dial": () => heading("People & speed dial") + `<form data-form="speed-dial" class="stack">${state.favorites.map((v, i) => `<label><span class="field-label">Home person ${i + 1}</span><select name="favorite${i}">${contactOptions(variant === "unassigned" ? "" : v)}</select></label>`).join("")}${state.keys.map((v, i) => `<label><span class="field-label">Hold ${i + 1} to call</span><select name="key${i}">${contactOptions(variant === "unassigned" ? "" : v)}</select></label>`).join("")}<small>Holding 1 or 2 calls immediately, without confirmation.</small><button class="primary" type="submit">Save</button>${goButton("Cancel", "settings")}${button("Contacts", "manage-contacts")}</form>`,
    display: () => heading("Display") + `<label class="setting-toggle">Larger text<input type="checkbox" data-change="large" ${draft?.large ? "checked" : ""}></label><label class="setting-toggle">High contrast<input type="checkbox" data-change="contrast" ${draft?.contrast ? "checked" : ""}></label>` +
      button("Save", "save-display", "primary") + goButton("Cancel", "settings"),
    language: () => heading("Language") + `<form data-form="language" class="stack"><label><span class="field-label">Language</span><select name="language" id="language-preview" data-change="language-preview"><option value="en" ${(draft?.language || state.language) === "en" ? "selected" : ""}>English</option><option value="es" ${(draft?.language || state.language) === "es" ? "selected" : ""}>Español</option><option value="he" ${(draft?.language || state.language) === "he" ? "selected" : ""}>עברית</option></select></label><div class="info-card" dir="${draft?.language === "he" ? "rtl" : "ltr"}"><strong>${esc(copy[draft?.language || state.language].home)}</strong><p>${esc(copy[draft?.language || state.language].phone)} · ${esc(copy[draft?.language || state.language].messages)}</p><bdi dir="ltr">202-555-0101 · 9:41</bdi></div><button class="primary" type="submit">Save</button>${goButton("Cancel", "settings")}</form>`,
    "sos-settings": () => heading("SOS contact") + `<form data-form="sos-settings" class="stack"><label><span class="field-label">Person to call</span><select name="sos">${contactOptions(variant === "unconfigured" ? "" : state.sos)}</select></label><label class="check-label"><input type="checkbox" name="consent" ${state.sosConsent && variant !== "not agreed" ? "checked" : ""}>They agreed to receive help calls.</label>${info("Not an emergency service. Needs mobile service and an answer. No message or location sent.", "warning")}<button class="primary" type="submit">Save</button>${button("Test call", "sos-test")}${goButton("Cancel", "settings")}</form>` +
      (state.confirm === "sos-test" ? info("Tell your contact before testing.") + button("Start test", "run-sos-test", "primary") + button("Cancel", "cancel-confirm") : ""),
    permissions: () => heading("Permissions") +
      Object.entries(state.readiness).map(([name, value]) => `<div class="permission-card"><h3>${({ Home: "Default home", Phone: "Default phone", Messages: "Default SMS", Contacts: "Contacts", Notifications: "Notifications", Media: "Photos" })[name]}</h3><p>${esc(variant === "standard" ? value.startsWith("Granted") ? "Allowed" : value.startsWith("Denied") ? "Not allowed" : "Not checked" : variant)}</p><div class="button-row">${button("Allow", "permission-grant", "secondary compact", `data-permission="${name}"`)}${button("Not now", "permission-deny", "secondary compact", `data-permission="${name}"`)}</div>${button("Settings", "permission-repair", "secondary compact", `data-permission="${name}"`)}</div>`).join("") +
      goButton("Setup", "setup") + noticeExternal(),
    privacy: () => heading("Privacy") + `<form data-form="privacy" class="stack"><label class="setting-toggle">Usage analytics<input name="analytics" type="checkbox" ${state.analytics ? "checked" : ""}></label><label class="setting-toggle">Crash reports<input name="crashes" type="checkbox" ${state.crashes ? "checked" : ""}></label><p>Optional. Change your choices anytime.</p><button class="primary" type="submit">Save</button>${button("Turn both off", "withdraw-consent")}${goButton("Cancel", "settings")}</form>`,
    reset: () => heading("Reset Claro?") + info("Clears Claro contacts, favorites, drafts, picture references and settings. Device data and purchases stay unchanged.", "warning") +
      (state.activeCall || variant === "active call" ? info("End your call first.", "error") + goButton("Return to call", "calling") : state.confirm === "reset" ? info("Reset all Claro changes?") + button("Cancel", "cancel-confirm", "primary") + button("Reset", "confirm-reset", "danger") : button("Continue", "reset-review", "danger")) + goButton("Cancel", "settings"),
    welcome: () => heading("Welcome to Claro") + `<div class="welcome-symbol">${icon("sun")}</div><p>Your people. One touch away.</p>${goButton("Set up Claro", "setup", "primary")}${goButton("Continue free", "home")}<small>No trial starts when you install.</small>`,
    setup: () => {
      const readiness = Object.values(state.readiness).every(v => v === "Granted · not service-tested");
      const accessMode = accessModeDraft || state.accessMode;
      return heading("Set up Claro") +
        (!state.configured || state.unlocked ? `<form data-form="setup-access" class="stack"><h3>1. Settings access</h3><label><span class="field-label">Unlock with</span><select name="accessMode" data-change="access-mode"><option value="date" ${accessMode === "date" ? "selected" : ""}>Date question</option><option value="pin" ${accessMode === "pin" ? "selected" : ""}>Caregiver PIN</option></select></label>${accessMode === "date"
          ? info("No secret to remember. Anyone who knows today's date can unlock.")
          : `${field("New PIN (4–6 digits)", "newPin", "", "password", 'inputmode="numeric" pattern="[0-9]{4,6}" required maxlength="6"')}${field("Confirm PIN", "confirmPin", "", "password", 'inputmode="numeric" required maxlength="6"')}${field("Recovery phrase", "recovery", "", "password", 'required minlength="6" maxlength="80" autocomplete="off"')}<small>Keep the PIN and recovery phrase somewhere safe.</small>`}<button class="primary" type="submit">Save</button></form>` : info(state.accessMode === "date" ? "Date question saved" : "Caregiver PIN saved") + button("Change access", "setup-access")) +
        row("2. People & speed dial", state.favorites.every(v => resolve(v)) ? "✓ Assigned" : "Needs a person", "go", 'data-route="speed-dial"', icon("contact")) +
        row("3. SOS contact", resolve(state.sos) && state.sosConsent ? "Test with your contact" : "Choose a contact", "go", 'data-route="sos-settings"', icon("sos")) +
        row("4. Permissions", readiness ? "✓ Allowed" : "Check access", "go", 'data-route="permissions"', icon("lock")) +
        row("5. Language", "", "go", 'data-route="language"', icon("settings")) +
        row("6. Display", "", "go", 'data-route="display"', icon("sun")) +
        row("7. Apps", "", "go", 'data-route="home-edit"', icon("apps")) +
        `<label class="check-label"><input type="checkbox" data-change="handover" ${state.handover ? "checked" : ""}>We checked calls, messages, ringtone and SOS together.</label>` +
        info(state.configured && readiness && state.handover ? "Checklist complete." : "Setup incomplete.") +
        goButton("Optional plans", "plans") + goButton("Home", "home", "primary");
    },
    plans: () => heading("Membership") + info("Calls, messages, SOS and your home stay free.") +
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
    membership: () => heading("Membership") + info(`<strong>${esc(membershipLabel(variant === "Free" ? state.membership : variant))}</strong>`) +
      `<p>Calls, messages, SOS, favorites and home settings stay free.</p>` +
      goButton("Plans", "plans", "primary") + button("Restore purchases", "restore") + button("Manage in Google Play", "manage-membership") + goButton("Trial ended", "trial-ended") + goButton("Home", "home") +
      noticeExternal() + (state.external ? button("Cancel renewal", "cancel-membership") + button("No purchases found", "restore-none") + button("Restore lifetime", "restore-demo") + button("Try later", "restore-failed") : ""),
    "trial-ended": () => heading("Trial ended") + info("Your calls, messages, SOS and home stay free. Your settings are unchanged.") + goButton("Continue free", "home", "primary") + goButton("Plans", "plans") + button("Restore purchases", "restore") + noticeExternal(),
    "pin-recovery": () => heading("Reset PIN") +
      (variant === "forgotten secret" ? info("Without the PIN or phrase, reset Claro in Android app settings. Claro settings and drafts will be lost. Device contacts and messages stay.", "warning") : "") +
      `<form data-form="recovery" class="stack">${field("Recovery phrase", "recovery", "", "password", 'required maxlength="80" autocomplete="off"')}${field("New PIN", "newPin", "", "password", 'required inputmode="numeric" pattern="[0-9]{4,6}" maxlength="6"')}${field("Confirm new PIN", "confirmPin", "", "password", 'required inputmode="numeric" maxlength="6"')}<button class="primary" type="submit">Reset PIN</button></form>${variant === "forgotten secret" ? "" : button("Forgot phrase?", "forgot-recovery")}${goButton("Cancel", "pin")}`
  };
  function messageLength() {
    const unicode = /[^\u0000-\u007f]/.test(state.body);
    const limit = unicode ? 70 : 160;
    const segments = state.body.length <= limit ? 1 : Math.ceil(state.body.length / (unicode ? 67 : 153));
    return `${state.body.length} · ~${segments} SMS · carrier charges may apply`;
  }
  function setTarget(contact, number) {
    state.target = { name: contact?.name || number || "Unknown person", number: number || contact?.numbers[0] || "" };
  }
  function setIncomingTarget() {
    if (variant === "known") setTarget(state.contacts.find(c => c.id === "mira"), "202-555-0101");
    else state.target = { name: variant === "private" ? "Private number" : "Unknown caller", number: variant === "private" ? "Withheld" : "202-555-0199" };
  }
  function confirmCall(contact, number) {
    setTarget(contact, number); navigate("call-confirm");
  }
  function placeCall(direct = false) {
    if (state.activeCall) { navigate("calling"); state.notice = "End your current call first."; render(); return; }
    if (["no signal", "no SIM", "permission denied"].includes(variant) || state.network !== "available" || state.readiness.Phone.startsWith("Denied") || !validNumber(state.target.number)) {
      const reason = !validNumber(state.target.number) ? "invalid number" : state.readiness.Phone.startsWith("Denied") ? "permission denied" : state.network !== "available" ? state.network : variant;
      navigate("call-failed"); variant = reason; render(); return;
    }
    if (emergencyNumber(state.target.number)) { state.external = "Emergency dialer"; render(); return; }
    state.activeCall = true; state.callState = "Connected";
    navigate("calling");
    if (direct) { reviewOnly("Long-press speed dial skips ordinary confirmation. The call outcome is simulated; no device action occurs."); render(); }
  }
  function shortcut(index, direct) {
    const c = variant === "unassigned" ? null : resolve(state.keys[index]);
    if (!c) { state.error = `Key ${index + 1} is not assigned. Ask a caregiver to set it. No call started.`; render(); return; }
    if (direct) { setTarget(c, c.number); placeCall(true); } else confirmCall(c, c.number);
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
      if (countdown <= 0) { clearInterval(countdownTimer); state.activeCall = true; const c = resolve(state.sos); setTarget(c, c.number); navigate("sos-active"); }
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
  const actions = {
    "classic-digit": classicDigit,
    "classic-delete": classicDelete,
    "classic-call": classicCall,
    "classic-end": classicEnd,
    "classic-back": back,
    "classic-confirm": () => {
      if (classicOptionsOpen) classicOK();
      else if (!classicConfirmAction()) openClassicOptions();
      else invokeClassicAction(classicConfirmAction());
    },
    "classic-clear": () => {
      const input = classicTarget();
      multiTap = null;
      if (input) { input.setSelectionRange(0, input.value.length); classicEdit(input, ""); }
      else { state.digits = ""; render(); }
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
    lock: () => { lock(); navigate("home"); },
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
      if (route === "settings" && variant === "session timeout") { state.intended = "settings"; lock(); navigate("pin"); state.notice = "Settings locked."; }
      if (route === "pin" && variant === "incorrect") state.error = state.accessMode === "date" ? "That isn't today's date. Use dd/MM/YYYY." : "Incorrect PIN.";
      if (route === "pin" && variant === "locked session") { lock(); state.notice = "Settings locked."; }
      if (route === "pin" && variant === "cooldown") state.cooldown = Date.now() + 30000;
      if (route === "pin-recovery" && variant === "cooldown") state.recoveryCooldown = Date.now() + 30000;
      if (route === "pin-recovery" && variant === "incorrect") state.error = "Recovery phrase did not match.";
      if (route === "privacy" && variant === "consent withdrawn") { state.analytics = false; state.crashes = false; delete state.formDrafts.privacy; state.notice = "Both turned off."; }
      if (route === "display" && variant === "huge text") draft.large = true;
      if (route === "home" && variant === "active call") state.activeCall = true;
      if (route === "sos-active" && variant === "ended") state.activeCall = false;
      if (route === "home-edit" && variant === "extra tiles") draft.extra = [...draft.allowed];
      if (route === "compose" && variant === "invalid recipient") state.recipient = "not-a-number";
      if (route === "reset" && variant === "reset complete") reviewOnly("Reset preview does not reset session memory; final confirmation performs the local reset.");
      if (route === "setup" && variant === "complete") reviewOnly("Completion preview does not verify native service or change the handover checklist.");
      if (route === "sos-countdown" && variant === "standard") state.sosArmed = true;
      render();
      if (route === "sos-countdown") startCountdown();
    },
    favorite: el => { const c = resolve(state.favorites[Number(el.dataset.slot)]); if (c) confirmCall(c, c.number); },
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
      if (contact.numbers.length === 1) confirmCall(contact, contact.numbers[0]);
      else { state.selected = contact.id; state.numberIndex = 0; navigate("contact-detail"); }
    },
    digit: el => { if (suppressClick?.element === el) { suppressClick = null; return; } state.digits = (state.digits + el.dataset.digit).slice(0, 24); state.error = ""; render(); },
    backspace: () => { state.digits = state.digits.slice(0, -1); render(); },
    dial: () => {
      if (!validNumber(state.digits)) { state.error = "Enter a valid number."; render(); return; }
      if (emergencyNumber(state.digits)) { actions.emergency(); return; }
      confirmCall(null, state.digits);
    },
    "key-call": el => shortcut(Number(el.dataset.key), false),
    "recent-call": el => { const c = state.contacts.find(p => p.id === el.dataset.id); confirmCall(c, c.numbers[0]); },
    contact: el => { state.selected = el.dataset.id; state.numberIndex = 0; navigate("contact-detail"); },
    "contact-call": () => { const c = selectedContact(); if (c) confirmCall(c, c.numbers[state.numberIndex] || c.numbers[0]); },
    "contact-message": () => { const c = selectedContact(); if (c) { setTarget(c, c.numbers[state.numberIndex] || c.numbers[0]); state.recipient = state.target.number; navigate("compose"); } },
    "add-contact": () => beginContactEdit(true),
    "edit-contact": () => beginContactEdit(false),
    "delete-contact": () => {
      if (!protectedAction("contact-detail", "delete-contact")) return;
      state.confirm = "delete-contact"; render();
    },
    "cancel-contact": () => { state.contactMode = ""; state.contactDraft = null; delete state.formDrafts.contact; state.error = ""; render(); },
    "confirm-delete-contact": () => {
      if (!state.unlocked) { navigate("pin"); return; }
      const c = selectedContact();
      if (!c || resolve(state.sos)?.id === c.id) { state.error = "Replace the SOS recipient before deleting this contact."; render(); return; }
      if (variant === "write denied" || variant === "save failure") { state.error = "Could not delete contact. Nothing changed."; render(); return; }
      state.contacts = state.contacts.filter(p => p.id !== c.id);
      state.favorites = state.favorites.map(v => v.startsWith(`${c.id}:`) ? "" : v);
      state.keys = state.keys.map(v => v.startsWith(`${c.id}:`) ? "" : v);
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
    answer: () => { setIncomingTarget(); state.activeCall = true; state.callState = "Connected"; navigate("calling"); },
    decline: () => {
      setIncomingTarget();
      state.activeCall = false; navigate("call-ended"); variant = "declined"; render();
    },
    "retry-call": () => {
      if (state.activeCall) { navigate("calling"); state.notice = "End your current call first."; render(); return; }
      const target = callOutcomeTarget();
      if (!validNumber(target.number)) { state.error = "No number available to call."; render(); return; }
      setTarget(target, target.number); navigate("call-confirm");
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
    "open-conversation": el => { const c = state.contacts.find(p => p.id === el.dataset.id); setTarget(c, c.numbers[0]); if (c.id === "mira") state.unread = false; navigate("conversation"); },
    "mms-thread": () => { state.target = { name: "Family group", number: "202-555-0101" }; navigate("conversation"); variant = "MMS"; render(); },
    reply: () => { state.recipient = state.target.number; navigate("compose"); },
    "choose-recipient": () => { state.picker = "message"; navigate("contacts"); },
    "pick-contact": el => {
      const c = state.contacts.find(p => p.id === el.dataset.id); state.selected = c.id; state.numberIndex = 0;
      const index = Number(el.dataset.numberIndex) || 0;
      state.recipient = c.numbers[index]; setTarget(c, c.numbers[index]); state.picker = ""; navigate("compose");
      state.notice = "Recipient selected."; render();
    },
    "cancel-picker": () => { state.picker = ""; navigate("compose"); },
    "cancel-compose": () => navigate("messages"),
    "send-message": () => {
      if (!validNumber(state.recipient) || emergencyNumber(state.recipient) || !state.body.trim()) { navigate("compose"); state.error = "Choose a contact and enter a message."; render(); return; }
      setTarget(recipientContact(), state.recipient);
      if (variant === "send failure" || variant === "unknown outcome" || state.readiness.Messages.startsWith("Denied") || state.network !== "available") { const unknown = variant === "unknown outcome"; navigate("message-failed"); variant = unknown ? "unknown outcome" : "known failure"; render(); return; }
      state.messages.push({ number: state.recipient, body: state.body });
      state.body = ""; navigate("conversation"); state.notice = "Sent · delivery not confirmed."; render();
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
    "previous-photo": () => { state.photoIndex = (state.photoIndex - 1 + state.photos.length) % state.photos.length; state.confirm = ""; render(); },
    "next-photo": () => { state.photoIndex = (state.photoIndex + 1) % state.photos.length; state.confirm = ""; render(); },
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
    "end-sos": () => { state.activeCall = false; variant = "ended"; state.notice = "Call ended. Help not confirmed."; render(); },
    emergency: () => { state.external = "Emergency dialer"; render(); },
    native: () => { state.external = ["messages", "conversation", "message-failed", "compose"].includes(route) ? "Messages" : ["gallery", "photo"].includes(route) ? "Photos" : "Phone"; render(); },
    "external-back": () => { state.external = ""; state.confirm = ""; state.notice = ""; render(); },
    app: el => {
      const name = el.dataset.app;
      if (!state.allowed.includes(name)) { state.error = "This app is not allowed. Ask a caregiver to review the app list."; render(); return; }
      if (variant === "missing app") { state.error = `${name} unavailable.`; render(); return; }
      state.external = name;
      render();
    },
    "app-filter": () => { state.appFilter = "all"; render(); },
    "app-favorites": () => { state.appFilter = "home"; render(); },
    "manage-contacts": () => navigate("contacts"),
    "setup-access": () => { if (protectedAction("setup", "setup-access")) render(); },
    "forgot-recovery": () => { variant = "forgotten secret"; state.error = ""; render(); },
    "move-app": el => { const i = Number(el.dataset.index), next = i + Number(el.dataset.delta); if (next >= 0 && next < draft.extra.length) [draft.extra[i], draft.extra[next]] = [draft.extra[next], draft.extra[i]]; render(); },
    "remove-app": el => { draft.extra.splice(Number(el.dataset.index), 1); render(); },
    "add-app": () => {
      const value = $("extra-app")?.value;
      if (!value) { state.error = "Choose an app first."; } else if (draft.extra.includes(value)) { state.notice = "Already on Home."; } else { draft.extra.push(value); state.notice = "Added. Save to keep changes."; }
      render();
    },
    "save-home": () => { state.extra = [...draft.extra]; state.allowed = [...draft.allowed]; delete state.preferenceDrafts[route]; draft = null; navigate("settings"); state.notice = "Saved."; render(); },
    "save-display": () => { state.large = draft.large; state.contrast = draft.contrast; delete state.preferenceDrafts[route]; draft = null; navigate("settings"); state.notice = "Saved."; render(); },
    "sos-test": () => { state.confirm = "sos-test"; render(); },
    "run-sos-test": startSOS,
    "permission-grant": el => { state.readiness[el.dataset.permission] = "Granted · not service-tested"; variant = "standard"; state.notice = "Allowed."; render(); },
    "permission-deny": el => { state.readiness[el.dataset.permission] = "Denied · repair needed"; variant = "standard"; state.notice = "Not allowed."; render(); },
    "permission-repair": el => { state.external = `${el.dataset.permission} settings`; render(); },
    "withdraw-consent": () => { state.analytics = false; state.crashes = false; delete state.formDrafts.privacy; state.notice = "Both turned off."; render(); },
    "reset-review": () => { state.confirm = "reset"; render(); },
    "confirm-reset": () => { if (state.activeCall) return; state = initial(); draft = null; navigate("home"); state.notice = "Claro reset. Device data unchanged."; render(); },
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
      if (!/^\d{4,6}$/.test(pin) || pin !== data.get("confirmPin")) { state.error = "Enter matching new PINs of 4–6 digits. Nothing changed."; render(); return; }
      state.pin = pin; state.attempts = 0; state.cooldown = 0; state.recoveryAttempts = 0; state.recoveryCooldown = 0; lock();
      navigate("pin"); state.notice = "PIN reset. Use your new PIN."; render();
    },
    "setup-access": data => {
      if (state.configured && !state.unlocked) { state.intended = "setup"; navigate("pin"); return; }
      const accessMode = data.get("accessMode");
      if (!["date", "pin"].includes(accessMode)) { state.error = "Choose a date question or caregiver PIN."; render(); return; }
      let pin = "", recovery = "";
      if (accessMode === "pin") {
        pin = String(data.get("newPin") || ""); recovery = String(data.get("recovery") || "").trim();
        if (!/^\d{4,6}$/.test(pin) || pin !== data.get("confirmPin") || recovery.length < 6 || recovery === pin) { state.error = "Use matching 4–6 digit PINs and a separate phrase of at least 6 characters."; render(); return; }
      }
      state.accessMode = accessMode; state.pin = pin; state.recovery = recovery; state.configured = true; state.unlocked = true;
      state.attempts = 0; state.cooldown = 0; state.recoveryAttempts = 0; state.recoveryCooldown = 0;
      accessModeDraft = null; state.error = ""; state.notice = "Settings access saved."; render(); touchSession();
    },
    contact: data => {
      if (!state.unlocked) { state.intended = "contact-detail"; navigate("pin"); return; }
      const name = String(data.get("name")).trim(), number = String(data.get("number")).trim(), other = String(data.get("other")).trim();
      state.contactDraft = { name, number, other };
      if (!name || !validNumber(number) || (other && !validNumber(other))) { state.error = "Enter a name and valid numbers. Nothing saved."; render(true); return; }
      if (variant === "save failure" || variant === "write denied" || state.readiness.Contacts.startsWith("Denied")) { state.error = "Could not save. Check Contacts access."; render(true); return; }
      const existing = state.contacts.find(c => c.id === state.editId);
      const nextNumbers = [number, ...(other ? [other] : [])];
      if (existing && [state.sos, ...state.keys, ...state.favorites].some(v => {
        const [id, index] = String(v).split(":");
        return id === existing.id && existing.numbers[Number(index)] !== nextNumbers[Number(index)];
      })) {
        state.error = "Number used by SOS or speed dial. Change those assignments first."; render(true); return;
      }
      const contact = { id: existing?.id || `person${Date.now()}`, name, numbers: [number, ...(other ? [other] : [])], color: existing?.color || "" };
      if (existing) state.contacts[state.contacts.indexOf(existing)] = contact; else state.contacts.push(contact);
      state.selected = contact.id; state.numberIndex = 0; state.contactMode = ""; state.contactDraft = null; delete state.formDrafts.contact; state.error = ""; state.notice = "Contact saved."; render();
    },
    compose: data => {
      state.recipient = String(data.get("recipient")).trim(); state.body = String(data.get("body"));
      if (!validNumber(state.recipient) || emergencyNumber(state.recipient) || !state.body.trim()) { state.error = "Choose a valid non-emergency recipient and enter a message. Draft retained."; render(true); return; }
      const c = state.contacts.find(c => c.numbers.includes(state.recipient)); setTarget(c, state.recipient);
      navigate("message-confirm");
    },
    "speed-dial": data => {
      const keys = [data.get("key0"), data.get("key1")], favorites = Array.from({ length: 4 }, (_, i) => data.get(`favorite${i}`));
      if ([...keys, ...favorites].some(v => v && (!resolve(v) || emergencyNumber(resolve(v).number)))) { state.error = "Choose valid, non-emergency contact numbers."; render(); return; }
      state.keys = keys; state.favorites = favorites; delete state.formDrafts["speed-dial"]; navigate("settings"); state.notice = "Saved."; render();
    },
    language: data => { state.language = data.get("language"); delete state.formDrafts.language; delete state.preferenceDrafts.language; draft = null; reviewOnly("Language saved for core screens. Secondary screens remain English draft."); navigate("home"); },
    "sos-settings": data => {
      const value = data.get("sos"), agreed = data.has("consent");
      if (value && (!resolve(value) || emergencyNumber(resolve(value).number))) { state.error = "SOS must use a valid trusted person, not an emergency number."; render(); return; }
      state.sos = value; state.sosConsent = agreed && Boolean(value); delete state.formDrafts["sos-settings"]; state.error = ""; state.notice = state.sos && state.sosConsent ? "Saved. Test with your contact." : "SOS off. Choose an agreed contact."; variant = "standard"; render();
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
    if (anchor && registry[anchor.hash.slice(1)]) { event.preventDefault(); navigate(anchor.hash.slice(1)); return; }
    const el = event.target.closest("[data-action]");
    if (!el || el.disabled) return;
    touchSession();
    const action = actions[el.dataset.action];
    if (action) action(el);
  });
  phone.addEventListener("submit", event => {
    const form = event.target.closest("[data-form]"); if (!form) return;
    event.preventDefault(); touchSession();
    forms[form.dataset.form]?.(new FormData(form));
  });
  phone.addEventListener("input", event => {
    const el = event.target; touchSession(); rememberForm(el);
    switch (el.dataset.input) {
      case "contact-search": state.search = el.value; render(true); break;
      case "message-search": state.search = el.value; render(true); break;
      case "app-search": state.search = el.value; render(true); break;
      case "recipient": state.recipient = el.value; break;
      case "body": state.body = el.value; $("message-length").textContent = messageLength(); break;
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
    const el = event.target; touchSession(); rememberForm(el);
    switch (el.dataset.change) {
      case "access-mode": accessModeDraft = el.value; state.error = ""; render(); break;
      case "contact-number": state.numberIndex = Number(el.value); break;
      case "large": draft.large = el.checked; render(); break;
      case "contrast": draft.contrast = el.checked; render(); break;
      case "language-preview": draft.language = el.value; render(); break;
      case "allow-app": {
        const value = el.value; draft.allowed = el.checked ? [...draft.allowed, value] : draft.allowed.filter(a => a !== value); draft.extra = draft.extra.filter(a => draft.allowed.includes(a)); render(); break;
      }
      case "handover": state.handover = el.checked; render(); break;
    }
  });
  device.addEventListener("pointerdown", event => {
    if (classicMode && event.target.closest(".classic-deck")) event.preventDefault();
    const key = event.target.closest('[data-action="digit"], [data-action="classic-digit"]');
    const classicKey = key?.dataset.action === "classic-digit";
    if (!key || (classicKey ? !classicSpeedDialAllowed() : route !== "dialer" && !dialerOptions[route]) || !["1", "2"].includes(key.dataset.digit) || (event.pointerType === "mouse" && event.button !== 0)) return;
    clearHold(); suppressClick = null; holdButton = key; holdStart = { x: event.clientX, y: event.clientY, id: event.pointerId };
    try { key.setPointerCapture(event.pointerId); } catch (_) { /* Capture may be unavailable for an interrupted pointer. */ }
    key.classList.add("holding"); key.setAttribute("aria-description", "Hold in progress. Release or move to cancel.");
    holdTimer = setTimeout(() => {
      if (holdButton !== key || (classicKey ? !classicSpeedDialAllowed() : route !== "dialer" && !dialerOptions[route])) return;
      suppressClick = { element: key }; clearHold(); shortcut(Number(key.dataset.digit) - 1, true);
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
    touchSession();
    if (event.key === "Escape") { clearHold(); if ($("clean-preview") && !document.body.classList.contains("classic-embed")) $("clean-preview").checked = false; if (state.external || state.confirm || state.contactMode) back(); }
  });
  $("phone-back").addEventListener("click", back);
  $("phone-home").addEventListener("click", () => navigate("home"));
  $("previous-screen").addEventListener("click", () => navigate(definitions[(registry[route].index - 1 + definitions.length) % definitions.length][0]));
  $("next-screen").addEventListener("click", () => navigate(definitions[(registry[route].index + 1) % definitions.length][0]));
  window.addEventListener("popstate", () => activate(location.hash.slice(1), true));
  window.addEventListener("hashchange", () => { const id = location.hash.slice(1); if (id !== route) activate(id, true); });
  document.addEventListener("visibilitychange", () => {
    clearHold();
    if (document.hidden && route === "sos-countdown") { clearInterval(countdownTimer); variant = "interrupted"; render(); }
  });
  window.addEventListener("pagehide", cleanup);
  if (classicMode) {
    classicDeck.innerHTML = `<div id="classic-controls" class="classic-context-keys" role="group" aria-label="Fixed phone controls">
        ${button("Confirm", "classic-confirm", "classic-context-key", 'id="classic-confirm"')}
        ${button(esc(t("back")), "classic-back", "classic-context-key", 'id="classic-back"')}
        ${button("", "classic-call", "classic-context-key primary", 'id="classic-call"')}
        ${button("", "classic-end", "classic-context-key classic-end", 'id="classic-end"')}
        <div class="classic-dpad" role="group" aria-label="Four-way navigation">
          ${["up", "left", "right", "down"].map(direction => button(icon(`arrow-${direction}`), "classic-nav", "classic-nav-key", `id="classic-nav-${direction}" data-direction="${direction}" aria-label="Navigate ${direction}" aria-controls="phone-content"`)).join("")}
          ${button("OK", "classic-ok", "classic-nav-key", 'id="classic-ok" aria-label="Open or select highlighted item" aria-controls="phone-content"')}
        </div>
      </div>
      <div class="classic-mode-status"><output id="classic-feedback" role="status" aria-live="polite"></output><span id="classic-input-mode" hidden></span></div>
      <div class="classic-number-keys" dir="ltr" role="group" aria-label="Number keypad">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map((digit, i) => button(`<strong>${digit}</strong><small ${i < 2 ? `data-speed-label="${i}"` : ""}>${[".,?!", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "+", "space", "abc / 123"][i]}</small>`, "classic-digit", "classic-number", `data-digit="${digit}" aria-label="${digit}${digit === "1" || digit === "2" ? ". Hold for speed dial from Home or Phone" : ""}"`)).join("")}</div>`;
    classicScrollObserver = new ResizeObserver(updateClassicNavigation);
    phone.addEventListener("scroll", updateClassicNavigation, { passive: true });
    document.addEventListener("selectionchange", () => {
      if (phone.contains(document.activeElement)) updateClassicNavigation();
    });
  }
  const entry = location.hash.slice(1);
  history.replaceState({ claro: true, parent: null }, "", registry[entry] ? `#${entry}` : "#home");
  activate(registry[entry] ? entry : "home");
})();
