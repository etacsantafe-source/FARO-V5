# FARO V5 — W01-B2-A B7 RED_FIX · puntero ABA
Fecha AR: 2026-11-16. Unidad S4_W01_B2_A_CANONICAL_FINANCE_APPROVED_SCOPE.
SHA funcional a1ff9c4598cc80fe282b9fa2edb40ebd936e9f02. Guardia protocolo 43868651130 SUCCESS;
CI 43868651234 COMPLETED FAILURE, job 133303658599: 8 tests,
7 PASS y 1 FAIL en generationPointerAbaAndHostEpochRevocationDeny.
Build compiló correctamente, XML PASS no emitido por 1 fallo.

## B1
Causa: S4W01B2FinanceApprovedScopeReader acepta expectedGenerationId
y durante read() hace val pinned=pointer.read(). Si el puntero activo
cambia A→B→A antes de la lectura, su generationId puede volver a A
con otra secuencia; el lector la adopta como referencia NUEVA y
la revisión antigua podría parecer vigente. El test detectó el defecto
sin tocar Reality ni producir ninguna transacción.

## B2
Reusar ActiveGenerationPointer completo (sequence + generationId),
ya usado por Planning8ApprovedObjectGate: no basta cotejar solo
StorageGenerationId. La generación autorizada debe ser el puntero
capturado por el host inicial, no uno extraído al consultar.
Conservar los otros 7 casos, host no compuesto y catálogo oficial.

## B3
A: inyectar ActiveGenerationPointer pinned al construir el lector y
verificar pointer.read()==pinned antes y después de Workspace,
catálogo y payload: ELEGIDA.
B: modificar o eliminar prueba ABA: RECHAZADA.
C: convertir UUID o generationId en una autorización nueva: RECHAZADA.
D: habilitar V2 para evitar la prueba: RECHAZADA.

## B4
Corregir EXACTAMENTE dos rutas ya autorizadas:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScope.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScopeTest.kt
Constructor recibe pinned: ActiveGenerationPointer, valida
pinned.generationId == expectedGeneration, y la función lee
pointer.read() == pinned en cada checkpoint. Fixture entrega
el pointer real capturado ANTES de la maniobra ABA.
No tocar GitHub Actions, FinanceOwner, Workspace, Reality,
migraciones o 180 contratos. Ningún grant ni Apply.

## B5
Repetir exactamente 8 tests del XML dirigido y comprobar que
A→B→A sigue denegado. No inventar resultado PASS; se requiere
CI exacta del nuevo SHA, guard, diff solo esas dos rutas,
writer RELEASED y B7 final individual.

## B7
RED_FIX scoped de a1ff9c4598cc80fe282b9fa2edb40ebd936e9f02, NO PASS. Permiso continúa READY
en la MISMA unidad, con last_code_review RED_FIX y evidencia exacta.
