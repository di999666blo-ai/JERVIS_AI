# ECC integration in BOBBY

## Purpose
This repository uses a selective, Android-focused adaptation of the open-source Everything Claude Code (ECC) engineering approach. The goal is to improve planning, implementation, security review, and verification while keeping BOBBY's Android runtime and existing in-app agent system separate.

Upstream project: https://github.com/affaan-m/ECC

## What is present in this repository
### Workflow skills
- `.agents/skills/tdd-workflow/`
- `.agents/skills/security-review/`
- `.agents/skills/verification-loop/`
- `.agents/skills/deep-research/`
- `.agents/skills/coding-standards/`
- `.agents/skills/android-kotlin-compose/` — BOBBY-specific Android adaptation
- `.agents/skills/android-verification/` — BOBBY-specific Android verification adaptation

### Specialist profiles
- Existing Android implementation, Creative Studio, 3D asset, security, public API integration, and QA profiles under `.github/agents/`
- Added BOBBY Planner, Architecture Reviewer, and Build Repair profiles.

## What this is not
- This is not a full installation of every upstream ECC agent, skill, hook, command, MCP server, memory runtime, or AgentShield component.
- It does not install ECC inside the Android APK and does not make development agents run on the user's phone.
- It does not grant GitHub Copilot or Codex entitlements. Agent-picker availability depends on the connected development tool and its plan/configuration.
- It does not add new external credentials or enable autonomous production actions.

## How to use
1. Start with BOBBY Planner for a multi-part feature.
2. Use Android Engineer for Kotlin/Compose implementation.
3. Use Architecture Reviewer for a design/diff review.
4. Use Security Reviewer for credentials, permissions, imports, network calls, or trading-related changes.
5. Use QA Verifier and Android Verification before calling work build-verified.
6. Use Build Repair only after capturing a reproducible build failure.

All agents must follow `AGENTS.md`, work only on `bobby-development`, preserve the current app, and report device testing honestly.

## Provenance
The existing selected ECC-derived skills are retained in place. The two Android skills and three BOBBY-specific agent profiles are local adaptations for this repository, not copied upstream ECC releases. Review the upstream license and installation guidance before adding further upstream components.
