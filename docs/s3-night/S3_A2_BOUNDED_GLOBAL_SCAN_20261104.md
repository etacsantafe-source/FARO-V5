# FARO V5 · S3.A2 G3 lectura canónica acotada y G1 · 04/11/2026

## Unidad funcional publicada (NO marcar PASS antes del XML)

- Rama `work/s3-night-20261102`; código `de5bfd01f4cb8166723877998ef39641099c867c`, título `fix(s3-a2): bound per-conversation canonical message scan and report partial coverage`.
- Ruta de propietario: `v5/workspace/src/main/kotlin/ar/com/faro/v5/workspace/ConversationWorkspaceStore.kt`.
- `readSearchableConversationText()` mantiene filtrado exclusivamente de mensajes USER ACCEPTED y FARO COMPLETED; limita a **250 mensajes elegibles recientes** por conversación y lee el elemento 251 solo como detector de truncamiento; cuando existe, `complete=false`. Se mantiene presupuesto de caracteres preexistente. Nunca un resultado parcial debe informarse como ausencia definitiva.
- Índice SQLite existente `workspace_message_chat_order(conversation_id, sequence)` verificado en esquema actual; no hay ALTER TABLE, nuevas tablas, backup/FBS ni egress.
- Regresión nueva `boundedRecentMessageScanDoesNotClaimCompleteOnLongHistory`: inserta 251 mensajes canónicos; verifica que una coincidencia antigua omitida produce PARTIAL con cero hits; una coincidencia reciente produce PARTIAL con un hit resoluble. `FaroV5WorkspaceSearchReadSourceTest` ahora espera 6/6. El workflow dirigido fue ajustado al conteo exacto.
- GitHub Actions run [41850653762](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41850653762), SHA exacto `de5bfd01f4cb8166723877998ef39641099c867c`: **IN_PROGRESS, XML no verificado al registrar esta nota**. NO confundir trigger/compilación con prueba aprobada. La prueba anterior 57/57 del Chat no requiere rerun.

## Diagnóstico READ_ONLY de autoridad y dependencias

- `ActiveGenerationStorageLocator.activeGenerationId()` lee el puntero al consultarlo, pero no emite por sí mismo cambios independientes.
- `FaroV5HomeReadinessInvalidations` observa readiness, commits Auxiliary y eventualmente puntero dado por otro dueño; su documento excluye ACL autónoma.
- `FaroV5V2WorkspaceHostBinding` abre un Workspace *quarantined*, NO host activo. En V1 no hay quinto Workspace para la composición de búsqueda; `searchReadPort=null` es la conducta segura. Los ACL epochs en tests son inyectados solo para demostrar rechazo.
- **G1 OPEN**: debe provenir de dueño real de permisos, con revocación/regrant monotónica observable incluso entre instancias; no derivarla de readiness ni de versión de esquema. Sin G1, **G2** no puede montarse en shipping.
- S3.A2 G3 más seguro y acotado; todavía no cierra G3 íntegro ni A2. G4 federación multi-owner real y G5 validación de aceptación/físico pendientes.

## Continuidad y controles

- S2 100% (8/8), congelada. S3 desarrollo orientativo ≈40% (35–45%); cierre formal 0/5. S3.A2 gates 0/5. No inflar estimación por un test/commit.
- Mantener S4, V2, Auto, Noche OFF; cola CLOUD STAGED; 172 operaciones intactas; Guardados +8 revisión no aprobada; no tocar main/V4.4 ni lanzar CI duplicadas.
- Orden de próxima ejecución: observar run #41850653762 hasta conclusión y XML; si rojo arreglar el cambio; si verde persistir CI exacto; retomar G1 solo con dueño ACL real, o G3 adyacente si bloqueado.
- Writer de este checkpoint: epoch 128, adquirido para registro durable y debe quedar RELEASED; un nuevo turno revalida CAS.

