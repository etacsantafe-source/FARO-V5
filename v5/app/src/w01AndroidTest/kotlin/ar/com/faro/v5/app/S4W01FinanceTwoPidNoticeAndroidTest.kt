package ar.com.faro.v5.app

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android API35 real OS: 2 DIFFERENT PIDs, never a synthetic SQL commit. */
@RunWith(AndroidJUnit4::class)
class S4W01FinanceTwoPidNoticeAndroidTest {
    private fun uri(packageName: String): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(packageName + ".finance9read")
        .appendPath("invalidated")
        .build()

    @Test
    fun t01RemoteServicePidDiffersAndObserverStopsAfterUnregister() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ar.com.faro.alpha.w01financedev", context.packageName)
        val localPid = Process.myPid()
        val received = CountDownLatch(1)
        val late = CountDownLatch(1)
        val callbacks = AtomicInteger(0)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (callbacks.incrementAndGet() == 1) received.countDown()
                else late.countDown()
            }
        }

        val remote = AtomicReference<Messenger?>()
        val connected = CountDownLatch(1)
        val binding = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remote.set(Messenger(service))
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                remote.set(null)
            }
        }
        var isRegistered = false
        var isBound = false
        try {
            context.contentResolver.registerContentObserver(uri(context.packageName), false, observer)
            isRegistered = true
            isBound = context.bindService(
                Intent(context, S4W01FinanceReadNoticeProbeService::class.java),
                binding,
                Context.BIND_AUTO_CREATE,
            )
            assertTrue("W01 private probe service was not bound", isBound)
            assertTrue("W01 private remote service did not connect",
                connected.await(15L, TimeUnit.SECONDS))
            val sender = checkNotNull(remote.get())

            val receipt = AtomicReference(CountDownLatch(1))
            val remotePid = AtomicInteger(-1)
            val published = AtomicBoolean(false)
            val reply = Messenger(Handler(Looper.getMainLooper(), Handler.Callback { msg ->
                if (msg.what == S4W01FinanceReadNoticeProbeService.RESPONSE_SYNTHETIC_EMPTY_NOTICE) {
                    remotePid.set(msg.arg1)
                    published.set(msg.arg2 == 1)
                    receipt.get().countDown()
                    true
                } else false
            }))
            fun emitProbe() {
                val message = Message.obtain(
                    null, S4W01FinanceReadNoticeProbeService.COMMAND_SYNTHETIC_EMPTY_NOTICE,
                )
                message.replyTo = reply
                sender.send(message)
                assertTrue("No remote Binder ACK", receipt.get().await(15L, TimeUnit.SECONDS))
                assertTrue("Remote notice was rejected", published.get())
            }

            emitProbe()
            assertTrue("ContentObserver did not receive cross-PID notice",
                received.await(15L, TimeUnit.SECONDS))
            assertTrue("Remote Android pid missing", remotePid.get() > 0)
            assertNotEquals("Same-process callback is not D1A acceptance",
                localPid, remotePid.get())
            assertEquals("Expected exactly one initial notice", 1, callbacks.get())

            context.contentResolver.unregisterContentObserver(observer)
            isRegistered = false
            receipt.set(CountDownLatch(1))
            emitProbe()
            assertFalse("Closed observer received a new callback",
                late.await(800L, TimeUnit.MILLISECONDS))
            assertEquals("Unregister did not stop notices", 1, callbacks.get())
            println("W01_D1A_SYNTHETIC_IPC_ONLY_TWO_ANDROID_PIDS_PASS")
        } finally {
            if (isRegistered) context.contentResolver.unregisterContentObserver(observer)
            if (isBound) context.unbindService(binding)
        }
    }

    @Test
    fun t02PrivateProviderRefusesAnyFinanceRead() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ar.com.faro.alpha.w01financedev", context.packageName)
        var denied = false
        try {
            context.contentResolver.query(uri(context.packageName), null, null, null, null)?.close()
        } catch (_: SecurityException) {
            denied = true
        }
        assertTrue("Private URI anchor must never expose Finance rows", denied)
        println("W01_D1A_PRIVATE_PROVIDER_READ_DENIED_PASS")
    }
}
