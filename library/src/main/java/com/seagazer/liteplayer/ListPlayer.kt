package com.seagazer.liteplayer

import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.seagazer.liteplayer.bean.DataSource
import com.seagazer.liteplayer.config.PlayerType
import com.seagazer.liteplayer.config.RenderType
import com.seagazer.liteplayer.helper.MediaLogger
import com.seagazer.liteplayer.player.IPlayerCore
import com.seagazer.liteplayer.widget.LitePlayerView

/**
 * A helper to play media list for recyclerView.
 *
 * Author: Seagazer
 * Date: 2020/6/29
 */
class ListPlayer constructor(val playerView: LitePlayerView) : IPlayerCore by playerView, LifecycleObserver {
    private var playingPosition = 0
    private var recyclerView: RecyclerView? = null
    private var layoutManager: LinearLayoutManager? = null
    private lateinit var listener: VideoListScrollListener
    private var autoPlay = true

    /**
     * Attach to recyclerView.
     *
     * @param recyclerView The recyclerView to attached.
     * @param autoPlay True auto play video when scroll changed, false you should click item to start play.
     * @param listener The video list scroll listener.
     */
    fun attachToRecyclerView(recyclerView: RecyclerView, autoPlay: Boolean, listener: VideoListScrollListener) {
        this.autoPlay = autoPlay
        if (autoPlay) {
            attachToRecyclerViewAutoPlay(recyclerView, listener)
        } else {
            attachToRecyclerViewClickPlay(recyclerView, listener)
        }
    }

    /**
     * Change the play logic.
     *
     * @param autoPlay True auto play video when scroll changed, false you should click item to start play.
     */
    fun setAutoPlayMode(autoPlay: Boolean) {
        if (this.autoPlay != autoPlay) {
            this.autoPlay = autoPlay
            if (recyclerView == null) {
                throw IllegalStateException("You must call attachToRecyclerView first")
            }
            recyclerView!!.run {
                removeOnScrollListener(autoPlayScrollListener)
                removeOnScrollListener(clickPlayScrollListener)
                if (autoPlay) {
                    attachToRecyclerViewAutoPlay(this, listener)
                } else {
                    attachToRecyclerViewClickPlay(this, listener)
                }
            }
        }
    }

    /**
     * Set full screen mode or not.
     */
    fun setFullScreenMode(isFullScreen: Boolean) {
        playerView.setFullScreenMode(isFullScreen)
    }

    private fun attachToRecyclerViewAutoPlay(recyclerView: RecyclerView, listener: VideoListScrollListener) {
        if (recyclerView.layoutManager !is LinearLayoutManager) {
            throw RuntimeException("Only support LinearLayoutManager because always single item video is playing")
        }
        // default config
        playerView.setPlayerType(PlayerType.TYPE_EXO_PLAYER)
        playerView.setRenderType(RenderType.TYPE_TEXTURE_VIEW)
        this.layoutManager = recyclerView.layoutManager as LinearLayoutManager
        this.recyclerView = recyclerView
        this.recyclerView!!.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                detachVideoContainer()
                val container = listener.getVideoContainer(playingPosition)
                container?.addView(playerView)
                MediaLogger.d("attach container: $container")
                val dataSource = listener.getVideoDataSource(playingPosition)
                if (dataSource != null) {
                    playerView.setDataSource(dataSource)
                    playerView.start()
                }
                this@ListPlayer.recyclerView!!.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        this.recyclerView!!.addOnScrollListener(autoPlayScrollListener)
        this.listener = listener
        unregisterLifecycle()
        registerLifecycle()
    }

    private fun attachToRecyclerViewClickPlay(recyclerView: RecyclerView, listener: VideoListScrollListener) {
        if (recyclerView.layoutManager !is LinearLayoutManager) {
            throw RuntimeException("Only support LinearLayoutManager because always single item video is playing")
        }
        // default config
        playerView.setPlayerType(PlayerType.TYPE_EXO_PLAYER)
        playerView.setRenderType(RenderType.TYPE_TEXTURE_VIEW)
        this.layoutManager = recyclerView.layoutManager as LinearLayoutManager
        this.recyclerView = recyclerView
        this.recyclerView!!.addOnScrollListener(clickPlayScrollListener)
        this.listener = listener
        unregisterLifecycle()
        registerLifecycle()
    }

