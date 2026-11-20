# Project instructions

- Each chain link represents one calendar day from the saved start date, left-to-right in rows. Keep missed days visible in place; today remains open until midnight. Show at least the goal length and extend for elapsed days. Do not use a rolling past-date window.
- Keep applicationId app.chains.personal and database chains.db permanently.
- Preserve all completion history, goals, reminders and break rules across updates. Schema changes need non-destructive migrations and tests.
- Today remains open until local midnight. Goal and rule edits must not remove marks.
- Increase versionCode and versionName for every published version. Update RELEASE_NOTES.md.
- Update checks/downloads are user initiated. Validate HTTPS GitHub host, size, SHA-256, package, versionCode and original signer before Android installation.
- Publish only clean source and APK; never include user data or signing files in repository content or release assets.
- Owner explicitly authorized the signing identity to be stored in encrypted GitHub Actions Secrets CHAINS_KEYSTORE_BASE64 and CHAINS_STORE_PASSWORD. Never print these values, publish them or replace the original key.
- Original signing certificate SHA-256: 1a1762340fe4846b2867ad78264a1344501181f63f9de5525d7c0fa00a97f191.
- Source of truth: rnghx/chains-android. Run .github/workflows/release.yml with publish=true after preparing changes. Wait for completion and verify unauthenticated public manifest/APK hash.
- Owner requests no persistent local project, APKs, archives or signing copy on the PC. Use repository editing and cloud builds; remove temporary local files if any were necessary. Deliver repository/download links, not local folders.
- Cloud build must pass habit checks, Android framework/database/UI/update tests, release build, lint and APK signature checks. Report device testing honestly.

- When the cloud environment reports starting, use wait_for_environment and continue the task after readiness; do not stop merely because startup is pending.

- Keep README, repository metadata, and release descriptions minimal; do not add product explanations.
