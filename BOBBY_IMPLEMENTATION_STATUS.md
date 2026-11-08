# BOBBY implementation status

This file is the acceptance ledger. A capability is not complete because a screen or interface exists: it must be implemented and tested on a supported Android device.

## Implemented foundation
- Android Kotlin/Jetpack Compose application scaffold and CI debug build.
- OpenAI-compatible chat client with encrypted API-key storage.
- Android speech recognition intent and system TextToSpeech integration.
- Movable floating overlay service (requires Android overlay permission).
- Local persistent user-memory store with search, deletion, JSON export and basic secret redaction.
- Safe first-stage local text importer (TXT, Markdown, CSV, HTML, JSON, XML, logs and source text; 2 MB limit).
- Demo-only trading risk guard. It does not connect to brokers or execute orders.
- Capability status model and ten-section navigation model definitions.

## Not yet complete / not yet device-tested
- Complete navigation screens and settings for all ten sections.
- Memory UI, automatic conversation summaries and memory retrieval injection.
- PDF, Word, Excel, PowerPoint, ZIP, audio, video and image extraction/analysis.
- Web search, page reader, browser workflows and video discovery.
- Live charts, broker adapters, Deriv authentication/market data, demo order lifecycle, and any live-trading workflow.
- Plugin installation sandbox, permission review and lifecycle.
- Offline LLM, offline speech guarantees, and explicit offline capability routing.
- Female voice guarantee, continuous duplex voice, wake word, interruption and Nigerian Pidgin coverage.
- Full Android device actions, contacts/calls/reminders/notifications and Instagram API integration.
- Full test suite, physical-device QA, release signing and production release.

## Safety
- Never store API keys, passwords, broker tokens or private credentials in ordinary memory.
- Trading remains disabled; the guard is a precondition helper only and is not a broker integration.
- Sensitive Android actions must use platform permission and confirmation flows.
- Original upstream repository has no declared licence in the inspected GitHub metadata. Reuse of upstream source remains subject to rights verification.


## Latest integration
- Added in-app Memory panel: create, list, delete, clear and export local memories.
- Added Android document picker wired to the bounded local text importer; imported content is not auto-uploaded.


## Personal onboarding and buddy features (2026-11-08)
- Added first-run profile onboarding for preferred name, age, birthday, and city.
- Added locally stored important dates and birthday/date matching reminders shown when the app opens.
- Added a city-based current-weather lookup using Open-Meteo geocoding and forecast endpoints; it requires internet and a city entered by the user. No background location tracking is used.
- Added friendly buddy-style greeting and emoji use.
- Not yet implemented: scheduled background birthday/important-day notifications, automatic daily weather push, and full conversational personalization from profile data.
- User profile data is stored locally in app preferences. Do not treat it as encrypted credential storage.


## Android communication sharing bridge (2026-11-08)
- Added an Android Sharesheet action that lets the user share the recent BOBBY conversation to an installed, compatible app (for example WhatsApp, email or supported social apps).
- Added an email-compose intent helper.
- This is user-mediated sharing only: BOBBY does not silently read private WhatsApp/email inboxes, send messages in the background, or publish directly to social accounts.
- Direct inbox access, real-time messaging and publishing require each service's supported APIs, account authorization, review/permissions and explicit user confirmation. YouTube upload and direct Instagram/Facebook publishing are not implemented by this bridge.


## Natural voice and calls (2026-11-08)
- User's required voice direction: calm, pleasant, anime-inspired female character voice with natural human-like delivery suitable for conversations and authorised calls.
- Current implementation uses Android system TextToSpeech with a best-effort English voice selection, rate and pitch adjustment. This is only a fallback; it does not guarantee a natural neural voice and varies by installed TTS engine/device.
- Production neural TTS provider integration, voice selection UI, pronunciation/prosody testing and target-device listening QA remain outstanding.
- Outgoing phone calls and call conversation are not implemented. Any future call flow must obtain approval and clearly identify BOBBY as an AI assistant rather than impersonating a person.


## Voice and visual identity update (2026-11-08)
- Added an OpenAI neural speech path using `gpt-4o-mini-tts` with the `nova` voice and a calm, warm, subtly anime-inspired adult female delivery instruction.
- Neural speech uses the existing configured API key, requires internet access and a compatible OpenAI API account, and can incur API charges. If the key is absent or the request fails, playback falls back to Android system TextToSpeech. The result is not guaranteed to sound identical across devices; target-phone listening tests remain required.
- Added original BOBBY-themed vector robot face artwork, adaptive Android launcher icon resources, and wired the robot artwork into the floating bubble.
- The floating bubble is still a visual launcher/shortcut; it is not yet a persistent wake-word microphone or autonomous voice agent.
- The newly generated promotional concept sheet is a design reference only; it has not been packaged as a full-resolution raster animation set. Separate production animation frames, splash artwork and final asset QA remain outstanding.

## Full specification audit — not complete
The current branch is a working foundation, not a finished implementation of the 14-section specification. Specifically outstanding: always-on wake-word/background microphone service, continuous duplex conversation, alarm/reminder scheduling, general phone automation, notification listener, approved calling and client call workflow, direct WhatsApp/email/social APIs, PDF/Office/ZIP/audio/video/image multimodal extraction, web search and browser agent, scheduled reminders, full trading dashboard and Deriv broker integration, plugin sandbox, offline local model, full settings/navigation, and device QA. Existing demo trading guard is not a broker connection or trading engine.
