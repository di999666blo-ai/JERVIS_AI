# BOBBY — Remaining Work & Delivery Checklist

Updated: 2026-11-09
Repository: di999666blo-ai/JERVIS_AI
Working branch: bobby-development
Do not merge to master without the owner's approval.

## Current verified build

- 🟢 GitHub Actions run #175 succeeded on commit `eba0ec803154f6ad7d0479f96b0dd9afe0e4c582`.
- 🟢 APK build, artifact upload and public release upload all succeeded.
- 🟢 Release asset: `BOBBY.apk` (version code 175; about 69.6 MB).
- Download: https://github.com/di999666blo-ai/JERVIS_AI/releases/latest/download/BOBBY.apk
- 🟡 Build is a debug-signed APK. A stable private signing key is required for seamless in-place upgrades.
- A successful CI build verifies compilation, not real phone behaviour. Physical testing remains the owner's responsibility.

## Work started in this update

- 🟡 Added an opt-in Android AccessibilityService bridge for visible UI interaction.
- 🟡 Added controller operations for visible-text button clicks, supported text entry, scrolling, coordinate tap/swipe gestures, Back and Home.
- 🟡 Added the service declaration and disclosure text. The owner must manually enable BOBBY under Android Accessibility settings; Android does not allow an app to silently grant itself this access.
- 🔴 The accessibility controller is a capability layer; the natural-language AI command router is not yet connected to all these operations.
- 🔴 No bypass of Android security prompts, permission checks, protected windows or app security boundaries is included.

## Remaining implementation — priority order

### P0 — Make BOBBY genuinely conversational
- Build a conversation/session manager that distinguishes the owner, introduced guests and unknown speakers, with explicit consent before saving voice profiles.
- Add social context: introductions ("meet my friend Ahmed"), names/pronunciation, polite greetings, context-aware follow-up questions and conversation turn-taking.
- Add an entertainment intent: offer chess, quizzes, riddles, music and conversation games; ask before launching apps or starting a game.
- Add speaker diarisation/voice identification only where the selected speech stack supports it reliably. Do not pretend voice identity is certain from a single short sample.
- Add interruption handling, barge-in, silence/end-of-turn detection and natural response timing. The current speech path is not a fully duplex, human-level live conversation.

### P0 — Connect voice commands to phone actions
- Connect the speech recogniser and AI tool planner to the new accessibility controller and existing app-launch/alarm actions.
- Add a confirmation policy: harmless actions can run after a clear command; messages, purchases, payments, deletion, trading and sensitive actions require confirmation.
- Add an action progress/status overlay and spoken completion/failure result.
- Add app discovery by installed launcher labels and robust package disambiguation.
- Handle unsupported controls gracefully; accessibility cannot operate every custom canvas, game surface, protected screen or remote desktop.

### P1 — Floating assistant experience
- Change bubble tap from opening the full dashboard to a compact voice session / tap-to-talk surface.
- Add listening, thinking, speaking, working and error states, with visible microphone status.
- Implement wake-word listening only with a foreground microphone service, persistent notification, battery policy handling and explicit user opt-in. Tap-to-talk is the initial safe baseline.

### P1 — Chess and entertainment
- Prefer an in-app chess board for dependable two-player play, clocks, legal moves and Bobby commentary.
- Third-party chess app control is best-effort only; custom-rendered boards may not expose accessible squares.
- Build quiz/riddle/music/game modules and guest-friendly turn-taking.
- Add music library scan/playback with correct media permissions and user-selected library access; mood classification must be presented as an estimate, not a fact.

### P1 — Screen understanding and recording
- Implement an explicit accessibility-based screen reader for visible accessible text, with a clear start/stop state.
- Keep MediaProjection screen capture user-approved and visibly indicated.
- Add a recorder foreground service, countdown, persistent recording indicator, stop/save/playback and storage controls.
- Respect protected content and Android's capture restrictions.

### P2 — Call handling and communications
- Investigate default-dialer role and Telecom InCallService for call answer/reject/end, with clear user choice.
- Directing BOBBY's generated speech into a cellular call's uplink is not generally available to ordinary apps; do not promise caller-side AI speech until a supported route is proven.
- Keep SMS/email composition user-mediated unless a separately authorised and policy-compliant workflow is implemented.

### P2 — Integrations and reliability
- Convert important plugin catalogue entries into tested provider adapters; label all others catalogue-only.
- Verify OpenAI image generation model/API, Meshy API, ElevenLabs cloning API and LiteRT-LM inference with valid provider credentials.
- Validate web search, citations, file extraction limits, memory search/edit/export and Android 8/API 26 compatibility.
- Add automated tests for command parsing, action confirmation, memory rules, app discovery and accessibility traversal.
- Add crash reporting opt-in, structured local diagnostics and a permission/status screen.

### P3 — Security and release
- Store all provider keys in Android Keystore-backed storage; never log secrets or screen text.
- Keep explicit user consent for microphone, accessibility, screen capture, contacts, notifications and overlay access.
- Do not disguise automation as physical human input or bypass Android permission/security boundaries.
- Configure a stable signing keystore through GitHub Actions secrets; never commit the keystore or passwords.
- Run CI after each implementation batch; publish APK only after green build and artifact verification.
- Update this checklist with actual commit/run IDs and distinguish compiled, provider-tested and phone-tested status.

## Honest limitations

- Android AccessibilityService can operate many visible, accessible controls after the user enables it. It is not a universal remote-control API.
- Human-sounding social conversation requires low-latency speech recognition, turn-taking and speech synthesis; current app components need integration and device testing.
- The AI cannot independently know a guest's identity, family details or personal history unless the user supplies that information or the guest consents to share it.
- Third-party games may block automation or expose no accessible board controls. An in-app chess implementation is the reliable fallback.
- A floating overlay, continuous wake word and background microphone use are subject to Android/OEM restrictions and battery settings.

## Acceptance criteria before calling BOBBY complete

1. Owner can say "Bobby, meet my friend Ahmed" and BOBBY greets him naturally without inventing facts.
2. BOBBY can continue a multi-turn group conversation, recognise uncertainty and avoid speaking over people.
3. "Bobby, entertain Ahmed" offers activities and starts the selected one after confirmation.
4. "Open chess" launches the selected installed app or BOBBY's own chess game.
5. Accessibility actions work only after explicit Android enablement and report failures accurately.
6. Floating bubble offers tap-to-talk and visibly reflects listening/thinking/speaking/working states.
7. Recorder, screen reading and call features disclose their limits and permissions.
8. CI is green, APK asset verified, signing strategy documented, and no feature is labelled complete solely because it compiles.
