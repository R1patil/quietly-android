// Quietly on YouTube (isolated world): hides feed videos that aren't informational or productive.
// background.js judges each video once and caches the verdict; yt.css does the hiding and drops Shorts and games.
// Runs at document_start and tags tiles from a MutationObserver, which fires before the browser paints:
// a new tile is hidden ("wait") before it is ever shown, so junk never flashes.

const TILES = 'ytd-rich-item-renderer, ytd-video-renderer, ytd-compact-video-renderer, yt-lockup-view-model'
const CLEANED = ['/', '/watch', '/results'] // home, sidebar, search
const GAMES = /\bgam(?:e|es|ing)\b|playables/i // topic chips like "Gaming", "Stealth games"
const seen = new Map() // video id → 'wait' (queued or being judged) | 'ok' | 'junk'
const queue = new Map() // video id → { id, title, channel } for the next batch
let busy = false
let gather = 0 // short wait so tiles rendering one by one share a request
let off = true // until the switch in settings loads

// The on/off switch lives in storage.sync: storage.local holds the API key and is locked away from this page.
chrome.storage.sync.get('ytOff').then(s => {
  off = !!s.ytOff
  sweep()
})
chrome.storage.onChanged.addListener((c, area) => {
  if (area !== 'sync' || !c.ytOff) return
  const wasOff = off
  off = !!c.ytOff.newValue
  if (wasOff && !off) seen.clear()
  sweep()
})

const text = el => (el?.textContent ?? '').replace(/\s+/g, ' ').trim()
// Channel link on home and search; plain first metadata line in the watch sidebar.
const channelOf = tile => [...tile.querySelectorAll('a[href^="/@"], a[href^="/channel/"], .ytContentMetadataViewModelMetadataText')].map(text).find(Boolean) || ''

function sweep() {
  const root = document.documentElement
  const on = !off && !!chrome.runtime?.id // switched off or extension reloaded: show everything
  root.toggleAttribute('data-wa-clean', on) // Shorts and games, on every page
  root.toggleAttribute('data-wa-feed', on && CLEANED.includes(location.pathname))
  if (!on) {
    for (const tile of document.querySelectorAll('[data-wa-yt]')) {
      tile.removeAttribute('data-wa-yt')
    }
    return
  }
  for (const chip of document.querySelectorAll('yt-chip-cloud-chip-renderer')) chip.toggleAttribute('data-wa-game', GAMES.test(text(chip)))
  if (!root.hasAttribute('data-wa-feed')) return
  for (const tile of document.querySelectorAll(TILES)) {
    if (tile.parentElement?.closest(TILES)) continue // hide the grid cell, not the card inside it
    if (tile.querySelector('a[href*="/shorts/"]')) {
      tile.dataset.waYt = 'junk'
      continue
    }
    const a = tile.querySelector('a[href*="watch?v="]')
    const id = a && new URL(a.href).searchParams.get('v')
    if (!id) continue
    if (tile.dataset.waId !== id) { // a new tile, or YouTube reused this one for another video
      tile.dataset.waId = id
      tile.dataset.waYt = seen.get(id) || 'wait'
    }
    if (seen.has(id)) continue
    const title = text(tile.querySelector('#video-title, h3'))
    if (!title) continue // not rendered yet; the mutation that adds it runs this again
    seen.set(id, 'wait')
    queue.set(id, { id, title, channel: channelOf(tile) })
  }
  if (!busy && queue.size && !gather) gather = setTimeout(judge, 150)
}

async function judge() {
  gather = 0
  if (busy || !queue.size) return
  busy = true
  const batch = [...queue.values()].slice(0, 40)
  for (const v of batch) queue.delete(v.id)
  let keep = null
  try {
    const res = await chrome.runtime.sendMessage({ type: 'triage', videos: batch })
    if (res?.ok) keep = res.keep
    else console.debug('[Quietly] triage', res?.error)
  } catch (e) {
    console.debug('[Quietly] triage', e)
  } finally {
    if (keep) {
      for (const v of batch) {
        const verdict = keep[v.id] ? 'ok' : 'junk'
        seen.set(v.id, verdict)
        for (const tile of document.querySelectorAll(`[data-wa-id="${CSS.escape(v.id)}"]`)) tile.dataset.waYt = verdict
      }
    } else {
      // Triaging failed or was offline: don't permanently freeze in seen
      for (const v of batch) {
        seen.delete(v.id)
        for (const tile of document.querySelectorAll(`[data-wa-id="${CSS.escape(v.id)}"]`)) tile.dataset.waYt = 'ok'
      }
    }
    busy = false
    if (queue.size) judge() // tiles that arrived meanwhile
  }
}

new MutationObserver(sweep).observe(document, { childList: true, subtree: true })
setInterval(sweep, 1000) // YouTube sometimes swaps a tile's link without adding nodes
