// Quietly service worker: the only code that holds the API key, calls OpenRouter, or writes memory.
import {
  DEFAULTS, CAPS, norm, isoDay, keyFor, chatKind, capRows, emptyMem, liveFacts, newRows, mergeSeen,
  applyFacts, harvest, buildReplyPrompt, buildExtractPrompt, cleanReplies, requestBody, parseCompletion,
  errorFor, bump, DEMO_CHAT, REPLY_SCHEMA, FACTS_SCHEMA, TRIAGE_SCHEMA, cleanVideos, buildTriagePrompt, keptIds, capCache,
  isDecisionModel, buildTriageDecisions, keptFromDecisions,
} from './core.js'

const store = chrome.storage.local
store.setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' }).catch(console.error) // keep the key away from the WhatsApp page
let q = Promise.resolve()
const tx = f => (q = q.then(f, f)) // one queue for every read-modify-write, so handlers never interleave
const learning = new Set() // one learn() per contact at a time
const forgets = new Map() // key → times forgotten, so a learn() already in flight can't bring a contact back

chrome.runtime.onInstalled.addListener(({ reason }) => {
  // Lock the key and memory away from the content script on web.whatsapp.com (persists; Chrome 140+).
  store.setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' }).catch(console.error)
  if (reason === 'install') chrome.runtime.openOptionsPage()
})

// The toolbar icon opens settings as a popup; the shortcut asks the WhatsApp or Gmail tab for suggestions.
chrome.commands.onCommand.addListener((cmd, tab) => {
  if (cmd === 'suggest' && tab?.id) chrome.tabs.sendMessage(tab.id, { type: 'trigger' }).catch(() => {}) // not WhatsApp, or needs a refresh
})

const PAGES = new Set(['https://web.whatsapp.com', 'https://mail.google.com'])
// Per-site settings: emails get their own learned style and style guide.
const siteOf = chat => (chat?.site === 'gmail' ? 'gmail' : 'whatsapp')
const KEYS = {
  whatsapp: { style: 'style', guide: 'styleGuide', guideDefault: DEFAULTS.styleGuide, tokens: 500 },
  gmail: { style: 'styleMail', guide: 'styleGuideMail', guideDefault: DEFAULTS.styleGuideMail, tokens: 1200 },
}
const FROM_PAGE = new Set(['suggest', 'forgetFact', 'openOptions', 'contactGet', 'contactNotes', 'contactLearn', 'picked'])
const YOUTUBE = 'https://www.youtube.com' // may only ask which videos to hide
const handlers = { suggest, forgetFact, forgetContact, clearStyle, openOptions, demo, contactGet, contactNotes, contactLearn, picked, triage }

// Local usage counters for the "This week" card. Never leaves the machine.
const stat = delta => tx(async () => {
  const { stats } = await store.get('stats')
  await store.set({ stats: bump(stats, isoDay(new Date()), delta) })
})

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (sender.id !== chrome.runtime.id || !Object.hasOwn(handlers, msg?.type)) return
  const page = PAGES.has(sender.origin)
  if (sender.origin === YOUTUBE ? msg.type !== 'triage' : page ? !FROM_PAGE.has(msg.type) : !sender.url?.startsWith(chrome.runtime.getURL(''))) return
  handlers[msg.type](msg, sender).then(
    r => sendResponse({ ok: true, ...r }),
    e => {
      console.warn('[Quietly]', msg.type, e)
      sendResponse({ ok: false, ...errorFor(e) })
    },
  )
  return true // keeps the channel open, which also keeps this worker alive until we answer
})

const isGroqKey = k => String(k || '').startsWith('gsk_')
const isChatModel = id => {
  const s = String(id || '').toLowerCase()
  if (s.includes('whisper') || s.includes('orpheus') || s.includes('canopy') || s.includes('embed') || 
      s.includes('guard') || s.includes('allam') || s.includes('vision') || s.includes('audio') || s.includes('tts')) {
    return false
  }
  return s.includes('llama') || s.includes('mixtral') || s.includes('gemma') || s.includes('deepseek') || s.includes('qwen')
}

const defaultModelFor = (apiKey, configured, fallback) => {
  const m = String(configured || '').trim()
  if (isGroqKey(apiKey)) {
    if (!m || m.startsWith('~') || !isChatModel(m)) return fallback || 'llama-3.3-70b-versatile'
    return m
  }
  // OpenRouter key:
  if (!m || (!m.includes('/') && !m.startsWith('~'))) {
    if (m.includes('70b')) return 'meta-llama/llama-3.3-70b-instruct:free'
    if (m.includes('8b')) return 'meta-llama/llama-3.1-8b-instruct:free'
    return DEFAULTS.model
  }
  return m
}

