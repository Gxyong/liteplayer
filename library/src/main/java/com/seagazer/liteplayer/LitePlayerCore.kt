package com.seagazer.liteplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import androidx.lifecycle.MutableLiveData
import com.seagazer.liteplayer.bean.DataSource
import com.seagazer.liteplayer.config.PlayerState
import com.seagazer.liteplayer.helper.MediaLogger
import com.seagazer.liteplayer.event.PlayerStateEvent
import com.seagazer.liteplayer.player.IPlayer

/**
 *  Core player manager to do play logic.
 *
 * Author: Seagazer
 * Date: 2020/6/19
 */
object LitePlayerCore : IPlayer {
    private const val MEDIA_VOLUME_DEFAULT = 1.0f
    private const val MEDIA_VOLUME_DUCK = 0.2f
    private var innerPlayer: IPlayer? = null
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var audioAttributes: AudioAttributes
    private var audioFocusRequest: AudioFocusRequest? = null
    private var shouldPlayWhenReady = false
    var isInit = false

    fun init(context: Context) {
        isInit = true
        this.context = context.applicationContext
        audioManager = LitePlayerCore.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }

    fun setupPlayer(player: IPlayer) {
        innerPlayer = player
        requestAudioFocus()
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (shouldPlayWhenReady || getPlayerState() == PlayerState.STATE_PREPARED) {
                    setPlayerState(PlayerState.STATE_STARTED)
                    innerPlayer?.start()
                    setVolume(MEDIA_VOLUME_DEFAULT, MEDIA_VOLUME_DEFAULT)
                }
                shouldPlayWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (getPlayerState() == PlayerState.STATE_STARTED) {
                    setVolume(MEDIA_VOLUME_DUCK, MEDIA_VOLUME_DUCK)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                shouldPlayWhenReady = getPlayerState() == PlayerState.STATE_STARTED
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                abandonAudioFocus()
                pause()
            }
        }
    }

    private fun requestAudioFocus() {
        val result: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
            }
            result = audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            result = audioManager.requestAudioFocus(
                audioFocusListener,
                audioAttributes.contentType,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        // Call the listener whenever focus is granted - even the first time!
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            shouldPlayWhenReady = true
            audioFocusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        } else {
            MediaLogger.w("Playback not started: Audio focus request denied")
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
            }
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    override fun registerStateObserver(liveData: MutableLiveData<PlayerStateEvent>) {
        innerPlayer?.registerStateObserver(liveData)
    }

    override fun getPlayer() = innerPlayer

    override fun setDataSource(source: DataSource) {
        innerPlayer?.setDataSource(source)
    }

    override fun start() {
        innerPlayer?.start()
        requestAudioFocus()
    }

    override fun start(startPosition: Long) {
        innerPlayer?.start(startPosition)
        requestAudioFocus()
    }

    override fun pause() {
        innerPlayer?.pause()
    }

    override fun resume() {
        innerPlayer?.resume()
        requestAudioFocus()
    }

    override fun seekTo(position: Long) {
        innerPlayer?.seekTo(position)
    }

    override fun stop() {
        innerPlayer?.stop()
    }

    override fun reset() {
        innerPlayer?.reset()
    }

    override fun destroy() {
        innerPlayer?.destroy()
        abandonAudioFocus()
    }

    override fun setSurfaceHolder(surfaceHolder: SurfaceHolder) {
        innerPlayer?.setSurfaceHolder(surfaceHolder)
    }

    override fun setSurface(surface: Surface) {
        innerPlayer?.setSurface(surface)
    }

    override fun getVideoWidth(): Int {
        return if (innerPlayer != null) {
            innerPlayer!!.getVideoWidth()
        } else {
            0
        }
    }

    override fun getVideoHeight(): Int {
        return if (innerPlayer != null) {
            innerPlayer!!.getVideoHeight()
        } else {
            0
        }
    }

    override fun getDuration(): Long {
        return if (innerPlayer != null) {
            innerPlayer!!.getDuration()
        } else {
            0
        }
    }

    override fun isPlaying(): Boolean {
        return if (innerPlayer != null) {
            innerPlayer!!.isPlaying()
        } else {
            false
        }
    }

    override fun getBufferedPercentage(): Int {
        return if (innerPlayer != null) {
            innerPlayer!!.getBufferedPercentage()
        } else {
            0
        }
    }

    override fun getCurrentPosition(): Long {
        return if (innerPlayer != null) {
            innerPlayer!!.getCurrentPosition()
        } else {
            0
        }
    }

    override fun setPlaySpeed(speed: Float) {
        innerPlayer?.setPlaySpeed(speed)
    }

    override fun setVolume(left: Float, right: Float) {
        innerPlayer?.setVolume(left, right)
    }

    override fun setPlayerState(state: PlayerState) {
        innerPlayer?.setPlayerState(state)
    }

    override fun getPlayerState(): PlayerState {
        return if (innerPlayer != null) {
            innerPlayer!!.getPlayerState()
        } else {
            PlayerState.STATE_NOT_INITIALIZED
        }
    }


}