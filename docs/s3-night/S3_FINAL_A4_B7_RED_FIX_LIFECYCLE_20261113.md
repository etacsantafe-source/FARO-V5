# A4 — B7 RED_FIX2: observe native Lifecycle directly
Unit S3_FINAL_A4_OPENING_POINTER; corrected source0a69df9b8bc9086f2964f07de8b053f8a23148db.
## B1
Android43536484053 job132266643927 compiled app/test and passed exact offline binary checks. New test ran, real three-source positive read (3 distinct canonical facts), partial/global distinction and pause denial passed before failure at line82 asserting cached resumed AtomicBoolean after moveToState(RESUMED). No full case PASS; ABA assertions not yet reached. Test observes Lifecycle.currentState inside LifecycleEventObserver callback and caches it; this is a timing-sensitive proxy, not the production predicate. Runtime source directly reads currentState.
## B2
Preserve app two opening-pointer predicates and all positive/read/ABA assertions. Reuse actual ActivityScenario and real Application. Remove the test-only observer/cache; capture actual Lifecycle object and read currentState as the native host does.
## B3
https://developer.android.com/guide/components/activities/testing and https://developer.android.com/reference/androidx/test/core/app/ActivityScenario : moveToState plus onActivity observes UI-thread lifecycle state. Choose direct actual lifecycle predicate and onActivity assertion after each transition. Reject setting a boolean true without OS observation, adding arbitrary sleeps, increasing timeout or skipping resume/ABA. No new dependency/cost/license.
## B4
One path only: v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3FinalA4OpeningPointerTest.kt. Capture Lifecycle from actual resumed Activity, use lambda currentState.isAtLeast(RESUMED), onActivity verifies exact STARTED/RESUMED after transitions. Remove unused observer/AtomicBoolean imports. No production, fixture data, source assertions or workflow change.
## B5
No simulated foreground authority: predicate comes from native Lifecycle, and transitions must be independently asserted on UI thread. Old reader must still deny after resume, and both NEW grant attempts must deny after actual durable ABA. Do not label earlier partial execution as a successful named JUnit case. Only this affected new test reruns.
## B7
RED_FIX same unit. New single-path READY and preflight required; full B7 stays pending.
