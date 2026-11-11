# BOBBY implementation status

A successful GitHub Actions APK build proves compilation, not device-level functionality. Features are only marked fully complete after functional and target-device testing.

## Implemented foundation
- Android Kotlin/Jetpack Compose app, black/red dashboard, floating robot overlay and GitHub Actions debug APK build.
- OpenAI-compatible online AI chat with configurable HTTPS endpoint/model and Android Keystore AES-GCM encrypted API-key storage.
- Expanded assistant system instructions for factual accuracy, uncertainty, untrusted document handling, action confirmation and trading safety.
- User-approved local memories are now included in the online assistant context (limited to recent 12 records); profile and local date/time context are also supplied.
- Text-file imports can now be attached to the conversation and analysed by the configured online AI (up to 12,000 characters per attachment). The user must understand that selected text is sent to their configured AI provider for analysis.
- Natural neural speech path instructed to speak the response in its detected language, with Android TTS fallback selecting a best-effort language from the reply. Voice recognition no longer forces English and follows the system recognizer's language settings. Incoming-call announcements are localized for several common languages, including Yoruba, Hausa and Igbo; installed TTS voices and recognizer support vary.
- Android Keystore AES-GCM vault and Deriv v3 WebSocket account-authorisation foundation. The current Deriv connector verifies account details only; it does not place trades.
- Local memory/profile, onboarding, weather, basic text-file import, permission centre and demo-only trading guard.
- Offline AI is wired to Google's LiteRT-LM CPU runtime with a multilingual Qwen3-0.6B assistant system prompt. The public 614 MB model downloads directly into app-private storage with size, free-space and SHA-256 verification. An in-app TEST MODEL action checks actual runtime loading/inference. For lower-memory phones, inference uses a reduced output limit (256 tokens) and smaller sampling configuration; model loading is refused when Android reports low memory or less than 700 MB currently available, and the runtime is released on Android memory-pressure callbacks. Target-device performance and memory stability remain unverified.

## Not complete / not device-tested
- PDF/Office/ZIP/image/audio/video extraction and vision/audio/video model analysis.
- In-app web search/browser and source citations.
- Deriv symbol catalogue, live tick streaming, charting, proposals, demo order lifecycle, portfolio and trading strategy. Live trading remains disabled.
- LiteRT-LM runtime and in-app model download/test flow are implemented; actual model load, inference speed, memory use and stability still need target-phone verification.
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


## 2026-11-09 — Multilingual voice and offline model hardening
- Removed the hard-coded `en-NG` speech-recognition language so the Android speech recognizer can follow the device/provider language settings instead of being forced to English.
- Android TTS now starts with the device's preferred locale and selects a best-effort voice matching the generated reply's language when the installed engine supports it.
- Neural speech instructions now explicitly request native-language pronunciation, tones, rhythm and code-switching instead of translating speech into English.
- Incoming-call announcements now use localized phrases for Yoruba, Hausa, Igbo, French, Spanish, Portuguese, German, Italian, Arabic, Hindi, Chinese, Japanese and Korean; the phone must have a compatible TTS voice installed.
- Offline Qwen3 model conversations now have a Bobby-specific multilingual system instruction, sampling configuration and bounded output length.
- Hardened the 614 MB Hugging Face download with available-storage checks, exact advertised-size checks and SHA-256 verification against the public model file.
- Added a TEST MODEL control in the Offline AI dialog to perform first local model load and inference and report success/failure in the UI.
- GitHub Actions compilation and APK artifact are pending for the latest commits. No real-device test has been performed. The lightweight 0.6B model has limited reasoning ability compared with larger online models, and 4 GB RAM phones may be slow or run out of memory.


## Latest build repair — 2026-11-08
- GitHub Actions run `42534298157` failed during Kotlin compilation. PdfBox-Android uses the relocated `com.tom_roush.pdfbox` package namespace, not `org.apache.pdfbox`; LiteRT-LM 0.18.0 `Message` also does not expose the newer `.text` property.
- Corrected the PDFBox imports and switched offline response extraction to the 0.18.0-compatible message string representation. Rebuild pending.


