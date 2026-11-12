# FARO V5 — Moto G15 corrected isolated X06 first launch — physical visual PASS

Fecha: 2026-11-12 AR, screenshot visible clock **15:10**. Unidad anterior certificada: `S3_G15_X06_ISOLATED_BOOT_IDENTITY_FIX`; B7 scoped prior `docs/s3-night/S3_G15_X06_ISOLATED_BOOT_IDENTITY_FIX_B7_PASS_SCOPED_20261112.md`.

## Physical evidence and exact scope

**FIRST_LAUNCH_VISUAL: PASS (human screenshot).** The user installed **the corrected** isolated developer APK following SHA256 verification and local signing, and shared an actual Moto G15 screenshot showing its activity fully rendered. This is not emulator or CI evidence; the screenshot remains in chat and is not committed to the public repository for privacy.

Verified sequence received in conversation:
- `gh run download 43272636986 -n faro-g15-isolated-x06-apk` completed.
- `sha256sum -c faro-g15-isolated-x06-sha256.txt` gave `faro-g15-isolated-x06.apk: OK`; SHA256 `7c94d14ce19bc5332e87b85b68899df7379f7032e75642b1240f94afe594e67c`.
- Local `apksigner verify --print-certs` cert SHA256 `7d01a911ee1a89577faed72475fa7a54b5099dc37bb0a6d911bceebe125729fa`, matches private previous isolated install. Private keystore and password never included in repo.
- `adb -s "$SERIAL" install -r "$SIGNED"` result `Performing Incremental Install ... Success ... Install command complete in 1962 ms`. No uninstall or clearing data.
- `adb -s "$SERIAL" logcat -c` and `shell am force-stop ar.com.faro.g15isolated.v2coredev` completed without user-reported errors; next human photo of visible application main screen confirms rendered UI.

Screenshot content: dark page titled **`FARO V2 — Núcleo local aislado`**, subtitle explaining local Workspace/Chat/Buscar without Groq/IA, Apply, notifications or Internet, line `Sin abrir. V2 habitual permanece apagado.`, six complete selectable labels: `ABRIR CHAT LOCAL DE DESARROLLO`; `ABRIR CHAT Y ACTIVIDAD LOCAL`; `CONSULTAR AVISOS REGISTRADOS (DEV)`; `CONSULTAR VENCIMIENTOS REGISTRADOS (DEV)`; `CONSULTAR GUARDADOS LOCALES (DEV)`; `CREAR REALITY8 VACÍA (DEV)`; warning Reality8 new empty DEV generation/no Reality7 copy and requires Chat local initialized.

**Important evidence limits:** This proves that on the corrected APK the first-launch Activity UI rendered at screenshot time, resolving the prior user-visible immediate crash for this attempt. It DOES NOT independently prove PID after 3s, absence of later AndroidRuntime errors, navigation, persisted state, search behavior, Guardados functionality, Reality8 creation, app switch stability, no network activity beyond audited manifest, or longitudinal crash-free behavior. Never call the **entire** physical campaign PASS. The photographed text `Sin abrir` is an app status phrase, not evidence that launch failed: UI is visibly rendered.

## Safety, follow-up physical campaign

Do not touch original package `ar.com.faro.alpha.v2coredev`; isolated ID `ar.com.faro.g15isolated.v2coredev` updates only itself. V2 productiva/S4/Auto/Noche remain OFF. Do not tap **CREAR REALITY8 VACÍA** without a separate deliberate accepted scenario. Next human-bounded exploratory step: tap **ABRIR CHAT LOCAL DE DESARROLLO**, report screenshot/visible state or crash. Then Chat+Actividad, Avisos, Vencimientos, Guardados, owner and recovery tests when independently planned; collect relevant AndroidRuntime only if failing. Existing B7 Android API35 and guard remains certified, no duplicate runs. `S3.A3` physical / `X03` live / `S3.A5` global still OPEN.
