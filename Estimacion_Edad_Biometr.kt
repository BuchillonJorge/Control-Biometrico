package es.anfac.dniereader.ui.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import es.anfac.dniereader.R
import es.anfac.dniereader.databinding.EntryValidateBiologicalAgeOptionsDialogBinding
import es.anfac.dniereader.interfaces.BiometricUtilsListener
import es.anfac.dniereader.utiles.BiometricUtils
import java.io.IOException
import kotlin.math.abs


class Estimacion_Edad_Biometr : DialogFragment () {

    private lateinit var binding: EntryValidateBiologicalAgeOptionsDialogBinding
    private lateinit var progresdailog: AlertDialog
    private var ImageBitmap : Bitmap? = null

    override fun getTheme(): Int {
        return R.style.FullScreenDialogTheme
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            EntryValidateBiologicalAgeOptionsDialogBinding.bind(inflater.inflate(R.layout.entry_validate_biological_age_options_dialog, container))
        dialog!!.setCancelable(true)
        initUI()
        return binding.root
    }
    private fun initUI(){
        binding.btnBiologicalAgeCamera.setOnClickListener{
            OpenCamera()
        }
        binding.btnBiologicalAgeNFC.setOnClickListener{
            OpenGaleria()
        }
        binding.btnCancel.setOnClickListener{dismiss()}
    }

    private fun OpenGaleria() {
      //  val intent = Intent(Intent.ACTION_PICK)
      //  intent.type = "image/*"
       // pickMedia.launch(intent)*/

      pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()){ result->
        if (result != null){
            val uri : Uri = result
            val inputStream = requireContext().contentResolver.openInputStream(result)
            val bit = BitmapFactory.decodeStream(inputStream)
            try {
                //val rotatedImage = rotateImage(result)
                //ImageBitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                //val rotatedImage = rotateImage(result)
                detecteFaces(bit){faceBitmap->


                try {
                    BiometricUtils(object : BiometricUtilsListener {
                        override fun onAgeDetected(biologicalAgeDetected: Int) {
                            if (biologicalAgeDetected != 0) {
                                binding.ivAge.setImageBitmap(faceBitmap)
                                val edadText = "Su edad biológica identificada es de"
                                val edadText1 = "años aproximadamente"
                                binding.tvEdadBio.setText(edadText + " " + biologicalAgeDetected + " " + edadText1)
                                if(biologicalAgeDetected > 18){
                                    Toast.makeText(requireContext(), "Usted es mayor de edad", Toast.LENGTH_LONG).show()
                                    //toastLong("Usted es mayor de edad")
                                }else{
                                    Toast.makeText(requireContext(), "Usted es menor de edad", Toast.LENGTH_LONG).show()
                                    //toastLong("Usted es menor de edad")
                                }
                            }else{
                                Toast.makeText(requireContext(), "Por favor seleccione una imagen correcta", Toast.LENGTH_LONG).show()
                                //toastLong("Por favor seleccione una imagen correcta")
                                binding.tvEdadBio.setText("")
                                binding.ivAge.setImageBitmap(null)
                            }
                        }

                        override fun onSmileDetected(smileDetected: Boolean) {

                        }

                        override fun onErrorDetected(errorDetected: String) {

                        }
                    }).detectAgeIn(faceBitmap)
                }catch (e: IOException){
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error edad", Toast.LENGTH_LONG).show()
                    //toastLong("Error edad")
                }}
            }catch (e: IOException){
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error", Toast.LENGTH_LONG).show()
                //toastLong("Error")
            }
        }

    }

