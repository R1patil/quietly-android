import {
  DEFAULTS, CAPS, exportData, importData, modelOptions, ytModelOptions, isoDay, weekSummary,
  styleFromPresets, PRESET_LABELS, PRESET_DEFAULTS, DEMO_CHAT,
} from './core.js'

const store = chrome.storage.local
const $ = id => document.getElementById(id)
const send = msg => chrome.runtime.sendMessage(msg)

function h(tag, props = {}, ...kids) {
  const el = document.createElement(tag)
  for (const [k, v] of Object.entries(props)) {
    if (k in el) el[k] = v
    else el.setAttribute(k, v)
  }
  el.append(...kids.filter(k => k != null && k !== false))
  return el
}

// Every field here has exactly one writer (this page), so background learning can never clobber your typing.
function autosave(el, key, value, anchor = el) {
  const tag = h('span', { className: 'saved' })
  anchor.after(tag)
  let t
  const save = () => {
    clearTimeout(t)
    t = 0
    store.set({ [key]: value() }).then(
      () => { tag.className = 'saved'; tag.textContent = 'Saved' },
      e => { tag.className = 'saved err'; tag.textContent = `Not saved: ${e.message}` },
    )
  }
  el.addEventListener('input', () => {
    clearTimeout(t)
    tag.textContent = ''
    t = setTimeout(save, 400)
  })
  el.addEventListener('change', save)
  addEventListener('pagehide', () => t && save()) // the popup closes on any outside click: flush a pending edit
}

// --- OpenRouter ------------------------------------------------------------

const all = await store.get(null)
$('apiKey').value = all.apiKey || ''
$('model').value = all.model || DEFAULTS.model
$('model').placeholder = DEFAULTS.model
$('styleGuide').value = all.styleGuide ?? DEFAULTS.styleGuide
autosave($('apiKey'), 'apiKey', () => $('apiKey').value.trim(), $('apiKey').parentElement)

function keyBadge(text, tone) {
  $('keyBadge').textContent = text
  $('keyBadge').className = `badge ${tone}`
}
keyBadge(all.apiKey ? 'Key saved' : 'No key', all.apiKey ? '' : 'muted')
$('apiKey').addEventListener('input', () => keyBadge($('apiKey').value.trim() ? 'Key saved' : 'No key', $('apiKey').value.trim() ? '' : 'muted'))
$('reveal').onclick = () => {
  const show = $('apiKey').type === 'password'
  $('apiKey').type = show ? 'text' : 'password'
  $('reveal').title = $('reveal').ariaLabel = show ? 'Hide key' : 'Show key'
}
const validModel = v => /^~?[\w.:~-]+(\/[\w.:~-]+)?$/.test(v)
autosave($('model'), 'model', () => {
  const v = $('model').value.trim()
  return validModel(v) ? v : $('model').placeholder || DEFAULTS.model
})
// YouTube sorting has its own model, independent of replies.
$('ytModel').value = all.ytModel || DEFAULTS.ytModel
autosave($('ytModel'), 'ytModel', () => {
  const v = $('ytModel').value.trim()
  return validModel(v) ? v : $('ytModel').placeholder || DEFAULTS.ytModel
})
autosave($('styleGuide'), 'styleGuide', () => $('styleGuide').value.slice(0, CAPS.guide))
$('styleGuideMail').value = all.styleGuideMail ?? DEFAULTS.styleGuideMail
autosave($('styleGuideMail'), 'styleGuideMail', () => $('styleGuideMail').value.slice(0, CAPS.guide))

