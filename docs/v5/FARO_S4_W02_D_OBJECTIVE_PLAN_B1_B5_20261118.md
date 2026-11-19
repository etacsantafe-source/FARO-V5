# FARO V5 · W02-D · Próximos pasos, dependencias y referencias

Unidad `S4_W02_D_OBJECTIVE_PLAN`. Base funcional inspeccionada `a297b8358c5e9b8d5cf3ef413e3bace58ecd02ae`. El permiso requiere B7 de W02-C, guardia y preflight de rutas exactas.

## B1
- P05 O03/PF14 requiere próximos pasos, vínculos a otros Objetivos o Asuntos y referencias a Finanzas con autorización específica. W02-C agrega historial y lifecycle. Reality posee los tres dominios en la misma conexión pero un port de Objetivos por sí solo no puede autorizar lectura de Asuntos o Finanzas.
- Aceptación. Próximo paso con título y fecha opcional, finalización explícita, alta de referencia local sin copiar datos fuente, rechazo de dependencia circular y proyección que degrada de forma segura si se deniega o revoca un permiso.

## B2
- V4.4 `bfa0ee71f5b9cd104baaadd71f1e77a61f203939`, FaroV4ObjectivesGateway y FaroV4ObjectivesSurface. La edición heredada recibe dependencyIds; la vista consultaba disponibilidad/capacidad en un panel separado. Recuperar pasos y bloqueos visibles. Evitar inferir dinero disponible a partir de una referencia o persistir esos montos en Objetivos.
- Usar `objective.update` existente con un comando canónico acotado y una sola mutación de Objetivos. Reutilizar su revisión y recibo, el port SQLite y la sección G4. Un grant de Search, de Objetivos, o de un owner diferente no autoriza referencias cruzadas.

## B3
- [OWASP Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html). ADAPTAR denegación por defecto y autorización granular por fuente/objeto. Exigir un grant antes de invocar el lector. Revalidar al volver a mostrar y antes de confirmar. DESCARTAR usar identidad implícita o mostrar ID bruto cuando la fuente no está disponible. Sin dependencia ni infraestructura externa.
- [Android permisos](https://developer.android.com/training/permissions/requesting). REFERENCIA del flujo de denegación y permisos acotados. Una referencia en FARO es permiso de aplicación local, no permiso de OS. Usar degradación parcial y una decisión explícita por owner. DESCARTAR pedir permisos de red, notificaciones, contactos o biometría.
- Comparadas dependencia autorreferencial, búsqueda DFS en snapshot e integridad solo por clave externa. USAR recorrido iterativo con visitados en el snapshot validado y revalidación en transacción. Una FK no impide ciclos; duplicar relaciones en otra DB agrega errores de sincronización. Referencias externas quedan opacas y su existencia se valida únicamente por el owner de origen. Cero costo o egress.

- [GitHub issue dependencies](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/creating-issue-dependencies) es REFERENCIA para distinguir bloqueado de bloqueante. [SQLite WITH](https://sqlite.org/lang_with.html) documenta eliminación de duplicados para evitar recorridos infinitos con ciclos. Se adopta conjunto de visitados en Kotlin porque la versión observada ya se valida en la transacción única. No introducir permisos GitHub, sync ni servicios externos a FARO.

## B4
- Persistir los pasos ordenados y las referencias por owner/ID opaco, sin detalles, títulos o saldos de Finance/Matter en la tabla de objetivos. Paso puede quedar sin fecha. Permitir añadir/editar y completar mediante Review. Rechazar id repetido, autorreferencia y ciclo en dependencias locales. El port de proyección sólo devuelve el texto verificado y degrada a genérico sin exponer identificadores denegados.
- Una Review que agrega una referencia externa exige grant de la fuente para ese objeto, relectura autorizada fuera de la transacción y comprobación del mismo epoch antes de Apply y antes de COMMIT. El lease de Objetivos sigue separado. Si el lector/ACL no se monta, sólo están disponibles los datos propios. Nunca cambiar saldo, pago, movimiento o Matter al escribir Objetivos.
- Mantener G4 v1/v2/v3. Agregar versión interna 4 solo con datos de plan. La extensión de SQLite es idempotente dentro de la transacción vigente. El formulario de B y cambios C conservan pasos/referencias. El montaje Android queda a E.
- Rutas candidatas a confirmar con el cierre C:
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveModel.kt`
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectivePlan.kt`
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveReviewedEditBinding.kt`
  - `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityObjectiveDetails.kt`
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5G4DomainSemanticCodec.kt`
  - `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W02ObjectiveReviewHost.kt`
  - `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W02ObjectivePlanTest.kt`
  - `.github/workflows/faro-v5-s4-w02-d-objective-plan.yml`

## B5
- Probar paso sin fecha y revocable, ciclo indirecto, referencia inexistente, fuente ajena negada, grant de Finance incapaz de habilitar Matter y revocación durante lectura. Sin datos ni IDs visibles en degradación. Mantener la revisión estable ante rechazo.
- Probar restore, reopen, persistencia y la semántica de commit único con recibo. Si la referencia pierde permiso, mostrar parcial sin inventar ahorro posible ni vaciar pasos propios. Regresión W02 B/C. V2 productiva OFF, W01 y W03 fuera de scope.
