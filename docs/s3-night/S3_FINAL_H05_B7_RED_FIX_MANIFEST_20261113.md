# H05 — B7 static RED_FIX: exact offline manifest
Unit S3_FINAL_H05_NATIVE_INSETS; reviewed code 0cfaca114139c273ba94b3e9535912932e7a7a41. Android 43532156707 is running; no Android result claimed. Protocol43532156515 SUCCESS.
## B1
Independent source/previous binary audit caught wrong new workflow expectation: v2CoreDev manifest explicitly removes its AndroidX DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. Historical M5 binary has NO permissions; new workflow expected one. X03 expectation includes INTERNET and its dynamic permission, remains correct. No application behavior defect established.
## B2
Reuse overlay and M5 authoritative binary contract; no change to manifest, helper or test. v2CoreDev/AndroidManifest.xml line removing permission is decisive. Do not add a permission to make the assertion pass.
## B3
Same primary manifest merge docs as original preaudit, https://developer.android.com/build/manage-manifests . The merge removes a declared permission; library defaults cannot override explicit remove in higher-priority overlay. Compare allowing arbitrary permissions (reject) vs exact empty set (choose). No dependency/license/cost change.
## B4
Only .github/workflows/faro-v5-s3-final-h05-insets-directed.yml: expected permissions = set() for offline CORE DEV. Preserve strict exact set check for both variants, package/component assertions and actual named tests. Existing live CI may fail at this assertion; do not report Android/font200 PASS until corrected run completes.
## B5
An assertion relaxation to any subset would hide unwanted permission: reject. Empty set matches preexisting protected invariant, no V2 production, data, signing, owner or test changed. Targeted rerun by new workflow push only; other regression jobs from Activity changes belong to old SHA. No human acceptance.
## B7
RED_FIX same unit, no final certification. Need separate READY with this proof and previous B7 RED_FIX before next functional commit.
