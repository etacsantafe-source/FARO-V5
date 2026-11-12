# FARO V5 — G15 X06 isolated APK permission declaration check B1–B5 delta y B7 RED_FIX
Fecha 2026-11-12 AR. Unidad S3_G15_X06_ISOLATED_APK_DELIVERY; mismo scope de workflow, sin tocar runtime.

## B1
CI 43248629257 SHA a75c019f8131f6573da0e910e6fc9a8dd53b897a SUCCESS y guard 43248629272 SUCCESS. Job 131288087198: Gradle BUILD SUCCESSFUL, paquete ar.com.faro.g15isolated.v2coredev, verificaciones aapt/apksigner completadas, APK SHA256 2dc44c5dff89d8f989f3e5ba772a8c402b9266ac736b1cad5ac4afe63cdc2145, artefacto 13958922682 ZIP sha256 eee6efa73c3cda0713791d98ea733478dc5fff5eecab745adc32fd5698bf5dac. La prueba de paquete/manifest no reemplaza las físicas, NOT_RUN.
Al auditar el código Python emitido dentro del workflow se encuentra la expresión raw de declaraciones de permisos con DOS barras antes de S: r"^permission: (\\S+)". Python la interpreta como barra invertida literal más S; no coincide con la línea típica de AAPT 'permission: <nombre>'. La prueba de uses-permission sí verificó permiso propio AndroidX y prohibió android.permission.*, pero el chequeo adicional de declaraciones es inoperante.

## B2
Reutilizar los tests de X06 y todas las comprobaciones buenas de 43248629257. V4.4 no participa en el manifesto ni se toca. Reaprovechar mismo workflow y mismo applicationId independiente; no tocar AndroidManifest, Kotlin, Gradle, versiones, firma ni las suites Android previamente certificadas.

## B3
Usar regex raw válida de Python r"^permission: (\S+)" — una sola barra antes de S —, con re.M y conjunto acotado a permiso propio ar.com.faro.g15isolated.v2coredev.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. No ampliar la aceptación de permisos; rechazar cualquier permiso de sistema o ajeno. Sin nuevas librerías. DESCARTAR suprimir el chequeo o relajar el manifest.

## B4
Única ruta funcional: .github/workflows/faro-v5-g15-isolated-x06-apk-directed.yml. Cambiar SOLAMENTE la expresión regular errónea. Mantener asserts de request_count, no android.permission., package, version, launcher, backup=false, AndroidX permiso propio, firma y sha. Compilar app, no APK androidTest; job GitHub dirigido sobre SHA exacto, guard, artifact.

## B5
Regla fail-closed: cualquier permission: distinta del permiso signature propio => STOP. Ninguna lectura de datos del G15 ni instalación; el appId previo ar.com.faro.alpha.v2coredev sigue intacto. Comparación de certificado original previo vs nuevo no aplica para coexistencia de diferentes appId. Clave de firmado estable de usuario requerida para posteriores upgrades de la variante nueva, conservarla solo localmente.

## B7
Resultado de auditoría estática adversarial del código a75c019f: RED_FIX mismo scope. No afirma que CI 43248629257 haya fallado; esta CI es SUCCESS, pero la cobertura del subchequeo declarativo es incompleta. Exige corrección mínima bajo nuevo permiso mismo unit, reejecutar solamente CI de entrega/manifest, sin repetir X06 1/1 ni X12 2/2 y sin certificar pruebas G15 por analogía.
