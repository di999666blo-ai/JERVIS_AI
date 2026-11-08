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
