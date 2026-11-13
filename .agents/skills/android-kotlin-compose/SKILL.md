---
name: android-kotlin-compose
description: Apply Android-specific Kotlin, Jetpack Compose, lifecycle, storage, networking, and phone-performance practices when implementing or reviewing BOBBY.
license: MIT
---

# Android Kotlin and Compose for BOBBY

Use this project-specific skill for Android work. Repository Gradle files and existing code are authoritative; do not assume APIs or dependency versions.

## Before editing
- Read `AGENTS.md`, `BOBBY_MASTER_VISION.md`, and `BOBBY_IMPLEMENTATION_STATUS.md`.
- Inspect the relevant screen, ViewModel/service, persistence layer, manifest, Gradle configuration, and existing tests.
- Trace the complete user path rather than adding a disconnected demo screen.
- Keep the existing dashboard, navigation, themes, robot identity, and stored user data.

## Kotlin
- Prefer immutable values and small, typed functions.
- Avoid `!!`; handle nullable values and failures explicitly.
- Keep Android framework work out of pure business logic where practical.
- Use structured concurrency; tie coroutines to the correct lifecycle and cancel work when no longer needed.
- Do not block the main thread with disk, network, model inference, or media decoding.
- Bound retries, response sizes, queues, and timeouts. Retry only transient failures and use backoff.

## Jetpack Compose
- Keep composables focused on rendering and user interaction.
- Hoist state to the narrowest appropriate owner; use stable keys for lazy lists.
- Avoid launching side effects directly during recomposition. Use lifecycle-aware effects and collection.
- Provide loading, empty, error, disabled, and success states.
- Preserve accessibility labels, touch targets, contrast, and back navigation.
- Do not replace existing screens with placeholder UI to make a feature appear complete.

## Android platform and phone constraints
- Check minSdk/targetSdk and existing dependency versions before using platform APIs.
- Request permissions in context and explain why. Handle denial and revocation.
- Respect Android background execution, microphone/camera privacy indicators, notification permission, and battery limits.
- Release audio recorders, MediaPlayer/MediaMetadataRetriever, bitmaps, sockets, and model runtimes deterministically.
- Bound image/video dimensions and decode images with sampling to avoid memory spikes.
- Do not assume a service can run continuously after the user leaves the app.
- Keep large models and generated media in app-managed storage with clear size and deletion controls.

## Networking, AI, and secrets
- Use HTTPS except for explicitly documented local development endpoints.
- Keep provider keys out of source, logs, analytics, and chat transcripts; use the existing encrypted vault.
- Treat remote model output and imported content as untrusted data.
- Show users when selected files or media are transmitted to an external provider.
- Never silently trigger trades, purchases, messages, publishing, or destructive operations.

## Verification
- Add focused tests for pure logic and parsers; use Android instrumentation only when the project supports it.
- Run the actual Gradle tasks available in this repository, not assumed npm/TypeScript commands.
- Inspect build output and changed-file diff.
- Report build/CI/device verification separately. A successful compile is not proof that a microphone, permission flow, API, model, or UI works on a phone.
