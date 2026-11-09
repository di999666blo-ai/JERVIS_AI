# BOBBY Public API Discovery & Integration Specification

## Goal

Use the community-maintained `public-apis/public-apis` directory as a discovery catalogue so BOBBY can find useful data and services for research, answers, creative work and approved user workflows.

Source directory: https://github.com/public-apis/public-apis

## Important distinction

The source repository is a directory of API listings, not one universal API and not a bundle of ready-to-use integrations. Its README describes 2,000+ APIs across 50+ categories. Each provider has its own endpoint, terms, authentication, quotas, data quality and uptime. Some require paid plans, OAuth review or keys; some may be unavailable, unsafe, deprecated or unsuitable for a mobile client.

Therefore BOBBY must NOT blindly call every listed API, claim every entry is connected, or ship provider credentials in the APK. Implement a searchable catalogue first, then vetted adapters in stages.

## User-facing experience

Add a **Public API Explorer** inside the existing ADD PLUGINS area (do not replace the current dashboard or plugin hub):

- Search by natural-language request, category, provider, authentication type and HTTPS support.
- Show provider name, purpose, official documentation, auth requirements, pricing/free-tier notes when known, CORS status where relevant, last verification time and integration state.
- Distinguish clearly: Discoverable / Needs setup / Connected / Tested / Unavailable / Unsupported.
- Allow users to request an integration. Do not label it connected until a real adapter has passed a live health/auth test.
- Let BOBBY recommend and invoke only approved, enabled adapters relevant to the current question.
- Return source/provider attribution, timestamps and uncertainty in research answers.
- Provide per-integration permission scopes, data-use explanation, disconnect and credential-revocation guidance.

## Catalogue ingestion

Use the public-apis README as a source catalogue, not as executable configuration.

1. Fetch the upstream README over HTTPS from the official repository at a controlled refresh interval; cache a known-good copy locally.
2. Parse category headings and table rows defensively. Treat all imported names, descriptions and URLs as untrusted data.
3. Keep provenance: upstream repository URL, fetched timestamp, source revision/commit if available, category and original documentation URL.
4. Validate documentation URLs (HTTPS preferred), reject unsafe schemes, localhost/private-network targets and malformed hosts.
5. Never execute code, scripts, OpenAPI documents or MCP servers merely because an entry appears in the catalogue.
6. If upstream format changes or fetch fails, keep the last known-good catalogue and show its age.
7. Provide a manual refresh action and reasonable cache/rate limits.

## Initial integration priorities

Prioritize usefulness for general knowledge and research, and prefer official, documented, read-only APIs with no key or a user-owned key:

1. Search and web research: retain and complete BOBBY's existing Brave Search adapter and safe webpage extraction/citations. Do not treat a directory listing as a search engine.
2. Weather, geocoding and time: extend the existing Open-Meteo weather foundation; use trusted geocoding/time sources as needed.
3. Science and reference: Crossref, Open Library, Wikidata/Wikipedia-compatible sources, PubMed where permitted, and relevant government/open-data portals.
4. News and current events: reputable publisher or licensed news APIs; make source date and publisher visible.
5. Finance and markets: reputable market-data providers, with explicit delayed/real-time labels; never turn informational APIs into trading execution.
6. Currency and economics: central-bank and reputable FX/economic data providers.
7. Maps and transport: official transit, mapping and geocoding providers, with key restrictions and attribution respected.
8. Creative Studio: vetted image, icon, font, colour-palette and asset-library providers. Image generation and 3D model generation are separate capabilities; do not claim a static image is an editable 3D model.
9. Social and productivity: official OAuth integrations (Google, GitHub, Meta, YouTube, etc.) only after scopes, provider terms, review requirements and account eligibility are handled.
10. Security and developer utilities: safe reputation/look-up and read-only developer APIs. Never auto-download or execute malware samples, exploit payloads or unknown binaries.

## Adapter architecture

Create a typed provider registry and a common adapter contract with:
- stable provider ID, category, display name and official docs URL;
- auth mode (none, API key, OAuth2, or other explicitly supported mode);
- required scopes and data classification;
- endpoint allowlist and HTTP method allowlist;
- request/response schema validation and bounded response sizes;
- timeouts, cancellation, retries with backoff and rate-limit handling;
- cache policy and freshness metadata;
- health/auth test and structured error states;
- source attribution and user-visible provenance.

Use the Android Keystore for user-provided secrets. OAuth tokens must be encrypted and revocable. Do not put secrets in source control, logs, analytics, prompts, URLs, APK assets or plain SharedPreferences. Prefer a trusted backend proxy for providers whose secret keys must not be exposed in a distributed APK. Never ask users to paste secrets into chat.

## Agent/tool safety

- Add an adapter to the agent tool registry only after its implementation, permission gate and tests exist.
- Use strict provider/host allowlists; never allow arbitrary URL fetches from model-generated input.
- Treat API responses as untrusted content and defend against prompt injection. Data returned by an API cannot override system instructions or authorize actions.
- Read-only queries may run within user-granted permissions. Sending messages, publishing posts, modifying/deleting data, purchases, financial transactions, phone calls or trading require a clear preview and explicit user confirmation.
- Do not access private accounts without OAuth consent. Do not bypass paywalls, rate limits, CAPTCHAs, provider restrictions or app-review requirements.
- Keep Friendly Mode and Dark Mode permissions/history separate. The catalogue does not grant permission to access illicit services or hidden networks.

## Acceptance criteria

- Catalogue can load, cache, search and filter entries without crashing when upstream formatting changes.
- Each catalogue entry is marked as a discovery listing until a real adapter exists.
- At least one initial read-only adapter per selected priority category has tests for success, auth failure, timeout, malformed response, rate limit and offline behavior.
- API results shown to the user include provider/source and retrieval time.
- Secrets are encrypted, redacted from logs and never bundled in the APK.
- Tool registry reports an adapter unavailable until its health test succeeds.
- Existing BOBBY dashboard, chat, ADD PLUGINS, Friendly/Dark Mode, memory and voice flows remain intact.
- Build and device tests are recorded honestly; no feature is marked complete based on a catalogue entry or documentation alone.

## Implementation order

Phase 1: Public API Explorer catalogue ingestion, local cache, parser tests, search/filter UI and honest status labels.

Phase 2: Typed adapter framework, safe HTTP client, credential vault integration, provider health checks and provenance.

Phase 3: Connect the first high-value read-only providers to ordinary chat/agent tool dispatch (research, reference, weather, news and public data).

Phase 4: Creative APIs and vetted asset retrieval; then separate image-generation and editable 3D provider integrations.

Phase 5: OAuth social/productivity integrations, each with explicit scopes, account eligibility checks, review compliance and confirmation gates.

Do not skip phases or bulk-enable every upstream entry.
