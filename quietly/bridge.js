// Quietly bridge, MAIN world. Answers one question: which chat is open?
// It reads the chat prop React keeps on the chat header and never touches WhatsApp's own modules.
// Only a JSON string crosses back to the isolated content script.
document.addEventListener('wahreply:req', () => {
  let out = null
  try {
    const h = document.querySelector('div#main header')
    const k = h && Object.keys(h).find(k => k.startsWith('__reactFiber$'))
    for (let f = k && h[k], i = 0; f && i < 30 && !out; f = f.return, i++) {
      const c = f.memoizedProps?.chat
      const id = c && (c.id || c.__x_id)
      const ct = c && (c.contact || c.__x_contact)
      const pn = ct && (ct.phoneNumber || ct.__x_phoneNumber)
      if (id) out = { jid: String(id), pn: pn ? String(pn) : null, depth: i }
    }
  } catch {}
  document.dispatchEvent(new CustomEvent('wahreply:res', { detail: JSON.stringify(out) }))
})
