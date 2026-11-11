# X03 U02 — preparación READ_ONLY de host real y consentimiento
Fecha: 2026-11-11 Argentina. Estado: INITIAL_READ_ONLY_FINDINGS_NEEDS_B1_B5_AND_INDIVIDUAL_PERMIT. No se emite permiso ni se modifica código U02.

## Evidencia nueva revisada durante CI U01
Fuentes exactas (sin cambios funcionales de esta preparación):
100644 blob 1c1639aaea4db373a8a10bf83b8f2ac3075414e1	.github/workflows/faro-v5-s2-chat-isolated.yml
100644 blob 14517a5716d10202a10a1f4a76aa641c3b28a713	v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5F1ConsentedQuarantineHost.kt
100644 blob 8ecd38137535e36122f230bf1cdd2d5c6f691aad	v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5F1IsolatedChatActivity.kt
100644 blob 6ce0ffcc29fd331cd437d35bb1a3ce4c12729d83	v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5QuarantinedV2ChatHostBinding.kt
100644 blob b1c9c3af63bf955f840ac67260f5c0da5815c690	v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5TrustedModelGatewayFactory.kt
100644 blob 3d8ec2611d95a12964abdefe69d6222bfe80c9f7	v5/kernel/runtime/src/main/kotlin/ar/com/faro/v5/kernel/runtime/ConservativeAiBudgetAuthorizer.kt


- La build debug aislada existente se arma con `-PfaroP6PhysicalApplicationId=ar.com.faro.alpha.s2isolated`; workflow S2 verifica APK `ar.com.faro.alpha.s2isolated.v5dev`. Es candidato de reuso, no build nueva ni prueba nueva ejecutada aquí.
- `FaroV5F1IsolatedChatActivity` usa directorio `faro-s2-f1-debug-only`, generation `f1-chat-isolated-debug-1`, staged graph con sentinel propio. No adoptar sobre el pointer activo de V2 DEV.
- Esa Activity ya conecta `applyRequestPort = ready.applyPort` y monta constructor de reviewed Apply/delivery en Reality aislada. Reusarla sin auditar esa autoridad mezclaría U02 con X09/X10. Falta elegir explícitamente una superficie limitada o justificar el aislamiento existente; no emitir READY por mera presencia del gateway.
- Host consentido F1 está en `src/debug`, no `src/main`. Consulta owner/Keystore antes de abrir stores. Binding vuelve a comprobar generación no activa y salud; el razonamiento verifica egress antes de PROCESSING, antes del modelo y antes de persistir salida.
- Activity F1 está exported en debug y solo cierra Session en onDestroy; no hay onPause propio que cierre modelo en esa Activity. El gesto de U01 no sustituye la auditoría U02 de foreground, revocación y callbacks en vuelo.
- Factory usa catálogo fijo, perfil autorizado y secretReader guardado; no acepta URL/modelo/claves desde el chat. No se hizo ninguna llamada al modelo ni se leyó una credencial.
- Costo: `pricing = emptyList()` en factory. `ConservativeAiBudgetAuthorizer` permite `AiMonetaryBudget.Disabled`; con `Capped` y sin pricing bloquea por TRUSTED_PRICING_REQUIRED. Por tanto «budget disabled» no demuestra «API gratis», y tampoco corresponde desactivar el control para destrabar. U02 debe verificar la modalidad gratuita y política costo cero sin pagos, sin secretos CI y sin declarar disponibilidad del proveedor basándose en comentarios históricos del código.

## Fuentes oficiales externas consultadas (solo delta U02)
- USAR: [AOSP Application Sandbox](https://source.android.com/docs/security/app-sandbox): UID separa recursos de apps. Verificar package/UID/storage y manifiesto final del host elegido.
- USAR: [AOSP Hardware-backed Keystore, access control](https://source.android.com/docs/security/features/keystore): claves APP están identificadas también por UID; no planear copiar credenciales de otro paquete ni asumir que un alias coincide entre apps.
- ADAPTAR: [Android Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle): auditar pausa/reanudación separadamente de destrucción; un host vivo no prueba que el gesto siga autorizado.
Fuentes leídas 2026-11-11; paráfrasis, sin código externo importado. Estas fuentes no certifican el producto ni gratuidad de un proveedor.

## Decisiones y pruebas aún necesarias antes de READY
1. Elegir host distinto de V2 CORE DEV sin cambiar su manifiesto. Comparar reuso S2 aislado frente a nueva superficie mínima; auditar impacto sobre S2 congelada antes de tocarla.
2. Definir límites exactos de Workspace/generación, foreground, consentimiento por gesto, revocación antes/durante respuesta y almacenamiento seguro; negar Apply implícito.
3. Fijar política de costo cero y prueba del binding real sin secretos en repo/Actions; no inventar respuesta sintética como IA real.
4. Auditar recovery PROCESSING/UNKNOWN sin replay del modelo, pinning de generación, salida revocada y cierre de Session.
5. Definir aceptación automática que distingue UI/puerto fake test-only, binding real y servicio remoto realmente usado. Mantener G15 físico para campaña final.
6. Publicar B1–B5 completos, scope exacto y permiso individual READY en commit separado; solo entonces preflight/B6 U02.

No se modifica S2, manifest V2 DEV, producción, S4, Auto/Noche o teléfono. Orden conservado X03 → X09 → X10 → X01 → X02 → X06 → X08 → X12. Esta preparación no cierra X03 global.

