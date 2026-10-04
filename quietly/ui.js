// Quietly shared UI (isolated world): the floating ✨ card, chips, refine row, notes panel and insert-and-verify.
// A site adapter (wa.js or gmail.js) loads first and defines SITE. Nothing here ever sends a message:
// you press send yourself. core.test.js scans this file to keep it that way.

const $ = (s, r = document) => r?.querySelector(s) ?? null
const $$ = (s, r = document) => (r ? [...r.querySelectorAll(s)] : [])
const sleep = ms => new Promise(r => setTimeout(r, ms))
const clip = (s, n) => (s.length > n ? s.slice(0, n - 1) + '…' : s)
const clean = s => String(s ?? '').replace(/[‎‏‪-‮⁦-⁩]/g, '').replace(/[\s  ]+/g, ' ').trim()
const flat = s => String(s ?? '').normalize('NFC').replace(/[\s️]/g, '')

let lastInserted = ''
let busy = false
let blocked = false
let lastHeader = null
let lastTitle = ''
let changedAt = 0

function localToday() {
  try {
    return new Intl.DateTimeFormat(document.documentElement.lang || navigator.language, { year: 'numeric', month: 'numeric', day: 'numeric' }).format(new Date())
  } catch {
    return ''
  }
}

// --- talking to the service worker -----------------------------------------

async function ask(msg) {
  if (!chrome.runtime?.id) throw new Error(`Refresh this ${SITE.label} tab`)
  let timer
  try {
    const res = await Promise.race([
      chrome.runtime.sendMessage(msg),
      new Promise((_, no) => { timer = setTimeout(() => no(new Error('Timed out')), 30000) }),
    ])
    return res || { ok: false, error: 'No answer — try again' }
  } catch (e) {
    const text = String(e?.message || e)
    if (/invalidated|Receiving end/i.test(text)) throw new Error(`Refresh this ${SITE.label} tab`)
    if (/channel closed/i.test(text)) throw new Error('Interrupted — try again')
    throw e
  } finally {
    clearTimeout(timer)
  }
}

const openOptions = () => ask({ type: 'openOptions' }).catch(e => say(e.message, 'error'))

function noteChange() {
  const { node: h, title: t } = SITE.marker()
  if (h === lastHeader && t === lastTitle) return false
  lastHeader = h
  lastTitle = t
  changedAt = Date.now()
  askInput.value = '' // an instruction is for one chat only
  hide() // chips and notices belong to the previous chat
  closePanel()
  blocked = SITE.blocked()
  return true
}

async function run({ refine = '' } = {}) {
  if (busy) return
  busy = true
  pill.disabled = true
  pill.setAttribute('aria-busy', 'true')
  try {
    say('Thinking…', 'busy')
    noteChange() // a switch the 500 ms poll hasn't seen yet still counts
    const at = changedAt
    const wait = 400 - (Date.now() - changedAt)
    if (wait > 0) await sleep(wait)
    if (noteChange() || changedAt !== at) return say('Chat changed — try again')
    const chat = SITE.readChat()
    if (chat.error) return say(chat.error)
    if (flat(chat.draft) === flat(lastInserted)) chat.draft = '' // the chip you just inserted is not a draft
    chat.prompt = askInput.value.replace(/\s+/g, ' ').trim()
    if (refine) Object.assign(chat, { refine, previous: currentChips })
    const res = await ask({ type: 'suggest', chat })
    if (!SITE.same(chat)) { // you moved on: drop the stale chips
      if (status.dataset.kind === 'busy') say('')
      return
    }
    if (!res.ok) return say(res.error, res.code ? 'gear' : 'error')
    showChips(res.replies, chat)
    if (status.dataset.kind !== 'learned') say([res.via && `via ${res.via}`, SITE.hintFor(chat)].filter(Boolean).join(' · '))
  } catch (e) {
    say(e.message, 'error')
  } finally {
    busy = false
    pill.disabled = false
    pill.removeAttribute('aria-busy')
  }
}

