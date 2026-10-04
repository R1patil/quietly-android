// Quietly: WhatsApp Web adapter (isolated world). Reads the open chat and puts a chosen reply in the message box.
// Loaded before ui.js, which provides the shared helpers ($, clean, flat, …) and the floating card.
// Live-check helpers (DevTools console, pick "Quietly" in the context dropdown): diag(), probe(), testInsert('hi').

const SEL = {
  pane: 'div#main', // not bare #main: WhatsApp also renders an SVG <mask id="main">
  header: 'header',
  title: '[data-testid="conversation-info-header-chat-title"]',
  titleFallback: 'header span[dir="auto"]',
  you: '[data-testid="you-label"]',
  rows: '[role="row"]',
  system: '[data-testid="msg-notification-container"]',
  bubble: '[data-testid="msg-container"]',
  pre: '[data-pre-plain-text]',
  text: '.selectable-text, [data-testid~="selectable-text"]',
  quote: '[data-testid="quoted-message"]',
  quoteText: '.quoted-mention',
  author: '[data-testid="author"]',
  out: '.message-out', // older builds only
  in: '.message-in', // older builds only
  ticks: '[data-icon^="msg-check"],[data-icon^="msg-dblcheck"],[data-icon^="status-check"],[data-icon^="status-dblcheck"],[data-icon="msg-time"],[data-icon="msg-wait"],[data-icon="status-time"]',
  composer: [
    'footer [data-testid="conversation-compose-box-input"]',
    'footer [contenteditable="true"][role="textbox"]',
    '[contenteditable="true"][data-lexical-editor="true"]',
  ],
  media: [
    ['[data-icon*="recalled"]', 'deleted'],
    ['[data-icon*="ptt"],[data-icon*="audio-play"],audio', 'voice note'],
    ['video,[data-icon*="media-play"],[data-icon*="msg-video"]', 'video'],
    ['[data-icon*="document"]', 'document'],
    ['img[src^="blob:"]', 'photo'],
  ],
}
const INSERT = 'exec' // the live check picks 'exec' (select + insertText) or 'paste' (select + delete + paste); ship one

let myLabel = '' // my push name: the label on my own (ticked) messages, the same in every chat

// --- reading the chat ------------------------------------------------------

// Same rules as WhatsApp's own copy: emoji images carry their character in data-plain-text / alt.
function waText(el) {
  if (!el) return ''
  const c = el.cloneNode(true)
  for (const n of c.querySelectorAll('[data-plain-text]')) n.replaceWith(n.getAttribute('data-plain-text'))
  for (const n of c.querySelectorAll('img[alt]')) n.replaceWith(n.alt)
  for (const n of c.querySelectorAll('br')) n.replaceWith('\n')
  return c.textContent.replace(/[‎‏‪-‮⁦-⁩]/g, '').replace(/[  ]/g, ' ').trim()
}

const pane = () => $(SEL.pane)

function chatTitle(m) {
  const el = $(SEL.title, m) || $(SEL.titleFallback, m)
  if (!el) return ''
  const c = el.cloneNode(true)
  for (const n of c.querySelectorAll(SEL.you)) n.remove()
  return clean(waText(c))
}

function composer(m) {
  for (const s of SEL.composer) {
    for (const el of $$(s, m)) if (!el.closest('[role="dialog"],[aria-modal="true"]') && el.getClientRects().length) return el
  }
  return null
}

// Synchronous round trip to bridge.js (MAIN world). Its answer is page-controlled, so only well-formed ids pass.
function bridge() {
  let out = null
  const on = e => {
    try { out = JSON.parse(e.detail) } catch {}
  }
  document.addEventListener('wahreply:res', on)
  document.dispatchEvent(new CustomEvent('wahreply:req'))
  document.removeEventListener('wahreply:res', on)
  const jid = typeof out?.jid === 'string' && /^[\d-]{5,40}@(c\.us|lid|g\.us|newsletter)$/.test(out.jid) ? out.jid : null
  const d = typeof out?.pn === 'string' ? out.pn.replace(/@c\.us$/, '') : ''
  return { jid, pn: /^\d{6,20}$/.test(d) ? `${d}@c.us` : null, depth: out?.depth ?? null }
}