async function llm(apiKey, rawModel, messages, name, schema, temperature, maxTokens) {
  const isGroq = isGroqKey(apiKey)
  const provider = isGroq ? 'Groq' : 'OpenRouter'
  let model = defaultModelFor(apiKey, rawModel, 'llama-3.3-70b-versatile')
  const url = isGroq
    ? 'https://api.groq.com/openai/v1/chat/completions'
    : 'https://openrouter.ai/api/v1/chat/completions'
  console.info(`[Quietly] Calling ${provider} (${url}) with model: ${model}`)
  const tokens = isGroq ? Math.min(maxTokens || 350, 400) : maxTokens
  let res = await fetch(url, {
    method: 'POST',
    signal: AbortSignal.timeout(30000), // Chrome kills the worker if a fetch takes over 30 s
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(requestBody({ model, messages, name, schema, temperature, maxTokens: tokens, isGroq })),
  })
  let data = await res.json().catch(e => {
    if (res.ok) throw e // timeout or dropped connection mid-body: transient, retry later
    return {}
  })

  // Automatic retry if rate-limited (429) on Groq (wait up to 20s)
  if (isGroq && !res.ok && res.status === 429) {
    const msg = data?.error?.message || ''
    const match = msg.match(/try again in ([\d.]+)s/i)
    const waitSec = match ? parseFloat(match[1]) : 3.5
    if (waitSec <= 20) {
      console.info(`[Quietly] Rate limited on Groq, waiting ${waitSec}s then retrying...`)
      await new Promise(r => setTimeout(r, Math.ceil(waitSec * 1000) + 500))
      res = await fetch(url, {
        method: 'POST',
        signal: AbortSignal.timeout(30000),
        headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody({ model, messages, name, schema, temperature, maxTokens: tokens, isGroq })),
      })
      data = await res.json().catch(e => {
        if (res.ok) throw e
        return {}
      })
    }
  }

  // Automatic retry if JSON generation failed on Groq (bypass Groq's server-side enforcer)
  if (isGroq && !res.ok && /failed to generate json/i.test(data?.error?.message || '')) {
    console.info(`[Quietly] Groq server-side JSON enforcer failed. Retrying without response_format...`)
    const retryInstruction = name === 'triage'
      ? 'Output ONLY raw JSON like {"keep":["v1","v4"]}. No explanation, no markdown.'
      : name === 'facts'
      ? 'Output ONLY raw JSON like {"facts":[{"id":"f1","op":"add","text":"...","who":"them"}]}. No explanation, no markdown.'
      : 'Output ONLY raw JSON like {"r1":{"intent":"...","text":"..."},"r2":{"intent":"...","text":"..."},"r3":{"intent":"...","text":"..."}}. No explanation, no markdown.'
    res = await fetch(url, {
      method: 'POST',
      signal: AbortSignal.timeout(25000),
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model,
        messages: [
          ...messages,
          { role: 'system', content: retryInstruction }
        ],
        temperature: 0.1,
        max_tokens: 1500,
      }),
    })
    data = await res.json().catch(e => {
      if (res.ok) throw e
      return {}
    })
  }

  // Automatic fallback on Groq: Query Groq's live models endpoint to see what this user's key actually has
  if (isGroq && !res.ok && (data?.error?.code === 'model_not_found' || /does not exist|decommissioned|requires terms|terms acceptance|allam|orpheus|canopy/i.test(data?.error?.message || ''))) {
    console.warn(`[Quietly] Model ${model} not usable on Groq. Fetching active chat models...`)
    try {
      const mr = await fetch('https://api.groq.com/openai/v1/models', {
        headers: { Authorization: `Bearer ${apiKey}` },
      })
      if (mr.ok) {
        const mj = await mr.json()
        const available = (mj.data || [])
          .filter(m => m.active !== false && isChatModel(m.id))
          .map(m => m.id)
        console.info('[Quietly] Available Groq chat models for this account:', available)
        const fallbackModel = available[0]
        if (fallbackModel && fallbackModel !== model) {
          model = fallbackModel
          await store.set({ model }).catch(() => {})
          console.info(`[Quietly] Retrying Groq with live chat model: ${model}`)
          res = await fetch(url, {
            method: 'POST',
            signal: AbortSignal.timeout(25000),
            headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody({ model, messages, name, schema, temperature: Math.min(temperature, 0.4), maxTokens, isGroq })),
          })
          data = await res.json().catch(e => {
            if (res.ok) throw e
            return {}
          })
        }
      }
    } catch (err) {
      console.warn('[Quietly] Failed to fetch live models from Groq:', err)
    }
  }

  const u = data.usage
  console.debug('[Quietly]', name, data.model || res.status, u ? `${u.prompt_tokens} in + ${u.completion_tokens} out tokens` : '', u?.cost != null ? `$${u.cost}` : '')
  return {
    out: parseCompletion(res.ok, res.status, data, provider), served: data.model || '', cost: +u?.cost || 0,
    tokensIn: +u?.prompt_tokens || 0, tokensOut: +u?.completion_tokens || 0,
  }
}

