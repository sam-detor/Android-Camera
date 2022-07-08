package com.example.android_camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.stm32usbserial.CommanderPacket
import com.example.stm32usbserial.CrtpPacket
import com.example.stm32usbserial.PodUsbSerialService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op


class MainActivity : AppCompatActivity() {
    private var previewSV: SurfaceView? = null
    private var cameraSource: CameraSource? = null
    private var psv = PreviewSurfaceView()

    private val job = SupervisorJob()
    private val mCoroutineScope = CoroutineScope(Dispatchers.IO + job)
    private val TAG = "MLKit-ODT"

    //car stuff:
    private var mTvDevName: TextView? = null
    private var mTvDevVendorId: TextView? = null
    private var mTvDevProductId: TextView? = null
    private var mTvRxMsg: TextView? = null
    private var mPodUsbSerialService: PodUsbSerialService? = null
    private var mBounded: Boolean = false

    //driving stuff
    private var myDriver: Driver = Driver()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewSV = findViewById(R.id.sv_preview)

    }

    override fun onStart() {
        //Log.d(TAG, "hi")
        super.onStart()

        //stuff for the car:
        // start and bind service
        val mIntent = Intent(this, PodUsbSerialService::class.java)
        startService(mIntent)
        bindService(mIntent, mConnection, BIND_AUTO_CREATE)
        // set filter for service
        val filter = IntentFilter()
        filter.addAction(PodUsbSerialService.ACTION_USB_MSGRECEIVED)
        filter.addAction(PodUsbSerialService.ACTION_USB_CONNECTED)

        //driving stuff


        cameraSource = CameraSource(this, object: CameraSource.CameraSourceListener {
            override fun processImage(image: Bitmap) {
                //Log.d(TAG, "hi")
                runObjectDetection(image)
            }
            override fun onFPSListener(fps: Int) {}
        })
        mCoroutineScope.launch {
            cameraSource?.initCamera()
        }
    }

    // car stuff: get service instance
    private var mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Toast.makeText(this@MainActivity, "Service is connected", Toast.LENGTH_SHORT).show()
            mBounded = true
            val mUsbBinder: PodUsbSerialService.UsbBinder = service as PodUsbSerialService.UsbBinder
            mPodUsbSerialService = mUsbBinder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Toast.makeText(this@MainActivity, "Service is disconnected", Toast.LENGTH_SHORT).show()
            mBounded = false
            mPodUsbSerialService = null
        }
    }
    private fun runObjectDetection(bitmap: Bitmap) {
        // Step 1: Create TFLite's TensorImage object]
        //val rotationMatrix = Matrix()
        //rotationMatrix.postRotate(90F)
        //val rotatedImage = Bitmap.createBitmap(bitmap,0,0,bitmap.width, bitmap.height, rotationMatrix, true)
        val processedImg = preProcessInputImage(bitmap)
        val image = processedImg?.let { InputImage.fromBitmap(it.bitmap, 0) }

        val localModel = LocalModel.Builder()
            .setAssetFilePath("lite-model_aiy_vision_classifier_birds_V1_3.tflite")
            // or .setAbsoluteFilePath(absolute file path to model file)
            // or .setUri(URI to model file)
            .build()

        // Step 2: Initialize the detector object
        val options = CustomObjectDetectorOptions.Builder(localModel)
            .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .setClassificationConfidenceThreshold(0.5f)
            .build()
        val detector = ObjectDetection.getClient(options)

        // Step 3: Feed given image to the detector
        if (image != null) {
            detector.process(image).addOnSuccessListener { results ->

                //debugPrint(results)
                // Step 4: Parse the detection result and show it
                /*
                val detectedObjects = results.map {
                    var text = "Unknown"

                    // We will show the top confident detection result if it exist
                    if (it.labels.isNotEmpty() && it.labels.first().text == "Branta canadensis") {
                        val firstLabel = it.labels.first()
                        text = "Goose, ${firstLabel.confidence.times(100).toInt()}%"

                    }
                    BoxWithText(it.boundingBox, text)

                }
                */
                val detectedObjects: MutableList<BoxWithText> = mutableListOf()

                for (result in results) {
                    if (result.labels.isNotEmpty() && result.labels.first().text == "Branta canadensis") {
                        val firstLabel = result.labels.first()
                        val text = "Goose, ${firstLabel.confidence.times(100).toInt()}%"
                        //detectedObjects.add(BoxWithText(Rect(result.boundingBox.top, result.boundingBox.right, result.boundingBox.bottom, result.boundingBox.left), text))
                        detectedObjects.add(BoxWithText(result.boundingBox, text))
                    }
                }

                // Draw the detection result on the input bitmap
                val visualizedResult = drawDetectionResult(processedImg.bitmap, detectedObjects)
                //Log.d(TAG, "hi")
                myDriver.drive(mPodUsbSerialService,detectedObjects)
                if (detectedObjects.size >= 1) {
                    calculateAndDraw(detectedObjects)
                }
                val rotationMatrix = Matrix()
                rotationMatrix.postRotate(90F)
                val rotatedImage = Bitmap.createBitmap(visualizedResult,0,0,visualizedResult.width, visualizedResult.height, rotationMatrix, true)
                psv.setPreviewSurfaceView(rotatedImage)
            }
                .addOnFailureListener {
                    //psv.setPreviewSurfaceView(bitmap)

                }
        }
    }

    private fun preProcessInputImage(bitmap: Bitmap): TensorImage? {
        val width: Int = bitmap.width
        val height: Int = bitmap.height

        val size = if (height > width) width else height
        val imageProcessor = ImageProcessor.Builder().apply {
            add(Rot90Op())
            //add(ResizeWithCropOrPadOp(size, size))
            //add(ResizeOp(width, height, ResizeOp.ResizeMethod.BILINEAR))
        }.build()
        val tensorImage = TensorImage(DataType.UINT8)
        tensorImage.load(bitmap)
        return imageProcessor.process(tensorImage)
    }

    private fun calculateAndDraw(detectedObjects: List<BoxWithText>) {
        var center = 0
        for (box in detectedObjects) {
            var indiv_center = 0;
            if (box.box.left > box.box.right) {
                indiv_center = (box.box.left -  box.box.right)/2 + box.box.right
            }
            else {
                indiv_center = (box.box.right -  box.box.left)/2 + box.box.left
            }
            center += indiv_center
        }
        center  = center/detectedObjects.size
        val message = "${center}%"
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = message
        }
    }

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

    /**
     * Draw bounding boxes around objects together with the object's name.
     */
    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<BoxWithText>
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
            val box = it.box
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
    // broadcast receiver to update message and device info
    private val mBroadcastReceiver = object: BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(p0: Context?, p1: Intent?) {
            when (p1?.action) {
                PodUsbSerialService.ACTION_USB_MSGRECEIVED -> {
                    mTvRxMsg?.text = mPodUsbSerialService?.mRxMsg
                }
                PodUsbSerialService.ACTION_USB_CONNECTED -> {
                    mTvDevName?.text = getString(R.string.str_devName) + mPodUsbSerialService?.mDevName
                    mTvDevVendorId?.text = getString(R.string.str_devVendorId) + mPodUsbSerialService?.mDevVendorId.toString()
                    mTvDevProductId?.text = getString(R.string.str_devProductId) + mPodUsbSerialService?.mDevProductId.toString()
                }
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