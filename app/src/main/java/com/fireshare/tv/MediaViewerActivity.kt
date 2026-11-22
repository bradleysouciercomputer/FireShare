package com.fireshare.tv

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import java.io.File

class MediaViewerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val folder = File(filesDir, "media").canonicalFile
        val file = File(folder, name).canonicalFile
        if (file.parentFile != folder || !file.isFile) {
            finish()
            return
        }

        if (file.extension.lowercase() in VIDEO_EXTENSIONS) {
            showVideo(file)
        } else {
            showImage(file)
        }
    }

    private fun showImage(file: File) {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { finish() }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_UP &&
                    (keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                     keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                ) {
                    finish()
                    true
                } else false
            }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(Color.BLACK)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, options))
            contentDescription = file.name
        }
        frame.addView(image, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val hint = TextView(this).apply {
            text = "Press Back to return"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(18, 10, 18, 10)
            setBackgroundColor(0x88000000.toInt())
        }
        frame.addView(hint, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ))
        setContentView(frame)
        frame.requestFocus()
    }

    private fun showVideo(file: File) {
        val video = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            setVideoPath(file.absolutePath)
        }
        val controller = MediaController(this)
        controller.setAnchorView(video)
        video.setMediaController(controller)
        setContentView(video, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        video.setOnPreparedListener { player ->
            player.isLooping = false
            video.start()
            controller.show(5000)
        }
        video.setOnErrorListener { _, _, _ ->
            val message = TextView(this).apply {
                text = "This video format could not be played on this Fire TV.\nPress Back to return."
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(40, 40, 40, 40)
                setBackgroundColor(Color.BLACK)
                isFocusable = true
                requestFocus()
            }
            setContentView(message)
            false
        }
    }

    companion object {
        const val EXTRA_NAME = "media_name"
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "mkv", "webm", "3gp", "mpeg", "mpg")
    }
}
