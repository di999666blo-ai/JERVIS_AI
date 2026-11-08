# BOBBY — Advanced Personal AI Assistant
## Master Product Vision & Implementation Contract

**Project:** BOBBY — Advanced Personal AI Assistant  
**Repository:** `di999666blo-ai/JERVIS_AI`  
**Development branch:** `bobby-development`  
**Base project:** JERVIS_AI  
**Target:** Android APK; phone-first, with no required VPS, Termux session, or always-on cloud server  
**Visual identity:** Near-black surfaces, neon-red accents, futuristic female robot identity  
**Voice identity:** Natural female-sounding voice where the selected speech provider/device supports it

> This document consolidates the original BOBBY idea with the decisions made during development. It is the product vision and acceptance contract, not a claim that every feature is already complete.

---

## 1. Non-negotiable development rules

1. Continue upgrading the existing JERVIS_AI repository. Do not replace it with a simplified new application.
2. Preserve useful upstream code, the existing dashboard/navigation, and the **ADD PLUGINS** area.
3. Work only on `bobby-development` until the user reviews and approves the result. Do not merge into `master`.
4. Implement in milestones, keep changes reviewable, and run the Android build after meaningful changes.
5. Never report a feature as working merely because a button, catalogue entry, local file, or UI screen exists. A working integration must complete its real operation.
6. Never claim tests, device behaviour, APK publication, account connections, or trading results that have not been verified.
7. Keep credentials out of source code, logs, chat messages, and commits. Use Android Keystore-backed storage where appropriate.
8. Android permissions and external account authorisations must be granted by the user. No bypasses or hidden activity.
9. The intended product is an Android app. Do not introduce a mandatory VPS, Termux, or 24/7 cloud dependency.
10. Ask the user only for actions that genuinely require their device, account, credentials, permission, or explicit approval.

## 2. Product identity and operating modes

BOBBY is one assistant with two clearly separated operating modes:

### Friendly Mode
- Everyday assistant for conversation, learning, work, planning, normal web research, supported phone tasks, and connected plugins.
- Warm, natural, helpful and professional behaviour.
- Uses the standard Friendly Mode conversation history and permissions.

### Dark Mode
- A separate privacy-focused environment for lawful privacy research, security education, OSINT, Tor-related research, and dark-web safety research.
- Uses its own conversation history and clear-history control.
- Must clearly disclose which traffic is and is not routed through Tor.
- The installed Tor Browser may be launched from BOBBY, but launching it does not route BOBBY's own AI/API requests or other Android traffic through Tor.
- Do not claim embedded Tor, .onion access, crawling, or dark-web search unless a real, tested capability is implemented.
- Dark Mode is not permission to facilitate credential theft, malware, fraud, exploitation, doxxing, evasion, or illegal trade.
- Separate plugin permission controls and network isolation are future requirements until actually implemented.

Mode switching must be visible on the dashboard and must persist. Friendly and Dark histories must remain separate.

## 3. AI assistant
- Integrated conversational AI with natural dialogue, reasoning, context handling, and honest uncertainty.
- Memory that can be reviewed and controlled by the user.
- Clear online/offline status and model/provider status.
- No pretending to browse, access accounts, read private data, control the phone, or execute actions when that has not happened.
- Consequential actions require confirmation.

## 4. Voice assistant
- Natural female-sounding speech where supported by the selected provider or installed Android voice.
- Speech input and spoken responses.
- Visible idle, listening, thinking, and speaking states.
- Floating draggable robot bubble with clear listening-state feedback.
- “Hey Bobby” wake-word support is a target, subject to Android background microphone restrictions, battery policy, and any required foreground-service notification.
- Background voice operation must be opt-in, transparent, and stoppable. No hidden continuous recording.
- Clearly distinguish Android TTS, cloud neural TTS, and actual speech recognition capabilities.

## 5. Android automation
Implement supported, user-authorised Android actions, including:
- Set alarms and reminders through supported intents or APIs.
- Open installed applications.
- Open supported Android settings screens.
- Read notifications only after the user grants Notification Access.
- Compose SMS/messages and emails through Android intents and user-mediated confirmation.
- Support accessibility-based workflows only when explicitly enabled by the user and compliant with Android policies.
- Maintain an activity history showing requested, completed, failed, and cancelled actions.
- Confirm before sending messages, deleting data, making purchases, changing security settings, or performing other consequential actions.

