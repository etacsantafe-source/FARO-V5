# FARO S2 — A3 CI workflow syntax correction (02/11/2026)

The first A3 CI trigger #41398800844 failed at workflow validation with no jobs. The generated YAML contained two literal `\\n` sequences in the inline comments before the Gradle command. This commit replaces them with actual newlines. The memory recovery remains: Gradle heap 3 GiB, Kotlin daemon 1.5 GiB, one worker. No production source changed. The next workflow trigger must be checked for actual compilation and tests; never count this validation failure as a PASS. Writer NONE, handoff CLEAN, S2 OPEN 6/8, V2 OFF.