// --- inserting -------------------------------------------------------------

// One attempt, then verify. A second attempt is how duplicate text happens, so on failure we copy instead.
async function insert(text) {
  const box = SITE.composer()
  if (!text || !box) return false
  lastInserted = text // set before the editor commits, so a quick ✨ doesn't mistake it for a draft
  const before = SITE.boxText(box)
  SITE.insertOnce(box, text)
  for (let i = 0; i < 20; i++) {
    await sleep(50) // the editor commits a tick later
    if (SITE.boxText(box) === flat(text)) {
      ask({ type: 'picked' }).catch(() => {})
      return true
    }
  }
  if (SITE.boxText(box) === before) {
    const copied = await navigator.clipboard.writeText(text).then(() => true, () => false)
    say(copied ? 'Copied — ⌘V to paste (⌘Z restores your draft)' : "Couldn't insert — type it yourself", 'error')
  } else {
    say('Check the message box')
  }
  return false
}
const testInsert = insert // console helper for the live check

// --- UI --------------------------------------------------------------------

function btn(label, title, onclick, cls = '') {
  const b = document.createElement('button')
  b.type = 'button'
  b.textContent = label
  b.title = title
  b.className = cls
  b.onmousedown = e => e.preventDefault() // keeps the caret in the page's message box
  b.onclick = e => { if (e.isTrusted) onclick(e) }
  return b
}

