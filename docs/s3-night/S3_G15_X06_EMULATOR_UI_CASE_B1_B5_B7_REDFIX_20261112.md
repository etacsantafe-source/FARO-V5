# FARO V5 — G15 isolated Android UI smoke exact-case RED_FIX, B1–B5 and B7
Date 2026-11-12 Argentina. SAME unit `S3_G15_X06_ISOLATED_BOOT_IDENTITY_FIX`. Scope: one CI workflow file, **no runtime source changes**.

## B1 — Source evidence and precise failure
CI `43270673659` exact code SHA `8d24bbde86d5f755ae8c104745924cf32e6a8498`, guard `43270673641` SUCCESS. The emulator installed APK, `adb shell am start -W` returned Status ok, and PID remained **2206** for all ten bounded attempts. Artifact `13969176166` `faro-g15-isolated-x06-redfix-ui-diagnostics` was inspected from its immutable ZIP: first-launch XML `faro-g15-isolated-window.xml` has package `ar.com.faro.g15isolated.v2coredev`, TextView `FARO V2 — Núcleo local aislado`, and `android.widget.Button text="ABRIR CHAT LOCAL DE DESARROLLO"` (capitalized by Android's Button transformation); screenshot `faro-g15-isolated-emulator.png` confirms actually visible UI and buttons; `dumpsys activity` shows topResumedActivity correct and visible. NO AndroidRuntime fatal error. Prior workflow grep was `grep -Fq 'Abrir Chat local de desarrollo'`, **case-sensitive mismatch**. Therefore CI test RED, application boot UI is independently PASS on emulator. Physical G15 still running prior isolated APK and remains FAIL pending updated artifact.
Artifacts are synthetic blank emulator, not user phone. Only supported user data from phone: both original and new isolated apps independently installed, previous isolated app closes.

## B2 — Reuse/comparison
Reuse Activity exact literal allowlist two DEV IDs `ar.com.faro.alpha.v2coredev`, `ar.com.faro.g15isolated.v2coredev`, 7 gated Authority predicates, and unchanged debug-only / V2 productive off / pointer and foreground. V4.4 is irrelevant to CI text matching and not imported. No rerun or retrofit of X06 four-owner tests, no new data or Android permissions. Reuse existing API35 first launch PID+UI test from the same workflow; only correct text matching.

## B3 — Alternatives
A **USAR** update exact grep string to `ABRIR CHAT LOCAL DE DESARROLLO` which is actual XML bytes returned by Android 35; fail-closed process+UI. B DESCARTAR `grep -i` to be permissive, even though semantically equivalent here; exact match is stronger. C DESCARTAR bypass UI check or assert only am start Status ok; that could conceal post-start crashes. Android Button text transformation as observed is primary evidence. No web dependency required.

## B4 — Bounded change
Only `.github/workflows/faro-v5-g15-isolated-x06-apk-directed.yml`: replace unique `grep -Fq 'Abrir Chat local de desarrollo'` with `grep -Fq 'ABRIR CHAT LOCAL DE DESARROLLO'` in emulator Bash script. Keep PID condition, limited loop, XML, activity/screenshot diagnostics, fail artifact, APK packaging/manifest/signature and SHA. Run guard and exact SHA Android action; ensure `G15_ISOLATED_FIRST_LAUNCH_UI_PASS` from logs, artifact with APK digest and UI verification, B7 audit prior to human installation.

## B5 — Adversarial review
Positive XML must contain correct package's button, not just text anywhere: XML produced from fresh emulator with the expected package, PID for expected package and top resumed Activity on case-evidence run. Workflow already checks those conditions. Negative: DO NOT match case-insensitive partial 'Chat', disable PID, loosen permissions/allowlist, install Android test bundle, grant INTERNET, unpin Reality generation or root. Do not clear/replace the user's original app. For upgrades to **new isolated package only**, signing MUST reuse persistent local private PKCS12 cert SHA256 `7d01a911ee1a89577faed72475fa7a54b5099dc37bb0a6d911bceebe125729fa`, no keys or passwords on GitHub.

## B7 — Previous commit RED_FIX
`8d24bbde86d5f755ae8c104745924cf32e6a8498` RED_FIX solely CI smoke text exact-case mismatch, not application execution; true first launch on emulator PASS evidenced by PID, XML, screenshot, dumpsys, but acceptance of workflow still RED. Correct only 1 file under this same unit, rerun UI smoke and close B7 once verified. G15 physical must be retested with newer re-signed APK by user.
