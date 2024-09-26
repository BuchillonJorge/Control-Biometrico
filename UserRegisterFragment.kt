package es.anfac.dniereader.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.journeyapps.barcodescanner.CameraPreview
import es.anfac.dniereader.R
import es.anfac.dniereader.biometric.faceauth.core.ProcessImageAndDrawResults
import es.anfac.dniereader.biometric.faceauth.core.VideoSource
import es.anfac.dniereader.databinding.FragmentUserRegisterBinding
import es.anfac.dniereader.managers.PreferenceManager
import java.io.File
import java.io.IOException


class UserRegisterFragment : DialogFragment () {

    private var currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var mPreview: VideoSource? = null
    private var mDraw : ProcessImageAndDrawResults? = null
    private lateinit var photoFile: String
    private val MIN_FACE_SIZE = 0.2f
    private val REQUEST_IMAGE_CAPTURE = 5

    private lateinit var preview: CameraPreview
    private lateinit var camera: Camera
    private lateinit var imageView: ImageView
    private lateinit var faceDetector: FaceDetector
    private var isTakingPicture = false


    private lateinit var archivo: File

    private val PICK_IMAGE_REQUEST = 1

    private lateinit var binding: FragmentUserRegisterBinding

    private var isCameraPermissionGranted = false


    private var userCropPhoto: Bitmap? = null

    override fun getTheme(): Int {
        return R.style.FullScreenDialogTheme
    }

    private val requestCameraPermissions = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val onCloseAlert = Runnable {
            requestCameraPermission()
        }
        when {
            isGranted -> openCameraDetectors()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> alert(
                onCloseAlert,
                "La aplicación necesita este permito para acceder a la cámara."
            )

            else ->Toast.makeText(requireContext(), "No se han concedido los permisos necesarios.", Toast.LENGTH_LONG).show()
                //toastLong("No se han concedido los permisos necesarios.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUserRegisterBinding.inflate(inflater, container, false)
        dialog?.setCancelable(true)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /*val dni = PreferenceManager.getUserDNI(requireContext())
        if (dni.isNotEmpty()) {
            binding.etGameUserRegisterDNI.setText(dni)
        }*/
        binding.etGameUserRegisterDNI.doOnTextChanged { _, _, _, _ ->
            checkFormValidAndShowTheButton()
        }

        /*val can = PreferenceManager.getUserCAN(requireContext())
        if (can.isNotEmpty()) {
            binding.etGameUserRegisterCAN.setText(can)
        }*/
        binding.etGameUserRegisterCAN.doOnTextChanged { text, _, _, _ ->
            checkFormValidAndShowTheButton()
            if (text!!.length > CAN_LENGTH) {
                binding.textInputUserRegisterCAN.error = "No más caracteres"
            } else {
                PreferenceManager.saveUserCAN(
                    requireContext(),
                    binding.etGameUserRegisterCAN.text.toString()
                )
                binding.textInputUserRegisterCAN.error = null
                if (text.length == CAN_LENGTH) {
                    binding.textInputUserRegisterCAN.endIconDrawable =
                        requireContext().getDrawable(R.drawable.ic_success)
                } else {
                    binding.textInputUserRegisterCAN.endIconDrawable =
                        requireContext().getDrawable(R.drawable.ic_cancel)
                }
            }
        }
        binding.textInputUserRegisterCAN.setEndIconOnClickListener {
            if (binding.etGameUserRegisterCAN.text?.length != CAN_LENGTH)
                binding.etGameUserRegisterCAN.setText("")
        }

        /*val userPhoto = PreferenceManager.getUserPhoto(requireContext())
        if (userPhoto == null) {
            binding.ivUserRegisterPhoto.setImageResource(R.drawable.ic_baseline_image_24)
            binding.ivUserRegisterPhoto.isClickable = true
        } else {
            binding.ivUserRegisterPhoto.setImageBitmap(userPhoto)
            binding.ivUserRegisterPhoto.isClickable = false
        }*/

        binding.ivUserRegisterPhoto.setOnClickListener {
            openOptionsUserPhotoDialog()
        }

        binding.btnEndFragment.setOnClickListener { c: View? ->
            dialog?.dismiss()
        }

        binding.btnGoToQrFragment.setOnClickListener { v: View? ->
            if (isFormValid()) {
                PreferenceManager.saveUserDNI(
                    requireContext(),
                    binding.etGameUserRegisterDNI.text.toString().trim()
                )
                PreferenceManager.saveUserCAN(
                    requireContext(),
                    binding.etGameUserRegisterCAN.text.toString().trim()
                )
                /*if (userCropPhoto != null)

                    PreferenceManager.saveUserPhoto(requireContext(), userCropPhoto)*/

                dialog!!?.dismiss()
                val EntryFragment = EntryFragment()
                EntryFragment.show(parentFragmentManager, "EntryFragment")
                Toast.makeText(requireContext(),"Generando Código QR",Toast.LENGTH_LONG).show()
                //toastLong("Generando Código QR")
            } else {
                Toast.makeText(requireContext(),"Datos incorrectos",Toast.LENGTH_LONG).show()
                //toastLong("Datos incorrectos")
            }
        }

        checkFormValidAndShowTheButton()
    }


