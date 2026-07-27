package org.opensapien.app.wear

import com.google.android.gms.wearable.Channel
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives WAV audio streamed from the watch (recorded while the phone was
 * unreachable), saves it to cache, transcribes with the phone-side engine,
 * writes the transcript file, then deletes the audio.
 *
 * TODO: implement onChannelOpened → receiveFile → transcribe → delete WAV.
 */
class WearAudioListenerService : WearableListenerService() {

    @Deprecated("Deprecated in Java")
    override fun onChannelOpened(channel: Channel) {
        // Wear Data Layer channel: /open_sapien/audio
        // receiveFile(channel, cacheWav) then hand to TranscriptionPipeline.
    }
}
