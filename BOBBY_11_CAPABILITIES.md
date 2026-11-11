# BOBBY — 11 Capability Areas

This is the product map for the chat-first BOBBY Android assistant. The status labels describe what exists in source today; they do not imply every feature has passed device testing.

| # | Capability | Purpose | Current implementation truth |
|---|---|---|---|
| 1 | Everyday AI assistant | Chat, explain, plan, summarise and reason over conversation context. | Online Gemini-compatible chat, model selection, searchable local conversation archive and user-approved memory context are implemented. Offline Qwen/LiteRT-LM is wired but requires download and target-phone inference testing. |
| 2 | Voice and communication | Speak to BOBBY and hear spoken replies. | Android speech recognition, Android TTS, neural voice path and floating-robot tap-to-talk exist. Always-on wake word, reliable duplex conversation, speaker identification and cellular-call audio injection are not implemented. |
| 3 | Internet research | Find current information and analyse sources. | Gemini Search grounding and optional Brave Search integration exist. Brave requires the user's own key. General browser automation and universal citation coverage are not complete. |
| 4 | Files and documents | Read and analyse user-selected documents. | TXT/Markdown/CSV/HTML/JSON/XML/source text, PDF, DOCX, XLSX, PPTX, bounded ZIP extraction and image preparation are implemented. Scanned-PDF OCR and audio/video transcription remain incomplete. Selected content can be sent to the configured AI provider. |
| 5 | Coding and software development | Plan coding work and help with repositories. | Local task planner/history, GitHub identity verification and a reviewable Codex GitHub Actions workflow foundation exist. Broad in-app GitHub write/PR operations are not connected; Codex workflow needs its own repository secret and review. |
| 6 | Image and creative generation | Work with images, visual concepts and 3D assets. | Creative Studio UI, image attachment analysis and specialist project instructions exist. A production image/3D generation provider and full native 3D pipeline require configuration and verification. |
| 7 | Phone assistance | Help operate the Android device. | App launch, Clock alarm handoff, local reminders, accessibility service, visible-text reading and user-approved MediaProjection capture exist. They require Android permissions and cannot control every app or protected screen. |
| 8 | Personal organisation | Store useful personal context and reminders. | Local profile, user-controlled memory vault, important dates, chat archive and scheduled local reminders exist. Full Google Calendar/Drive synchronisation is not complete. |
| 9 | Connected accounts and services | Connect external services through real adapters. | GitHub read-only identity verification and Deriv account verification foundation exist, alongside integration setup surfaces. Catalogue entries are not live integrations; broad OAuth/API actions remain incomplete. |
| 10 | Automation and agents | Plan and carry out approved multi-step tasks. | Registered tool inventory, local task history, reminders and a limited set of approved Android actions exist. Planning is not automatic execution; consequential actions require explicit confirmation. |
| 11 | Trading intelligence | Support trading research and risk decisions. | Trader Skill Pack provides analysis-only workflows and Deriv account verification foundation. BOBBY does not execute broker orders or provide autonomous live trading. |

## Cross-cutting privacy and safety

- Provider credentials use Android Keystore-backed encrypted storage where implemented. Never commit credentials or paste them into ordinary chat.
- Android microphone, accessibility, overlay, notification and screen-capture permissions require explicit user approval.
- Files/images sent to an online model leave the device and are processed by the configured provider.
- Tor Browser protects traffic inside Tor Browser only. It does not route BOBBY's own network traffic and does not make the device untraceable.
- No trading profit or security guarantee is made.

## Capability Hub screen

The app's sidebar now includes **All 11 capabilities**. The screen explains each area's purpose, what code exists and the important gaps. It is a transparent status page, not a claim that every integration is fully working.

## Verification

A green GitHub Actions build proves the Android project compiles and the APK artifact uploads. It does not prove voice, provider credentials, accessibility, offline model inference or other runtime behaviours on the target phone. Physical-device testing remains necessary.