const host = document.createElement('div')
host.style.cssText = 'position:fixed;z-index:2147483647;pointer-events:none;right:16px;bottom:16px;max-width:calc(100vw - 32px)'
const root = host.attachShadow({ mode: 'open' })
const css = document.createElement('style')
// Your shadcn theme tokens, verbatim; :host(.dark) follows the page's own theme (see sync()).
css.textContent = `
:host { all: initial;
  --background: oklch(0.9605 0.0046 258.3248); --foreground: oklch(0.2153 0.0187 235.1251);
  --card: oklch(1 0 0); --card-foreground: oklch(0.2153 0.0187 235.1251);
  --popover: oklch(1 0 0); --popover-foreground: oklch(0.2153 0.0187 235.1251);
  --primary: oklch(0.4335 0.0754 182.2315); --primary-foreground: oklch(1 0 0);
  --secondary: oklch(0.9644 0.0208 166.1014); --secondary-foreground: oklch(0.4335 0.0754 182.2315);
  --muted: oklch(0.9605 0.0046 258.3248); --muted-foreground: oklch(0.5589 0.0255 233.7233);
  --accent: oklch(0.7610 0.2015 149.7403); --accent-foreground: oklch(1 0 0);
  --destructive: oklch(0.6257 0.2058 29.0773);
  --border: oklch(0.9436 0.0051 228.8204); --input: oklch(0.9436 0.0051 228.8204); --ring: oklch(0.7610 0.2015 149.7403);
  --radius: 16px; --shadow-md: 0px 2px 10px 0px hsl(0 0% 0% / 0.10), 0px 2px 4px -1px hsl(0 0% 0% / 0.10);
  --shadow-lg: 0px 2px 10px 0px hsl(0 0% 0% / 0.10), 0px 4px 6px -1px hsl(0 0% 0% / 0.10);
  --font-sans: "Segoe UI", "Helvetica Neue", Helvetica, "Lucida Grande", Arial, Ubuntu, Cantarell, "Fira Sans", -apple-system, sans-serif;
}
:host(.dark) {
  --background: oklch(0.1854 0.0182 238.2143); --foreground: oklch(0.9436 0.0051 228.8204);
  --card: oklch(0.2848 0.0230 235.6578); --card-foreground: oklch(0.9436 0.0051 228.8204);
  --popover: oklch(0.2848 0.0230 235.6578); --popover-foreground: oklch(0.9436 0.0051 228.8204);
  --primary: oklch(0.6509 0.1283 170.4258); --primary-foreground: oklch(0.2153 0.0187 235.1251);
  --secondary: oklch(0.2933 0.0423 172.8195); --secondary-foreground: oklch(0.6509 0.1283 170.4258);
  --muted: oklch(0.2456 0.0195 239.1061); --muted-foreground: oklch(0.6637 0.0236 235.1968);
  --accent: oklch(0.7610 0.2015 149.7403); --accent-foreground: oklch(0.2153 0.0187 235.1251);
  --border: oklch(0.3351 0.0253 234.8586); --input: oklch(0.3351 0.0253 234.8586); --ring: oklch(0.6509 0.1283 170.4258);
  --shadow-md: 0px 4px 12px 0px hsl(0 0% 0% / 0.40), 0px 2px 4px -1px hsl(0 0% 0% / 0.40);
  --shadow-lg: 0px 4px 12px 0px hsl(0 0% 0% / 0.40), 0px 4px 6px -1px hsl(0 0% 0% / 0.40);
}
* { box-sizing: border-box; }
[hidden] { display: none !important; }
.wrap { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; pointer-events: none; max-height: var(--room, 100vh);
  font: 13px/1.4 var(--font-sans); color: var(--card-foreground); -webkit-font-smoothing: antialiased; }

/* command bar: one floating card */
.cmd { pointer-events: auto; display: flex; flex-wrap: wrap; align-items: center; gap: 6px; max-width: 100%; min-height: 0; overflow-y: auto;
  padding: 6px; border: 1px solid var(--border); border-radius: calc(var(--radius) + 4px);
  background: color-mix(in oklch, var(--card) 92%, transparent); backdrop-filter: blur(8px); box-shadow: var(--shadow-lg); }
.bar { display: contents; }

button { font: inherit; cursor: pointer; border: 1px solid transparent; transition: background-color .12s, color .12s, box-shadow .12s, opacity .12s; }
button:focus-visible, input:focus-visible { outline: none; box-shadow: 0 0 0 3px color-mix(in oklch, var(--ring) 50%, transparent); }
button:disabled { cursor: progress; }

.pill { position: relative; width: 32px; height: 32px; flex: none; border-radius: 999px; padding: 0; font-size: 15px;
  background: var(--primary); color: var(--primary-foreground); box-shadow: var(--shadow-md); }
.pill:hover { background: color-mix(in oklch, var(--primary) 88%, black); }
.pill svg { display: block; width: 18px; height: 18px; margin: auto; }
.pill[aria-busy="true"] { color: transparent; }
.pill[aria-busy="true"]::after { content: ""; position: absolute; inset: 8px; border-radius: 50%;
  border: 2px solid color-mix(in oklch, var(--primary-foreground) 35%, transparent); border-top-color: var(--primary-foreground);
  animation: spin .7s linear infinite; }

.ask { display: flex; flex: none; width: min(240px, 45vw); }
.ask input { width: 100%; height: 32px; padding: 0 12px; border-radius: 999px; font: inherit; color: var(--card-foreground);
  background: transparent; border: 1px solid var(--input); transition: box-shadow .12s, border-color .12s; }
.ask input::placeholder { color: var(--muted-foreground); }
.ask input:focus-visible { border-color: var(--ring); }

.chip { max-width: 100%; min-height: 32px; padding: 6px 12px; border-radius: 999px; text-align: left; line-height: 1.3;
  background: var(--secondary); color: var(--secondary-foreground); border-color: color-mix(in oklch, var(--secondary-foreground) 15%, transparent);
  animation: rise .12s ease-out both; }
.chip:hover { background: color-mix(in oklch, var(--accent) 22%, var(--secondary)); }
.chip:nth-of-type(2) { animation-delay: .03s; } .chip:nth-of-type(3) { animation-delay: .06s; }
.ghost { width: 30px; height: 30px; padding: 0; border-radius: 999px; background: transparent; color: var(--muted-foreground); flex: none; font-size: 16px; line-height: 1; }
.ghost:hover { background: var(--muted); color: var(--card-foreground); }

/* status: popover */
.status { pointer-events: auto; max-width: 100%; padding: 8px 12px; font-size: 12.5px; color: var(--popover-foreground);
  background: var(--popover); border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow-md);
  display: flex; flex-wrap: wrap; align-items: center; gap: 4px 6px; animation: rise .12s ease-out both; }
.status:empty { padding: 0; border: 0; box-shadow: none; background: none; animation: none; }
.status[data-kind="info"], .status[data-kind="busy"] { color: var(--muted-foreground); }
.status[data-kind="error"], .status[data-kind="gear"] { color: var(--destructive); border-left: 3px solid var(--destructive); }
.status .ghost { width: 22px; height: 22px; font-size: 13px; }
.badge { display: inline-flex; align-items: center; gap: 4px; padding: 1px 8px; border-radius: 999px; font-size: 11.5px; font-weight: 600;
  background: color-mix(in oklch, var(--accent) 18%, transparent); color: var(--card-foreground); }
.fact { display: inline-flex; align-items: center; gap: 2px; padding: 1px 2px 1px 8px; border-radius: 999px; background: var(--muted); }

.chip.draft { flex-basis: 100%; border-radius: calc(var(--radius) - 4px); padding: 10px 12px; white-space: pre-wrap;
  line-height: 1.45; max-height: 10.5em; overflow-y: auto; }
.chip kbd { margin-left: 8px; font: 600 10.5px var(--font-sans); opacity: .55; }
.refine { flex-basis: 100%; display: flex; flex-wrap: wrap; gap: 4px; padding: 2px 2px 0; }
.refine button { height: 26px; padding: 0 10px; border-radius: 999px; font-size: 12px; background: transparent;
  color: var(--muted-foreground); border-color: var(--border); }
.refine button:hover { background: var(--muted); color: var(--card-foreground); }
.tools { display: flex; }
/* calm idle: just the sparkle until you hover, focus or use it */
.cmd:not(:hover):not(:focus-within):not(.open) .ask,
.cmd:not(:hover):not(:focus-within):not(.open) .tools { display: none; }

.panel { pointer-events: auto; width: min(360px, 90vw); padding: 12px; display: grid; gap: 8px; font-size: 12.5px;
  color: var(--popover-foreground); background: var(--popover); border: 1px solid var(--border);
  border-radius: var(--radius); box-shadow: var(--shadow-lg); animation: rise .12s ease-out both; }
.panel header { display: flex; align-items: center; gap: 8px; }
.panel header strong { font-size: 13.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.panel header .ghost { margin-left: auto; width: 26px; height: 26px; font-size: 14px; }
.panel .lbl { font-weight: 600; font-size: 12px; }
.panel textarea { width: 100%; min-height: 64px; resize: vertical; font: inherit; color: inherit; background: transparent;
  border: 1px solid var(--input); border-radius: calc(var(--radius) - 4px); padding: 7px 10px; }
.panel textarea:focus-visible { border-color: var(--ring); box-shadow: 0 0 0 3px color-mix(in oklch, var(--ring) 50%, transparent); outline: none; }
.panel ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; max-height: 140px; overflow: auto; }
.panel li { display: flex; align-items: flex-start; gap: 6px; padding: 4px 4px 4px 8px; border-radius: 8px; }
.panel li:hover { background: var(--muted); }
.panel li span { flex: 1; }
.panel li small, .panel .muted { color: var(--muted-foreground); }
.panel li small { white-space: nowrap; }
.panel li .ghost { width: 22px; height: 22px; font-size: 13px; }
.switch { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.switch input { appearance: none; width: 32px; height: 18px; border-radius: 999px; background: var(--input); position: relative;
  cursor: pointer; transition: background-color .15s; flex: none; margin: 0; }
.switch input::after { content: ""; position: absolute; top: 2px; left: 2px; width: 14px; height: 14px; border-radius: 50%;
  background: var(--card); box-shadow: 0 1px 2px hsl(0 0% 0% / .3); transition: transform .15s; }
.switch input:checked { background: var(--primary); }
.switch input:checked::after { transform: translateX(14px); }
.switch input:focus-visible { box-shadow: 0 0 0 3px color-mix(in oklch, var(--ring) 50%, transparent); outline: none; }
.saved { font-size: 11px; color: var(--muted-foreground); }

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes rise { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: none; } }
@media (prefers-reduced-motion: reduce) { * { animation: none !important; transition: none !important; } }`
const wrap = document.createElement('div')
wrap.className = 'wrap'
const status = document.createElement('div') // always rendered so screen readers announce updates
status.className = 'status'
status.setAttribute('role', 'status')
status.setAttribute('aria-live', 'polite')
const cmd = document.createElement('div')
cmd.className = 'cmd'
const pill = btn('', 'Suggest replies', () => run(), 'pill') // icon: the SVG sparkle below
// The logo's sparkle, built node by node (no HTML strings, no extension URLs exposed to the page).
const spark = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
spark.setAttribute('viewBox', '0 0 24 24')
spark.setAttribute('aria-hidden', 'true')
const sparkPath = document.createElementNS('http://www.w3.org/2000/svg', 'path')
sparkPath.setAttribute('d', 'M11 3C11.6 8.1 13.4 9.9 18.5 10.5C13.4 11.1 11.6 12.9 11 18C10.4 12.9 8.6 11.1 3.5 10.5C8.6 9.9 10.4 8.1 11 3ZM18.5 15C18.7 16.6 19.4 17.3 21 17.5C19.4 17.7 18.7 18.4 18.5 20C18.3 18.4 17.6 17.7 16 17.5C17.6 17.3 18.3 16.6 18.5 15Z')
sparkPath.setAttribute('fill', 'currentColor')
spark.append(sparkPath)
pill.append(spark)
// Optional one-off instruction. Submitting the form (from our own input) runs ✨; it never touches the page's message box.
const askForm = document.createElement('form')
askForm.className = 'ask'
const askInput = document.createElement('input')
askInput.type = 'text'
askInput.maxLength = 200
askInput.placeholder = 'Optional: what should the reply say?'
askInput.setAttribute('aria-label', 'Optional instruction for the replies')
askForm.append(askInput)
let typedSubmit = false
askInput.addEventListener('keydown', e => { typedSubmit = e.isTrusted && e.keyCode === 13 })
askForm.onsubmit = e => {
  e.preventDefault()
  if (typedSubmit) run()
  typedSubmit = false
}
const bar = document.createElement('div')
bar.className = 'bar'
bar.hidden = true
const tools = document.createElement('div')
tools.className = 'tools'
tools.append(btn('\u{1F4DD}', 'Notes & memory for this chat', () => togglePanel(), 'ghost'))
const panel = document.createElement('div')
panel.className = 'panel'
panel.hidden = true
panel.setAttribute('role', 'dialog')
panel.setAttribute('aria-label', 'Memory for this chat')
cmd.append(pill, askForm, tools, bar)
wrap.append(panel, status, cmd)
askInput.addEventListener('input', () => cmd.classList.toggle('open', !bar.hidden || !!askInput.value))
root.append(css, wrap)
document.body.append(host)
host.addEventListener('keydown', e => e.stopPropagation()) // keys on our buttons stay ours

