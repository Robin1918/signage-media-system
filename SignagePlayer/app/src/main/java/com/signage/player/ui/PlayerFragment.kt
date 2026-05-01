package com.signage.player.ui

import android.graphics.BitmapFactory
import android.media.MediaPlayer
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

    private var mediaFiles: MutableList<MediaFile> = mutableListOf()
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    // Duration to show each image (ms)
    private val imageDuration = 8000L

    private val nextMediaRunnable = Runnable { playNext() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        videoView = view.findViewById(R.id.videoView)
        imageView = view.findViewById(R.id.imageView)

        loadAndPlay()
    }

    fun reloadMedia() {
        handler.removeCallbacks(nextMediaRunnable)
        loadAndPlay()
    }

    private fun loadAndPlay() {
        mediaFiles = MediaFileManager.getMediaFiles(requireContext()).toMutableList()
        if (mediaFiles.isEmpty()) {
            showEmptyState()
            return
        }
        // Keep current index valid
        if (currentIndex >= mediaFiles.size) currentIndex = 0
        playAt(currentIndex)
    }

    private fun playAt(index: Int) {
        val file = mediaFiles[index]
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

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            videoView.start()
        }

        videoView.setOnCompletionListener {
            playNext()
        }

        videoView.setOnErrorListener { _, _, _ ->
            playNext()
            true
        }
    }

    private fun showImage(path: String) {
        videoView.visibility = View.GONE
        videoView.stopPlayback()
        imageView.visibility = View.VISIBLE

        try {
            val bitmap = BitmapFactory.decodeFile(path)
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            playNext()
            return
        }

        handler.removeCallbacks(nextMediaRunnable)
        handler.postDelayed(nextMediaRunnable, imageDuration)
    }

    private fun playNext() {
        if (mediaFiles.isEmpty()) {
            // Try reloading
            loadAndPlay()
            return
        }
        currentIndex = (currentIndex + 1) % mediaFiles.size
        playAt(currentIndex)
    }

    private fun showEmptyState() {
        videoView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        imageView.setImageResource(R.drawable.ic_empty_state)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(nextMediaRunnable)
        videoView.stopPlayback()
    }
}
