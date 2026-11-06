# FARO V5 S3.A4 N031 FBS2 G4 codec — B7 RED_FIX (2026-11-06)

## B7 — Final adversarial review: RED_FIX for code f33dcc24be6c5b164d0a86239ca94263fd542540

- Exact CI PASS for unit-only codec: 42213197401, XML 5/5 new plus 7/7 legacy. Protocol 42213197241 SUCCESS, V2-core 42213196905 SUCCESS.
- Critical integration gap found by source review: FaroV5FiveStoreStagedRestore.restoreAuthenticated accepts a decoded FBS2 v2 with g4Domains != null, but only restores its legacy fourStore and Workspace portions, and does not re-verify G4. This might report StagedVerified for an incomplete restore.
- This is not a witnessed user-data incident. It is an unacceptable potential incomplete staged restore; B7 is RED_FIX, not PASS.
- Same unit S3_A4_N031_FBS2_G4_CODEC_V2 must add a fail-closed pre-write guard in the old staged restore: reject any g4Domains != null before pinning archive or mutating stores. Negative regression must prove unchanged owners, pointer, and no false StagedVerified.
- No production activation, FBS2 G4 capture/restore approval, N031 or A4 closure. Retain all old proofs. Fresh B1–B5 and READY required for the correction in its own limited paths.
