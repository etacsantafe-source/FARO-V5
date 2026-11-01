# FARO V5 — T4 · Campaña física única (preparación, NO ejecutar de noche)

**Estado:** PLANIFICADO, NO EJECUTADO. Este documento no autoriza instalar APK, cambiar firma ni habilitar V2. Debe usarse sólo cuando F1–F4 estén READY_FOR_T4 con SHA y gates automáticos exactos y el usuario autorice la campaña.

## Preflight reproducible y protección
1. Congelar el commit final de `work/faro-noche-v15-20261101` después de F4 PASS; comprobar que el árbol de código corresponde a las pruebas aprobadas. Registrar HEAD, XML, Actions ID y las cuatro F.
2. Construir **una sola** APK de pruebas Android reproducible desde ese SHA, sin credenciales embebidas ni firma privada de producción; paquete debug por defecto `ar.com.faro.alpha.v5dev` (`v5/app/build.gradle.kts`: `ar.com.faro.alpha` + sufijo `.v5dev`). Registrar SHA-256 del APK, versionCode/name, fingerprint del certificado con `apksigner verify --print-certs`, y `adb shell pm path`/SHA del instalado tras consentimiento. No asumir equivalencia de firmas.
3. Si `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, **detener**; no desinstalar ni borrar datos sin autorización humana y copia/plan de preservación. No tocar FARO V4.4 ni paquete de producción.
4. Dispositivo Moto G15, Android, acceso a red cuando corresponda. Preparar credencial Groq de prueba suministrada en dispositivo mediante Keystore, nunca en GitHub, logs, APK ni capturas. El usuario controla consentimientos, picker SAF y cualquier acción externa.
5. Garantizar que `V2_ACTIVE_ENABLED=false` en producto. Todas las activaciones/Apply/restores de T4 usan aislamiento instrumentado, no puntero activo V1 ni Reality de producción. Un error de aislamiento es ROJO inmediato.

## Secuencia única de pruebas físicas
| Paso | Acción humana/observación | VERDE requerido |
|---|---|---|
| T4-0 | Abrir APK debug y reiniciar FARO; revisar navegación V1 y estado de mantenimiento | V1 utilizable; V2 producción OFF; sin escrituras indebidas |
| T4-F1a | Abrir host Chat aislado instrumentado; Composer → Workspace → persistencia y reabrir | Mensajes sobreviven recreación; no duplicados |
| T4-F1b | Otorgar consentimiento específico y guardar credencial en Android Keystore; probar Groq real con texto inocuo y revocar | Solicitud/resultado observable sólo tras consentimiento; revocación bloquea nuevas llamadas; sin secreto en logs |
| T4-F1c | Review humano → aprobación → Apply atómico sobre Reality aislada → receipt → Delivery, reintentar y reiniciar | Sin Apply previo a aprobación, receipt durable, entrega observable una vez, idempotencia |
| T4-F2a | Instrumentar cinco SQLite en generación temporal; interrumpir tras publicar puntero aislado y recuperar | Recuperación WAL, Workspace/Reality/Evidence/Auxiliary consistentes; gate cerrado hasta validación |
| T4-F2b | Simular target incompleto, health fallido, rollback y caída durante rollback | V1 intacto, no doble replay, sin admisión de writes al target, rollback verificado |
| T4-F3a | Con consentimiento específico, elegir destino SAF `content://`, exportar FAROBAK2 cifrado y verificar archivo | Cinco owners autenticados, bytes/hash verificados, sin credenciales, destino elegido únicamente |
| T4-F3b | Probar cancelación, contraseña incorrecta, archivo alterado y proveedor mutable | Ningún journal/Apply no autorizado, resultado fallido explícito, V1 preservado |
| T4-F3c | Elegir archivo SAF con consentimiento nuevo; restaurar en cuarentena y reiniciar/reanudar journal | Cinco owners consistentes, journal y pinning correctos, sin switch a producción |
| T4-F4 | Encadenar Chat aislado → recuperación → SAF export/restore → reinicio y repetir operaciones | Sin pérdida, doble efecto ni egress no autorizado; V2 OFF |

## Criterios de salida
- Registrar por paso: hora, APK SHA, versión, evidencia observable, PASS/FAIL/NOT_RUN y observaciones. Los pasos externos se realizan sólo con intervención humana.
- Ante ROJO: parar operación afectada, preservar logs sin secretos, corregir y repetir sólo casos afectados en emulador y teléfono.
- F1–F4 `READY_FOR_T4` no equivalen a `CERTIFIED`. S2.A6 permanece 3/8 hasta que sus ocho criterios congelados tengan evidencia final; no activar S3/S4 por este plan.
- Si el dispositivo o el usuario no están disponibles, mantener `T4=NOT_RUN`; no fabricar resultado ni generar APK por la noche.
