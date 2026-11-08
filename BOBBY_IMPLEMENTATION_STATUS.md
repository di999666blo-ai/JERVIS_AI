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


## 2026-11-08 — Searchable plugin directory and GitHub identity adapter
- Expanded the in-app plugin catalogue to 100+ provider listings across categories, with local name/category/description search.
- Listings are explicitly labelled as catalogue-only unless a real adapter exists; enabling a listing does not claim a connection.
- Added a read-only GitHub identity verification adapter using the encrypted Android Keystore token vault. Users can save a fine-grained token, test it against GET /user, and disconnect/remove it.
- GitHub repository/issue/Actions write operations are still not implemented. OAuth flows for providers are still pending.
- Build and device verification pending for this milestone.


## 2026-11-08 — Offline AI, web research and Android quick actions (build repair in progress)
- Integrated Google's LiteRT-LM Android runtime dependency (0.18.0) and a CPU inference wrapper for an installed `.litertlm` model.
- Added an in-app download flow for the public Apache-2.0 Qwen3-0.6B LiteRT-LM model (approximately 614 MB) from Hugging Face into app-private storage; model is not bundled into APK. Offline inference is used when no online AI key is configured and as fallback when online AI fails, if the model is installed.
- Added Brave Search API adapter with encrypted API-key storage, search results, source opening and send-results-to-Bobby analysis workflow. User must create/authorise a Brave Search API key in the app.
- Added platform-mediated alarm and app-launch commands; Android Clock/target app still presents its own confirmation UI. No permission bypass.
- Latest build attempt failed at Gradle configuration because Kotlin 2.3 rejects legacy `kotlinOptions.jvmTarget`; migrating to compilerOptions in this commit before rebuilding.
- No successful build or on-device inference claim yet for these new milestones.


## Verified build milestone — 2026-11-08
- Commit `125f2d0ce9ba62017c9b8e3b54fea56c109311c3` built successfully with GitHub Actions run `42532783140`.
- APK artifact `bobby-debug-apk` was uploaded successfully (debug APK, approximately 60.3 MB).
- The build includes: searchable 100+ provider directory, encrypted GitHub token and read-only GitHub identity verification, Brave Search API adapter and research UI, LiteRT-LM 0.18.0 runtime integration, Qwen3-0.6B in-app model download, offline inference/fallback wiring, and Android alarm/app-launch intents.
- Not yet verified: runtime inference on a real phone, Hugging Face download completion, Brave API connection with a user key, GitHub connection with a user's token, and actual alarm/app-launch behaviour on device.


## 2026-11-08 — Scheduled reminder foundation
- Added local reminder scheduling using Android AlarmManager, persisted reminder metadata, notification channel/receiver and boot-time restoration.
- Added natural command parsing for reminders such as `remind me in 15 minutes to check the oven`.
- Exact alarms are used only when Android grants exact-alarm access; otherwise BOBBY falls back to an inexact idle-aware alarm. Notifications require Android notification permission.
- Build and device behaviour pending verification.


## 2026-11-08 — PDF, Office, ZIP and image file extraction
- Added local PDF text extraction with PdfBox-Android, bounded DOCX/XLSX/PPTX XML extraction, safe bounded ZIP text extraction, and image downscaling/JPEG preparation.
- File parsing now runs off the UI thread. Images are sent only to the configured vision-capable AI provider after the user selects them; local text/PDF/Office extraction happens on-device.
- Audio/video transcription and OCR for scanned PDFs are still not implemented. The current Qwen3 offline model is text-only; image understanding uses the configured online vision endpoint.
- Build verification pending.
