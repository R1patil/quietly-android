// Quietly: Gmail adapter (isolated world). Reads the open thread and puts a chosen draft in the reply box,
// above your signature and the quoted thread. It never sends: no Send button, no keyboard shortcuts.
// Loaded before ui.js, which provides the shared helpers ($, clean, flat, …) and the floating card.
// Live-check helpers (DevTools console, pick "Quietly" in the context dropdown): diag(), probe(), testInsert('Hi,\n\ntest').

const SEL = {
  main: '[role="main"]',
  subject: 'h2.hP',
  threadId: '[data-thread-perm-id], [data-legacy-thread-id]',
  message: 'div.adn', // expanded messages; collapsed ones only show a snippet and are skipped
  messageId: '[data-message-id], [data-legacy-message-id]',
  sender: '.gD[email]',
  recipients: '.g2[email]',
  date: '.g3',
  body: '.a3s',
  // removed from a body before reading: quoted history, signatures, images
  noise: '.gmail_quote, blockquote, .yj6qo, .adL, .gmail_signature, .gmail_signature_prefix, img, style, script',
  box: 'div[contenteditable="true"][g_editable="true"]',
  // everything in the reply box from here down is yours already (signature, quoted thread)
  boxStop: '.gmail_signature_prefix, .gmail_signature, .gmail_quote, .gmail_extra',
}

const EMAIL_IN = /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/
const BLOCKS = new Set(['DIV', 'P', 'LI', 'TR', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'BLOCKQUOTE', 'UL', 'OL', 'TABLE'])

// Text with paragraph breaks, from a detached clone (innerText needs layout, so do it by hand).
function mailText(el, strip = SEL.noise) {
  if (!el) return ''
  const c = el.cloneNode(true)
  for (const n of c.querySelectorAll(strip)) n.remove()
  for (const n of c.querySelectorAll('br')) n.replaceWith('\n')
  for (const n of c.querySelectorAll('*')) if (BLOCKS.has(n.tagName)) n.append('\n')
  return c.textContent.replace(/[  ]/g, ' ').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim()
}

const mainView = () => $(SEL.main)
const subjectEl = () => $(SEL.subject, mainView())
const visible = el => !!el && el.getClientRects().length > 0

// Your address: Gmail puts it in the tab title ("Inbox (3) - you@example.com - Gmail").
function myEmail() {
  const t = EMAIL_IN.exec(document.title)?.[0]
  if (t) return t.toLowerCase()
  const acct = $$('a[aria-label*="@"], [aria-label*="@"][role="button"]').map(e => EMAIL_IN.exec(e.getAttribute('aria-label'))?.[0]).find(Boolean)
  return acct ? acct.toLowerCase() : ''
}

function threadId() {
  const s = subjectEl()
  return s?.getAttribute('data-thread-perm-id') || s?.getAttribute('data-legacy-thread-id') ||
    $(SEL.threadId, mainView())?.getAttribute('data-thread-perm-id') || $(SEL.threadId, mainView())?.getAttribute('data-legacy-thread-id') ||
    location.hash
}

// The reply box you're typing in: inline in the thread, or a pop-out compose window.
function replyBox() {
  const boxes = $$(SEL.box).filter(visible)
  return boxes.find(b => b.contains(document.activeElement)) || boxes.at(-1) || null
}

function messages() {
  return $$(SEL.message, mainView()).filter(m => $(SEL.sender, m) && $(SEL.body, m))
}

function parseMessage(m, me) {
  const from = $(SEL.sender, m)
  const email = (from.getAttribute('email') || '').toLowerCase()
  const mine = !!me && email === me
  const dateEl = $(SEL.date, m)
  const id = $(SEL.messageId, m)?.getAttribute('data-message-id') || $(SEL.messageId, m)?.getAttribute('data-legacy-message-id') ||
    m.getAttribute('data-message-id') || m.getAttribute('data-legacy-message-id') || `${email}|${dateEl?.getAttribute('title') || ''}`
  const text = mailText($(SEL.body, m))
  return {
    id, me: mine, sure: !!me, tick: mine, email,
    who: mine ? '' : clean(from.getAttribute('name') || from.textContent),
    when: clean(dateEl?.getAttribute('title') || dateEl?.textContent),
    text: text || '[no text — attachment or image]',
    to: $$(SEL.recipients, m).map(e => (e.getAttribute('email') || '').toLowerCase()),
  }
}

