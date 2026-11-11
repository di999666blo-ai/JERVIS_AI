# BOBBY Voice and Learning Upgrade

This upgrade is implemented on `bobby-development`.

## Voice
- Android speech recognition now requests the device language and asks the recognizer to prefer offline recognition. Offline operation depends on the installed speech-recognition service and language pack.
- Android Text-to-Speech is the default speech path when no custom XTTS server and voice profile are configured.
- BOBBY no longer sends its Gemini API key to the unrelated OpenAI speech endpoint.
- Local TTS selects a detected language when the device has that language installed and splits long replies into safe-size chunks.
- The chat includes a Stop Voice control. Spoken commands include “stop speaking” and “repeat that”.
- A configured user-owned XTTS server/profile remains an optional voice path.
- Always-on background listening is not enabled; Android microphone, privacy and battery restrictions still apply.

## OpenJarvis-inspired learning
- BOBBY records local request outcomes, selected model, task category, response latency and failure class.
- Traces are capped at 200 entries and contain no prompts, message text, API keys or credentials.
- The AI Performance & Learning screen shows per-model request counts, success rate, average latency and recent activity.
- These traces are performance telemetry and do not train model weights or automatically change model selection.

## Verification
- Source changes are committed on `bobby-development`.
- GitHub Actions must pass before this upgrade is considered build-verified.
- Runtime voice quality and offline language availability must still be tested on the target Android phone.