// Model list for the searchable dropdown: your account's list when a key is set, else the public one.
async function loadModels() {
  const info = $('modelInfo')
  info.textContent = 'Loading models…'
  const key = $('apiKey').value.trim()
  const isGroq = key.startsWith('gsk_')

  if (isGroq) {
    try {
      const res = await fetch('https://api.groq.com/openai/v1/models', {
        headers: { Authorization: `Bearer ${key}` },
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const j = await res.json()
      const GROQ_PRIORITY = [
        'llama-3.3-70b-versatile',
        'llama-3.1-8b-instant',
        'llama3-70b-8192',
        'llama3-8b-8192',
        'mixtral-8x7b-32768',
        'gemma2-9b-it',
        'deepseek-r1-distill-llama-70b',
      ]

      const isChatModel = id => {
        const s = String(id || '').toLowerCase()
        if (s.includes('whisper') || s.includes('orpheus') || s.includes('canopy') || s.includes('embed') || 
            s.includes('guard') || s.includes('allam') || s.includes('vision') || s.includes('audio') || s.includes('tts')) {
          return false
        }
        return s.includes('llama') || s.includes('mixtral') || s.includes('gemma') || s.includes('deepseek') || s.includes('qwen')
      }

      const list = (j.data || [])
        .filter(m => m.active !== false && isChatModel(m.id))
        .map(m => ({ value: m.id, label: `${m.id} · free (Groq)` }))
        .sort((a, b) => {
          const pA = GROQ_PRIORITY.indexOf(a.value)
          const pB = GROQ_PRIORITY.indexOf(b.value)
          if (pA !== -1 && pB !== -1) return pA - pB
          if (pA !== -1) return -1
          if (pB !== -1) return 1
          return a.value.localeCompare(b.value)
        })

      const modelNames = list.map(m => m.value)
      console.log('[Quietly] Groq chat models from key:', modelNames)

      const current = $('model').value.trim()
      const currentYt = $('ytModel').value.trim()

      const fill = (id, os) => $(id).replaceChildren(...os.map(o => h('option', { value: o.value, label: o.label })))
      fill('models', list)
      fill('ytModels', list)

      let chosen = current
      if (!chosen || !modelNames.includes(chosen) || !isChatModel(chosen)) {
        chosen = modelNames[0] || 'llama-3.3-70b-versatile'
        $('model').value = chosen
        await store.set({ model: chosen })
      }
      let chosenYt = currentYt
      if (!chosenYt || !modelNames.includes(chosenYt) || !isChatModel(chosenYt)) {
        chosenYt = modelNames.find(n => n.includes('8b')) || chosen
        $('ytModel').value = chosenYt
        await store.set({ ytModel: chosenYt })
      }
      info.textContent = `Groq connected! ${list.length} models available (click to select):`

      const pills = $('modelPills')
      if (pills) {
        pills.replaceChildren(...list.map(m => {
          const btn = h('button', {
            type: 'button',
            className: m.value === chosen ? 'primary' : '',
            style: 'padding: 4px 10px; font-size: 11px; border-radius: 12px; cursor: pointer;',
          }, m.value)
          btn.onclick = () => {
            $('model').value = m.value
            store.set({ model: m.value })
            Array.from(pills.children).forEach(c => c.className = '')
            btn.className = 'primary'
          }
          return btn
        }))
      }
    } catch (e) {
      info.textContent = `Couldn't load Groq models (${e.message}). You can still type a model ID.`
    }
    return
  }

  const get = async (url, auth) => {
    const r = await fetch(url, auth ? { headers: { Authorization: `Bearer ${auth}` } } : {})
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return (await r.json()).data
  }
  try {
    const list = key
      ? await get('https://openrouter.ai/api/v1/models/user', key).catch(() => get('https://openrouter.ai/api/v1/models'))
      : await get('https://openrouter.ai/api/v1/models')
    const current = $('model').value.trim() || $('model').placeholder || DEFAULTS.model
    const opts = modelOptions(list, current)
    const fill = (id, os) => $(id).replaceChildren(...os.map(o => h('option', { value: o.value, label: o.label })))
    fill('models', opts)
    fill('ytModels', ytModelOptions(list, $('ytModel').value.trim() || $('ytModel').placeholder || DEFAULTS.ytModel))
    const cur = opts.find(o => o.value === current)
    info.textContent = `${opts.length} models${cur && cur.label !== cur.value ? ` · current: ${cur.label}` : ''}`
  } catch (e) {
    info.textContent = `Couldn't load models (${e.message}). You can still type a model ID.`
  }
}
loadModels()

// The datalist only shows options matching the current text, so empty the field on focus to show the whole
// list (current model as placeholder) and put it back if nothing was picked. Programmatic edits don't autosave.
function picker(el, fallback) {
  el.placeholder = fallback
  el.addEventListener('focus', () => {
    el.placeholder = el.value || fallback
    el.value = ''
  })
  el.addEventListener('blur', () => {
    if (!validModel(el.value.trim())) {
      el.value = el.placeholder
      el.dispatchEvent(new Event('change')) // re-save: typing then deleting may have saved ''
    }
    el.placeholder = fallback
  })
}
picker($('model'), DEFAULTS.model)
picker($('ytModel'), DEFAULTS.ytModel)

$('test').onclick = async () => {
  const key = $('apiKey').value.trim()
  const info = $('keyInfo')
  if (!key) return (info.textContent = 'Paste a key first')
  info.textContent = 'Checking…'
  await store.set({ apiKey: key })

  if (key.startsWith('gsk_')) {
    try {
      const r = await fetch('https://api.groq.com/openai/v1/models', {
        headers: { Authorization: `Bearer ${key}` },
      })
      const j = await r.json().catch(() => ({}))
      if (!r.ok) {
        keyBadge('Invalid Groq key', 'bad')
        return (info.textContent = j?.error?.message || `HTTP ${r.status}`)
      }
      keyBadge('Groq connected', 'ok')
      if (step === 1) goStep(2)
      await loadModels()
    } catch (e) {
      keyBadge('Error', 'bad')
      info.textContent = `Couldn't reach Groq: ${e.message}`
    }
    return
  }

  try {
    const r = await fetch('https://openrouter.ai/api/v1/key', { headers: { Authorization: `Bearer ${key}` } })
    const j = await r.json().catch(() => ({}))
    if (!r.ok) {
      keyBadge('Invalid key', 'bad')
      return (info.textContent = j?.error?.message || `HTTP ${r.status}`)
    }
    keyBadge('Connected', 'ok')
    if (step === 1) goStep(2)
    const d = j.data || {}
    const money = v => `$${(+v || 0).toFixed(2)}`
    info.textContent = [
      `Used ${money(d.usage)}`,
      d.limit == null ? '⚠ no credit limit on this key: create a dedicated key with a limit' : `${money(d.limit_remaining)} left of ${money(d.limit)}`,
      d.expires_at && `expires ${String(d.expires_at).slice(0, 10)}`,
    ].filter(Boolean).join(' · ')
    loadModels() // the key works: reload with your account's model list
  } catch (e) {
    info.textContent = `Couldn't reach OpenRouter: ${e.message}`
  }
}

// --- learned style -----------------------------------------------------------

function renderStyle(style = {}, mail = false) {
  const s = style.stats || {}
  const plural = `${s.n} ${mail ? 'email' : 'message'}${s.n === 1 ? '' : 's'}`
  const badges = !s.n ? []
    : mail ? [plural, `~${s.medianWords} words`, `${s.greetPct}% greeting`, `${s.signoffPct}% sign-off`]
    : [plural, `~${s.medianWords} words`, `${s.lowerPct}% lowercase`, `${s.dotEndPct}% end with "."`, `${s.emojiPct}% emoji`]
  $(mail ? 'statsMail' : 'stats').replaceChildren(...(badges.length ? badges.map(b => h('span', { className: 'badge' }, b)) : [h('span', { className: 'meta' }, 'Nothing learned yet.')]))
  $(mail ? 'bankMail' : 'bank').replaceChildren(...(style.bank || []).slice().reverse().map(t => h('li', {}, mail ? t.slice(0, 120) + (t.length > 120 ? '…' : '') : t)))
}

$('clearStyle').onclick = async () => {
  if (confirm('Forget everything learned about your texting style?')) await send({ type: 'clearStyle' })
}
$('clearStyleMail').onclick = async () => {
  if (confirm('Forget everything learned about your email style?')) await send({ type: 'clearStyle', site: 'gmail' })
}

// --- contacts --------------------------------------------------------------

const cards = new Map() // key → card; built once so a notes field is never re-rendered under your cursor
const empty = h('p', { className: 'empty' }, 'No contacts yet. Use ✨ in a chat first.')
$('contacts').before(empty)

function card(k, notes) {
  const title = h('span', { className: 'name' })
  const kind = h('span', { className: 'badge' })
  const meta = h('span', { className: 'meta' })
  const area = h('textarea', { rows: 3, maxLength: CAPS.notes, value: notes, placeholder: 'Who they are, tone, how to reply. e.g. "college friend, roast him, Hinglish"' })
  const facts = h('ul', { className: 'facts' })
  const forget = h('button', {
    type: 'button',
    className: 'danger',
    onclick: async () => {
      if (!confirm(`Forget everything about ${title.textContent}?`)) return
      await store.remove(`notes:${k}`)
      await send({ type: 'forgetContact', key: k })
    },
  }, 'Forget contact')
  const el = h('details', {}, h('summary', {}, title, kind, meta),
    h('div', { className: 'panel' }, h('span', { className: 'label' }, 'Notes'), area, h('span', { className: 'label' }, 'Learned facts'), facts, h('div', { className: 'row' }, forget)))
  autosave(area, `notes:${k}`, () => area.value.slice(0, CAPS.notes))
  return { el, title, kind, meta, facts, area }
}

function factItem(k, f) {
  const when = `${f.who === 'me' ? 'you said' : 'they said'} · ${f.at}${f.exp ? ` · until ${f.exp}` : ''}`
  return h('li', {}, h('span', { className: 'text' }, f.text, h('small', { title: `From the message: “${f.q}”` }, when)),
    h('button', { type: 'button', className: 'icon', title: 'Forget this fact', onclick: () => send({ type: 'forgetFact', key: k, factId: f.id }) }, '×'))
}

function renderContacts(data) {
  const keys = Object.keys(data).filter(k => k.startsWith('mem:')).map(k => k.slice(4))
    .sort((a, b) => (data[`mem:${b}`].usedAt || 0) - (data[`mem:${a}`].usedAt || 0))
  const box = $('contacts')
  for (const [k, c] of cards) if (!keys.includes(k)) { c.el.remove(); cards.delete(k) }
  keys.forEach((k, i) => {
    const mem = data[`mem:${k}`]
    let c = cards.get(k)
    if (!c) cards.set(k, (c = card(k, data[`notes:${k}`] || '')))
    const group = k.endsWith('@g.us')
    const mail = k.startsWith('email:')
    c.title.textContent = mem.name || (mail ? k.slice(6) : k)
    c.kind.textContent = mail ? 'Gmail' : group ? 'WhatsApp group' : 'WhatsApp'
    c.meta.textContent = mem.usedAt ? `used ${new Date(mem.usedAt).toLocaleDateString()}` : ''
    const list = (mem.facts || []).map(f => factItem(k, f))
    c.facts.replaceChildren(...(list.length ? list : [h('li', {}, h('small', {}, group ? 'Groups learn no facts.' : 'No facts yet.'))]))
    if (box.children[i] !== c.el) box.insertBefore(c.el, box.children[i] || null)
  })
  empty.hidden = keys.length > 0
}

renderStyle(all.style)
renderStyle(all.styleMail, true)
renderContacts(all)

// --- this week ---------------------------------------------------------------

function renderWeek(stats) {
  const w = weekSummary(stats, isoDay(new Date()))
  const tile = (value, label) => h('div', { className: 'tile' }, h('strong', {}, value), h('span', {}, label))
  $('week').replaceChildren(
    tile(String(w.asked), 'times you asked'),
    tile(String(w.inserted), 'replies inserted'),
    tile(w.inserted ? `${w.asIsPct}%` : '—', 'sent without edits'),
    tile(`${w.minutesSaved} min`, `saved · $${w.cost.toFixed(3)} spent`),
  )
}
renderWeek(all.stats)

// --- YouTube -----------------------------------------------------------------

// ponytail: kept in storage.sync, not local: yt.js on youtube.com must read it, and local is locked to trusted pages.
const ytBtn = $('ytToggle')
function showYt(on) {
  ytBtn.ariaPressed = String(on)
  ytBtn.className = on ? 'primary' : ''
  ytBtn.textContent = on ? 'On' : 'Off'
}
showYt(!(await chrome.storage.sync.get('ytOff')).ytOff)
ytBtn.onclick = () => {
  const on = ytBtn.ariaPressed !== 'true'
  showYt(on)
  chrome.storage.sync.set({ ytOff: !on }).catch(() => showYt(!on))
}

// What really answered: a router or the fallback can hand the batch to another model.
function showYtLast(l) {
  $('ytLast').textContent = !l ? 'No videos sorted yet.' : [
    `Last batch: ${l.n} videos`,
    `asked ${l.model} → answered by ${l.served || 'unknown'}`,
    `${l.tokensIn} in + ${l.tokensOut} out tokens`,
    `$${(+l.cost || 0).toFixed(4)}`,
    new Date(l.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
  ].join(' · ')
}
showYtLast(all.ytLast)

// --- guided setup ------------------------------------------------------------

let step = 0
function goStep(n) {
  step = n
  for (const el of document.querySelectorAll('.step')) {
    const k = +el.dataset.step
    el.classList.toggle('done', k < n)
    el.classList.toggle('now', k === n)
  }
  for (const el of document.querySelectorAll('[data-pane]')) el.hidden = +el.dataset.pane !== n
}
function startSetup() {
  document.body.classList.add('setup')
  goStep($('apiKey').value.trim() ? 2 : 1)
}

let presets = { ...PRESET_DEFAULTS, ...all.presets }
function renderPresets() {
  for (const seg of document.querySelectorAll('[data-preset]')) {
    const k = seg.dataset.preset
    seg.replaceChildren(...Object.entries(PRESET_LABELS[k]).map(([v, label]) => h('button', {
      type: 'button',
      ariaPressed: String(presets[k] === v),
      onclick: async () => {
        presets = { ...presets, [k]: v }
        $('styleGuide').value = styleFromPresets(presets)
        await store.set({ presets, styleGuide: $('styleGuide').value })
        renderPresets()
      },
    }, label)))
  }
}
renderPresets()
$('sample').replaceChildren(...DEMO_CHAT.rows.map(r => h('div', { className: `bubble${r.me ? ' me' : ''}` }, r.text)))

$('stylesNext').onclick = async () => {
  if (!all.presets) await store.set({ presets, styleGuide: styleFromPresets(presets) }) // kept the defaults
  $('styleGuide').value = styleFromPresets(presets)
  goStep(3)
}
$('demo').onclick = async () => {
  $('demo').disabled = true
  $('demoInfo').textContent = 'Thinking…'
  $('demoChips').replaceChildren()
  try {
    const r = await send({ type: 'demo' })
    if (!r?.ok) throw new Error(r?.error || 'No answer')
    $('demoChips').replaceChildren(...r.replies.map(t => h('span', {}, t)))
    $('demoInfo').textContent = 'These are real suggestions. Happy? Press Done, then open WhatsApp Web.'
  } catch (e) {
    $('demoInfo').textContent = e.message
  } finally {
    $('demo').disabled = false
  }
}
$('setupDone').onclick = async () => {
  await store.set({ setupDone: true })
  document.body.classList.remove('setup')
}
$('rerunSetup').onclick = startSetup
if (!all.apiKey || all.setupDone !== true) startSetup()

// The service worker writes facts and style while this page is open: redraw those parts, never the text fields.
store.onChanged.addListener(async changes => {
  if (changes.stats) renderWeek(changes.stats.newValue)
  if (changes.ytLast) showYtLast(changes.ytLast.newValue)
  for (const [k, c] of Object.entries(changes)) { // notes edited from the WhatsApp 📝 panel
    const area = k.startsWith('notes:') && cards.get(k.slice(6))?.area
    if (area && document.activeElement !== area) area.value = c.newValue || ''
  }
  if (!Object.keys(changes).some(k => k === 'style' || k === 'styleMail' || k.startsWith('mem:'))) return
  const data = await store.get(null)
  renderStyle(data.style)
  renderStyle(data.styleMail, true)
  renderContacts(data)
})

// --- backup ----------------------------------------------------------------

$('export').onclick = async () => {
  const blob = new Blob([JSON.stringify(exportData(await store.get(null)), null, 1)], { type: 'application/json' })
  const a = h('a', { href: URL.createObjectURL(blob), download: `wa-reply-backup-${new Date().toISOString().slice(0, 10)}.json` })
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}

if (location.search === '?popup') {
  $('import').addEventListener('click', e => {
    e.preventDefault()
    chrome.runtime.openOptionsPage()
  })
}

$('import').onchange = async e => {
  const f = e.target.files?.[0]
  if (!f) return
  try {
    const data = importData(JSON.parse(await f.text()))
    await store.set(data)
    $('io').textContent = `Imported ${Object.keys(data).length} items.`
    setTimeout(() => location.reload(), 800)
  } catch (err) {
    $('io').textContent = `Import failed: ${err.message}`
  }
  e.target.value = ''
}
