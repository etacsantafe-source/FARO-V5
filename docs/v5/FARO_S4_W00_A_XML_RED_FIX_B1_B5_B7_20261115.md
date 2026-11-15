# S4 W00-A - RED_FIX unico del verificador XML, protocolo B1-B7
Fecha 2026-11-15 AR. Baseline de fallo bd01b5e21b9d6a1bf5ce2711e35e14018a78ccda. Unidad S4_W00_A_REALITY_TWO_EXISTING_OWNERS_ATOMICITY_TEST.
No agregar pruebas, no modificar codigo Kotlin, no reabrir S3 ni W01. Writer epoch 913 DOC_ONLY.

## B1 - Hallazgo concreto y causal
Run dirigido 43764293416, job 132965807598: gradle :store:reality:test con el filtro S4W00TwoOwnerAtomicityFeasibilityTest BUILD SUCCESSFUL, cinco casos en archivo XML, tests=5 failures=0 errors=0 skipped=0.
Paso Verify exact JUnit XML falla porque JUnit nombra cada testcase con sufijo de llamada (), mientras el conjunto esperado contiene los nombres de metodos sin parentesis. El log exacto muestra S4_W00_A_XML_FAIL ((5, 0, 0, 0), {cinco nombres terminados en ()}). Se guardo ZIP JUnit artifact 14181299552. No se observan fallos de Kotlin o SQLite. Guard 43764293312 SUCCESS en bd01b5e21b9d6a1bf5ce2711e35e14018a78ccda; G4 regressions 43764293307 SUCCESS.

## B2 - Comparacion y no repeticion
El test Kotlin (blob 560303edf148341a3c19703478e3f857cb51a97b) y cinco aserciones quedaron verdes en Gradle. No repetir ni editar el test ni importar V4.4. El cambio pertenece exclusivamente al oraculo del workflow .github/workflows/faro-v5-s4-w00-a-two-owner-atomicity-directed.yml. Ajustar normalizacion de nombres sin debilitar chequeo de cardinalidad ni failures/errors/skipped.

## B3 - Alternativas
A ADAPTAR: igualdad exacta de cinco nombres despues de retirar SOLO un sufijo final () documentado en JUnit XML para test method, manteniendo set exacto y counts. B DESCARTAR: ignorar todos los nombres o solo revisar BUILD SUCCESSFUL, porque aceptaria ejecuciones de otra clase. C DESCARTAR: renombrar cinco metodos o modificar tests productivos por formato XML. Para semantica de XML referir documentacion oficial JUnit Platform https://junit.org/junit5/docs/current/user-guide/ y Gradle test https://docs.gradle.org/current/userguide/java_testing.html. Evidencia primaria definitiva: log exacto del runner del SHA.

## B4 - Scope B6 de correccion minima
Cambiar SOLO .github/workflows/faro-v5-s4-w00-a-two-owner-atomicity-directed.yml, el Python de verificacion: names={v.attrib.get('name','').removesuffix('()') for v in root.findall('testcase')}. Mantener counts==(5,0,0,0), nombres exactos y total de 5; no cambios Kotlin/Gradle/Reality/owner. Ejecutar GitHub Actions trigger push y comprobar XML5/0/0/0, guard mismo SHA. B7 independiente posterior.

## B5 - Adversarial previo
El normalizador acepta solo sufijo literal final (), no quita parametros, duplicados ni nombres arbitrarios; los nombres siguen comparados al conjunto de cinco. No convertir test de una DB de dos owners en receipt multiowner ni W06 Household+Finance. No reintentar Apply ni tocar device o secretos. STOP si XML falla distinto tras correccion: nuevo diagnostico B1/B5 en vez de ampliar alcance.

## B7 - Disposicion formal del codigo anterior
Codigo SHA bd01b5e21b9d6a1bf5ce2711e35e14018a78ccda: RED_FIX del ORACULO XML UNICAMENTE. Kotlin compilado, cinco tests JUnit ejecutados sin fallos; workflow CI global FAILURE debido a comparador de nombres. No PASS global anticipado. Se habilita nueva correccion SOLO del mismo unit_id, con permiso READY por workflow unico, proof de este documento y guard documental antes del siguiente commit funcional.
