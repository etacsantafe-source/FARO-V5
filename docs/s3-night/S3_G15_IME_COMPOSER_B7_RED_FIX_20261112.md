# IME composer — B7 RED_FIX causal, 2026-11-12

Unit: `S3_G15_IME_COMPOSER_RESIZE_ANDROID`.
Source: `b39dc586e1e6a438b5cb3b4473b2a5bafae9c5e3`.
Guard: `43297920817` SUCCESS (seven-block-evidence).
Android API35: `43297918999`, attempt 2, job `131462560032`, FAILURE.

## B7
Compilation, signatures, isolated application/test IDs, binary adjustResize and no INTERNET checks passed. Actual OS IME was visible, height 833 pixels, top Y=1507. Both named tests failed on composer bounds `(0,1966)-(1080,2120)` below the keyboard. This is a valid negative reproduction, NOT Android PASS. No successful XML was manufactured. Artifact `13981635949`, ZIP SHA256 `f5814d10d080c595032f9c6c1bbe3610055a08944ae6e8c83b2e2d7e04bd35c0` contains the failed-run APK/logs, not a certified corrected deliverable.

Attempt 1 failed before compilation resolving Kotlin 2.4.10 from Maven/Plugin Portal. A direct read of all three missing POMs subsequently returned HTTP200 with actual Maven XML, justifying one same-SHA infrastructure retry. No dependency/version change was made. Attempt 2 proved the actual UI failure above.

## Causal B2/B3/B5 delta
The original B1-B5 already explicitly prepares alternative B, Compose IME insets, if manifest resize alone fails. This result falsifies alternative A on API35. Android setup-e2e and insets-ui references in the original audit explain this Android15 edge-to-edge behavior. Inspection of `FaroV5ShellNavigation.kt` shows Scaffold's `innerPadding` applied to NavDisplay, including navigation bars, but without consumption propagated to the nested Chat. Add `consumeWindowInsets(WindowInsets.navigationBars).imePadding()` only to the active Chat Column: consume the already-applied navigation inset, then add only the outstanding keyboard inset. This changes layout only, no owner/SQLite/network/grant/Send semantics.

Retain both strict real-IME tests and their geometry threshold. Wait for Compose to settle before bounds capture and preserve measurement/screenshot BEFORE assertions so any remaining failure is diagnosable. No coordinate-based send, relaxed bounds, duplicate padding, physical device action, new route or remote provider. Exact original four-path permission remains; only Surface + test need delta. A5 READY remains prohibited until IME B7 PASS.

Disposition: RED_FIX same unit; B6 causal correction pending; no physical fix claimed; S3/A5 OPEN.
