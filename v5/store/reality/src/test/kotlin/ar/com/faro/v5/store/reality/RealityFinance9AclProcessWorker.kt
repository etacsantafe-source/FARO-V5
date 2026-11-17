package ar.com.faro.v5.store.reality

import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * M1C0 test infrastructure: a REAL second OS PID (Python3 SQLite stdlib).
 * Never included in production. Markers carry no human secret or permission.
 */
internal object RealityFinance9AclProcessWorker {
    private val python = """
import os,sqlite3,sys,time
database,action,marker,principal,generation,sequence,operation,object_scope=sys.argv[1:]
con=sqlite3.connect(database,timeout=15.0,isolation_level=None)
con.execute('PRAGMA foreign_keys=ON')
pid=os.getpid()
print('READY:'+str(pid),flush=True)
start=time.monotonic()
while not os.path.exists(marker):
    if time.monotonic()-start > 15.0:
        print('MARKER_TIMEOUT',flush=True)
        sys.exit(17)
    time.sleep(0.005)
print('ATTEMPT:'+str(pid),flush=True)
if action=='REVOKE':
    con.execute('BEGIN IMMEDIATE')
    row=con.execute('SELECT policy_epoch,revoked FROM finance_acl_policy WHERE principal_hash=? AND generation_id=? AND pointer_sequence=? AND operation_id=? AND object_scope=?',
        (principal,generation,int(sequence),operation,object_scope)).fetchone()
    if row is not None and row[1]==0:
        next_epoch,revision=con.execute('SELECT next_policy_epoch,policy_revision FROM finance_acl_state WHERE singleton_id=1').fetchone()
        if next_epoch>=9223372036854775807 or revision>=9223372036854775807:
            con.execute('ROLLBACK')
            sys.exit(13)
        con.execute('UPDATE finance_acl_policy SET revoked=1,policy_epoch=? WHERE principal_hash=? AND generation_id=? AND pointer_sequence=? AND operation_id=? AND object_scope=? AND policy_epoch=? AND revoked=0',
            (next_epoch,principal,generation,int(sequence),operation,object_scope,row[0]))
        con.execute('UPDATE finance_acl_state SET next_policy_epoch=?,policy_revision=? WHERE singleton_id=1',
            (next_epoch+1,revision+1))
    con.execute('COMMIT')
    print('DONE:'+str(pid),flush=True)
elif action=='CRASH':
    con.execute('BEGIN IMMEDIATE')
    con.execute("INSERT INTO finance_account VALUES ('crash-only','Transient','ARS','CASH')")
    os._exit(77) # real OS process death before COMMIT
else:
    sys.exit(19)
    """.trimIndent()

    internal class Child(
        val process: Process,
        val reader: BufferedReader,
        val pid: Long,
        private val signalPath: Path,
    ) : AutoCloseable {
        fun beginAttempt() {
            Files.writeString(signalPath, "GO")
            val message = reader.readLine()
            check(message == "ATTEMPT:" + pid) {
                "second OS process did not attempt a real SQLite write: " + message
            }
        }

        fun finish(expectedExit: Int = 0): String {
            check(process.waitFor(18, TimeUnit.SECONDS)) {
                "second OS process stalled at SQLite lock"
            }
            val remaining = reader.readText()
            check(process.exitValue() == expectedExit) {
                "second OS process exit=" + process.exitValue() + " output=" + remaining
            }
            return remaining
        }

        override fun close() {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(3, TimeUnit.SECONDS)
            }
            runCatching { reader.close() }
        }
    }

    fun start(
        root: Path,
        database: Path,
        action: String,
        scope: Finance9AclScope,
    ): Child {
        val marker = root.resolve("m1c0-signal-" + UUID.randomUUID() + ".flag")
        val proc = ProcessBuilder(
            "python3", "-u", "-c", python,
            database.toString(), action, marker.toString(), scope.principalHash,
            scope.generationId, scope.pointerSequence.toString(),
            scope.operationId, scope.objectScope,
        ).redirectErrorStream(true).start()
        val reader = proc.inputStream.bufferedReader()
        val firstLine = reader.readLine()
        check(firstLine == "READY:" + proc.pid()) {
            proc.destroyForcibly()
            "Second PID did not start: " + firstLine
        }
        check(proc.pid() != ProcessHandle.current().pid()) {
            "M1C0 worker must have a distinct OS PID"
        }
        println("M1C0_REAL_TWO_PID_CONFIRMED parent=" +
            ProcessHandle.current().pid() + " child=" + proc.pid())
        return Child(proc, reader, proc.pid(), marker)
    }
}
