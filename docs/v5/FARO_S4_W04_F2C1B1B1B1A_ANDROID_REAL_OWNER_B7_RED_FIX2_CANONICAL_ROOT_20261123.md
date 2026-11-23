# FARO V5 W04 F2C1B1B1B1A — B7 RED_FIX2: Android NOFOLLOW/CANTOPEN pre-owner

2026-11-23. Unit `S4_W04_F2C1B1B1B1A_ANDROID_REAL_ACTIVE_OWNER_SELECTED_DEV`. Reviewed functional commit `3fc08fc87fb1747d1eca07dc52d630133000c7ac`. **CI RED: B7 NOT PASS**. Exact three-path READY remains in force, but RED_FIX modifies ONLY the existing AndroidTest fixture route.

## B7 — Evidence actually observed
- Source code `3fc08fc87fb1747d1eca07dc52d630133000c7ac`, mandatory guard `45214695549` SUCCESS.
- API35 Android CI `45214695516` job `137832560669`: **COMPLETED FAILURE**, Android build and AndroidTest Kotlin compilation passed; 3 methods instrumented, **1 PASS (t02 only), 2 FAIL** t01/t03. Original artifact `14839425750`, ZIP digest `sha256:1f11eb6fd14d0eef9c226a449ed11e4910b4d993b3b50169afe99c6192dcfbf6` (uploaded by CI).
- t01/t03 fail at `S4W04CommercialOwnerBackedPositiveAndroidTest.kt:75`, inside realFixture before any positive owner session. Stack: `android.database.SQLException: Error code: 14` → `androidx.sqlite.driver.bundled.BundledSQLiteDriverKt.nativeOpen` → `RealityFinance9StagedFilePort.migrateTrustedStagedEight` → `FaroV5Finance9StagedMigration.migrate`. Source `RealityFinance9StagedFilePort` uses `SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW`. This is SQLite CANTOPEN, not a failing owner grant, test assertion, or D4/E rendering.
- `realFixture` created its root under `activity.application.filesDir` without canonicalizing its parent. On Android, the reported app-private directory path may include intermediate symlinks. `NOFOLLOW` is a valid security protection and **must not be removed from production**. That Android symlink ancestry is the *leading hypothesis*, not yet verified.

## RED_FIX bounded, single existing READY route
Only `v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialOwnerBackedPositiveAndroidTest.kt`: resolve `activity.application.filesDir.canonicalFile`, create fixture root directly within it, then use `root.canonicalFile` and `root.toPath().toRealPath()` for the same root passed to ALL staging, owner, issuer and lock APIs; check canonical directory remains strictly inside app private filesDir, regular/non-symlink, and `toRealPath()==path`. Do so for both real and unmounted negative fixture roots. No new store, no replacement SQL driver, no toggled flags, no user data, no changes to production Kotlin. Re-run existing Android API35 three tests, check exact XML + offline APK original hashes.
- If this fails with SQLite CANTOPEN again, collect the new real error and STOP; do NOT turn off SQLITE_OPEN_NOFOLLOW, guess Android permissions, or broaden permit without a separate new B1–B5.
- Background primary rationale: SQLite C API flags https://www.sqlite.org/c3ref/open.html (SQLITE_OPEN_NOFOLLOW) and Android app-private storage https://developer.android.com/training/data-storage/app-specific. Fail-closed remains required, SQLite files aren't copied to untrusted storage.

## Closing criteria
3/3 real API35 XML success, guard SUCCESS and independent B7 audit; no previous B0/B1A/JVM tests rerun. Next F2C1B1B1B1B is separate D4/E selected-observation historical UI, then F3. V2 productive OFF, main/V4.4 untouched, G15 physical OFF.