    /**
     * If set the autoPlay false, call this method to play when click the item of recyclerView.
     *
     * @param position Click adapter position.
     */
    fun onItemClick(position: Int) {
        detachVideoContainer()
        val container = listener.getVideoContainer(position)
        container?.addView(playerView)
        MediaLogger.d("attach container: $container")
        val dataSource = listener.getVideoDataSource(position)
        if (dataSource != null) {
            playerView.setDataSource(dataSource)
            playerView.start()
        }
        if (position != RecyclerView.NO_POSITION) {
            playingPosition = position
        }
    }

    private val autoPlayScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            layoutManager?.let { lm ->
                val currentFirst = lm.findFirstCompletelyVisibleItemPosition()
                if (currentFirst != RecyclerView.NO_POSITION && playingPosition != currentFirst) {
                    MediaLogger.d("detach container")
                    detachVideoContainer()
                    MediaLogger.d("position: playing=$playingPosition, first=$currentFirst")
                    // 当前第一个不等于上次播放的index，播放当前第一个
                    if (playingPosition != currentFirst) {
                        val container = listener.getVideoContainer(currentFirst)
                        container?.addView(playerView)
                        MediaLogger.d("attach container: $container")
                        val dataSource = listener.getVideoDataSource(currentFirst)
                        if (dataSource != null) {
                            playerView.setDataSource(dataSource)
                            playerView.start()
                        }
                    }
                }
                if (currentFirst != RecyclerView.NO_POSITION) {
                    playingPosition = currentFirst
                }
            }
        }
    }

    private val clickPlayScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            layoutManager?.let { lm ->
                val currentFirst = lm.findFirstCompletelyVisibleItemPosition()
                val currentLast = lm.findLastCompletelyVisibleItemPosition()
                MediaLogger.d("position: playing=$playingPosition, first=$currentFirst,last=$currentLast")
                if (currentFirst != RecyclerView.NO_POSITION && currentLast != RecyclerView.NO_POSITION &&
                    currentFirst != currentLast &&
                    (playingPosition < currentFirst || playingPosition > currentLast)
                ) {
                    MediaLogger.d("detach container")
                    detachVideoContainer()
                }
            }
        }
    }

    private fun detachVideoContainer() {
        if (playerView.parent != null) {
            val parent: ViewGroup = playerView.parent as ViewGroup
            MediaLogger.d("detach container: $parent")
            playerView.stop()
            parent.removeView(playerView)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun onActivityResume() {
        MediaLogger.d("-->")
        resume()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onActivityStop() {
        MediaLogger.d("-->")
        pause()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun onActivityDestroy() {
        MediaLogger.d("-->")
        unregisterLifecycle()
        detachRecyclerView()
    }

    private fun registerLifecycle() {
        if (recyclerView!!.context is FragmentActivity) {
            MediaLogger.d("attached, register lifecycle")
            (recyclerView!!.context as FragmentActivity).lifecycle.addObserver(this)
        }
    }

    private fun unregisterLifecycle() {
        if (recyclerView!!.context is FragmentActivity) {
            (recyclerView?.context as FragmentActivity).lifecycle.removeObserver(this)
        }
    }

    /**
     * You should call this method if use the original Activity when the activity onDestroy.
     * If use sub class of FragmentActivity, it can call this method automatic when activity onDestroy.
     */
    fun detachRecyclerView() {
        recyclerView?.removeOnScrollListener(autoPlayScrollListener)
        recyclerView?.removeOnScrollListener(clickPlayScrollListener)
        playerView.stop()
        playerView.destroy()
    }

    fun setPlayerType(playerType: PlayerType) {
        playerView.setPlayerType(playerType)
    }

    fun setRenderType(renderType: RenderType) {
        playerView.setRenderType(renderType)
    }

    interface VideoListScrollListener {
        fun getVideoContainer(position: Int): ViewGroup?
        fun getVideoDataSource(position: Int): DataSource?
    }

}