    override fun onResume() {
        super.onResume()
        checkFormValidAndShowTheButton()
    }

    private fun checkFormValidAndShowTheButton() {
        if (binding.etGameUserRegisterDNI.text?.isNotEmpty() == true && binding.etGameUserRegisterCAN.text?.length == CAN_LENGTH) {
            binding.btnGoToQrFragment.visibility = View.VISIBLE
        } else {
            binding.btnGoToQrFragment.visibility = View.INVISIBLE
        }
    }

    private fun openOptionsUserPhotoDialog() {
        val optionsUserPhotoDialog = Dialog(requireContext())
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(optionsUserPhotoDialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        optionsUserPhotoDialog.window?.attributes = lp

        optionsUserPhotoDialog.setCancelable(false)
        optionsUserPhotoDialog.setCanceledOnTouchOutside(false)

        optionsUserPhotoDialog.setContentView(R.layout.option_camera_galery_dialog_qr)

        val btnPhotoCamera =
            optionsUserPhotoDialog.findViewById<MaterialButton>(R.id.btnPhotoCamera)
        val btnPhotoGallery =
            optionsUserPhotoDialog.findViewById<MaterialButton>(R.id.btnPhotoGallery)
        val tvOptionsTitle = optionsUserPhotoDialog.findViewById<TextView>(R.id.tvOptionsTitle)

        tvOptionsTitle.setText("Opciones de Captura")
        btnPhotoCamera.setOnClickListener {
            optionsUserPhotoDialog.dismiss()
            checkPermissionsAndOpenCamera()
        }
        btnPhotoGallery.setOnClickListener {
            optionsUserPhotoDialog.dismiss()
            openGalleryas()
        }

        optionsUserPhotoDialog.show()
    }

    private fun isFormValid(): Boolean {
        return binding.etGameUserRegisterDNI.text.toString()
            .isNotEmpty() &&
                binding.etGameUserRegisterCAN.text.toString()
                    .isNotEmpty() && binding.etGameUserRegisterCAN.text.toString().length == CAN_LENGTH
    }

    private fun requestCameraPermission() {
        requestCameraPermissions.launch(Manifest.permission.CAMERA)
    }

    private fun checkPermissionsAndOpenCamera() {
        isCameraPermissionGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!isCameraPermissionGranted)
            requestCameraPermission()
        else
            openCameraDetectors()
    }

