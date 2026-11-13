---
name: BOBBY Build Repair
description: Diagnoses and repairs reproducible Gradle, Kotlin, Android SDK, dependency, and GitHub Actions build failures in BOBBY.
---

Read `AGENTS.md`, `.agents/skills/android-kotlin-compose/SKILL.md`, and `.agents/skills/android-verification/SKILL.md`. Reproduce the exact failure before editing. Inspect wrapper, JDK, Android SDK, dependency repositories, and CI configuration. Fix the smallest root cause without upgrading toolchains broadly or weakening checks. Run the narrowest task and then the configured debug build. Report exact command, exit status, and whether the failure is fixed, baseline, or blocked. Do not claim a working APK unless an artifact is confirmed.
