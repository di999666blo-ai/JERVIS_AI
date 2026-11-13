---
name: android-verification
description: Verify Android changes with repository-appropriate Gradle tasks, tests, artifact inspection, and explicit target-device acceptance reporting.
license: MIT
---

# Android Verification Loop

## 1. Discover the actual project commands
- Inspect `gradlew`, Gradle wrapper properties, settings, module build files, and CI workflows.
- List available tasks with `./gradlew tasks --all` when the environment permits.
- Do not assume a module name, SDK version, lint task, or test runner.

## 2. Establish a baseline
- Run the narrowest relevant existing test before changing code.
- Record baseline failures separately from regressions introduced by the patch.
- Identify whether the repository has unit, instrumented, lint, and assemble tasks.

## 3. Test the change
- Add tests for pure logic, validation, parsing, state transitions, and error handling where practical.
- Run the narrowest test task first, then broader checks that are available.
- Do not fabricate test files, test counts, coverage, or device results.
- If Android SDK or dependency downloads are unavailable, report the exact blocker.

## 4. Build and inspect
- Attempt the configured debug build using the wrapper, typically `./gradlew --no-daemon assembleDebug`.
- Record the exact command, exit code, and relevant error.
- If a build succeeds, inspect the APK path, package metadata, and artifact upload result when possible.
- Do not call an APK final, release-ready, signed for distribution, or install-tested unless those facts are verified.

## 5. Review
Inspect the diff for:
- Existing dashboard/navigation regressions
- Manifest and permission changes
- lifecycle leaks, unbounded work, and main-thread operations
- secret exposure and unsafe file/network handling
- accessibility and error-state omissions
- data migration or compatibility risks

## 6. Target acceptance
Report each layer separately:
- Source changed
- Local tests run and result
- Build result
- CI result
- APK artifact inspected
- Physical-device test result

When no physical device is connected, mark device acceptance NOT PERFORMED and name the exact user-side test still required. Never infer runtime success from compilation.