    /*private fun openCameraDetector() {
        startForResult.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))

        //findNavController(requireView()).navigate(R.id.action_userRegisterFragment_to_detectorFragment)
    }
*/
    private fun alert(callback: Runnable?, message: String?) {
        val dialog = AlertDialog.Builder(
            requireContext()
        )
        dialog.setMessage(message)
        dialog.setNegativeButton(
            "Ok"
        ) { dialog1, _ -> dialog1.dismiss() }
        if (callback != null) {
            dialog.setOnDismissListener { callback.run() }
        }
        dialog.show()
    }

    companion object {
        private const val DNI_LENGTH = 9
        private const val CAN_LENGTH = 6
    }

    private fun openCameraDetectors() {
        //   startForResult.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->


            val takePictureIntent =
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                    takePictureIntent.resolveActivity(requireActivity().packageManager)?.also {
                        //startActivityForResults(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                        startForResults.launch(takePictureIntent)
                    }
                }

            //findNavController(requireView()).navigate(R.id.action_userRegisterFragment_to_detectorFragment)
        }
    }


    private val startForResults =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result:
                                                                                      ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data?.extras?.get("data") as Bitmap
                try {
                    proceImages(intent)
                }catch (e:IOException){
                    Log.e("TomarFoto", "Error + ${e.message}")
                }

            }
        }
    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result:
                                                                                      ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data?.extras?.get("data") as Bitmap
                try {

                    proceImagess(intent)

                } catch (e: IOException) {

                }
            }
        }

    @SuppressLint("SuspiciousIndentation")
    private fun proceImages(image: Bitmap) {
        val imageInput = InputImage.fromBitmap(image, 0)
        val detector = FaceDetection.getClient()

        detector.process(imageInput)
            .addOnSuccessListener { faces ->
                if (!faces.isEmpty()){
                for (face in faces) {
                    val bounds = face.boundingBox
                    val width = bounds.width().toInt()
                    val height = bounds.height().toInt()

                        if (width<70 || height < 70) {
                            Toast.makeText(requireContext(),"Por favor debe acercarse más a la cámara al tomar la foto",Toast.LENGTH_LONG).show()
                        //toastLong("Por favor debe acercarse más a la cámara al tomar la foto")
                    } else {
                        val croppedFace = Bitmap.createBitmap(
                            image,
                            bounds.left,
                            bounds.top,
                            bounds.width(),
                            bounds.height()
                        )

                        binding.ivUserRegisterPhoto.setImageBitmap(croppedFace)
                        binding.ivUserRegisterPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP)

                        userCropPhoto = croppedFace
                        if (userCropPhoto != null) {
                            PreferenceManager.saveUserPhoto(requireContext(), userCropPhoto)
                        }
                    }
                }
            }else{
                    Toast.makeText(requireContext(),"No se identificaron rostros",Toast.LENGTH_LONG).show()
                //toastLong("No se identificaron rostros")
            }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Error: ${e.message}")
            }
    }

    private fun openGalleryas() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Selecciona una imagen"),
            PICK_IMAGE_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            val imageUri = data.data
            processImagen(imageUri)
        }
    }


    @SuppressLint("SuspiciousIndentation")
    private fun processImagen(uri: Uri?) {
        if (uri != null) {
            val bitmap = UriToBitmap(uri)
            if (bitmap != null) {
                detectFacess(bitmap)
            }
        }
    }

    private fun UriToBitmap(uri: Uri): Bitmap? {
        val options = BitmapFactory.Options()

        options.inJustDecodeBounds = true
        val scaleFactor = Math.max(
            1,
            Math.max(
                options.outWidth / binding.ivUserRegisterPhoto.width,
                options.outHeight / binding.ivUserRegisterPhoto.height
            )
        )
        options.inJustDecodeBounds = false
        options.inSampleSize = scaleFactor
        var bitmap = BitmapFactory.decodeStream(
            requireContext().contentResolver.openInputStream(uri),
            null,
            options
        )


        return bitmap
    }


    @SuppressLint("SuspiciousIndentation")
    private fun detectFacess(bitmap: Bitmap) {
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)

        // Configuración del detector de caras
        val options = com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
            .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(com.google.mlkit.vision.face.FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        val detector = com.google.mlkit.vision.face.FaceDetection.getClient(options)
        val rotaciones = listOf(0f, 90f, 180f, 270f)

        // Recorrer las rotaciones hasta encontrar un rostro
        var encontrado = false

        fun detectarCara(index:Int){

            if (index>=rotaciones.size){
                return
            }

        //for (rotacion in rotaciones) {

                val matrix = Matrix()
                matrix.postRotate(rotaciones[index])

                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )


                val imagen =
                    com.google.mlkit.vision.common.InputImage.fromBitmap(rotatedBitmap, 0)

                detector.process(imagen)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            encontrado = true
                            val face: com.google.mlkit.vision.face.Face =
                                faces[0] // Solo procesamos la primera cara
                            val boundingBox = face.boundingBox
                            val croppedBitmap = Bitmap.createBitmap(
                                rotatedBitmap,
                                // bitmap,
                                boundingBox.left,
                                boundingBox.top,
                                boundingBox.width(),
                                boundingBox.height()
                            )
                            try {
                                binding.ivUserRegisterPhoto.setImageBitmap(croppedBitmap)

                                userCropPhoto = croppedBitmap
                                if (userCropPhoto != null) {
                                    PreferenceManager.saveUserPhoto(requireContext(), userCropPhoto)
                                }
                            } catch (e: IOException) {

                            }
                            /*ajustarImagenw(
                                croppedBitmap
                            )
                        )
                        userCropPhoto = ajustarImagenw(croppedBitmap)
                        if (userCropPhoto != null) {
                            PreferenceManager.saveUserPhoto(requireContext(), userCropPhoto)
                        }*/

                            //}
                            /*else {
                        toastLong("No se encontró rostro")
                    }*/

                        }else{
                            detectarCara(index+1)
                        }
                    }.addOnCompleteListener {
                        if (it.isSuccessful && encontrado) {
                            //return@addOnCompleteListener
                        }
                    }
            }
        detectarCara(0)
    }

