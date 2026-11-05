# Privacy Policy — Sali-ZZai

_Last updated: 2026-11-02_

Sali-ZZai ("the app") is a native Android client for the Z.ai platform. The app is **server-less**: it has no backend of its own and no user accounts.

## Summary

- **No account, no tracking.** No analytics SDK, no ads, no advertising ID.
- **All data stays on your device** — chats, history and imported ZIP files are stored locally in a Room database and app-private storage.
- **Your Z.ai API key is encrypted at rest** using EncryptedSharedPreferences and sent only to Z.ai in the Authorization header of your own requests.
- **Uninstalling the app deletes everything** the app stored.

## Data stored on your device

| Data | Where | Purpose |
| --- | --- | --- |
| Z.ai API key | EncryptedSharedPreferences (Android Keystore) | Authenticate your Z.ai requests |
| Chats and history | Room database (app-private) | Local conversation history |
| Imported ZIP files and extracted trees | App-private storage / in-memory | On-device ZIP analysis |
| Settings | DataStore (app-private) | App preferences |

## Data that leaves your device

Only when you send a message, generate an image, or run a web search: the app calls the **Z.ai API directly from your device**, using your key. Those requests are governed by Z.ai's own terms and privacy policy. The app never proxies through any other server, and never shares your data with anyone else.

## Permissions

The app requests only permissions needed for core functionality (for example storage access for the ZIP viewer). It does not access contacts, location, microphone or camera without an explicit user action.

## Data deletion

- Delete individual chats and history inside the app at any time.
- Clear the API key from Settings.
- Uninstalling removes all app-private data permanently.

## Children

The app is not directed at children under 13.

## Contact

Open an issue in this repository for privacy questions about the app's on-device behavior.
