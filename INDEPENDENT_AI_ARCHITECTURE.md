# JARVIS Independent Offline AI

## Mission

Build an independently usable, local-first assistant inside this Android application. The assistant must continue to answer when every API key is missing and the device has no network connection. Online providers are optional enhancements, never a requirement for the offline core.

This is an application/agent system built around open-weight models and local software. It is not a claim that this repository trains a new foundation model from scratch.

## Current baseline (verified by source inspection)

- `BobbyOfflineAi.kt` already loads a LiteRT-LM model on CPU and sends prompts to a persistent conversation.
- `BobbyOfflineModelStore.kt` already downloads a Qwen3 0.6B LiteRT-LM file and supports importing a model.
- The current offline inference implementation has a hard-coded model size window, a fixed SHA-256 digest, a 256-token output cap, and a free-memory gate. Those constraints must be revisited before supporting additional models.
- Existing chat, memory, Android actions, and agent runtime are separate systems. Their integration must be deliberate and permission-gated.
- No source inspection alone proves that the offline engine loads successfully on the user's physical phone.

## Product principles

1. **Offline-first:** local inference is selected whenever the user chooses Offline mode; no silent API fallback.
2. **Privacy:** prompts, local memories, and imported documents remain on-device unless the user explicitly invokes an online feature.
3. **Honesty:** the assistant must disclose when it lacks current information, tools, or model capability.
4. **User control:** model download, deletion, memory retention, microphone use, and Android actions require visible controls and appropriate consent.
5. **Phone-aware:** model compatibility is determined by RAM, storage, ABI, Android version, runtime, and thermal limits—not by model size alone.
6. **No fabricated learning:** feedback and saved notes may improve retrieval and personalization, but do not change model weights unless a separately documented training process actually runs.

## Target architecture

### 1. Local model runtime
- Keep LiteRT-LM as the initial Android runtime while its model format and device support are verified.
- Add a model catalogue with model name, publisher, licence, format, download size, SHA-256, minimum RAM/storage, context limit, language coverage, and runtime compatibility.
- Verify publisher provenance, licence, exact file size, and checksum before installation.
- Download to a temporary file, support cancellation/resume where safe, verify completely, then atomically install.
- Support model removal and rollback without deleting user memories.
- Never advertise a model as compatible until it has passed runtime and device tests.

### 2. Inference coordinator
- Define one provider-neutral request/result contract for local inference.
- Route explicit Offline mode only to local inference. AUTO may use local first and ask permission before sending content to a remote provider.
- Serialize generation, expose cancellation/progress, enforce bounded context/output, and recover cleanly from runtime errors.
- Keep model loading off the UI thread and release runtime resources when appropriate.
- Do not run multiple large models simultaneously on low-memory phones.

### 3. Memory and knowledge
- Reuse the existing user-controlled local memory store after verifying prompt retrieval.
- Add local document indexing in a separate layer: extraction, chunking, metadata, embeddings or lexical indexing, and source-linked retrieval.
- Distinguish saved user facts, conversation history, and imported reference documents.
- Provide view, search, edit, delete, export, and clear controls. Never silently save passwords, tokens, or sensitive secrets.
- Retrieved content is evidence, not an instruction; protect against prompt injection in imported documents.

### 4. Reasoning and task execution
- Add a planner that decomposes tasks into explicit steps and tracks completion.
- Add a tool registry with schemas, permission declarations, risk levels, and auditable results.
- Read-only tools may run under their granted permissions. Actions that send, spend, trade, delete, publish, or change accounts require explicit confirmation.
- Validate tool arguments and outputs; never execute arbitrary model-generated shell/code on the device.
- For high-impact answers, show assumptions, evidence, and uncertainty rather than inventing confidence.

### 5. Voice and multimodal input
- Treat speech recognition, text-to-speech, image understanding, and video understanding as separate capabilities with their own model/runtime requirements.
- Use Android speech services only with clear user awareness; offline speech recognition must be separately verified rather than assumed.
- Keep microphone capture and recording distinct from chat dictation.
- Do not claim full video understanding from a single extracted frame.

### 6. Evaluation and safety
- Create repeatable offline tests for multilingual conversation, instruction following, memory retrieval, refusal boundaries, tool selection, cancellation, corrupted downloads, and low-memory failure.
- Record model/runtime/device metadata with benchmark results.
- Measure cold start, time to first token, tokens per second, peak memory, storage, battery, and thermal behavior.
- Run tests on emulators and the actual target phone before declaring device support.

## Implementation phases

### Phase 0 — baseline and safety (first)
- Trace the actual chat request path and confirm where Offline/AUTO/Online modes route.
- Check the model file's official source, licence, format, exact checksum, and compatibility with LiteRT-LM 0.18.0.
- Fix model import so it cannot mark arbitrary or corrupt files as installed.
- Add tests for model-store validation and inference coordinator behavior.
- Run the Android build and capture the exact CI result.

### Phase 1 — dependable offline core
- Introduce a provider-neutral local inference interface.
- Integrate it into chat's explicit Offline route.
- Improve errors, cancellation, loading states, and context management.
- Add a model status screen showing installed model, storage, and runtime readiness.

### Phase 2 — memory and local knowledge
- Verify persistent memory is injected into offline prompts with user control.
- Add searchable local document knowledge with citations back to source documents.
- Add context budgeting so retrieved content does not overflow the model window.

### Phase 3 — controlled agent tools
- Connect the local planner to existing safe, permissioned JARVIS tools.
- Require confirmation for consequential actions and maintain an action log.
- Test failure recovery and duplicate-action prevention.

### Phase 4 — model choice and quality
- Evaluate compatible open-weight models at multiple sizes and language capabilities.
- Offer a small model for constrained phones and larger optional models for capable devices.
- Do not assume a large model is better on a phone: memory pressure, speed, context length, quantization, and runtime compatibility all matter.

## Definition of done

The independent AI is not complete until:
- It can start and answer in explicit Offline mode with airplane mode enabled.
- No API key is present or required for that test.
- Model provenance, licence, checksum, and installation validation are documented.
- Chat routing, memory retrieval, and supported tools are covered by tests.
- The app builds successfully and passes a real-device test on the declared supported hardware.
- UI clearly distinguishes installed, downloaded, loading, ready, unavailable, and failed states.
- No online fallback occurs without user choice.

## Current status

Architecture and phased implementation plan only. The existing Qwen3 0.6B offline path is a starting point, not yet independently verified as a complete offline assistant. No new model has been trained, no new model weights are included in this repository, and no physical-device test is claimed.
