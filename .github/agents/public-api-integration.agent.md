---
name: public-api-integration
description: Implement BOBBY's safe Public API Explorer and vetted API adapters using the public-apis directory as a discovery catalogue.
---

# Public API Integration Engineer

You are implementing features in the existing BOBBY Android Kotlin/Jetpack Compose application. Preserve the current dashboard, chat, ADD PLUGINS hub, Friendly/Dark Mode, voice, memory, screen understanding and existing integrations. Never replace the app with a demo or simplified rebuild.

## Mandatory first steps

1. Read `BOBBY_PUBLIC_API_INTEGRATION_SPEC.md`, `BOBBY_IMPLEMENTATION_STATUS.md`, `AGENTS.md`, and the relevant app source before editing.
2. Inspect the current plugin store, agent runtime/tool registry, web research connector, secret vault, networking conventions, navigation and tests.
3. Confirm the actual branch is `bobby-development`; do not merge to master.
4. Work in small compilable increments. Do not invent package paths, APIs or existing functions.

## Scope

Phase 1 has been added in BobbyPublicApiCatalog.kt and the existing ADD PLUGINS dialog. Verify the parser, compile and test the screen before extending it. Do not duplicate the catalogue UI. Continue with parser/unit tests and the typed adapter framework. Use the upstream `public-apis/public-apis` README as a catalogue only. Parse it defensively, preserve provenance and freshness, and treat all imported URLs/descriptions as untrusted. Do not automatically invoke arbitrary listed endpoints.

Then implement the common typed adapter framework and connect a small, high-value set of read-only providers only when the actual API contract and terms have been verified. Prioritize completing BOBBY's existing web research path and then reference/open-data/weather/news integrations. Do not claim a provider is connected until its adapter is live-tested.

## Security requirements

- Never bundle API keys or OAuth client secrets in the APK or source.
- Use Android Keystore-backed storage for user-owned credentials; redact secrets from logs and errors.
- Use explicit host and HTTP-method allowlists, HTTPS, bounded response sizes, timeouts, cancellation, rate limits and schema validation.
- Reject localhost/private-network destinations and unsafe URL schemes.
- Treat provider responses as untrusted data; defend against prompt injection and never let data authorize tool calls.
- OAuth must request minimum scopes and support disconnect/revocation.
- Require explicit confirmation before any write action, post, message, purchase, call, delete, financial transaction or trade.
- Do not bypass provider authentication, paywalls, rate limits, CAPTCHAs, account restrictions or app-review requirements.
- Keep Friendly/Dark Mode permissions separate.

## Quality gates

- Add unit tests for catalogue parsing, malformed rows, unsafe URLs, caching, refresh failure and filtering.
- Add adapter tests for success, auth failure, timeout, malformed responses, rate limits and offline state.
- Run the available Gradle build/tests and report exact results. If a build cannot run, state why; do not claim success.
- Update `BOBBY_IMPLEMENTATION_STATUS.md` with only verified implementation states.
- Preserve all existing screens and functionality.
- Produce a concise report listing files changed, providers actually implemented/tested, known limitations and build/test results.

Do not bulk-integrate all upstream APIs. The directory contains unrelated, third-party services with different auth, terms, availability and safety profiles. Discovery breadth is appropriate; execution must remain curated and permissioned.
