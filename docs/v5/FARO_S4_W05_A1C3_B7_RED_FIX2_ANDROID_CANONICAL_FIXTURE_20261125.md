# FARO V5 · W05-A1C3 Android stock DEV

## B7 — RED_FIX for API35 fixture staging

- Code `cc13d34395a0f54e1bdb5220580b66272fa0187a`, guard `45730137574` SUCCESS. CI run `45730137551`, job `139609806072`, reached API35. Original ZIP `15076977054`, SHA-256 `56db58638289a0a7bc811526a5fdecc0d1b7b86b81481d77d2668493db065563`. Original XML independently inspected: JVM 3/3, Android 1/5, four failures, zero errors or skipped. Only the Android no-completed-generation case passed.
- Four tests failed before opening the panel: `SQLITE_CANTOPEN` error 14 in `RealityFinance9StagedFilePort.migrateTrustedStagedEight`. The instrumented fixture used the noncanonical Android noBackup directory under an aliased ancestor. W04 already demonstrated the same stack and corrected it by staging under the canonical private root without relaxing SQLite NOFOLLOW.
- Correct only the Android test's fixture root within the existing permit. Resolve the private noBackup parent with `toRealPath()` before deriving the fixed child. Preserve the emulator/package and symlink checks before reset. Re-run the same eight cases. The runtime panel and all generation/owner safeguards remain as in the prior code.
- No B7 PASS or A1C4 acceptance from this run. Physical G15 NOT_RUN.
