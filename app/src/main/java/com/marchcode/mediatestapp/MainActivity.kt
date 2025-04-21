package com.marchcode.mediatestapp

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.marchcode.mediatestapp.databinding.ActivityMainBinding
import java.io.File

@UnstableApi
class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var videoUri: Uri
    var outputFile: File? = null
    var mediaItem: MediaItem? = null

    var currentFlippedStatus = false
    var currentRotatedStatus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.addVideoButton.setOnClickListener {
            pickVideo()
        }
        binding.rawVideo.setOnClickListener {
            if (!binding.rawVideo.isPlaying) {
                binding.rawVideo.start()
            } else {
                binding.rawVideo.pause()
            }
        }
        binding.flipButton.setOnClickListener {
        }
    }

    private fun pickVideo() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()){
        uri ->
        if (uri != null){
            videoUri = uri
            mediaItem = MediaItem.Builder()
                .setUri(videoUri)
                .build()

            binding.rawVideo.setVideoURI(uri)
            binding.rawVideo.start()
            binding.flipButton.let {
                it.isEnabled = true
                it.setOnClickListener{
                    flipVideo()
                }
            }
            binding.rotateButton.let {
                it.isEnabled = true
                it.setOnClickListener{
                    rotateVideo()
                }
            }
            binding.trimButton.let {
                it.isEnabled = true
                it.setOnClickListener{
                    trimVideo()
                }
            }

            currentFlippedStatus = false
            currentRotatedStatus = 0
        }
    }

    private fun saveVideo(){
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outputFile!!.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MyAppVideos")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = this.contentResolver
            val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

            videoUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(outputFile!!.readBytes())
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
    }

    private fun trimVideo() {
        val clippingConfiguration = ClippingConfiguration.Builder().setStartPositionMs(0L)
            .setEndPositionMs(5000).build()

        mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(clippingConfiguration)
            .build()

        convertVideo()
    }
    private fun shareVideo(){
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "video/mp4"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM,
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", outputFile!!))
        startActivity(Intent.createChooser(intent, "Share Video"))
    }

    private fun flipVideo(){
        if (!currentFlippedStatus){
            currentFlippedStatus = true
        } else {
            currentFlippedStatus = false
        }
        val flip = ScaleAndRotateTransformation.Builder().setScale(if (currentFlippedStatus) -1f else 1f,1f).build()
        convertVideo(flip)
    }

    private fun rotateVideo(){
        if (currentRotatedStatus >= 270){
            currentRotatedStatus = 0
        } else {
            currentRotatedStatus += 90
        }
        val rotate = ScaleAndRotateTransformation.Builder().setRotationDegrees(currentRotatedStatus.toFloat()).build()
        convertVideo(rotate)
    }

    private fun convertVideo(vararg effects: Effect){

        val editedMediaItem = EditedMediaItem.Builder(mediaItem!!)
            .setEffects(Effects(listOf() , effects.toList()))
            .build()

        val movieDir = File(filesDir, "Movies")
        if (!movieDir.exists()) {
            movieDir.mkdirs()
        }

        outputFile  = File( filesDir, "Movies/video_edited.mp4")

        val transformer = Transformer.Builder(this)
            .addListener(transformerListener)
            .build()
        transformer.start(editedMediaItem, outputFile!!.path)
    }

    private val transformerListener = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            super.onCompleted(composition, exportResult)
            Toast.makeText(this@MainActivity, "Video Edited", Toast.LENGTH_SHORT).show()

            binding.rawVideo.setVideoURI(Uri.fromFile(outputFile))
            binding.rawVideo.start()

            binding.saveButton.let {
                it.isEnabled = true
                it.setOnClickListener{
                    saveVideo()
                }
            }

            binding.shareButton.let {
                it.isEnabled = true
                it.setOnClickListener{
                    shareVideo()
                }
            }
        }

        override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException
        ) {
            super.onError(composition, exportResult, exportException)
            exportException.printStackTrace()
            Toast.makeText(this@MainActivity, "Error Editing Video", Toast.LENGTH_SHORT).show()
        }
    }
}