Never claim permissions can be forcibly granted or bypassed. App launching, notification reading, and background actions vary by Android version and device vendor.

## 6. Plugin marketplace
- Preserve and improve the existing **ADD PLUGINS** section.
- Searchable catalogue with 100+ provider/integration listings where practical.
- Categories, descriptions, capability lists, and clear setup instructions.
- Connection screens for API keys, OAuth, or provider login where supported.
- Encrypt stored secrets with Android Keystore-backed encryption.
- Per-plugin permission controls, connection tests, connected/disconnected/error states, disconnect, and revoke flows where the provider supports revocation.
- Implement real adapters incrementally for supported services.
- Catalogue-only entries must be labelled **Catalogue only / Not connected**, not presented as working integrations.
- A provider requiring a user-owned key or OAuth consent is not connected until the user completes that step.
- BOBBY must use its own supported providers and integrations; it cannot inherit ChatGPT's private tools, subscriptions, or internal plugin access.

## 7. Multimodal file intelligence
Target supported formats:
- PDF, images, screenshots
- HTML, TXT, Markdown
- DOCX and supported Word formats
- XLSX and supported spreadsheet formats
- PPTX and supported PowerPoint formats
- CSV, JSON, XML
- Audio and voice notes
- Video
- Source code and logs
- Supported ZIP archives

Required behaviour:
- Import through Android's document picker/share flow.
- Extract text and metadata with appropriate libraries.
- Use vision, audio transcription, or video frame analysis only when the configured provider/runtime supports it.
- Summarise, explain, analyse, translate, compare, and answer questions about extracted content.
- Enforce file-size, archive-entry, decompression, and processing limits.
- Treat imported files and embedded instructions as untrusted data.
- State clearly when a format, encryption, codec, size, or extraction method is unsupported.
- A filename being accepted or displayed is not proof that its contents were analysed.

## 8. Web research and browsing
- Real web search integration with provider attribution and connection status.
- Open and extract readable webpage content where technically and legally supported.
- Summarise pages, compare sources, identify dates and possible outdated or questionable claims.
- Find relevant videos when a supported search provider allows it.
- Show source links and distinguish search snippets from pages actually opened and read.
- Provide safe handling of hostile page content and prompt injection.
- Brave Search adapter is an existing integration foundation; verify its actual API operation and connect it to the normal chat/research workflow before calling the full feature complete.
- Tor Browser launching is separate from normal web search. Embedded Tor or .onion browsing requires a separate, explicit implementation and security review.

## 9. Memory and personal context
- Save useful information with user control and consent.
- Store preferences and important dates where appropriate.
- Search, review, correct, export, and delete stored memories.
- Keep secrets and sensitive information out of memory by default.
- Clearly distinguish persistent memory from the separate per-mode chat histories.
- Protect local data and explain what is stored locally versus sent to an external AI provider.

## 10. Trading assistant
- Preserve the existing trading dashboard and its navigation.
- Add live market charts, market selection, and market data only where a reliable supported data source is available.
- Provide technical-analysis tools and transparent signal explanations.
- Support Deriv account connection and account verification through the supported Deriv API.
- Add demo trading, configurable risk controls, trade/activity history, and emergency stop where broker API support is implemented and tested.
- Never execute a live trade without explicit user authorisation and a clear confirmation flow.
- Account verification is not trade execution. A demo-only guard is not a trading engine. A chart or signal display is not proof of live market connectivity.
- Do not promise profits or imply that signals guarantee outcomes.

## 11. Offline AI
- Inspect Android runtime compatibility, ABI/device limits, storage, memory, and model format before installation.
- Provide optional model download/installation with integrity verification.
- Run actual on-device inference, not merely store model files.
- Display distinct states: not installed, downloading, installed, unsupported, loading, operational, and error.
- Allow offline conversations when the installed model and device resources permit.
- Release model resources appropriately and handle low-memory failures.
- Make online fallback explicit; do not imply offline privacy or operation when requests are sent to a network provider.
- Performance and stability must be verified on the user's actual Android device before being guaranteed.