// Match the page's own light/dark theme by sampling the background behind the message box.
function isDark(el) {
  for (let e = el; e; e = e.parentElement) {
    const m = /rgba?\(([\d.]+)[ ,]+([\d.]+)[ ,]+([\d.]+)(?:[ ,/]+([\d.]+))?/.exec(getComputedStyle(e).backgroundColor)
    if (m && (m[4] === undefined || +m[4] > 0.5)) return 0.299 * m[1] + 0.587 * m[2] + 0.114 * m[3] < 128
  }
  return matchMedia('(prefers-color-scheme: dark)').matches
}

function say(text = '', kind = '') {
  status.dataset.kind = kind || (text ? 'info' : '')
  status.replaceChildren(text)
  if (kind === 'gear') status.append(btn('⚙', 'Open settings', openOptions, 'ghost'))
}

const MAC = /Mac/i.test(navigator.platform)
const REFINE = SITE.refines
let currentChips = []

function showChips(replies, chat) {
  currentChips = replies
  const chips = replies.map((t, i) => {
    const b = btn(t, 'Put in the message box', () => {
      if (noteChange() || !SITE.same(chat)) return hide()
      hide()
      askInput.value = ''
      insert(t)
    }, SITE.name === 'gmail' ? 'chip draft' : 'chip')
    b.setAttribute('aria-label', t)
    const k = document.createElement('kbd')
    k.setAttribute('aria-hidden', 'true')
    k.textContent = `${MAC ? '⌥' : 'Alt+'}${i + 1}`
    b.append(k)
    return b
  })
  const refine = document.createElement('div')
  refine.className = 'refine'
  refine.append(...REFINE.map(([id, label]) => btn(label, `Rewrite these: ${label.slice(3)}`, () => run({ refine: id }))))
  bar.replaceChildren(
    ...chips,
    btn('⚙', 'Settings', openOptions, 'ghost'),
    refine,
  )
  bar.hidden = false
  cmd.classList.add('open')
}

function hide() {
  bar.hidden = true
  bar.replaceChildren()
  currentChips = []
  cmd.classList.toggle('open', !!askInput.value)
  if (status.dataset.kind !== 'busy') say('')
}

// --- notes panel: this chat's notes, facts and learning switch ---------------

function closePanel() {
  panel.hidden = true
  panel.replaceChildren() // nothing private lingers in the page
}

async function togglePanel() {
  if (!panel.hidden) return closePanel()
  const chat = SITE.ident()
  let res
  try {
    res = await ask({ type: 'contactGet', chat })
  } catch (e) {
    return say(e.message, 'error')
  }
  if (!res.ok) return say(res.error, 'error')
  const el = (tag, cls, text) => {
    const n = document.createElement(tag)
    if (cls) n.className = cls
    if (text != null) n.textContent = text
    return n
  }
  const head = el('header')
  head.append(el('strong', '', res.name || 'This chat'), el('span', 'badge', res.kind === 'group' ? 'group' : '1:1'), btn('×', 'Close', closePanel, 'ghost'))
  const notes = el('textarea')
  notes.maxLength = 800
  notes.value = res.notes || ''
  notes.placeholder = SITE.notesHint
  notes.setAttribute('aria-label', 'Notes for this chat')
  notes.disabled = res.readOnly
  const saved = el('span', 'saved')
  let t
  const save = () => {
    clearTimeout(t)
    t = 0
    ask({ type: 'contactNotes', chat, notes: notes.value }).then(r => { saved.textContent = r.ok ? 'Saved' : r.error }, e => { saved.textContent = e.message })
  }
  notes.addEventListener('input', e => {
    if (!e.isTrusted) return
    clearTimeout(t)
    saved.textContent = ''
    t = setTimeout(save, 400)
  })
  notes.addEventListener('blur', () => t && save())
  const list = el('ul')
  const facts = res.facts || []
  if (!facts.length) list.append(el('li', 'muted', res.kind === 'group' ? 'Groups learn no facts.' : 'No facts yet.'))
  for (const f of facts) {
    const li = el('li')
    const txt = el('span', '', f.text)
    txt.append(el('small', '', ` · ${f.who === 'me' ? 'you said' : 'they said'} · ${f.at}`))
    li.append(txt, btn('×', 'Forget this fact', () => {
      li.remove()
      ask({ type: 'forgetFact', key: res.key, factId: f.id }).catch(e => say(e.message, 'error'))
    }, 'ghost'))
    list.append(li)
  }
  const sw = el('label', 'switch')
  const box = el('input')
  box.type = 'checkbox'
  box.checked = !res.noLearn
  box.disabled = res.readOnly
  box.setAttribute('aria-label', 'Learn from this chat')
  box.addEventListener('change', e => {
    if (!e.isTrusted) return (box.checked = !box.checked)
    ask({ type: 'contactLearn', chat, learn: box.checked }).catch(err => say(err.message, 'error'))
  })
  sw.append(box, el('span', '', 'Learn from this chat'))
  panel.replaceChildren(head, el('span', 'lbl', 'Notes'), notes, saved, el('span', 'lbl', 'Learned facts'), list, sw)
  if (res.readOnly) panel.append(el('span', 'muted', "This chat's id is unknown right now, so memory is read-only."))
  panel.hidden = false
  notes.focus()
}

function notice({ key, name, facts }) {
  if (!Array.isArray(facts) || !facts.length) return
  status.dataset.kind = 'learned'
  const badge = document.createElement('span')
  badge.className = 'badge'
  badge.textContent = `🧠 ${clip(String(name ?? ''), 40)}`
  status.replaceChildren(badge)
  for (const f of facts) {
    const span = document.createElement('span')
    span.className = 'fact'
    span.append(String(f.text ?? ''), btn('×', 'Forget this', () => {
      span.remove()
      ask({ type: 'forgetFact', key, factId: f.id }).catch(e => say(e.message, 'error'))
    }, 'ghost'))
    status.append(span)
  }
}

function sync() {
  noteChange()
  const box = SITE.composer()
  cmd.hidden = !box || blocked
  host.classList.toggle('dark', isDark(SITE.darkFrom(box)))
  if (box) {
    const r = SITE.anchor(box)
    const width = Math.min(Math.max(0, r.width - 16), SITE.name === 'gmail' ? 680 : Infinity)
    const bottom = Math.min(Math.max(innerHeight - r.top + 8, 16), innerHeight - 160) // keep the card on screen
    Object.assign(host.style, { left: `${r.left + 8}px`, right: 'auto', bottom: `${bottom}px`, width: `${width}px` })
    host.style.setProperty('--room', `${innerHeight - bottom - 8}px`) // never taller than the space above the box
  } else {
    Object.assign(host.style, { left: 'auto', right: '16px', bottom: '16px', width: 'auto' })
  }
}
setInterval(sync, 500) // ponytail: 500 ms poll; MutationObserver + rAF batching if CPU ever shows up
sync()

// Esc closes our bar only, and only while it is open, so the page doesn't also close the chat or draft.
addEventListener('keydown', e => {
  // Option/Alt+1/2/3 picks a chip; e.code because Option+1 types a symbol on a Mac
  if (!bar.hidden && e.isTrusted && e.altKey && !e.ctrlKey && !e.metaKey && /^Digit[1-3]$/.test(e.code)) {
    const chip = bar.querySelectorAll('.chip')[+e.code.slice(5) - 1]
    e.preventDefault()
    e.stopImmediatePropagation()
    return chip?.onclick({ isTrusted: true })
  }
  if (e.key !== 'Escape') return
  if (!panel.hidden) {
    e.preventDefault()
    e.stopImmediatePropagation()
    return closePanel()
  }
  if (bar.hidden) return status.dataset.kind !== 'busy' && say('')
  e.preventDefault()
  e.stopImmediatePropagation()
  hide()
}, true)

chrome.runtime.onMessage.addListener(msg => {
  if (msg?.type === 'trigger') run()
  else if (msg?.type === 'learned') notice(msg)
})
