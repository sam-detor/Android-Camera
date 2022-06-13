package com.example.android_camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions


class MainActivity : AppCompatActivity() {
    private var previewSV: SurfaceView? = null
    private var cameraSource: CameraSource? = null
    private var psv = PreviewSurfaceView()

    private val job = SupervisorJob()
    private val mCoroutineScope = CoroutineScope(Dispatchers.IO + job)
    private val TAG = "MLKit-ODT"
    //640 by 480
    private var old_img = Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888)
    /*private val options = ObjectDetectorOptions.builder()
        .setBaseOptions(BaseOptions.builder().useGpu().build())
        .setMaxResults(1)
        .build()
    private val objectDetector = ObjectDetector.createFromFileAndOptions(
        this, "coco_ssd_mobilenet_v1_1.0_quant.tflite", options
     */

    /*
    companion object {
        val done = true
    }
    */
     private var done = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewSV = findViewById(R.id.sv_preview)
        requestPermission()
    }

    override fun onStart() {
        //Log.d(TAG, "hi")
        super.onStart()
        cameraSource = CameraSource(this, object: CameraSource.CameraSourceListener {
            override fun processImage(image: Bitmap) {
                //Log.d("TEST-H", image.height.toString())
                //Log.d("TEST-W", image.width.toString())
                //Log.d(TAG, "hi")
                if(done)
                {
                    lifecycleScope.launch(Dispatchers.Default)
                    { runObjectDetection(image)}
                    //runObjectDetection(image)
                }
                else
                {
                    Log.d("TEST", "hi")
                    psv.setPreviewSurfaceView(old_img)
                }
            }
            override fun onFPSListener(fps: Int) {}
        })
        mCoroutineScope.launch {
            cameraSource?.initCamera()
        }
    }
    private fun runObjectDetection(bitmap: Bitmap) {

        done = false
        // Step 1: Create TFLite's TensorImage object
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap, 300, 300, false)
        val image = TensorImage.fromBitmap(resizedBitmap)
        val options = ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setNumThreads(2).useGpu().build())
            .setMaxResults(1)
            .setScoreThreshold(0.5f)
            .build()

        val objectDetector = ObjectDetector.createFromFileAndOptions(
            this, "model.tflite", options)

        val results = objectDetector.detect(image)

        // Step 4: Parse the detection result and show it
        val resultToDisplay = results.map {
            // Get the top-1 category and craft the display text
            val category = it.categories.first()
            val text = "${category.label}, ${category.score.times(100).toInt()}%"

            // Create a data object to display the detection result
            DetectionResult(it.boundingBox, text)
        }
        // Draw the detection result on the bitmap and show it.
        val imgWithResult = drawDetectionResult(resizedBitmap, resultToDisplay)
        runOnUiThread {
            psv.setPreviewSurfaceView(imgWithResult)
        }
        old_img = imgWithResult
        done = true
    }
/*
    private fun debugPrint(detectedObjects: List<DetectedObject>) {
        //Log.d("MLKit-ODT", "hi")
        detectedObjects.forEachIndexed { index, detectedObject ->
            val box = detectedObject.boundingBox
            val TAG = "MLKit-ODT"
            Log.d(TAG, "Detected object: $index")
            Log.d(TAG, " trackingId: ${detectedObject.trackingId}")
            Log.d(TAG, " boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})")
            detectedObject.labels.forEach {
                Log.d(TAG, " categories: ${it.text}")
                Log.d(TAG, " confidence: ${it.confidence}")
            }
        }
    }
*/
    /**
     * drawDetectionResult(bitmap: Bitmap, detectionResults: List<DetectionResult>
     *      Draw a box around each objects and show the object's name.
     */
    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<DetectionResult>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResults.forEach {
            // draw bounding box
            pen.color = Color.RED
            pen.strokeWidth = 8F
            pen.style = Paint.Style.STROKE
            val box = it.boundingBox
            canvas.drawRect(box, pen)


            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            pen.style = Paint.Style.FILL_AND_STROKE
            pen.color = Color.YELLOW
            pen.strokeWidth = 2F

            pen.textSize = 96F
            pen.getTextBounds(it.text, 0, it.text.length, tagSize)
            val fontSize: Float = pen.textSize * box.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.textSize) pen.textSize = fontSize

            var margin = (box.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvas.drawText(
                it.text, box.left + margin,
                box.top + tagSize.height().times(1F), pen
            )
        }
        return outputBitmap
    }

    /** request permission */
    private fun requestPermission() {
        /** request camera permission */
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                } else {
                    Toast.makeText(this, "Request camera permission failed", Toast.LENGTH_SHORT).show()
                }
            }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    inner class PreviewSurfaceView {
        private var left: Int = 0
        private var top: Int = 0
        private var right: Int = 0
        private var bottom: Int = 0
        private var defaultImageWidth: Int = 0
        private var defaultImageHeight: Int = 0
        fun setPreviewSurfaceView(image: Bitmap) {
            val holder = previewSV?.holder
            val surfaceCanvas = holder?.lockCanvas()
            surfaceCanvas?.let { canvas ->
                if (defaultImageWidth != image.width || defaultImageHeight != image.height) {
                    defaultImageWidth = image.width
                    defaultImageHeight = image.height
                    val screenWidth: Int
                    val screenHeight: Int

                    if (canvas.height > canvas.width) {
                        val ratio = image.height.toFloat() / image.width
                        screenWidth = canvas.width
                        left = 0
                        screenHeight = (canvas.width * ratio).toInt()
                        top = (canvas.height - screenHeight) / 2
                    } else {
                        val ratio = image.width.toFloat() / image.height
                        screenHeight = canvas.height
                        top = 0
                        screenWidth = (canvas.height * ratio).toInt()
                        left = (canvas.width - screenWidth) / 2
                    }
                    right = left + screenWidth
                    bottom = top + screenHeight
                }

                canvas.drawBitmap(
                    image, Rect(0, 0, image.width, image.height),
                    Rect(left, top, right, bottom), null)
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
/**
 * DetectionResult
 *      A class to store the visualization info of a detected object.
 */
data class DetectionResult(val boundingBox: RectF, val text: String)
/**
 * A general-purpose data class to store detection result for visualization
 */
data class BoxWithText(val box: Rect, val text: String)