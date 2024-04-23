package com.example.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {
    private var customProgress: Dialog? = null
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrPaint: ImageButton? = null

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackground: ImageView = findViewById(R.id.ivBackground)
                imageBackground.setImageURI(result.data?.data)
            } else {
                Toast.makeText(this, "Failed", Toast.LENGTH_LONG).show()
            }
        }

    private var clearBtn: ImageButton? = null
    private var mGalleryBtn: ImageButton? = null
    private var activityResult =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            permissions.entries.forEach {
                val permissioName = it.key
                val isGranted = it.value
                if (isGranted) {
                    Toast.makeText(
                        applicationContext,
                        "Permission to access the gallery - Allowed",
                        Toast.LENGTH_LONG
                    ).show()

                    val pickIntent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (permissioName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(
                            applicationContext,
                            "Permission to access the gallery - Denied",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawingView)
        drawingView?.setSizeForBrush(20f)

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.linear_layout_colors)
        mGalleryBtn = findViewById(R.id.galleryBtn)
        mGalleryBtn!!.setOnClickListener {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                showRationale("Kids Drawing App needs to access your storage", "Kids Drawing App")
            } else {
                activityResult.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        //Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
        clearBtn = findViewById(R.id.clearBtn)
        clearBtn?.setOnClickListener {
            drawingView?.reset()
            val imageBackground: ImageView = findViewById(R.id.ivBackground)
            imageBackground.setImageDrawable(null)
        }
        mImageButtonCurrPaint = linearLayoutPaintColors[0] as ImageButton
        mImageButtonCurrPaint!!.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.palete_selected
            )
        )
        val ib_brush: ImageButton = findViewById(R.id.brushBtn)
        ib_brush.setOnClickListener {
            showBrushSizeChooserDialogue()
        }

        val ib_undo: ImageButton = findViewById(R.id.undoBtn)
        ib_undo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val ib_redo: ImageButton = findViewById(R.id.redoBtn)
        ib_redo.setOnClickListener {
            drawingView?.onClickRedo()
        }

        val ib_save: ImageButton = findViewById(R.id.saveBtn)
        ib_save.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            val dialogView = layoutInflater.inflate(R.layout.name_dialogue, null)
            val nameEditText = dialogView.findViewById<EditText>(R.id.nameText)

            builder.setTitle("Name of your file")
                .setView(dialogView)
                .setPositiveButton("Save") { dialog, _ ->
                    val name = nameEditText.text.toString()
                    if (name.isNotEmpty() && Regex("[a-zA-Z0-9_]+").matches(name)) {
                        showProgressDialogue()
                        lifecycleScope.launch {
                            val flDrawingView: FrameLayout =
                                findViewById(R.id.flDrawingViewContainer)
                            saveBitmapToGallery(
                                this@MainActivity,
                                getBitmapFromView(flDrawingView),
                                name
                            )
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "File name cannot be empty or contain invalid characters",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                }
                .create()
                .show()


        }
    }

    private fun isRWritingStorageAllowed(): Boolean {
        val result =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun showBrushSizeChooserDialogue() {
        val brushDialogue = Dialog(this)
        brushDialogue.setContentView(R.layout.brush_layout)
        brushDialogue.setTitle("Brush size: ")
        val smallBtn = brushDialogue.findViewById<ImageButton>(R.id.ib_small_brush)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(5f)
            brushDialogue.dismiss()
        }
        val mediumBtn = brushDialogue.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10f)
            brushDialogue.dismiss()
        }
        val largeBtn = brushDialogue.findViewById<ImageButton>(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(15f)
            brushDialogue.dismiss()
        }
        brushDialogue.show()
    }

    fun paintClicked(view: View) {
        if (mImageButtonCurrPaint !== view) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.palete_selected
                )
            )
            mImageButtonCurrPaint?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.palete_normal
                )
            )
            mImageButtonCurrPaint = view
        }
    }

    private fun showRationale(message: String, title: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialogue, _ ->
                dialogue.dismiss()
            }
        builder.create()
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas: Canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        bgDrawable.draw(canvas)
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(bitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (bitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                    val f =
                        File(externalCacheDir?.absoluteFile.toString() + File.separator + "KidsDrawingApp_" + System.currentTimeMillis() / 1000 + ".png")
                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath
                    Log.d("Result", "${result}")
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap?,
        name: String
    ): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (bitmap != null) {
                var displayName = ""
                try {
                    displayName = "${name.trim()}_KidsDrawingApp.png"

                    // Insert image into MediaStore
                    val imageUri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(
                                MediaStore.Images.Media.DATE_ADDED,
                                System.currentTimeMillis() / 1000
                            )
                        }
                    )

                    // Write bitmap to OutputStream
                    if (imageUri != null) {
                        context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }

                        result = imageUri.toString()
                    }

                    // Show toast message
                    withContext(Dispatchers.Main) {
                        cancelProgressDialogue()
                        val message =
                            if (result.isNotEmpty()) "Image saved to gallery" else "Failed to save image"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialogue() {
        customProgress = Dialog(this@MainActivity)
        customProgress?.setContentView(R.layout.custom_dialogue)
        customProgress?.show()
    }

    private fun cancelProgressDialogue() {
        if (customProgress != null) {
            customProgress?.dismiss()
            customProgress = null
        }
    }


}