function labelOf(row, quote) {
  const pre = $$(SEL.pre, row).find(e => !quote?.contains(e))
  const m = /^\[([^\]]*)\]\s*(.*?):\s*$/.exec(pre?.getAttribute('data-pre-plain-text') || '')
  return { pre, when: clean(m?.[1]), label: clean(m?.[2]) }
}

const ticked = (row, quote) => !!$(SEL.out, row) || $$(SEL.ticks, row).some(e => !quote?.contains(e))

function onRight(row, m) {
  const b = ($(SEL.bubble, row) || $(SEL.pre, row) || row).getBoundingClientRect()
  const p = m.getBoundingClientRect()
  const right = p.right - b.right < b.left - p.left
  return getComputedStyle(m).direction === 'rtl' ? !right : right
}

function learnMyLabel(rowEls) {
  for (const r of rowEls) {
    const quote = $(SEL.quote, r)
    if (!ticked(r, quote)) continue
    const { label } = labelOf(r, quote)
    if (label) return (myLabel = label)
  }
  return myLabel
}

function parseRow(row, ctx) {
  if (row.matches(SEL.system) || $(SEL.system, row)) return null
  const quote = $(SEL.quote, row)
  const { pre, when, label } = labelOf(row, quote)
  const body = $$(SEL.text, row).filter(e => !quote?.contains(e) && !e.parentElement?.closest(SEL.text)).at(-1)
  const bare = row.cloneNode(true) // same row without the quoted message
  $(SEL.quote, bare)?.remove()
  const barePre = $(SEL.pre, bare)
  let text = waText(body) || (barePre ? waText(barePre) : '')
  let kind = ''
  if (!/https?:\/\/|www\./i.test(text)) kind = SEL.media.find(([s]) => $(s, bare))?.[1] || '' // a link preview is not a photo
  text = clean([kind && `[${kind}]`, text].filter(Boolean).join(' '))
  if (!text) return null // off-screen (virtualized) or a bubble type we can't read
  // Ticks only ever mark MY messages; incoming ones are recognised by their sender label.
  const tick = ticked(row, quote)
  const forwarded = !!$('[data-icon^="forward"]', bare) // someone else's words: still mine to send, but not my style
  let me
  let sure = true
  if (tick) me = true
  else if ($(SEL.in, row) || (label && (label === ctx.title || (ctx.myLabel && label !== ctx.myLabel)))) me = false
  else {
    me = onRight(row, ctx.m)
    sure = false
  }
  const author = $$(SEL.author, row).find(e => !quote?.contains(e))
  const id = row.getAttribute('data-id') || $('[data-id]', row)?.getAttribute('data-id') ||
    ($('[data-testid^="conv-msg-"]', row)?.getAttribute('data-testid') || '').replace(/^conv-msg-/, '') ||
    `${when}|${label}|${text.slice(0, 40)}`
  return { id, me, sure, tick: tick && !forwarded, who: me ? '' : clean(waText(author)) || label, when, label, quote: clip(waText($(SEL.quoteText, quote)), 40), text }
}

function scroller(el) {
  for (let e = el?.parentElement; e && e !== document.body; e = e.parentElement) {
    const o = getComputedStyle(e).overflowY
    if ((o === 'auto' || o === 'scroll') && e.scrollHeight > e.clientHeight) return e
  }
  return null
}

function readChat() {
  const m = pane()
  if (!m) return { error: 'Open a chat first' }
  const box = composer(m)
  const title = chatTitle(m)
  const { jid, pn } = bridge()
  if (!box || jid?.endsWith('@newsletter')) return { error: "Can't reply in this chat" }
  const rowEls = $$(SEL.rows, m).filter(r => !r.closest('footer')).slice(-40)
  const sc = scroller(rowEls.at(-1))
  if (sc && sc.scrollHeight - sc.scrollTop - sc.clientHeight > 200) return { error: 'Scroll to the latest message' }
  const ctx = { m, title, myLabel: learnMyLabel(rowEls) }
  const rows = rowEls.map(r => parseRow(r, ctx)).filter(Boolean).slice(-25)
  if (!rows.length) {
    diag()
    return { error: "Couldn't read this chat — details in the console" }
  }
  const self = !!$(SEL.you, $(SEL.header, m))
  if (self || !rows.some(r => !r.me)) return { error: 'Nothing to reply to yet' }
  // A sender who is neither this contact nor me means bubbles from the previous chat are still on screen.
  const direct = /@(c\.us|lid)$/.test(jid || '') || !!pn
  const stale = direct && rows.some(r => !r.tick && r.label && r.label !== title && r.label !== ctx.myLabel)
  return { site: 'whatsapp', title, jid, pn, self, stale, today: localToday(), draft: clean(waText(box)), rows }
}