// Jev (decision model): one yes/no question per video, answered with a confidence. Billed per input token only.
async function decide(apiKey, model, videos) {
  const { body, ids } = buildTriageDecisions(videos)
  const res = await fetch('https://openrouter.ai/api/alpha/decisions', {
    method: 'POST',
    signal: AbortSignal.timeout(25000),
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ model, ...body }),
  })
  const data = await res.json().catch(e => {
    if (res.ok) throw e
    return {}
  })
  const u = data.usage
  console.debug('[Quietly] decide', data.model || res.status, u ? `${u.input_tokens} in + ${u.output_tokens} out tokens` : '', u?.cost != null ? `$${u.cost}` : '')
  if (!res.ok || data.error) throw Object.assign(new Error(data.error?.message || `HTTP ${res.status}`), { status: res.status, body: data })
  return {
    kept: keptFromDecisions(data.answers, ids), served: data.model || '', cost: +u?.cost || 0,
    tokensIn: +u?.input_tokens || 0, tokensOut: +u?.output_tokens || 0,
  }
}

async function memoryFor(key, title) {
  const got = await store.get([`mem:${key}`, `notes:${key}`])
  if (got[`mem:${key}`] || got[`notes:${key}`] || !key.startsWith('title:')) {
    return { mem: got[`mem:${key}`] || null, notes: got[`notes:${key}`] || '' }
  }
  // The bridge is down: borrow the one record with this name, read-only.
  const all = await store.get(null)
  const hits = Object.keys(all).filter(k => k.startsWith('mem:') && all[k]?.name === title)
  if (hits.length !== 1) return { mem: null, notes: '' }
  return { mem: all[hits[0]], notes: all[`notes:${hits[0].slice(4)}`] || '' }
}

async function suggest({ chat }, sender) {
  const site = siteOf(chat)
  if (site === 'gmail' && sender.origin !== 'https://mail.google.com') throw new Error('Wrong page')
  const K = KEYS[site]
  const s = await store.get(['apiKey', 'model', K.guide, K.style])
  if (!s.apiKey) throw Object.assign(new Error('no key'), { code: 'no_key' })
  const title = String((site === 'gmail' ? chat?.name : chat?.title) ?? '')
  const rows = capRows(chat?.rows, site)
  if (!rows.some(r => !r.me)) throw new Error('Nothing to reply to yet')
  const key = keyFor(chat)
  const kind = chatKind(chat)
  const model = defaultModelFor(s.apiKey, s.model, 'llama-3.3-70b-versatile')
  const { mem, notes } = await memoryFor(key, title)
  const learnable = !chat.refine && (kind === 'direct' || kind === 'group') && !chat.stale && !mem?.noLearn // skip learning during rewrites to conserve TPM
  if (learnable) learn({ apiKey: s.apiKey, model, key, kind, title, site, todayLocal: String(chat.today ?? ''), rows }, sender.tab.id) // runs alongside the reply call
  const style = s[K.style] || {}
  const previous = Array.isArray(chat.previous) ? chat.previous : []
  const { messages, examples, facts } = buildReplyPrompt({
    chat: { ...chat, site }, rows, notes, facts: mem?.facts, style, styleGuide: s[K.guide] ?? K.guideDefault, draft: chat.draft, instruction: chat.prompt,
    refine: chat.refine, previous,
  })
  const { out, served, cost } = await llm(s.apiKey, model, messages, 'replies', REPLY_SCHEMA, 0.8, K.tokens)
  const replies = cleanReplies(out, { rows, notes, facts, examples, stats: style.stats, extra: [chat.draft, chat.prompt, ...previous], site })
  stat({ asked: 1, shown: replies.length, cost })
  if (!replies.length) throw new Error('No usable suggestions — ✨ again')
  await tx(async () => {
    const k = `mem:${key}`
    const got = await store.get([K.style, k])
    const st = got[K.style] || {}
    const upd = { [K.style]: { ...st, recentChips: [...(st.recentChips || []), ...replies.map(norm)].slice(-CAPS.chips) } }
    if (learnable || got[k]) upd[k] = { ...emptyMem(title), ...got[k], name: title, usedAt: Date.now() }
    await store.set(upd)
  })
  const org = m => m.split('/')[0]
  const fb = DEFAULTS.fallback.replace(/-\d+$/, '')
  const viaFallback = org(served) !== org(model) || (served.startsWith(fb) && !model.startsWith(fb))
  return { replies, via: served && viaFallback ? served.split('/').pop() : '' }
}

