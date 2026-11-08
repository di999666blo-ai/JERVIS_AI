# BOBBY — Android assistant rebuild

## Product direction
BOBBY is a new Android-first assistant implementation. The legacy repository is a Python desktop prototype, not an Android app. This branch preserves the original default branch and starts a clean Android application alongside the legacy files.

## Experience requirements
- Spoken conversation: speech recognition input and spoken replies, not text-only chat.
- Language-aware voice: select or detect supported languages; include Nigerian Pidgin as a first-class conversational option. Language coverage depends on the Android speech recognizer and installed TTS voices, so the app must show actual device support rather than promise every language.
- Wake phrase: “Bobby” while the app is not open. This requires an explicit microphone foreground service, persistent notification, battery/background handling and a wake-word engine. Android/OEM restrictions mean it cannot be guaranteed on every device; implement opt-in with clear privacy controls.
- Animated floating assistant: draggable overlay with idle/listening/thinking/speaking states, expressive motion and gestures. Requires Android overlay permission and a foreground service.
- Multimodal files and links, online model providers, offline capabilities, editable memory, browser/research and a guarded trading workspace are planned as separate modules.
- Sensitive actions (messages, calls, purchases, account changes and live trades) require confirmation.

## Truth and safety
No promise of perfect language coverage, unlimited intelligence, guaranteed wake-word availability, or profitable trading. API keys must never be committed to Git. Use Android secure storage and provider-specific APIs. Video understanding may require frame/audio extraction and cloud or device resources.

## Implementation stages
1. Android application foundation, voice I/O and animated assistant UI.
2. Language capability discovery, language selection, Pidgin prompt/response support and speech fallback.
3. Wake-word foreground service with explicit opt-in and privacy indicator.
4. Floating overlay service with drag, gestures and lifecycle controls.
5. AI provider adapters, streaming conversation and error handling.
6. File ingestion, OCR, PDF/Office parsing, audio/video processing and web research.
7. Memory, plugin permissions and guarded trading integrations.
8. Device/build testing and signed APK release.

## Current status
The initial Android scaffold is being added on `bobby-development`. This is not yet a finished APK.