// Everything ui.js needs to know about WhatsApp.
const SITE = {
  name: 'whatsapp',
  label: 'WhatsApp',
  refines: [
    ['shorter', '✂️ Shorter'], ['warmer', '\u{1F642} Warmer'], ['formal', '\u{1F454} Formal'],
    ['funnier', '\u{1F602} Funnier'], ['english', '\u{1F310} English'], ['hinglish', '\u{1F310} Hinglish'],
  ],
  notesHint: 'Who they are, tone, how to reply. e.g. "college friend, roast him, Hinglish"',
  // cheap, polled every 500 ms: has the open chat changed?
  marker() {
    const m = pane()
    return { node: $(SEL.header, m), title: chatTitle(m) }
  },
  // who this chat is, for memory (sent to the service worker)
  ident() {
    const m = pane()
    return { site: 'whatsapp', title: chatTitle(m), ...bridge(), self: !!$(SEL.you, $(SEL.header, m)) }
  },
  same(chat) {
    return chatTitle(pane()) === chat.title && bridge().jid === chat.jid
  },
  blocked: () => !!bridge().jid?.endsWith('@newsletter'),
  readChat,
  composer: () => composer(pane()),
  anchor: box => (box.closest('footer') || box).getBoundingClientRect(),
  darkFrom: box => box?.closest('footer') || pane() || document.body,
  boxText: box => flat(waText(box)),
  hintFor: chat => (!chat.jid && !chat.pn ? 'using name' : ''),
  // one attempt; ui.js verifies and never retries (a retry is how duplicate text happens)
  insertOnce(box, text) {
    box.focus()
    getSelection().selectAllChildren(box)
    if (INSERT === 'paste') {
      if (flat(waText(box))) document.execCommand('delete')
      const dt = new DataTransfer()
      dt.setData('text/plain', text)
      box.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true }))
    } else {
      document.execCommand('insertText', false, text)
    }
  },
}


// --- live-check helpers (console only) -------------------------------------

function diag() {
  const m = pane()
  const n = s => (m ? m.querySelectorAll(s).length : -1)
  const ids = $$('[data-id]', m).slice(-3).map(e => e.getAttribute('data-id') || '')
  const b = bridge()
  const info = {
    panes: document.querySelectorAll('#main').length, divMain: !!m, header: !!$(SEL.header, m), title: !!$(SEL.title, m),
    rows: n(SEL.rows), system: n(SEL.system), bubbles: n(SEL.bubble), convMsg: n('[data-testid^="conv-msg-"]'),
    dataId: n('[data-id]'), packedIds: ids.filter(i => i.includes('_')).length, pre: n(SEL.pre), text: n(SEL.text),
    quote: n(SEL.quote), author: n(SEL.author), legacyIn: n(SEL.in), legacyOut: n(SEL.out), ticks: n(SEL.ticks),
    composer: SEL.composer.map(n), lexical: n('[data-lexical-editor="true"]'), virtualized: n('[data-virtualized="true"]'),
    icons: [...new Set($$('[data-icon]', m).map(e => e.getAttribute('data-icon')))].sort(),
    dir: m ? getComputedStyle(m).direction : null,
    bridge: { jid: b.jid ? b.jid.replace(/^[\d-]+/, '…') : null, pn: !!b.pn, depth: b.depth },
  }
  console.log('[Quietly] diag', info)
  return info
}

// Redacted per-row view: no message text, names or numbers leave the console.
function probe() {
  const chat = readChat()
  if (chat.error) return chat.error
  const rows = chat.rows.map(r => ({
    me: r.me, sure: r.sure, tick: r.tick, words: r.text.split(/\s+/).length, media: r.text.startsWith('['),
    quote: !!r.quote, labelIsTitle: !!r.label && r.label === chat.title, labelIsMine: !!r.label && r.label === myLabel, author: !!r.who,
  }))
  console.table(rows)
  return { jid: chat.jid ? chat.jid.replace(/^[\d-]+/, '…') : null, pn: !!chat.pn, self: chat.self, stale: chat.stale, today: chat.today, myLabelKnown: !!myLabel, rows: rows.length }
}