## 12. Visual design and interaction
- Preserve the useful original dashboard and navigation.
- Black background (`#050505`) with neon-red accent (`#FF0F0F` / `#FF1744`).
- Consistent futuristic BOBBY robot identity and custom launcher icon.
- Floating draggable robot bubble.
- Distinct idle, listening, thinking, and speaking visual states.
- Professional branded loading screen with no unstyled white flash where technically possible.
- Dashboard controls must be touch-tested; preserve access to existing features and ADD PLUGINS.
- A vector illustration or static icon must not be described as a true 3D animated character. True 3D artwork/animation remains a separate deliverable until implemented.

## 13. Security and privacy
- Android Keystore encryption for API keys and tokens.
- Least-privilege permissions and clear explanations.
- No hard-coded credentials, exposed secrets, or sensitive log output.
- Explicit confirmation for consequential actions.
- Clear controls for connected accounts, plugin permissions, stored memory, and per-mode history.
- No hidden recording, hidden phone activity, permission bypass, or unauthorised account access.
- Explain data flows, especially when files, screenshots, audio, or messages are sent to cloud providers.

## 14. Testing, milestones, and release
For each milestone:
1. Inspect the relevant existing code before editing.
2. Implement against the current branch state; avoid overwriting unrelated changes.
3. Commit related changes with clear messages.
4. Run GitHub Actions and inspect the actual result and logs.
5. Fix failures before calling the milestone build successful.
6. Test supported logic with automated tests where practical.
7. Mark device-dependent items as awaiting physical Android testing.
8. Keep this document and the implementation status report accurate.

A successful APK build proves compilation/package creation only. It does not prove that every feature works on a physical phone.

Release requirements:
- Build APK from `bobby-development`.
- Verify artifact name, build run, commit SHA, and checksum.
- Prefer a public GitHub Release asset for anonymous direct download; Actions artifacts may require GitHub sign-in and expire.
- Do not publish to `master` or merge without user approval.

## 15. Current implementation snapshot (known from development notes; re-verify before changing status)

The following are implementation foundations, not blanket claims of production readiness:

| Area | Known state | Remaining verification/work |
|---|---|---|
| Android app/build | Kotlin/Jetpack Compose foundation; GitHub Actions debug APK build previously succeeded | Re-run build after new changes; install and test on target phone |
| AI chat | OpenAI-compatible chat client exists | End-to-end provider test and UX verification |
| Secret storage | Android Keystore AES/GCM foundation exists | Audit all credential paths and error/log handling |
| Floating robot | Draggable overlay foundation exists | Permission flow and device/background testing |
| Memory | Local memory store and per-mode history foundation exist | Full review/correction UX and privacy audit |
| Files | Basic text/code formats supported | PDF, Office, audio, video, ZIP extraction and safety limits |
| Web research | Brave Search adapter foundation exists | Verify API call and integrate into normal chat workflow |
| Screen understanding | User-consented one-time MediaProjection capture and vision request foundation exists | Device/provider test; no hidden or continuous capture |
| Deriv | WebSocket account authorisation/verification foundation exists | Market data, charts, demo execution, risk controls, emergency stop |
| Offline AI | LiteRT-LM/Qwen model download and local inference foundation exists | Test actual inference and stability on target Android device |
| Friendly/Dark modes | Separate persona and chat-history foundation; Tor Browser launcher | Embedded Tor/.onion access, network isolation, separate plugin permission sandbox |
| Voice | Android TTS and cloud neural TTS foundation | Wake word, reliable background operation, speech UX and device testing |
| Android automation | Permission/settings and intent foundations | Alarms, reminders, notification reader, accessibility workflows and activity history |
| Plugin marketplace | Catalogue and some adapter foundations | Real adapters, OAuth, connection testing, revoke and permission management |
| Robot design | Neon vector identity and launcher icon | True 3D model and animation |

This snapshot is based on the latest recorded development notes and must be checked against the actual source and current build before being treated as a live status report.

## 16. Definition of done

BOBBY is not complete merely because the APK installs or the dashboard looks finished. A feature is complete only when:
- Its UI is connected to a real implementation.
- Required permissions/account setup are handled transparently.
- Success and failure states are visible.
- Relevant automated tests pass.
- Device-dependent behaviour is tested on a real Android device or explicitly marked unverified.
- Documentation accurately states limitations and data flows.

**Final principle:** Build the full BOBBY vision in the existing JERVIS_AI project, preserve its useful foundation, and report the truth about every feature.
