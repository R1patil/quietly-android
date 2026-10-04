# Quietly (Desktop Chrome Extension)

Quietly is an AI-powered browser extension that drafts contextual replies on **WhatsApp Web** and **Gmail**, and aggressively declutters your **YouTube** feed to maintain peak focus and academic rigor.

It operates with **zero middlemen** using your own **Groq Cloud API** or **OpenRouter API** key. It only suggests replies: you always review and send them yourself.

---

## 🌟 Key Capabilities

### 1. Contextual AI Replies (WhatsApp Web & Gmail)
- **Fast 1-Tap Suggestions**: Press the floating ✨ icon or hit `Alt+Shift+S` (`Ctrl+Shift+S` on macOS) to generate 3 replies tuned to your personal style.
- **Tone & Style Learning**: Adapts to how you write over time by observing your own sent messages (kept private in local storage).
- **Contact Memory & Facts**: Safely tracks notes and relevant context for 1:1 contacts to keep replies grounded and accurate.
- **Safety First**: Zero-automation guarantee. The extension *never* sends a message on your behalf; suggestions are strictly inserted into the input box for manual review.

### 2. Strict Academic YouTube Feed Triage ("Default = Hide Everything")
- **Hard-Kill Shorts**: Automatically intercepts and purges all `/shorts/` links, reel shelves, and short-form video previews before they render.
- **Zero-Tolerance for Distractions**: Filters out comedy, pranks, memes, standup, reactions, clickbait, gossip, music videos, and gaming.
- **Academic & Skill-Building Whitelist**: Only retains serious computer science, programming, science, mathematics, engineering, academic lectures, and verified educational tutorials.
- **No-Flicker Injection**: New incoming video cards remain invisible until triaged by the AI model, then fade in smoothly if approved.
- **Local Verdict Cache**: Judged video IDs are cached in local browser storage, saving tokens and speeding up navigation.

---

## ⚡ Supported AI Providers & Models

Quietly natively supports both **Groq Cloud** (recommended for ultra-fast, sub-second latency) and **OpenRouter**.

| Provider | Recommended Model | Latency | Focus Area |
| :--- | :--- | :--- | :--- |
| **Groq Cloud** | `qwen/qwen3.8-27b` | ~250ms | Real-time reply generation & fast YouTube triage |
| **Groq Cloud** | `llama-3.3-70b-versatile` | ~450ms | In-depth reasoning and nuanced email drafting |
| **OpenRouter** | `anthropic/claude-haiku-4.5` | ~900ms | High-precision conversational matching |
| **OpenRouter** | `~typesafe/jev-latest` | ~200ms | Ultra-cheap yes/no decision model for YouTube triage |

---

## 📥 Installation

1. **Clone or Download** this repository to your local drive:
   ```
   D:\MIT\quietly
   ```
2. Open Google Chrome (or any Chromium browser like Brave, Edge, or Kiwi Browser on Android) and navigate to:
   ```
   chrome://extensions
   ```
3. Enable **Developer mode** using the toggle switch in the top-right corner.
4. Click **Load unpacked** in the top-left corner and select the `quietly` folder (`D:\MIT\quietly`).
5. Open the extension popup from your browser toolbar:
   - Paste your **Groq API Key** (`gsk_...`) or OpenRouter key.
   - Choose your preferred models for Replies and YouTube Triage.
   - Toggle **YouTube** filter **On**.
   - Click **Save & Test**.

---

## 🛡️ Privacy & Security Architecture

- **Direct API Communication**: Network calls only communicate directly with `https://api.groq.com/*` or `https://openrouter.ai/*`. There are no telemetry trackers, remote analytics, or intermediate proxy servers.
- **Sandboxed Credential Storage**: Your API key is stored securely in `chrome.storage.local` and is never included in backup exports.
- **Minimal Surface Permissions**: Declares only `storage` and necessary domain content scripts (`web.whatsapp.com`, `mail.google.com`, `www.youtube.com`).

---

## 🧪 Testing & Verification

The extension is written in clean, modern vanilla JavaScript (Manifest V3) with zero runtime dependencies. It includes a complete automated test suite verifying prompts, JSON parsing, hygiene rules, and triage pipelines.

Run the test suite locally:
```sh
node --test
```
*Current status: 32 / 32 tests passing.*

---

## 📄 License

Distributed under the [MIT License](LICENSE).
