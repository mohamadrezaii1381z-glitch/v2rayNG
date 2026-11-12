package com.v2ray.ang.ui.hardvpn.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.Closeable
import java.util.UUID

/**
 * The single HardVPN <-> v2rayNG engine boundary.
 *
 * Everything here is a thin pass-through to the existing daemon architecture:
 *  - [LauncherManager] starts/stops/restarts the real VpnService (VPN, proxy-only or root),
 *  - [MessageHelper] messages are the established cross-process control channel,
 *  - ping batches reuse [com.v2ray.ang.service.CoreTestService] and persist results in MMKV,
 *  - traffic comes from the daemon's own outbound counters ([AppConfig.MSG_QUERY_TRAFFIC]).
 *
 * No second engine, service or tunnel is created, and no state is fabricated: the UI only
 * ever sees [EngineEvent]s that the daemon actually emitted.
 */
class HardVpnEngine(private val app: AngApplication) : Closeable {

    sealed interface EngineEvent {
        data object Running : EngineEvent
        data object NotRunning : EngineEvent
        data object StartSuccess : EngineEvent
        data class StartFailure(val message: String?) : EngineEvent
        data object StopSuccess : EngineEvent
        data class Traffic(val uplinkTotal: Long, val downlinkTotal: Long) : EngineEvent
        data class PingProgress(val text: String) : EngineEvent
        data object PingFinished : EngineEvent
        data object PingCancelled : EngineEvent
    }

    private val events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 32)
    val engineEvents: SharedFlow<EngineEvent> = events.asSharedFlow()

    private var closed = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val key = safeIntent.getIntExtra("key", 0)
            val content = safeIntent.getStringExtra("content").orEmpty()
            val requestId = safeIntent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID).orEmpty()
            val event = when (key) {
                AppConfig.MSG_STATE_RUNNING -> EngineEvent.Running
                AppConfig.MSG_STATE_NOT_RUNNING -> EngineEvent.NotRunning
                AppConfig.MSG_STATE_START_SUCCESS -> EngineEvent.StartSuccess
                AppConfig.MSG_STATE_START_FAILURE -> EngineEvent.StartFailure(content)
                AppConfig.MSG_STATE_STOP_SUCCESS -> EngineEvent.StopSuccess
                AppConfig.MSG_TRAFFIC_RESULT -> parseTraffic(content)
                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> EngineEvent.PingProgress(content)
                AppConfig.MSG_MEASURE_CONFIG_FINISH -> EngineEvent.PingFinished
                AppConfig.MSG_MEASURE_CONFIG_CANCEL -> EngineEvent.PingCancelled
                else -> null
            }
            if (event != null) {
                if (key == AppConfig.MSG_MEASURE_CONFIG_NOTIFY) {
                    LogUtil.d(AppConfig.TAG, "HardVPN ping progress $requestId")
                }
                events.tryEmit(event)
            }
        }
    }

    init {
        try {
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Utils.receiverFlags()
            )
            MessageHelper.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "HardVPN engine: failed to register receiver", e)
        }
    }

    private fun parseTraffic(content: String): EngineEvent.Traffic? {
        val parts = content.split(',')
        if (parts.size < 2) return null
        val up = parts[0].toLongOrNull() ?: return null
        val down = parts[1].toLongOrNull() ?: return null
        return EngineEvent.Traffic(up, down)
    }

    fun isVpnMode(): Boolean = SettingsManager.isVpnMode()

    /** Engine selection that the daemon will actually start. */
    fun selectedGuid(): String? = MmkvManager.getSelectServer()

    fun start() {
        LauncherManager.startService(app)
    }

    fun stop() {
        LauncherManager.stopService(app)
    }

    /**
     * Restarts the active daemon so a new server or DNS setting takes effect. v2rayNG's own
     * restart path is used; if no daemon is running it stays stopped.
     */
    fun restartIfRunning() {
        LauncherManager.restartService(app)
    }

    /** Asks the daemon for this session's traffic totals; it answers with [AppConfig.MSG_TRAFFIC_RESULT]. */
    fun requestTraffic() {
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_QUERY_TRAFFIC, "")
    }

    /** Bounded real-ping batch through the existing test service. */
    fun startPingBatch(sourceId: String, guids: List<String>): String? {
        if (guids.isEmpty()) return null
        val requestId = UUID.randomUUID().toString()
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            subscriptionId = sourceId,
            serverGuids = guids,
            onlyTcp = false,
        )
        MessageHelper.sendMsg2TestService(app, message, requestId)
        return requestId
    }

    fun cancelPing() {
        MessageHelper.sendMsg2TestService(
            app,
            TestServiceMessage(AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
    }

    fun sessionStartedAtMillis(): Long =
        MmkvManager.decodeSettingsLong(AppConfig.CACHE_SESSION_STARTED_AT, 0L)

    override fun close() {
        if (closed) return
        closed = true
        try {
            app.unregisterReceiver(receiver)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "HardVPN engine: failed to unregister receiver", e)
        }
    }
}
