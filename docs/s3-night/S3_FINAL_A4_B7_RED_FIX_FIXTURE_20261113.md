# A4 — B7 RED_FIX: named fixture timestamps
Unit S3_FINAL_A4_OPENING_POINTER. Code6c799f6026395e9461e5aed72e7fe39b097f7914; guard43535432563 SUCCESS.
## B1
Android43535432782 job132263160062 FAILED at compileV2CoreDevAndroidTestKotlin, new test line47 NO_VALUE_FOR_PARAMETER updatedAtEpochMillis. Application compiled; no new Android case ran. No A4 PASS. Test used positional constructors with optional nullable link/ack/payment fields before required timestamp; timestamp argument filled the optional slot instead. Exact constructors read in ReminderModel.kt and MonthlyPlanModel.kt.
## B2
Reuse existing B1-B5, authority two-predicate delta and all runtime untouched. Existing source fixtures use named updatedAtEpochMillis; preserve canonical records and positive three-source assertions.
## B3
Kotlin primary docs https://kotlinlang.org/docs/functions.html#named-arguments (existing language feature): optional parameters before required fields require named arguments to skip safely. ELEGIR explicit updatedAtEpochMillis for rule/occurrence/monthly item; DESCARTAR adding dummy link/ack/payment values (changes semantics), weakening constructor or test coverage. No dependency/cost/license change.
## B4
Only v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3FinalA4OpeningPointerTest.kt, name the three timestamp arguments. No SQL/schema/owner/policy/callback/workflow/app changes. Rebuild affected instrumentation and rerun this new case only through directed push.
## B5
Keep optional link/ack/payment null, statuses ONCE/PENDING/PLANNED and all currentness/lifecycle/ABA assertions. No fake successful XML, no physical/provider operation. No new unit until actual test succeeds and B7 verified.
## B7
RED_FIX same unit; no runtime behavior regression established from a test constructor error. New one-path READY/preflight required before correction.
