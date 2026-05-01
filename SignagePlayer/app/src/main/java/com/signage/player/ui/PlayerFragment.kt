package com.signage.player.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.signage.player.R
import com.signage.player.model.MediaFile
import com.signage.player.model.MediaType
import com.signage.player.utils.MediaFileManager
import java.io.File

class PlayerFragment : Fragment() {

    private lateinit var videoView: VideoView
    private lateinit var imageView: ImageView

    private var mediaList: List<MediaFile> = emptyList()
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val IMAGE_DURATION = 8000L

    private val nextRunnable = Runnable { playNext() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        videoView = view.findViewById(R.id.videoView)
        imageView = view.findViewById(R.id.imageView)
        loadAndPlay()
    }

    fun reloadMedia() {
        handler.removeCallbacks(nextRunnable)
        loadAndPlay()
    }

    private fun loadAndPlay() {
        mediaList = MediaFileManager.getMediaFiles(requireContext())
        if (mediaList.isEmpty()) {
            showEmpty()
            return
        }
        if (currentIndex >= mediaList.size) currentIndex = 0
        playAt(currentIndex)
    }

    private fun playAt(index: Int) {
        val file = mediaList[index]
        (activity as? MainActivity)?.updateCurrentFile(file.name)
        when (file.type) {
            MediaType.VIDEO -> playVideo(file.path)
            MediaType.IMAGE -> showImage(file.path)
        }
    }

    private fun playVideo(path: String) {
        imageView.visibility = View.GONE
        videoView.visibility = View.VISIBLE
        videoView.stopPlayback()
        videoView.setVideoURI(Uri.fromFile(File(path)))
        videoView.setOnPreparedListener { it.isLooping = false; videoView.start() }
        videoView.setOnCompletionListener { playNext() }
        videoView.setOnErrorListener { _, _, _ -> playNext(); true }
    }

    private fun showImage(path: String) {
        videoView.visibility = View.GONE
        videoView.stopPlayback()
        imageView.visibility = View.VISIBLE
        try {
            imageView.setImageBitmap(BitmapFactory.decodeFile(path))
        } catch (e: Exception) {
            playNext(); return
        }
        handler.removeCallbacks(nextRunnable)
        handler.postDelayed(nextRunnable, IMAGE_DURATION)
    }

    private fun playNext() {
        if (mediaList.isEmpty()) { loadAndPlay(); return }
        currentIndex = (currentIndex + 1) % mediaList.size
        playAt(currentIndex)
    }

    private fun showEmpty() {
        videoView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(nextRunnable)
        videoView.stopPlayback()
    }
}
