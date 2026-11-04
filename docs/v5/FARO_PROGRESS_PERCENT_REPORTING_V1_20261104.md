# FARO V5 — reporte durable del porcentaje de avance · V1 · 04/11/2026

## Regla obligatoria para cada turno FARO V5

A partir de autorización expresa del usuario el 04/11/2026, **al finalizar cada turno de FARO**, incluidas reanudaciones en chats nuevos usando solo `Continuar FARO V5`, agregar un bloque breve **📊 Avance** con porcentajes de cierre verificables, junto con el **🌡️ Termómetro del chat** ya acordado.

No depender del contexto del chat anterior: recuperar primero `docs/v5/FARO_CONTINUITY_POINTER.json`, esta regla y el checkpoint funcional actual en GitHub. No volver a pedir esta instrucción ni reabrir tareas ya PASS por SHA.

## Denominador explícito, fórmula conservadora

- **S2:** 8 subetapas certificadas de 8 = **100% de cierre**. Está congelada: no reabrir.
- **S3:** subetapas funcionales **A1, A2, A3, A4, A5**: `100 × número de subetapas CLOSED con aceptación/CI sobre 5`. Se informa **% de cierre de S3**, no un porcentaje ficticio de horas o de código. No confundir S3-N00 (prueba de coordinación) y S3-P00 (contrato de diseño) con cierre de una subetapa funcional.
- **Subetapa activa A2:** gates documentados **G1..G5** en `docs/s3-night/S3_A2_AUTHORITY_AND_COVERAGE_GATE_20261104.md`. `100 × gates íntegramente PASS sobre 5`. Un gate `PARTIAL` no suma un PASS. Mostrar `0/5` si todavía no hay gates cerrados, aunque partes de G3 ya hayan pasado CI.
- Porcentaje entero, exacto según contador. Indicar **Δ en puntos porcentuales respecto del turno anterior** únicamente si hay una base previa comparable en el checkpoint. Si no, escribir `sin cambio verificable`; no atribuir avances no documentados.
- **Distinción:** `55/55` o `57/57` son pruebas dirigidas de unidades, **NO significan 55%/57% de FARO**. Las tareas parciales se mencionan como tales, con prueba/commit. Los gates de cierre exigen autoridad, UI real y pruebas de aceptación según contrato. Las pruebas físicas continúan pendientes hasta una sola campaña final de S3.
- Si no existe denominador auditable para una fase o producto, `N/D (no medible aún)`, no inventar estimaciones. S4 sigue OFF y **no forma parte** del porcentaje de S3. Ningún porcentaje da permiso para abrir S4, activar V2, Noche o Auto.

## Línea base comprobada al 04/11/2026

| Medida | Evidencia | Porcentaje |
|---|---|---|
| S2 cierre certificado | 8/8, estado CLOSED | **100%** |
| S3 cierre funcional | A1 PARTIAL, A2 PARTIAL, A3 OPEN, A4 PARTIAL, A5 OPEN; ninguna cerrada integralmente | **0% (0/5)** |
| S3.A2 gates de cierre | G1 ACL real OPEN; G2 shipping bind OPEN; G3 contenido e in-chat PARTIAL; G4 dominios reales OPEN; G5 aceptación/físico OPEN | **0% (0/5)** |

La línea base **no invalida trabajo parcial existente**. En A2 se verificaron `7/7` pruebas nuevas y `57/57` de superficie sobre commit `09fa2b21d60011dc147971fa169829e3c8657b64`, run `41837562790` SUCCESS; búsqueda dentro de Chat con 250 mensajes/64k caracteres y cobertura parcial, **sin autoridad real ACL ni montaje de búsqueda global**.

## Formato de pie de turno

`📊 Avance: S2 100% (8/8) · S3 0% cerrado (0/5) · S3.A2 0% de gates cerrados (0/5; G3 parcial) · Δ sin cambio verificable.`

`🌡️ Chat: [VERDE|AMARILLO|NARANJA|ROJO] (cualitativo; NUNCA % ficticio de tokens).`

Actualizar los números **solo al demostrar cierres reales** mediante fuente de verdad (git HEAD, CI exacto, XML, contratos y aceptación); conservar denominadores o registrar una versión nueva de la métrica antes de cambiarlos. En las siguientes unidades, preferir indicar un progreso parcial útil con evidencia antes que inflar la cifra. Mantener el método híbrido de **una subetapa prioritaria hasta cierre**, sin dispersarse por porcentajes.

## Continuidad

Puntero canónico: `docs/v5/FARO_CONTINUITY_POINTER.json`; checkpoint funcional vigente: `docs/s3-night/S3_A2_IN_CHAT_BOUNDED_CHECKPOINT_20261104.md`.

Comando único suficiente en chat nuevo: **Continuar FARO V5**. Respetar restricciones, restaurar metodología y emitir porcentajes + termómetro al final del turno.

## Actualización autorizada por el usuario — doble porcentaje (04/11/2026)

**Además del cierre formal, es obligatorio informar el porcentaje estimado de avance funcional de S3** en todos los cierres de turno FARO V5. La cifra de cierre formal por sí sola (0/5 = 0%) invisibiliza el desarrollo parcial; no volver a presentarla como único avance.

- Línea base funcional aproximada S3 al 04/11/2026: **≈40%**, intervalo razonable **35–45%**; **juicio técnico orientativo**, no métrica de CI, ni certificación ni tiempo trabajado.
- Evidencia cualitativa: A1 tiene la interfaz/drawer/Inicio parcial; A2 dispone de búsqueda canónica e in-chat con CI, pero falta ACL real y federación shipping; A4 tiene Avisos con propietario Auxiliary parcialmente integrado; A3 Guardados y A5 integración/cierre quedan mucho menos desarrollados. El conjunto justifica avance importante, pero no todavía la mayoría cerrada.
- No repartir porcentajes falsamente exactos por subetapa ni inferir avance de líneas de código, número de tests, commits o fechas. Revisar la estimación tras unidades verificadas de trabajo y comunicar su **variación orientativa** (p. ej. `≈40% → ≈45%`); no aumentar la cifra solo por auditar o documentar.
- Mantener **un segundo número independiente**: S3 **0% de cierre formal (0/5 subetapas CLOSED)** al punto de partida. Ambos son válidos: desarrollo estimado vs cierre certificado.
- Formato al terminar **cada turno**: `📊 S3 ≈40% desarrollado (estimado, 35–45%) · 0% cerrado formalmente (0/5) · S2 100% (8/8). 🌡️ Chat: [color]`. Actualizar la estimación según evidencia nueva y el porcentaje formal solo con cierre PASS real.
- En cualquier nuevo chat con `Continuar FARO V5`, recuperar el puntero y conservar esta **preferencia obligatoria**; no pedir que el usuario la recuerde. El método híbrido sigue priorizando cerrar S3.A2; porcentajes no autorizan S4, V2, Auto, Noche ni nuevas operaciones Guardados.
