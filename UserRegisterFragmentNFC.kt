package es.anfac.dniereader.ui.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import es.anfac.dniereader.R
import es.anfac.dniereader.camera.AbstractScanActivity
import es.anfac.dniereader.camera.ScanResultManager
import es.anfac.dniereader.camera.ScannerFullActivity
import es.anfac.dniereader.databinding.FragmentUserRegisterNfcBinding
import es.anfac.dniereader.managers.PreferenceManager
import es.anfac.dniereader.utiles.toastLong


class UserRegisterFragmentNFC() : DialogFragment() {
    private lateinit var binding: FragmentUserRegisterNfcBinding

    private lateinit var nfcAdapter: NfcAdapter
    private var dniNumber: String = ""
    private var can: String = ""
    private var userFace: Bitmap? = null

    override fun getTheme(): Int {
        return R.style.FullScreenDialogTheme
    }

    enum class Function {
        QR_DNI, QR_GALLERIA, QR_CAMERA, QR_FORM
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUserRegisterNfcBinding.inflate(inflater, container, false)
        dialog?.setCancelable(true)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //binding.btnAceptar.setOnClickListener {
          //  val can = binding.etCan.text.toString()
            //PreferenceManager.saveUserCAN(requireContext(), binding.etCan.text.toString())
            //if (can.isNotEmpty()) {
                // Cerrar el diálogo
                //dismiss()

                // Solicitar la foto del DNI
                tomarFotoDni()

            //}

    //    }
    }

    private fun tomarFotoDni() {
        val intent = Intent(requireContext(), ScannerFullActivity::class.java)
        intent.putExtra(AbstractScanActivity.SHOW_CAN_DIALOG, true)
        intent.putExtra(AbstractScanActivity.USE_CAMERA, false)
            //intent.putExtra(AbstractScanActivity.USE_NFC, false)
        launcher.launch(intent)
    }

                        private val launcher =
                            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                                if (result.resultCode == Activity.RESULT_OK) {
                                    //val data = result.data
                                    // procesar el resultado
                                    val resultData = ScanResultManager.getData()
                                    //ScanResultManager.clearData()

                                    if (resultData != null) {
                                        dniNumber = resultData.rfidData.document_number
                                        userFace = resultData.images.rfidPhoto
                                        can = resultData.mrzData.can

                                        if (!dniNumber.isNullOrEmpty()) {
                                            PreferenceManager.saveUserDNI(requireContext(), dniNumber)
                                            binding.tvdni.setText(dniNumber)
                                        }
                                        if (!can.isNullOrEmpty()){
                                            PreferenceManager.saveUserCAN(requireContext(), can)
                                        }
                                        if (userFace != null) {
                                            PreferenceManager.saveUserPhoto(requireContext(), userFace)
                                            binding.imageView.setImageBitmap(userFace)
                                        }
                                        Log.d("DNI", "Número de DNI: $dniNumber")
                                        Log.d("DNI", "Rostro del usuario: $userFace")
                                        Log.d("DNI", "CAN: $can")
                                        val EntryFragment = EntryFragment()
                                        EntryFragment.show(parentFragmentManager, "EntryFragment")
                                        toastLong("Generando código QR")
                                    } else {
                                        toastLong("Fallo en la extracción de DNI y rostro")
                                        Log.d("DNI", "No se han obtenido datos")
                                    }
                                }
                                toastLong("Enfoque correctamente su documento DNI")
                            }
                    }