async function learn({ apiKey, model, key, kind, title, site, todayLocal, rows }, tabId) {
  if (learning.has(key)) return
  learning.add(key)
  try {
    const k = `mem:${key}`
    const gen = forgets.get(key)
    const { [k]: before } = await store.get(k)
    const fresh = newRows(rows, before?.seen)
    if (!fresh.length) return
    await tx(async () => {
      const sk = KEYS[site].style
      const { [sk]: style = {} } = await store.get(sk)
      const h = harvest(style, fresh, site)
      if (h.added) await store.set({ [sk]: h.style })
      if (h.asIs) stat({ asIs: h.asIs })
    })
    const today = isoDay(new Date())
    let got = null
    if (kind === 'direct' && fresh.some(r => !r.me && r.sure)) {
      const at = rows.indexOf(fresh[0])
      const { messages, ids } = buildExtractPrompt({
        name: title, facts: liveFacts(before?.facts, today), context: rows.slice(Math.max(0, at - 6), at), fresh, today, todayLocal,
      })
      try {
        const r = await llm(apiKey, model, messages, 'facts', FACTS_SCHEMA, 0, 900)
        got = { ids, out: r.out }
        stat({ cost: r.cost })
      } catch (e) {
        if (!e.deterministic) return // network or limits: keep `seen` so the next ✨ retries this window
        console.warn('[Quietly] facts skipped', e) // same input would fail again: move on instead of re-billing
      }
    }
    let added = []
    if (forgets.get(key) !== gen) return
    await tx(async () => {
      if (forgets.get(key) !== gen) return
      const { [k]: cur } = await store.get(k) // fresh read: facts may have been deleted meanwhile
      const mem = { ...emptyMem(title), ...cur, name: title }
      if (got) ({ facts: mem.facts, added } = applyFacts(mem, got.out, got.ids, today))
      mem.seen = mergeSeen(mem.seen, fresh)
      await store.set({ [k]: mem })
    })
    if (added.length) {
      const facts = added.map(f => ({ id: f.id, text: f.text }))
      chrome.tabs.sendMessage(tabId, { type: 'learned', key, name: title, facts }).catch(() => {})
    }
  } catch (e) {
    console.warn('[Quietly] learn', e)
  } finally {
    learning.delete(key)
  }
}

async function forgetFact({ key, factId }) {
  const k = `mem:${key}`
  await tx(async () => {
    const { [k]: mem } = await store.get(k)
    if (mem) await store.set({ [k]: { ...mem, facts: (mem.facts || []).filter(f => f.id !== factId) } })
  })
  return {}
}

async function forgetContact({ key }) {
  forgets.set(key, (forgets.get(key) || 0) + 1)
  await tx(() => store.remove(`mem:${key}`)) // the options page removes notes:<key> itself (it owns that key)
  return {}
}

async function clearStyle({ site } = {}) {
  const sk = KEYS[site === 'gmail' ? 'gmail' : 'whatsapp'].style
  await tx(async () => {
    const { [sk]: style = {} } = await store.get(sk)
    await store.set({ [sk]: { recentChips: Array.isArray(style.recentChips) ? style.recentChips : [], bank: [], stats: { n: 0 } } })
  })
  return {}
}