function people() {
  const me = myEmail()
  const rows = messages().map(m => parseMessage(m, me))
  const them = rows.filter(r => !r.me)
  const last = them.at(-1)
  const others = new Set(them.map(r => r.email))
  const lastAll = rows.at(-1) ? [rows.at(-1).email, ...rows.at(-1).to].filter(e => e && e !== me) : []
  for (const e of lastAll) others.add(e)
  return { me, rows, email: last?.email || '', name: last?.who || '', group: others.size >= 2 }
}

// What you've typed so far: the reply box above the signature and quoted thread.
function draftText(box) {
  return mailText(box, SEL.boxStop)
}

function readChat() {
  const box = replyBox()
  if (!box) return { error: 'Open a reply first' }
  if (!subjectEl()) return { error: 'Open the email thread you are replying to' }
  const p = people()
  if (!p.me) return { error: "Couldn't find your address — details in the console", diag: diag() }
  if (!p.rows.length) {
    diag()
    return { error: "Couldn't read this thread — details in the console" }
  }
  if (!p.rows.some(r => !r.me)) return { error: 'Nothing to reply to yet' }
  return {
    site: 'gmail', id: threadId(), title: clean(subjectEl().textContent), email: p.email, name: p.name, group: p.group,
    today: localToday(), draft: draftText(box), rows: p.rows.slice(-8).map(({ to, email, ...r }) => r),
  }
}

// Everything ui.js needs to know about Gmail.
const SITE = {
  name: 'gmail',
  label: 'Gmail',
  refines: [['shorter', '✂️ Shorter'], ['formal', '\u{1F454} More formal'], ['warmer', '\u{1F642} Warmer'], ['english', '\u{1F310} English']],
  notesHint: "Who they are, how formal to be, what you're working on together.",
  marker() {
    const s = subjectEl()
    return { node: s, title: s ? `${threadId()}|${clean(s.textContent)}` : '' }
  },
  ident() {
    const s = subjectEl()
    const p = people()
    return { site: 'gmail', id: threadId(), title: clean(s?.textContent), email: p.email, name: p.name, group: p.group }
  },
  same(chat) {
    return threadId() === chat.id && clean(subjectEl()?.textContent) === chat.title
  },
  blocked: () => false,
  readChat,
  composer: () => (subjectEl() ? replyBox() : null),
  anchor: box => box.getBoundingClientRect(),
  darkFrom: box => box?.closest('[role="dialog"], table, td') || mainView() || document.body,
  boxText: box => flat(draftText(box)),
  hintFor: () => '',
  // One attempt: replace what you typed above the signature/quote, never the signature or quote itself.
  insertOnce(box, text) {
    box.focus()
    const r = document.createRange()
    r.setStart(box, 0)
    const stop = $(SEL.boxStop, box)
    if (stop) r.setEndBefore(stop)
    else r.setEnd(box, box.childNodes.length)
    const sel = getSelection()
    sel.removeAllRanges()
    sel.addRange(r)
    document.execCommand('insertText', false, stop ? `${text}\n\n` : text)
  },
}

// --- live-check helpers (console only) -------------------------------------

function diag() {
  const v = mainView()
  const n = s => (v ? v.querySelectorAll(s).length : -1)
  const info = {
    main: !!v, subject: !!subjectEl(), threadId: !!threadId() && threadId() !== location.hash, myEmail: !!myEmail(),
    messages: n(SEL.message), senders: n(SEL.sender), bodies: n(SEL.body), quotes: n('.gmail_quote, blockquote'),
    boxes: $$(SEL.box).length, visibleBoxes: $$(SEL.box).filter(visible).length,
    boxStops: replyBox() ? $$(SEL.boxStop, replyBox()).map(e => e.className) : null,
  }
  console.log('[Quietly] diag', info)
  return info
}

// Redacted per-message view: no text, names or addresses leave the console.
function probe() {
  const chat = readChat()
  if (chat.error) return chat.error
  const rows = chat.rows.map(r => ({ me: r.me, words: r.text.split(/\s+/).length, paragraphs: r.text.split(/\n\n/).length, date: !!r.when, sender: !!r.who }))
  console.table(rows)
  return { group: chat.group, contact: !!chat.email, draftWords: chat.draft ? chat.draft.split(/\s+/).length : 0, rows: rows.length }
}
