# A5 offline — B7 PASS scoped, single-root Android API35
Date 2026-11-12 AR. Unit `S3_A5_OFFLINE_REVIEW_APPLY_ACTIVITY_SINGLE_ROOT`.
Source `25c5ff41c96476a5b98b64047b96e2bd345f043a`.
Android `43305744264` SUCCESS; protocol guard `43305743811` SUCCESS.

## B7
Four NEW directed Android tests passed with exactly one start(1)/successful end(0) per named test, JUnit4/0/0/0, no skips:
- offlineExplicitReviewApplyAndAuthorizedActivityShareCanonicalReceipt: real Workspace PreparedPlan fixture ONLY in test APK; explicit Review approval leaves Reality revision0 and no receipt; explicit Apply produces COMMITTED; receipt/fingerprint/transaction identity equals canonical Reality lookup; a separate foreground history opening renders the receipt of the SAME staged root with canonical recorded commit time. No display name/transaction ID rendered as UI content.
- offlineDoubleClickAndReopenNeverDuplicateActivityReceipt: double click admits one Apply, one Reality revision and one receipt; repeated reopen retains exact receipt and denies a second Apply; Activity reads that same single receipt.
- offlineActivityGrantRevokedOnPauseAndReturnRequiresNewGesture: old reader immediately denies after pause, no automatic grant on return or plain Chat reopening, unavailable UI without phantom zero, fresh separate gesture gives canonical receipt; old token remains denied.
- offlineMissingGrantAndGenerationIdentityNeverBecomePhantomZero: no grant yields unavailable; explicitly authorized genuine empty history is distinguished; corrupted real stage descriptor denies read, redacts Activity, and restoring the original bytes does NOT resurrect consumed authority.

The fixture creates a canonical Profile declared-name PreparedPlan through the Workspace owner. It never inserts receipts/Activity rows, simulates a model response, or copies data between roots/UIDs. Workspace/Reality are real SQLite stores of existing host root `faro-s3-x03-u02-isolated-debug-only`. The bridge pins real directory/database file identities and verifies the actual immutable staged descriptor + live quarantined host on reads. A technical DEBUG session grant is not human authentication and is not a fabricated ActiveGenerationPointer. `FaroV5RealHistoryActivityHostBinding.kt` and its productive/null policy gate were NOT changed.

The offline opening never constructs a provider, reads/creates a credential or requires a Free claim; no assistant dispatch/settings port is mounted; model gateway has DENY egress and cannot return a reply. Test emulator Wi-Fi/data disabled. Historical debug launcher exported=true/singleTask and debug INTERNET permission are unchanged in the baseline-identical debug manifest, not new permissions or a network-free-debug-APK claim. Launcher intents only reach admission; read grants require foreground buttons. CORE DEV binary still lacks INTERNET and excludes this debug Activity. No APK installed on G15; no handset/S2/user data accessed. Tests compare protected preexisting S2 root before/after and clean only their disposable fixture.

## Independent artifact verification
Downloaded artifact13983833247 and recomputed ZIP SHA256 `4a8c49863a084b3112713e0f56c9d2c95dae3008aef1c3d26f1f64e4e19b003d`. Parsed actual XML and raw instrumentation named events, checked exact source SHA, recomputed all three APK digests, independently decoded binary AndroidManifest.xml of application/test/CORE DEV. Debug package ar.com.faro.alpha.s2isolated.v5dev; test instrumentation targets exactly that ID. CORE DEV package ar.com.faro.alpha.v2coredev; no debug host/INTERNET. CI apksigner verified all three APKs. Machine record: `docs/s3-night/S3_A5_OFFLINE_ARTIFACT_VERIFICATION_20261112.json`.

App debug APK SHA256 `ef6deca97f18b86e7a7966861343dd3bdef8c93dea05610be8bab3b7d5f18e9f`; androidTest `079e78b78d641c303c4fda5f404c6fd4e2311ea63a8196bebaca5e5f23e7821c`; CORE DEV `b697a11c9b446d4220678228d63abafa6a8dbf2b26f1773ababb5a8b0fc8b407`.

Existing repository push triggers also ran X03-U02 43305743940, X09 43305743824, X10 43305743934 and Home43305743968: all SUCCESS. They were not manually dispatched/repeated or counted as new independent acceptances. No causal regression observed in these automatic checks. IME prior 2/2 retained without another IME campaign.

## Scope and disposition
B7 PASS_SCOPED_OFFLINE_DEBUG_PROFILE_PLAN. Original four functional files only, fifth permitted main binding untouched. Permit consumed/BLOCKED. Update acceptance matrix READ_ONLY: offline Review→approval→Apply→real receipt→authorized Activity now demonstrated in a single root. X03 real Groq/HTTP/Free/billing, production human authentication/active pointer, all contracts, physical G15 and global A5/S3 remain OPEN/NOT_RUN as applicable; no broader certification inferred. S3 formal0/5, development percentage not recalculated from these tests. V2 productive/S4/Auto/Noche OFF; main/V4.4/S2 untouched. Mission's two units complete within their explicit scopes; writer RELEASED after final checkpoint.