// Guided setup: real suggestions for the built-in sample chat. No memory, no learning, nothing stored.
async function demo() {
  const s = await store.get(['apiKey', 'model', 'styleGuide'])
  if (!s.apiKey) throw Object.assign(new Error('no key'), { code: 'no_key' })
  const rows = capRows(DEMO_CHAT.rows)
  const { messages } = buildReplyPrompt({ chat: DEMO_CHAT, rows, styleGuide: s.styleGuide ?? DEFAULTS.styleGuide })
  const model = defaultModelFor(s.apiKey, s.model, 'llama-3.3-70b-versatile')
  const { out, served } = await llm(s.apiKey, model, messages, 'replies', REPLY_SCHEMA, 0.8, 500)
  const replies = cleanReplies(out, { rows })
  if (!replies.length) throw new Error('No usable suggestions — try again')
  return { replies, served }
}

// 📝 panel in WhatsApp: the current chat's notes, facts and learning switch.
const nameOf = chat => String((chat?.site === 'gmail' ? chat?.name : chat?.title) ?? '')

async function contactGet({ chat }) {
  const key = keyFor(chat)
  const { mem, notes } = await memoryFor(key, nameOf(chat))
  const facts = liveFacts(mem?.facts, isoDay(new Date())).map(f => ({ id: f.id, text: f.text, who: f.who, at: f.at }))
  return { key, name: nameOf(chat), kind: chatKind(chat), notes, facts, noLearn: !!mem?.noLearn, readOnly: key.startsWith('title:') }
}

async function contactNotes({ chat, notes }) {
  const key = keyFor(chat)
  if (key.startsWith('title:')) throw new Error("Can't save notes while the chat id is unknown")
  await tx(() => store.set({ [`notes:${key}`]: String(notes ?? '').slice(0, CAPS.notes) }))
  return {}
}

async function contactLearn({ chat, learn: on }) {
  const key = keyFor(chat)
  if (key.startsWith('title:')) throw new Error("Can't change this while the chat id is unknown")
  const title = nameOf(chat)
  await tx(async () => {
    const k = `mem:${key}`
    const { [k]: cur } = await store.get(k)
    await store.set({ [k]: { ...emptyMem(title), ...cur, name: cur?.name || title, noLearn: !on } })
  })
  return {}
}

// YouTube: which feed videos are worth showing. Each video is judged once; verdicts are cached by id.
async function triage({ videos }) {
  const list = cleanVideos(videos)
  const s = await store.get(['apiKey', 'ytModel', 'yt', 'ytBy'])
  if (!s.apiKey) throw Object.assign(new Error('no key'), { code: 'no_key' })
  const isGroq = isGroqKey(s.apiKey)
  const model = isGroq
    ? defaultModelFor(s.apiKey, s.ytModel, 'llama-3.3-70b-versatile')
    : (String(s.ytModel || '').trim() || DEFAULTS.ytModel)
  const known = s.ytBy === model ? { ...s.yt } : {} // switched model: judge every video again
  const todo = list.filter(v => !Object.hasOwn(known, v.id))
  if (todo.length) {
    let r
    if (!isGroq && isDecisionModel(model)) r = await decide(s.apiKey, model, todo)
    else {
      const { messages, ids } = buildTriagePrompt(todo)
      // ~250 tokens is enough for the answer; the rest is room for a router that picks a thinking model. Only used tokens are billed.
      r = await llm(s.apiKey, model, messages, 'triage', TRIAGE_SCHEMA, 0, 1500)
      r.kept = keptIds(r.out, ids)
    }
    const { kept, served, cost, tokensIn, tokensOut } = r
    stat({ cost })
    const fresh = Object.fromEntries(todo.map(v => [v.id, kept.has(v.id) ? 1 : 0]))
    Object.assign(known, fresh)
    await tx(async () => {
      const got = await store.get(['yt', 'ytBy'])
      const base = got.ytBy === model ? got.yt : {}
      const ytLast = { model, served, tokensIn, tokensOut, cost, n: todo.length, at: Date.now() } // shown in settings
      await store.set({ yt: capCache({ ...base, ...fresh }), ytBy: model, ytLast })
    })
  }
  return { keep: Object.fromEntries(list.map(v => [v.id, known[v.id] === 1])) }
}

async function picked() {
  stat({ inserted: 1 })
  return {}
}

async function openOptions() {
  // Same popup as the toolbar icon; a full tab if Chrome won't open it (e.g. window not focused).
  await chrome.action.openPopup().catch(() => chrome.runtime.openOptionsPage())
  return {}
}
