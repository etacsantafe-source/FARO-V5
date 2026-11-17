package ar.com.faro.v5.app

import android.app.Service
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process

/**
 * D1A ONLY: non-exported isolated-W01 Messenger service, mounted only in
 * w01FinanceDev AndroidManifest. This is a SYNTHETIC transport probe:
 * there is NO Finance9 SQL COMMIT, no grant, no balance, no principal,
 * no use of publishAfterVerifiedCommitForDevOnly and no OS notification UI.
 */
class S4W01FinanceReadNoticeProbeService : Service() {
    private val incoming by lazy {
        Messenger(Handler(Looper.getMainLooper(), Handler.Callback { message ->
            if (message.what != COMMAND_SYNTHETIC_EMPTY_NOTICE) {
                false
            } else {
                val emitted = runCatching {
                    val uri = Uri.Builder()
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .authority(packageName + ".finance9read")
                        .appendPath("invalidated")
                        .build()
                    contentResolver.notifyChange(uri, null, 0)
                    true
                }.getOrDefault(false)
                val response = Message.obtain(
                    null, RESPONSE_SYNTHETIC_EMPTY_NOTICE,
                    Process.myPid(), if (emitted) 1 else 0,
                )
                runCatching { message.replyTo?.send(response) }
                true
            }
        }))
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Defensive if the declaration is ever copied to another variant:
        // only this debuggable, sandbox-separated W01 package can bind.
        if (packageName != EXACT_W01_DEV ||
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0
        ) return null
        return incoming.binder
    }

    companion object {
        private const val EXACT_W01_DEV = "ar.com.faro.alpha.w01financedev"
        internal const val COMMAND_SYNTHETIC_EMPTY_NOTICE = 0x0F0101
        internal const val RESPONSE_SYNTHETIC_EMPTY_NOTICE = 0x0F0102
    }
}