## Verified build — 2026-11-08 23:23 UTC
- Latest checked branch commit `060384bd9ad773cba5e664a5149f727058e64050` built successfully in GitHub Actions run `42534667935`; debug APK artifact `bobby-debug-apk` uploaded (approximately 67 MB).
- The successful build includes PDFBox Android extraction, DOCX/XLSX/PPTX/ZIP text extraction, image downscaling for vision requests, LiteRT-LM offline model download/inference wiring and test control, plugin directory (142 provider entries), Brave Search adapter, GitHub identity verification, scheduled reminders, alarm/app-launch actions, user-authorised screen capture/vision flow, and notification-access-based incoming-call announcements.
- This is compile/build verification only. The phone must still download and test the optional 614 MB Qwen3 0.6B model; real inference, API credentials, notification access, screen capture, alarms and reminders require device testing.
- Still incomplete: wake-word always-on assistant, complete OAuth/API adapters for the entire catalogue, Deriv live market streaming/order execution, audio/video transcription, scanned-PDF OCR and full offline multimodal inference.


## 2026-11-09 — Neon robot artwork and launcher icon refresh
- Reworked `bobby_robot_face.xml` into a more detailed neon-red anime-inspired assistant face with crown, headphones, illuminated eyes and layered dark hair. This asset is used by the floating robot bubble and adaptive launcher icon foreground.
- Updated the adaptive launcher icon background with a dark red neon gradient and border. Both `ic_launcher.xml` and `ic_launcher_round.xml` already reference these shared drawables, so the APK launcher icon updates automatically.
- This is a lightweight vector illustration inspired by the supplied reference, not a true 3D model/render. Full-body 3D character animation and distinct listening/thinking/speaking/sleeping artwork remain incomplete.
- Build verification and on-device icon/bubble appearance check pending.


## 2026-11-09 — Creative Studio and 3D specialist profiles
- Added `.github/agents/creative-studio.agent.md` for visual art direction, image-generation workflows, UI artwork, character concepts, Android asset integration and asset optimization.
- Added `.github/agents/3d-asset-engineer.agent.md` for actual editable 3D assets, GLB/glTF, materials, rigging, animation and Android rendering constraints.
- Added routing guidance to GitHub Copilot instructions and the Codex workflow so visual tasks load the appropriate specialist profiles.
- These files are agent instruction profiles, not proof that an image-generation provider, Blender runner, 3D generation API or Copilot agent picker is connected. Actual image/3D generation requires an available generation tool or configured provider. Codex can follow these profiles to implement integrations and asset pipelines.
- No new 3D model or generated image asset was produced by this configuration change. No Android runtime 3D renderer was added or device-tested.

## 2026-11-09 — GitHub coding-agent foundation
- Added a manually triggered GitHub Actions workflow at `.github/workflows/bobby-codex-agent.yml` using the official OpenAI Codex GitHub Action.
- The workflow is restricted to `bobby-development`, reads the existing `AGENTS.md` and selected ECC-derived skills, uses a workspace-scoped Codex permission profile, and proposes code changes through a reviewable pull request rather than merging automatically.
- Added GitHub Copilot repository instructions and specialist agent profiles for Android engineering, security review and QA verification under `.github/`.
- The repository still contains a selected set of five ECC-derived skills, not the full ECC agent catalogue, hook system, MCP servers or native plugin runtime. Codex is the execution engine in the new GitHub workflow; ECC supplies project workflow guidance.
- The Codex workflow cannot run until the repository owner adds an `OPENAI_API_KEY` GitHub Actions secret. This is an OpenAI API credential and is separate from a ChatGPT subscription or the API key stored inside the BOBBY Android app.
- The workflow has not yet completed an agent run. Proposed changes require review and explicit merge. GitHub Copilot cloud-agent availability depends on repository/account enablement and is not assumed.
- Android CI builds triggered by these commits are pending. The latest verified APK build remains the earlier successful build; no new APK is claimed for this milestone.


## 2026-11-09 — Public API Explorer foundation (implementation added; build verification pending)
- Added `BobbyPublicApiCatalog`, which downloads the upstream public-apis README over HTTPS, parses category/provider metadata, validates documentation links, stores a local cache and preserves the last known-good catalogue when refresh fails.
- Added a searchable Public API Explorer section inside the existing ADD PLUGINS dialog, including manual refresh, source link, API metadata and open-documentation actions.
- Registered a read-only `public_api_catalog` planner tool for catalogue discovery. It does not execute third-party APIs.
- Added the Public API Directory to the existing plugin catalogue. Directory listings remain discovery-only and are not represented as connected adapters.
- This is Phase 1 foundation only. No bulk API integrations, OAuth connections, arbitrary endpoint execution, image-generation service or 3D-generation service were added by this change.
- Gradle build, automated parser tests and physical Android device verification have not yet been run; compilation and runtime behavior remain unverified.