    private fun detecteFaces(bitmap: Bitmap, callback: (Bitmap) -> Unit) {
        // Opciones del detector de rostros
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        val inputImage = InputImage.fromBitmap(bitmap, 270)
        val detector = FaceDetection.getClient(options)

        detector.process(inputImage)
            .addOnSuccessListener { faces: List<Face> ->
                if (faces.isNotEmpty()) {
                    // Obtener la primer cara detectada
                    val face = faces[0]

                    // Alternativa: Aquí puedes obtener el ángulo (rotación) de la imagen.
                    // Por ahora vamos a asumir una rotación de 90 grados en caso de que sea vertical.
                    val rotatedBitmap = rotateBitmapIfNeeded(bitmap)

                    // Ajustar el rostro y centrarlo en el ImageView
                    val croppedBitmap = cropFace(rotatedBitmap, face)
                    ImageBitmap = croppedBitmap

                    callback(croppedBitmap)
                } else {
                    Toast.makeText(requireContext(), "No se detectaron rostros", Toast.LENGTH_LONG).show()
                    //toastLong("No se detectaron rostros")
                    Log.d("MainActivity", "No se detectaron rostros.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error al detectar rostros: ", e)
            }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        // Rotar la imagen si es necesario (suponiendo rotación de 90 grados aquí)
        val matrix = Matrix()
        matrix.postRotate(270f)  // Rotar 90 grados si es vertical
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    private fun cropFace(bitmap: Bitmap, face: Face): Bitmap {
        // Obtener los límites de la cara detectada
        val bounds = face.boundingBox
        val rotarFace = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
        return rotarFace
    }


    private fun OpenCamera() {
        startForResult.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }
    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result:
        ActivityResult->
        if (result.resultCode == Activity.RESULT_OK){
            val intent = result.data
            try {
                ImageBitmap = intent?.extras?.get("data") as Bitmap
                processImage(ImageBitmap!!)

                /*try {
                    BiometricUtils(object : BiometricUtilsListener {
                        override fun onAgeDetected(biologicalAgeDetected: Int) {
                            if (biologicalAgeDetected != 0) {
                                //processImage(ImageBitmap!!)
                                //binding.ivAge.setImageBitmap(ImageBitmap)
                                val edadText = "Su edad biológica identificada es de"
                                val edadText1 = "años aproximadamente"
                                binding.tvEdadBio.setText(edadText + " " + biologicalAgeDetected + " " + edadText1)
                                if(biologicalAgeDetected > 18){
                                    Toast.makeText(requireContext(), "Usted es mayor de edad", Toast.LENGTH_LONG).show()
                                    //toastLong("Usted es mayor de edad")
                                }else{
                                    Toast.makeText(requireContext(), "Usted es menor de edad", Toast.LENGTH_LONG).show()
                                    //toastLong("Usted es menor de edad")
                                }
                            }else{
                                Toast.makeText(requireContext(), "Por favor tome correctamente la fotografía", Toast.LENGTH_LONG).show()
                                //toastLong("Por favor tome correctamente la fotografía")
                                binding.tvEdadBio.setText("")
                                binding.ivAge.setImageBitmap(null)
                            }
                        }

                        override fun onSmileDetected(smileDetected: Boolean) {
                        }

                        override fun onErrorDetected(errorDetected: String) {

                        }

                    }).detectAgeIn(ImageBitmap!!)
                }catch (e:IOException){
                    e.printStackTrace()
                }*/
            }catch (e: IOException){
                e.printStackTrace()
            }
        }else {
            Toast.makeText(requireContext(), "No se han tomado fotos", Toast.LENGTH_LONG).show()
            //toastLong("No se han tomado fotos")
        }
    }

    private fun processImage(bitmap: Bitmap) {
        // Configurar la detección de rostros
        val image = InputImage.fromBitmap(bitmap, 0)

        val options = FaceDetectorOptions.Builder()
            //.setTrackingEnabled(false)
            .build()

        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val width = face.boundingBox.width().toInt()
                    val height = face.boundingBox.height().toInt()
                    if (width < 70 || height < 70) {
                        Toast.makeText(requireContext(), "Por favor debe acercarse más a la cámara al tomar la foto", Toast.LENGTH_LONG).show()
                    }else{
                    adjustAndShowFace(bitmap, face.boundingBox)
                    }

                } else {
                    binding.tvEdadBio.text = "No se detectó rostro"
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Error: ${e.message}")
            }
    }

    private fun adjustAndShowFace(bitmap: Bitmap, boundingBox: Rect) {
        // Asegurarse de que la caja delimitadora esté dentro de los límites de la imagen
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            abs(boundingBox.left),
            abs(boundingBox.top),
            boundingBox.width(),
            boundingBox.height()
        )

        BiometricUtils(object : BiometricUtilsListener{
            override fun onAgeDetected(biologicalAgeDetected: Int) {
                if (biologicalAgeDetected != 0){
                    binding.ivAge.setImageBitmap(croppedBitmap)
                    val edadText = "Su edad biológica identificada es de"
                    val edadText1 = "años aproximadamente"
                    binding.tvEdadBio.setText(edadText + " " + biologicalAgeDetected + " " + edadText1)
                    if(biologicalAgeDetected > 18){
                        Toast.makeText(requireContext(), "Usted es mayor de edad", Toast.LENGTH_LONG).show()
                    }else{
                        Toast.makeText(requireContext(), "Usted es menor de edad", Toast.LENGTH_LONG).show()
                    }
                }else{
                    Toast.makeText(requireContext(), "Por favor tome correctamente la fotografía", Toast.LENGTH_LONG).show()
                    binding.tvEdadBio.setText("")
                    binding.ivAge.setImageBitmap(null)
                }
            }

            override fun onSmileDetected(smileDetected: Boolean) {

            }

            override fun onErrorDetected(errorDetected: String) {

            }

        }).detectAgeIn(ImageBitmap!!)


        // Ajustar la imagen recortada en el ImageView
        //binding.tvEdadBio.text = "Rostro detectado"
    }


    fun rotateImage (uri: Uri): Bitmap{
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        val matrix = Matrix()
        matrix.postRotate(270f)
        val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        return rotatedBitmap
    }
}
