# Privacy Policy — Sali-ZZai

_Last updated: 2026-11-08_

Sali-ZZai ("the app") is a native Android client for the Z.ai platform. The app is **server-less**: it has no backend of its own and no user accounts.

## Summary

- **No account, no tracking.** No analytics SDK, no ads, no advertising ID.
- **Local storage, direct provider requests.** Chat history and imported ZIP metadata are stored locally in the Room database and app-private storage. When you send prompts, attachments, audio or image-generation requests, the required content is sent directly to the configured API provider as described below.
- **Your Z.ai API key is encrypted at rest** using EncryptedSharedPreferences and Android Keystore. If encrypted storage cannot be recovered, the fallback is in-memory only; the app does not save the key as plaintext. The key is sent in the Authorization header to the configured HTTPS API host when you make requests.
- **Uninstalling the app deletes everything** the app stored.

## Data stored on your device

| Data | Where | Purpose |
| --- | --- | --- |
| Z.ai API key | EncryptedSharedPreferences (Android Keystore); process memory only if Keystore fails | Authenticate your Z.ai requests |
| Chats and history | Room database (app-private) | Local conversation history |
| Imported ZIP files and extracted trees | App-private storage / in-memory | On-device ZIP analysis |
| Settings | DataStore (app-private) | App preferences |

## Data that leaves your device

When you send a message, attach an image/file, generate an image, transcribe audio, or run a web search, the app sends the required request content directly from your device to the configured HTTPS API host using your API key. When using the default configuration this is Z.ai; custom hosts should be trusted by you. Those requests are governed by the destination provider's terms and privacy policy. The app does not proxy requests through an app-owned server.

## Local profiles and sign-in limitations

Email/password profiles are stored locally. The current OTP flow generates a demo code inside the app and does not send an email. Google Credential Manager is used to obtain a credential, but this server-less app does not verify the ID token with a backend. These features are not online account authentication and should not be relied on to protect remote resources.

## Permissions

The app requests only permissions needed for core functionality. Microphone access is used only when you explicitly start voice input; document and image access uses the Android document picker.

## Data deletion

- Delete individual chats and history inside the app at any time.
- Clear the API key from Settings.
- Uninstalling removes all app-private data permanently.

## Children

The app is not directed at children under 13.

## Contact

Open an issue in this repository for privacy questions about the app's on-device behavior.