## 2026-11-09 — Live web research in normal BOBBY chat (implementation added; build verification pending)
- Connected the existing Brave Search adapter to the ordinary AI chat path for questions containing explicit freshness/research intent (latest, today, current, news, price, weather, scores, web search, etc.).
- Search runs only when the user has configured a Brave Search key in Web Research settings. Up to six result titles, descriptions and source URLs are passed as untrusted research context to the configured AI provider.
- BOBBY is instructed to distinguish source claims, cite supplied URLs and disclose when search could not be performed. If no search key exists, it must not pretend to browse.
- This is a limited search-trigger heuristic, not a full browser, arbitrary API execution engine, or guaranteed retrieval for every query. No web search key is bundled.
- Build, automated tests and Android-device verification remain pending.


## 2026-11-09 — Creative Studio adapters (implementation added; verification pending)
- Added an in-app Creative Studio entry for OpenAI image generation and Meshy text-to-3D GLB generation.
- Image generation uses the configured OpenAI API key and official Images API. Meshy API key is stored with Android Keystore encryption.
- 3D generation follows Meshy's two-stage preview/refine workflow, polls task status, downloads a textured GLB, and exports it through Android's document picker.
- Provider accounts, API access and credits are required. No credentials are bundled. This does not yet include an embedded interactive 3D viewport.
- Build, tests, API live calls and Android-device verification remain pending.

- Creative Studio source file commit: `488fc2ef0b8875891d538dbddf3ebaf5ac6be7e3` (branch `bobby-development`). Dashboard and plugin catalogue integration commits: `6bfaee59879c77fa43688f3dfdf1605462eb99fd` and `3afbf1a00020867b5b9ea6a162a090e92f656575`.
- GitHub combined status query returned no checks at verification time; this is not evidence of a successful build. The Android Actions workflow is configured to build on pushes to `bobby-development`, but its result and APK artifact have not yet been confirmed.


## 2026-11-09 — Custom BOBBY voice cloning (implementation added; build and provider tests pending)
- Added an in-app CUSTOM BOBBY VOICE dialog and audio-file picker to the existing dashboard.
- Added ElevenLabs Instant Voice Cloning integration using the documented `POST /v1/voices/add` multipart endpoint. The user must affirm ownership or explicit speaker permission before uploading a sample.
- ElevenLabs API key and cloned voice ID are stored encrypted using the existing Android Keystore-backed `BobbySecretVault`.
- Updated speech output to prefer the selected cloned ElevenLabs voice (`/v1/text-to-speech/{voice_id}`), then fall back to the existing OpenAI neural voice, then Android system TTS.
- Added a preview button and local disconnect/clear action. Clearing local settings does not delete the remote ElevenLabs voice from the provider account.
- No additional APK is required. An ElevenLabs account/API key is still required, and cloning/speech use is subject to the provider's plan, limits and possible charges. The recording is uploaded to ElevenLabs; it is not cloned entirely on-device.
- No provider key is bundled. API key storage is encrypted on the phone.
- Source commits: voice clone UI/connector `ce7a23fddc1c49a16ff054b2f264da0ba499b7bc`, neural speech routing `104951c6d16cd665e1eb4777c23c0417a7e34b03`, dashboard entry `ebbef4c46663cfbdbb56a4ee9f9f7c4a95c7262f`.
- Build, live API calls and physical Android-device verification are still pending. Do not treat this feature as tested until the APK is built and exercised with a valid provider key and permitted audio sample.


