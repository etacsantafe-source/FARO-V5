# FARO V5 — S3 · Cierre formal con excepción humana H07-HOME
**Fecha:** 2026-11-15, Argentina. **Unidad:** S3_FINAL_U05_FORMAL_H07_EXCEPTION_CLOSURE.
**Decisión humana expresa:** «Autorizo», en respuesta a la propuesta explícita de **cerrar S3 sin repetir la campaña, aceptando documentadamente la excepción H07-HOME**.
**Rama:** `work/s3-night-20261102` · **HEAD de partida:** `2e310269e7e3bd0b9afc254b24fc0d565d353a46` · **Writer:** CAS epoch 908, alcance DOC_ONLY.

## B1 — Evidencia técnica y física previa
- Programación de S3: **5/5 etapas verificadas en alcance DEV** (A1 Inicio, A2 Buscar, A3 Guardados, A4 Lecturas, A5 Integración), según `docs/s3-night/S3_FINAL_PROGRAMMING_GLOBAL_B7_20261114.md`.
- APK integrada aislada: `ar.com.faro.alpha.s3integrateddev`, SHA-256 `de6c7f24d684453e6d6fffda9c51dfe4efa697540c37aade98172bc6968d7af0`, artefacto 14111788066; Android API35 `43588792359` SUCCESS; no se confunde con APK androidTest.
- Evidencia humana auditada en `docs/s3-night/S3_FINAL_G15_PHYSICAL_U05_B7_SCOPED_20261115.md`; su checkpoint `2e310269e7e3bd0b9afc254b24fc0d565d353a46` tiene guardia `43734062527` SUCCESS y writer RELEASED epoch 907 al inicio de esta unidad.
- R00: Reality8 activa, journal COMPLETED y fence tras lectura técnica de ADB. H01/H02/H04: PASS físicos históricos preservados, **no trasladados automáticamente** a instalaciones nuevas. H03: Activity con «Aplicación confirmada», Avisos cero en fuente autorizada y Pendientes sin vencimientos en ventana local hasta 7 días. H05: fuente 200%, TalkBack/foco/navegación y regreso HOME confirmados con su alcance humano. H06: consentimiento remoto y respuesta Groq observados; Free **declarado por usuario en la casilla** sin auditoría independiente de facturación. H08: propuesta nombre `Prueba S3` → revisión explícita → aprobación sin efecto → Apply único → estado «Aplicado y registrado» persistente + recibo de Activity a `2026-11-15T05:37:04.822Z`.
- H07: consulta iniciada y **aplicación cerrada durante la espera**; al reabrir «No se pudo completar» sin respuesta tardía ni reenvío observado; credencial revocada y nueva apertura remota rechazada con `IllegalStateException`. **El gesto exacto HOME durante petición inequívocamente en vuelo NO se probó.**

## B2/B3 — Comparación y alternativas sin reabrir código
Se preservan las auditorías B2/B3 anteriores (V4.4, Android lifecycle, restricciones de Groq/Keystore) con sus SHA y sus alcances originales. Alternativa A (hacer otra campaña o repetir H07 con HOME): descartada por decisión humana explícita, sin afirmar equivalencia técnica. Alternativa B (**aceptación acotada de riesgo H07-HOME**, elegida): cierre S3 con deuda de validación residual documentada. No se cambia implementación ni contrato de seguridad.

## B4 — Decisión de aceptación
- **S3_CERRADA_FORMALMENTE: SÍ, 5/5 etapas en alcance DEV**, mediante excepción de aceptación humana explícita sobre H07-HOME.
- **H07_HOME_IN_FLIGHT: EXCEPCION_ACEPTADA_NO_CERTIFICADA**, **NO PASS**. La prueba de cerrar proceso durante la solicitud queda **PASS físico acotado**. El retorno HOME en vuelo es un escenario distinto, no demostrado y no necesariamente equivalente.
- **Riesgo residual aceptado:** una respuesta que termine durante una pausa HOME podría comportarse de modo distinto a un cierre completo del proceso; se mantienen las protecciones de lifecycle y pruebas automatizadas previamente verificadas, sin elevarlas a observación física de HOME.
- Free y costo: casilla Free **atestada por el usuario**, sin afirmar verificación bancaria externa; no se cargaron secretos al repositorio. La clave remota fue revocada al finalizar la campaña.
- H03 Avisos/Pendientes: fuentes **vacías con cobertura explícita** en el entorno de prueba; no hubo casos positivos de esas fuentes en el G15 ni afirmación de cobertura global.
- H01/H02/H04: certificación física histórica retenida exactamente con su alcance. No se declara prueba física completa de cada flujo en la APK integrada.
- El cierre es **de etapa S3 DEV**, **NO** liberación de app productiva, habilitación de V2, S4 funcional o promoción de main.

## B5 — Controles adversariales
1. Excepción limitada estrictamente a **H07-HOME físico**, no a consentimiento, aislamiento, integridad, writer o recibos.
2. Ningún FAIL físico abierto de los controles probados se convierte artificialmente en PASS. Error de reapertura `IllegalStateException` se registra como bloqueo observado, con deuda UX de mensaje técnico; causa interna no demostrada en la captura.
3. No exigir nueva consulta Groq, nueva clave ni repetición de Apply. No modificar S2, CORE DEV, X03, Reality8 del teléfono, paquete, secretos o V4.4.
4. Permiso funcional `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` permanece **BLOCKED**; S4 preparación P00–P07 previamente documentada se conserva; P08 es siguiente etapa **READ_ONLY/DOC**, S4 no se abre formalmente.
5. Cualquier validación futura de HOME será **nueva unidad acotada por necesidad**, no trabajo pendiente para declarar el cierre de S3.

## B6 — Publicación y pruebas
Cambios permitidos en esta unidad: **solo este archivo documental y `docs/v5/FARO_CONTINUITY_POINTER.json`**. Cero código, cero APK nueva, cero CI Android repetida, cero acceso a cuentas o teléfono. Reutilizar guardia anterior SUCCESS `43734062527`; obtener y verificar guardia del **nuevo HEAD** después de publicar. Realizar CAS acquire/verify/release del writer único epoch 908, y comprobar en remoto RELEASED + checkpoint exacto.

## B7 — Resultado formal y continuidad
**DECISIÓN: S3_CLOSED_FORMALLY_5_OF_5_DEV_WITH_USER_ACCEPTED_H07_HOME_EXCEPTION.** No afirmar `H07_HOME_PASS`; resultado documental independiente de nueva prueba física. **Protocolo de programación B1–B7 previo conservado**; alcance de esta unidad: B1–B5 conciliación documental, B6 documentos, B7 auditoría final del cierre.

**S2:** CLOSED 8/8, congelada. **S3:** CLOSED 5/5 en alcance DEV con excepción aceptada. **S4:** NO ABIERTA, sin código; próximo trabajo permitido **P08 documental READ_ONLY**, partiendo de `docs/v5/FARO_S4_PREPARATION_P00_P08_HANDOFF_20261112.md` y recuperación de HEAD/permiso. **V2 productiva, Auto, Noche:** OFF. `main`, V4.4, datos y catálogo intactos.

**Checkpoint definitivo:** el commit remoto que contiene este documento; verificar SHA real, diff documental, guardia CI SUCCESS y writer RELEASED. No escribir en este archivo una afirmación anticipada de esas verificaciones.