private fun encontrado (){
    Toast.makeText(requireContext(), "Rostro encontrado", Toast.LENGTH_LONG).show()
}

    private fun ajustarImagenw(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scaleWidth = binding.ivUserRegisterPhoto.width.toFloat() / width
        val scaleHeight = binding.ivUserRegisterPhoto.height.toFloat() / height
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)
    }



    private fun proceImagess(image: Bitmap) {
        val imageInput = InputImage.fromBitmap(image, 0)
        val detector = FaceDetection.getClient()

        detector.process(imageInput)
            .addOnSuccessListener { faces ->
                if (!faces.isEmpty()){
                for (face in faces) {
                    val bounds = face.boundingBox
                    val croppedFace = Bitmap.createBitmap(
                        image,
                        bounds.left,
                        bounds.top,
                        bounds.width(),
                        bounds.height()
                    )

                    // Ajustar el tamaño del ImageView para que no se pierda resolución
                    val width = binding.ivUserRegisterPhoto.width
                    val height = binding.ivUserRegisterPhoto.height
                    val ratio = Math.min(
                        width.toDouble() / croppedFace.width,
                        height.toDouble() / croppedFace.height
                    )
                    val newWidth = (croppedFace.width * ratio).toInt()
                    val newHeight = (croppedFace.height * ratio).toInt()
                    val resizedFace =
                        Bitmap.createScaledBitmap(croppedFace, newWidth, newHeight, true)

                    binding.ivUserRegisterPhoto.setImageBitmap(resizedFace)
                    binding.ivUserRegisterPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP)

                    userCropPhoto = resizedFace
                    if (userCropPhoto != null) {
                        PreferenceManager.saveUserPhoto(requireContext(), userCropPhoto)
                    }
                }
            }else{
                Toast.makeText(requireContext(), "No se detectaron rostros", Toast.LENGTH_LONG).show()
            }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Error: ${e.message}")
            }
    }
}