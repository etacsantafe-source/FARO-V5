# X08 — canonical read interruption and ABA, queued B1–B5
2026-11-12. Unit S3_A5_7_X08_REAL_OWNER_INTERRUPTION_ABA_ANDROID. Reviewed base a9c6abe4368e85e2863642675faee88bf9f2bac7, X02 correction code 5cdeae4cb1d6947f0e222487430b28a53bba1e98. Queued only: X02 and X06 must close scoped B7 before this unit receives READY.

## B1 — exact remaining gap
Certified canonical Workspace truncation API35 1/1 (42633273852), pause/recreate revocation 1/1 (42717372040) and existing JVM revoked-Finance test are retained and must not be requested again. Missing: deterministic revocation after actual canonical owner snapshot acquisition but before its post-read authority check; real owner close at that boundary; full durable pointer A-B-A on genuine Reality hits.
Inspected FaroV5FinanceSearchReadSource.readAuthorized: before authorityStamp() reads snapshot revision, next finance.read() returns actual snapshot, then authorityStamp() must match before. FinanceSnapshotReadApi is already an injectable read-only interface. No new production hook needed. Existing adapter takes genuine typed sources and computes coverageBySource, filters denied hits, revalidates openAuthorized and one-shot target. FileActiveGenerationPointer.compareAndSet publishes increasing sequences and listeners shared by root; V2DEV Session pins full openingPointer, not generation ID alone.
Existing real seed pattern: FaroV5G5ActiveFourOwnerSearchTest and FaroV5FederatedSearchSurfaceAdapterTest. Do not re-run either historic suite.

## B2 — reuse, V4.4
V4.4 UX is reference only; it cannot supply V5 ACL stamps. Reuse canonical owner transactions, existing verified DEV grant class, finance SnapshotReadApi, existing federator/screen and real pointer record. Tests use fresh private temporary root; close every store and remove fixture. No fake Search page/hit, second durable owner or unbounded index.

## B3 — external research and alternatives
Read official primary sources 2026-11-12:
- https://developer.android.com/develop/ui/compose/testing/synchronization : observable UI synchronization for async work; no sleeps.
- https://developer.android.com/guide/components/activities/testing : ActivityScenario handles pause/recreation, AndroidX owns lifecycle.
- https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/ : equality conflation means Unit-valued StateFlow cannot represent repeated invalidations. Keep existing monotonic version and real current-authority checks; signals are hints, not grants.
- https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/callback-flow.html : subscriptions need deterministic unregister cleanup. Existing host lifecycle cleanup retained.
- Primary implementation reference https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/StateFlow.kt and design issue https://github.com/Kotlin/kotlinx.coroutines/issues/1973 : maintained Kotlin project, Apache-2.0, reference only, no vendored code.
ADAPT A: test-only wrapper delegates every read to real Finance owner, deterministically invokes interruption after the second real snapshot read during direct readAuthorized, checks boundary count and denial. The first read is before-stamp; second is canonical acquisition. No forged snapshots. Then actual federator + SearchScreen test partial coverage and rejected old genuine locator.
REJECT B: production fault hook (unnecessary scope and runtime complexity).
REJECT C: random timing/sleeps, fabricated exceptions/pages/hits, or replacing whole owner with fake (does not certify actual source).
REFERENCE D: JVM-only source tests useful for core but insufficient for the queued Android semantic scenario. Use existing dependencies, no new licence/import, no paid API or device.

## B4 — bounded implementation and acceptance
Exactly two candidate paths:
1. v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X08OwnerInterruptionUiTest.kt
2. .github/workflows/faro-v5-s3-a5-7-x08-owner-interruption-android-directed.yml
Three new tests:
- x08FinanceGrantRevokedAfterCanonicalReadYieldsPartial: real seeded Finance + Objectives + Matters + Workspace, genuine issued Finance hit before interruption; direct source read wrapper closes only Finance grant after second real snapshot; source denies output, old opener unavailable, actual federator retains other readable owners and UI says PARTIAL. Query matching denied Finance only returns partial empty, never definitive no-results.
- x08OwnerClosedAfterSnapshotCannotPublishOrNavigate: canonical Reality snapshot followed by actual RealityStore.close at the same boundary; post-read rejects, all three Reality sources unavailable while Workspace stays readable -> PARTIAL; no retained Reality hit/detail, no fake complete empty.
- x08DurablePointerABARejectsRealityHitWithoutRegrant: actual DEV Session and real Reality hit; FileActiveGenerationPointer publishes A->B->A in test fixture only, sequence increases; old port denies search/open/target despite same generation ID. This is injected test pointer drift, not a production generation switch. No existing Workspace ABA suite repeated.
For first two, render existing SearchScreen against actual canonical adapter in isolated test host and label evidence as source/adapter + Android semantics, not native full-host integration. No production code edit allowed. New workflow only class, API35 offline, exact 3/0/0/0 events/XML, two APK identities/no INTERNET/signatures/hashes/source SHA, guard. Fixtures use official owner ports and real dev grant class after verified root open; cleanup on failure. If wrapper cannot achieve boundary without production route, NEEDS_PREP before code.

## B5 — adversarial decision
No data/model/Apply/OS delivery. Test wrapper may interrupt but cannot fabricate snapshot or grant persistence. Capture hit BEFORE revocation and assert old locator + one-shot target denied AFTER. Full pointer sequence compared, independent pointer instance, no ABA recovery by string equality. Partial empty cannot be COMPLETE or UNAVAILABLE while Workspace is healthy; no text leakage from denied owner. Owner-down must actually close SQLite, not merely set a boolean. Bound reads and assertions, no flaky sleeps. No changes to main/S2, prior tests, manifests, production flags, Internet, catalogue, phone, Auto/Noche or paid APIs.
DICTAMEN PREAUDITED_QUEUED_TWO_TEST_PATHS_THREE_ANDROID_TESTS. No READY until X06 B7 PASS and exact HEAD revalidation/document guard. B7 will be scoped to these three interruption scenarios, not global X08/A5 closure.
