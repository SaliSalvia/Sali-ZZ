# Device Testing Matrix — Sali-ZZai

minSdk 26 · targetSdk 35. Every release candidate must pass the priority tier; tiers 2–3 are sampled per release.

## Tier 1 — every release (mandatory)

| Device class | Android | Checks |
| --- | --- | --- |
| Low-end (2–3 GB RAM, API 26–28) | e.g. 2017-era device | Cold start < 5s, chat scroll smooth, ZIP of 10 MB does not OOM |
| Mainstream (API 30–33) | e.g. Pixel 4a class | Full feature pass: chat, image gen, discover, ZIP tree, settings |
| Latest (API 34–35) | e.g. Pixel 8 class | Edge-to-edge, predictive back, notification/runtime permission flows |

## Tier 2 — per release (sampled)

- Foldable or tablet: layout adapts, no crash on configuration change (rotation, fold).
- RTL device language (FA): chat bubbles, icons, markdown alignment.
- Dark theme: contrast, splash, markdown code blocks.
- Large font scale (1.3x) and display size: no clipped text.

## Tier 3 — periodic

- 2G/slow network: SSE stream stays responsive, cancellation works, retry recovers.
- Backgrounding mid-stream: no crash on return; stream state consistent.
- Battery: no wakelock leaks; idle drain flat.
- Clean install → upgrade from previous release: data intact (Room migrations).

## Feature checks per device pass

- [ ] New chat → send message → streamed answer renders (markdown + code)
- [ ] Stop/cancel mid-stream works
- [ ] Image generation completes and saves
- [ ] Web search returns results
- [ ] ZIP import → tree view → text extraction summary
- [ ] Settings: API key entry, validation error states, clear key
- [ ] History: open, search, delete
- [ ] Rotate + theme switch mid-chat: state preserved

## Defect tracking

Failures are logged in GitHub Issues with the tier, device, and Android version, labeled 'device-testing'.
