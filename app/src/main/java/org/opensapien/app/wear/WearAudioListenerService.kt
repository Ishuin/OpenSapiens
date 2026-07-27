package org.opensapien.app.wear

import android.net.Uri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File

/**
 * Receives WAV audio streamed from the watch (recorded while the phone was
 * unreachable or live). Saves to `wear_inbox/`, then hands off to
 * [WearTranscribeWorker] which transcribes on-device, writes the transcript,
 * and deletes the audio.
 */
class WearAudioListenerService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != AUDIO_CHANNEL_PATH) return
        val inbox = File(filesDir, INBOX_DIR).apply { mkdirs() }
        val wav = File(inbox, "wear_${System.currentTimeMillis()}.wav")
        val channelClient = Wearable.getChannelClient(this)
        channelClient.registerChannelCallback(
            channel,
            object : ChannelClient.ChannelCallback() {
                override fun onInputClosed(
                    ch: ChannelClient.Channel,
                    closeReason: Int,
                    appErrorCode: Int,
                ) {
                    channelClient.unregisterChannelCallback(this)
                    // Transfer finished (file fully written) — transcribe via
                    // WorkManager so it survives this service being unbound.
                    WearTranscribeWorker.enqueue(applicationContext)
                }
            },
        )
        channelClient.receiveFile(channel, Uri.fromFile(wav), false)
    }

    companion object {
        const val AUDIO_CHANNEL_PATH = "/open_sapien/audio"
        const val INBOX_DIR = "wear_inbox"
    }
}
