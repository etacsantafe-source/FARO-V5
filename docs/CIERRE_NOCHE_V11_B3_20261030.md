# FARO V5 — Segundo piloto V1.1 · B3 cierre 17:40, 30/10/2026
Estado: **CLOSED / PASS_REVIEW / HANDOFF CLEAN**. **S2.A6 OPEN 3/8**, sin certificación de criterio nuevo.

## Ventana, exclusividad y autoridad
- Repo privado exclusivo `etacsantafe-source/FARO-V5`, rama `work/cierre-noche-v11-pilot-20261030`. Writer B-1740 epoch8 adquirido mediante `update_ref(expected_sha)` CAS a las 17:40:07 -03, commit `c2b6eb1fdfafb3efcde7cdad53586ac8a01059e5`; Noche A desactivada, B única habilitada, ningún writer previo ni Actions concurrentes.
- Cierre observado `2026-10-30T20:48:44.256Z` (Argentina aprox. 2026-10-30 17:48:44); tiempo desde adquisición del writer **8.62 minutos**. No se atribuye cómputo continuo ni 30 minutos. Cierre anticipado porque el único cambio seguro dirigido ya fue certificado y los siguientes trabajos requieren integración de mayor alcance.
- Writer liberado a **NONE / epoch8** en el mismo commit de este checkpoint; sin lease, sin segundo authority.
- `main` observado en `9bae214541f466a62e149853931b96abb8d74ed9` sin cambios. V2 OFF; S3/S4 cerrados; V1, V4.4, FARO Auto y repo histórico sin escrituras.

## F1 — Entrega funcional de seguridad
- Hallazgo: el contexto crudo de 11.984 caracteres cabe en el límite de 12.000, pero al agregar etiquetas USUARIO/FARO ocupa 12.012; el recorte inverso podía incluir una respuesta FARO sin el mensaje del usuario que la originó, revelando información derivada.
- Corrección: tras aplicar el presupuesto etiquetado, excluir toda respuesta FARO cuyo mensaje USER del mismo turno no esté efectivamente incluido; la lista revalidada de egreso coincide exactamente con los fragmentos enviados.
- Código + prueba de regresión y oráculo XML: commit `b030c2fa464a211921015fac47c1e25d37247dc2`. La prueba reproduce el caso límite 11.950 caracteres en el turno histórico y comprueba que el modelo recibe sólo el turno actual.
- [Actions #41125074207](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41125074207): **terminal SUCCESS**, SHA exacto del código. Logs job `124301160337`: F1 `FaroV5ActiveChatReasoningSessionT2Test` **18/18**, dirigidas **38/38**, F4 críticas **36/36**, Surface **31/31**; todos `failures=errors=skipped=0`. Compilación Android Kotlin/Java y structural PASS. No Android E2E ni T4.
- Actions queued/in_progress verificadas **0** tras el resultado; no se canceló ni duplicó CI.

## Brechas indispensables sin expandir S2.A6
- F1: `FaroV5Application` todavía no instala `ComposedModelGateway` real ni perfil/credenciales/egreso desde la autoridad de Ajustes/Keystore. El puerto host está dormido por `V2_ACTIVE_ENABLED=false`. Requiere integración segura, no hardcodear Groq ni credenciales ni saltar consentimiento.
- F2: ciclo completo Android activación, muerte de proceso y recuperación V2 pendiente de certificación.
- F3: opción A aprobada; ya existe frontera SAF/export y restauración en cuarentena, pero falta UI mínima de consentimiento/selector, además de iniciar de forma autorizada el journal RESTORE que la entrada actual exige. No adelantar Ajustes S5.
- F4: 36/36 JVM es parcial; falta Android integral.
- T4: teléfono físico **NOT_RUN**, no sustituible por JVM.
- Criterios 1–3 PASS históricos, 4–8 pendientes; **3/8** sin reinterpretar tests. No abrir S3/S4.

## Handoff
- Noche B termina aquí y debe quedar desactivada; Noche A ya está desactivada. Mantener sólo el Informe Final READ_ONLY 18:20 -03. No programar más sesiones, no extender la ventana, no crear nuevas Actions. El informe final verificará GitHub y reportará estado real.
