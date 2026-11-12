# FARO V5 — Moto G15: descarga y firma local persistente de APK X06 aislada
Fecha: 2026-11-12 AR. Estado: PREPARADO, **NO EJECUTADO** en G15. Solo uso manual/consentimiento humano. B7 de entrega: `docs/s3-night/S3_G15_X06_ISOLATED_APK_DELIVERY_B7_PASS_SCOPED_20261112.md`.

## Identidades: nunca confundir
Instalada original: `ar.com.faro.alpha.v2coredev`, certificado `c58d223a1f16237341b930c2b1ccd6eb1f432ecd64586b820c673c91ec11e093`.
Nueva aislada: `ar.com.faro.g15isolated.v2coredev`, **otra aplicación**, sin importar que ambas muestren label `FARO V2 CORE DEV`. No mover bases a mano, no quitar original, no `pm clear`.
CI final `43250634025` SUCCESS, source `0e47ce3651b1c4bb5eed55172e59bc0d18c61d87`, artifact `13959292406`, ZIP sha256 `c7f94f17e2a38e8978931ca9b8f511614644f72b4da3c94fab0c5511a0bf0cfe`, APK original sha256 `2a93a89502eaf074ee4320ee74e84eb764f6266b162e5eba60013615ff1a4b76`. Sólo permiso self AndroidX signature, sin INTERNET ni permisos Android. Sin androidTest. Físicas NOT_RUN.

## Paso 1 — descarga en Termux, READ_ONLY
Precondición `gh` CLI autenticado con permisos de lectura. Si la herramienta falta, STOP y resolver instalación por separado; no usar enlaces sandbox rotos.
```bash
mkdir -p "$HOME/faro-g15-isolated"
gh run download 43250634025 -R etacsantafe-source/FARO-V5 -n faro-g15-isolated-x06-apk -D "$HOME/faro-g15-isolated"
cd "$HOME/faro-g15-isolated"
ls -lh
sha256sum -c faro-g15-isolated-x06-sha256.txt
apksigner verify --print-certs faro-g15-isolated-x06.apk
```
Criterio: hash APK PASS con valor exacto anterior. Si un ZIP se descarga por Chrome, ubicarlo y descomprimir a carpeta privada antes de verificar. No confiar en nombre del archivo.

## Paso 2 — antes de la PRIMERA instalación, firma estable propiedad humana
CI GitHub Actions genera certificado debug efímero; en builds nuevos la firma puede cambiar. Para permitir futuros upgrades del nuevo package sin resetear sus datos, re-firmar la COPIA local con clave propia y conservar esa misma clave para todas las futuras versiones de la variante. Refirmar **antes de instalar la primera vez**. Fuente: https://developer.android.com/tools/apksigner (apksigner puede firmar y verificar APK, --out preserva entrada).
En Termux:
```bash
command -v keytool
command -v apksigner
mkdir -p "$HOME/.faro-private-keys"
chmod 700 "$HOME/.faro-private-keys"
keytool -genkeypair -storetype PKCS12 -keystore "$HOME/.faro-private-keys/g15-isolated.p12" -alias g15-isolated -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=FARO G15 LOCAL, O=PRIVATE DEV, C=AR"
chmod 600 "$HOME/.faro-private-keys/g15-isolated.p12"
apksigner sign --ks "$HOME/.faro-private-keys/g15-isolated.p12" --ks-key-alias g15-isolated --out "$HOME/faro-g15-isolated/g15-isolated-signed.apk" "$HOME/faro-g15-isolated/faro-g15-isolated-x06.apk"
apksigner verify --print-certs "$HOME/faro-g15-isolated/g15-isolated-signed.apk"
sha256sum "$HOME/faro-g15-isolated/g15-isolated-signed.apk"
```
Los comandos piden contraseñas de manera interactiva; NUNCA mostrar contraseña en chat, stdout, scripts, GitHub o argumentos de shell. No generar otra clave si `g15-isolated.p12` ya existe: comprobar antes si existe con `test -e` y conservarla; `keytool` podría sobreescribir/inutilizar futuras actualizaciones. Hacer respaldo CIFRADO de clave y contraseña en medio bajo control humano, porque Termux puede perder datos al desinstalarse. El hash del APK **re-firmado** será diferente al hash del artefacto CI, como es correcto. Comparar certificado del APK firmado ahora y en futuras entregas.

## Paso 3 — preflight de instalación (NO ejecutado / no autoriza instalar)
```bash
adb get-state
adb shell pm path ar.com.faro.alpha.v2coredev
adb shell pm path ar.com.faro.g15isolated.v2coredev
```
Esperado: primer paquete EXISTE y segundo NO EXISTE; si segundo ya existe, STOP y obtener su certificado para comparar antes de actualizar. Mantener original y todos sus datos. **No ejecutar** `adb install` hasta que el usuario elija hacerlo explícitamente con paquete y certificado verificados. No copiar APK androidTest ni ejecutar `am instrument`. El launcher de la nueva app se abre por componente completo para diferenciar dos apps con la misma etiqueta.