## 2026-11-09 — BOBBY 3D avatar, screen vision and startup loading screen (build/device verification pending)
- Added an original full-body BOBBY robot avatar as a real interactive 3D scene, built from 3D geometry with dark metallic armor, crimson emissive details, illuminated eyes, headphones and a crown-like headpiece. It is accessible from the dashboard's **3D ROBOT** button.
- The built-in avatar does not require a Meshy subscription or a separate APK. The WebView downloads the Three.js renderer from jsDelivr at runtime, so an internet connection is required for the first render.
- The Creative Studio 3D workflow now includes a ready-made BOBBY robot design prompt, Meshy generation and an interactive GLB preview. Meshy is optional and still subject to its own API access and credits.
- Added an animated black/crimson BOBBY startup screen with pulsing robot emblem and progress indicator; it displays briefly while the app initializes.
- Screen capture was already implemented and is retained: the **SEE MY SCREEN** control requests Android MediaProjection consent, captures one screen frame, then passes it to the configured vision-capable AI model for analysis. It is user-authorized, not hidden or continuous capture. Image analysis requires an AI provider/model that supports vision.
- Source commits include: 3D GLB preview `810a479d85fca9fde0887b1da735bd46de4c87b1`, BOBBY 3D preset `4a80820d4063e40c2a3fc009be92acc2ea8f3187`, startup wiring `265dbfce1ee8a11b8ffd7cff2a81b20de1cafa18`, startup screen `306e97cf0b151f0d4f0dcdf60871e293b777a11f`, original interactive robot scene `e73d83f79fb334e262443e107732b329a5202542`, dashboard button `a07196dcb82c90738e40ea79bac6902a5e87c3d6`.
- APK compilation and physical-device testing remain pending. Do not claim the new screens are working on-device until a successful Actions build and installation test are confirmed.


## 2026-11-09 — Categorised Memory Vault upgrade
- Extended the existing local memory store with stable-ID edit/update support and natural-language save commands such as “Bobby, remember this: …”.
- Added automatic category assignment for Personal, Projects, Important Dates, Instructions and General memories. This is rule-based classification, not a claim of perfect semantic categorisation.
- Updated memory retrieval to search using multiple terms from the latest user question and include matching user-approved records in the online AI context, rather than injecting a fixed set of the newest records.
- Updated the existing Memory Vault UI with category entry, search, edit, update, delete, clear and JSON export controls. Data remains in app-private SharedPreferences on this phone and survives ordinary app restarts; it is not cloud-synchronised or an encrypted filesystem vault.
- Basic credential-pattern filtering remains in place. Users should not save passwords, API tokens, financial credentials or highly sensitive personal data.
- Source commits: memory capture/update/retrieval `fbcf64b70942d452bed185ec1c4d7720f1944893`, chat integration `b36e85333b106336a62a87f57735e6394e7c913e`, category/search UI `05219a1ab69969829c2e9369b001a2539cf3f63d`, search matching `0da16808d795c76af11457b44a10787e843a1bbb`, callback label `c6929835e37714e69b289c645a7bea20378762b4`, editable vault UI `970d5bbc2e77637595ac46337e0cb6ec6463e66e`, save/update correction `ab5450ef225d3d91e4ba0ba434806865869bf591`.
- Build and runtime verification pending. Automatic save/retrieval, UI editing, persistence and secret filtering must be tested in the APK before marking complete.



## 2026-11-11 — Creative Studio video and music adapter upgrade (build and device verification pending)
- Audited the existing Creative Studio instead of creating a duplicate generator. The app already had an OpenAI Images API image generator, a Meshy text-to-3D GLB workflow, local PDF/HTML/XML/JSON/Markdown/CSV/code-file creation, and fal.ai queue-based video/TTS generation.
- Updated the video generator from the older MiniMax H3 Max route and mismatched request fields to the live-schema-verified `bytedance/seedance-2.5/us/text-to-video` endpoint. The request now uses supported fields: prompt, 5-second duration, 720p resolution, 16:9 aspect ratio, H.264, and synchronized audio.
- Added an original-music tab using the live-schema-verified `elevenlabs/music/v2.5` endpoint. Users can set a prompt, duration from 3 to 600 seconds, and instrumental-only mode. Existing ElevenLabs v4 text-to-speech remains available.
- Updated the 11-capability overview so it names the actual Creative Studio providers and clearly states that API keys, account access, and provider credits may be required.
- Current live fal catalog/schema checks confirmed the endpoint IDs and request/response fields; this was a schema inspection, not a paid generation test. No media was generated and no user provider key was accessed.
- Provider credentials continue to be stored in the Android Keystore-backed vault. Generated media is stored in app-private storage until exported through Android's document picker.
- Remaining: build the APK, test video/audio/music queue completion and downloads with a user-authorised provider key, test OpenAI image generation and Meshy 3D on device, add reference-image/video editing workflows, add media playback/preview and a visible generated-assets library, and test on the Huawei Android 8 target device.
- Source commits: video/music adapter update `33d51ddd3ae51ba181c4ee1dba1c941ad4874c0c`; capability overview update `15e0d28387f5521fd80c6c14a2af4201d29526eb`.
- Status: implementation updated; APK build and provider/device tests pending. Do not mark the creative studio fully green until these checks pass.
