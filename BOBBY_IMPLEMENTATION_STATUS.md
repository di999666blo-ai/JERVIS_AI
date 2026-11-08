# BOBBY implementation status

A successful GitHub Actions APK build proves compilation, not device-level functionality. Features are only marked fully complete after functional and target-device testing.

## Implemented foundation
- Android Kotlin/Jetpack Compose app, black/red dashboard, floating robot overlay and GitHub Actions debug APK build.
- OpenAI-compatible online AI chat with configurable HTTPS endpoint/model and Android Keystore AES-GCM encrypted API-key storage.
- Expanded assistant system instructions for factual accuracy, uncertainty, untrusted document handling, action confirmation and trading safety.
- User-approved local memories are now included in the online assistant context (limited to recent 12 records); profile and local date/time context are also supplied.
- Text-file imports can now be attached to the conversation and analysed by the configured online AI (up to 12,000 characters per attachment). The user must understand that selected text is sent to their configured AI provider for analysis.
- Natural speech path with Android TTS fallback.
- Android Keystore AES-GCM vault and Deriv v3 WebSocket account-authorisation foundation. The current Deriv connector verifies account details only; it does not place trades.
- Local memory/profile, onboarding, weather, basic text-file import, permission centre and demo-only trading guard.
- Offline model file manager can import/remove .litertlm files into app-private storage. Model inference is NOT wired or verified.

## Not complete / not device-tested
- PDF/Office/ZIP/image/audio/video extraction and vision/audio/video model analysis.
- In-app web search/browser and source citations.
- Deriv symbol catalogue, live tick streaming, charting, proposals, demo order lifecycle, portfolio and trading strategy. Live trading remains disabled.
- LiteRT-LM runtime initialization, model inference, model selection and performance validation on the target phone.
- Always-on wake word, background microphone, continuous voice conversation and interruption.
- Alarm/reminder scheduling, notification reading, broad phone automation and call workflows.
- Direct WhatsApp/email/social integrations and publishing.
- Full plugin sandbox/management, release signing, complete navigation and physical-device QA.

## Security and limits
- AI API keys and Deriv credentials are encrypted locally with Android Keystore; never commit keys or paste them into chat.
- Text imported for AI analysis is sent to the configured AI provider. Do not attach sensitive documents unless you accept that transfer.
- Start with minimum Deriv Read scope. Trade scope is not currently used; no trade execution is implemented.
- Android permissions cannot be forced or bypassed. Sensitive actions require visible user confirmation.
- Offline model file import is not offline AI capability. Online AI requires an API key, network access and may incur provider charges.
- BOBBY must identify itself as AI in calls. No trading profit guarantees.

## Next milestones
1. Validate Deriv account authorization and build a live public-market/tick viewer.
2. Add chart rendering and a demo-only proposal/order lifecycle with strict risk limits and emergency stop.
3. Integrate a compatible LiteRT-LM runtime/model and test on the target Android device.
4. Add approved wake-word/background voice and platform-supported phone automation.
5. Add document parsers and multimodal AI, web research, communication integrations and complete QA.


## Plugin hub milestone (in development)
- Added an in-app plugin hub/catalogue entry point and local registry for GitHub, Deriv, Web Research and Google Drive, plus custom HTTPS connector registration.
- GitHub fine-grained token can be stored using the Android Keystore encrypted vault. Use minimum read-only repository permissions.
- Important: registry/configuration is not the same as an active connector. GitHub API operations, OAuth-based Drive access, web research, and custom connector execution still require their dedicated adapters and permission flows. The UI labels these limitations instead of pretending they work.
