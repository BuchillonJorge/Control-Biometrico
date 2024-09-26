package es.anfac.dniereader.ui.fragments

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.google.android.gms.common.util.IOUtils
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import es.anfac.dniereader.R
import es.anfac.dniereader.databinding.FragmentEntryBinding
import es.anfac.dniereader.managers.CryptoManager
import es.anfac.dniereader.managers.PreferenceManager
import es.anfac.dniereader.models.QRData
import es.anfac.dniereader.utiles.QREncoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class EntryFragment : DialogFragment (){
    private lateinit var binding: FragmentEntryBinding

    var initQR = false

    override fun getTheme(): Int {
        return R.style.FullScreenDialogTheme
    }

    private var borderWidth = 4
    private var currentPixelX = 0
    private var currentPixelY = 0

    private var qrWidth by Delegates.notNull<Int>()
    private var qrHeight by Delegates.notNull<Int>()

    // Variables para dibujar de blanco los cuatros bordes del codigo QR
    private var borderStartX1 = -1
    private var borderStartX2 = -1
    private var borderEndX1 = -1
    private var borderEndX2 = -1
    private var borderStartY1 = -1
    private var borderStartY2 = -1
    private var borderEndY1 = -1
    private var borderEndY2 = -1

    private var includeEmbeddings by Delegates.notNull<Boolean>()
    private var includeWhiteDots by Delegates.notNull<Boolean>()
    private lateinit var dni: String
    private lateinit var can: String
    private var userPhoto: Bitmap? = null
    private lateinit var embeddingEncoded: String
    private lateinit var qrDataEncodedBitmap: Bitmap
    private lateinit var qrDataJsonCipher: String

    private val FINDER_PATTERN_SIZE = 7
    private val CIRCLE_SCALE_DOWN_FACTOR = 21f / 30f

    // The Paints
    private lateinit var paintWhite: Paint
    private lateinit var paintBlack: Paint

     override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEntryBinding.inflate(inflater, container, false)
        dialog!!.setCancelable(true)
         return binding.root
    }

     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserData()

        qrWidth = getDimen()
        qrHeight = getDimen()

        includeEmbeddings = PreferenceManager.getIncludeEmbeddings(requireContext())
        includeWhiteDots = PreferenceManager.getIncludeWhiteDots(requireContext())

        paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
            //strokeWidth = 1f
        }
        paintBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            color = Color.BLACK
            style = Paint.Style.FILL
            //strokeWidth = 1f
        }

        binding.btnSaveEntry.setOnClickListener {
            dialog?.dismiss()
            saveEntryImage()
        }

        if (userPhoto == null)
           // binding.swIncludeEmbeddings.isEnabled = false
        //binding.swIncludeEmbeddings.isChecked =
            PreferenceManager.getIncludeEmbeddings(requireContext())
        /*binding.swIncludeEmbeddings.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->

            includeEmbeddings = checked
            PreferenceManager.saveIncludeEmbeddings(requireContext(), includeEmbeddings)
            if (PreferenceManager.getGenerateNewQrFormat(requireContext())) {
                generateQRCodeImage()

            } else {
                generateQR()
            }
        }*/

        val isNewQrFormat = PreferenceManager.getGenerateNewQrFormat(requireContext())
        binding.swIncludeWhiteDots.isEnabled = isNewQrFormat
        //binding.swGenerateNewQrFormat.isEnabled = isNewQrFormat
         binding.swGenerateNewQrFormat.isChecked = true
         binding.swIncludeWhiteDots.isEnabled = true
         binding.swIncludeWhiteDots.visibility = View.VISIBLE
         generateQRCodeImage()
         //binding.swGenerateNewQrFormat.isEnabled = true

        binding.swGenerateNewQrFormat.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            PreferenceManager.saveGenerateNewQrFormat(requireContext(), checked)

            binding.swIncludeWhiteDots.isEnabled = checked
            if (PreferenceManager.getGenerateNewQrFormat(requireContext())) {
                binding.swIncludeWhiteDots.visibility = View.VISIBLE
           //     binding.swIncludeEmbeddings.visibility = View.GONE
                generateQRCodeImage()
            } else {
                binding.swIncludeWhiteDots.visibility = View.GONE
             //   binding.swIncludeEmbeddings.visibility = View.VISIBLE
                generateQR()
            }
        }

        binding.swIncludeWhiteDots.isChecked = PreferenceManager.getIncludeWhiteDots(requireContext())
        binding.swIncludeWhiteDots.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->

            includeWhiteDots = checked
            PreferenceManager.saveIncludeWhiteDots(requireContext(), includeWhiteDots)
            if (PreferenceManager.getGenerateNewQrFormat(requireContext())) {
                generateQRCodeImage()
            } else {
                generateQR()
            }
        }
         generateQRCodeImage()

        //generateEntry()
    }

    private fun saveEntryImages() {

        var fileOutputStream : OutputStream
        var file : File?=null
        val root = Environment.getExternalStorageDirectory().absolutePath
        var myDir = File("$root/DemoApps")
        if (!myDir.exists()){
            myDir.mkdirs()
        }
        val fname = "Images."+System.currentTimeMillis()+".jpeg"
        file = File(myDir, fname)
        try {
            fileOutputStream = FileOutputStream(file)
            qrDataEncodedBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
            Toast.makeText(requireContext(), "Imagen guardada correctamente", Toast.LENGTH_LONG).show()
            fileOutputStream.flush()
            fileOutputStream.close()
            PreferenceManager.clearUserPref(requireContext())
            dismiss()
        }catch (e:IOException){
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_LONG).show()

        }


            /*
                    val timestamp =
                        SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).format(System.currentTimeMillis())
                    val imageName = "EntradaQR_$timestamp"
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "image/jpeg"
                    intent.putExtra(Intent.EXTRA_TITLE, imageName)

                    startActivityForResult(intent, REQUEST_CODE_SAVE_ENTRY_FILE_IMAGE)
              */  }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
            }

    @Throws(Exception::class)
    private fun saveEntryPDF(parentPath: String, name: String) {
        //val pdfFilePath = File(FileUtil.getMediaAppDirectory(requireContext()), "$name.pdf")
        val pdfFilePath = File(parentPath, "$name.pdf")
        val writer = PdfWriter(pdfFilePath)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        //setup document text font
        lateinit var font: PdfFont
        try {
            requireContext().assets.open("fonts/corporate_s_light.ttf").use { inStream ->
                val calibriFontData = IOUtils.toByteArray(inStream)

            }
        } catch (e: IOException) {
            font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
        }
        //val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
        document.setFontSize(20f).setBold().setFont(font)

        //setup document margins
        document.setMargins(
            MARGIN_TOP,
            MARGIN_HORIZONTAL,
            document.bottomMargin,
            MARGIN_HORIZONTAL
        )

        //add content
        val paragraph = Paragraph()


        val imageBytes = ByteArrayOutputStream()
        qrDataEncodedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, imageBytes)
        val qrImage = Image(ImageDataFactory.create(imageBytes.toByteArray()))
        qrImage.setHeight(125f)
        qrImage.setWidth(125f)
        qrImage.setFixedPosition(60f, 365f)
        document.add(qrImage)

        //close document
        document.close()

        // notify the creation of the PDF

        // Get the URI Path of file.
        val uriPdfPath = FileProvider.getUriForFile(
            requireContext(),
            requireContext().packageName + ".provider",
            pdfFilePath
        )

        // Start Intent to View PDF from the Installed Applications.
        val pdfOpenIntent = Intent(Intent.ACTION_VIEW)
        pdfOpenIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        pdfOpenIntent.clipData = ClipData.newRawUri("", uriPdfPath)
        pdfOpenIntent.setDataAndType(uriPdfPath, "application/pdf")
        pdfOpenIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        try {
            startActivity(pdfOpenIntent)
        } catch (activityNotFoundException: ActivityNotFoundException) {
        }
    }

    private fun loadUserData() {
        dni = PreferenceManager.getUserDNI(requireContext())
        if (dni.isNotEmpty()) {
            binding.etUserDNI.isEnabled = false
            binding.etUserDNI.setText(dni)
        }
        can = PreferenceManager.getUserCAN(requireContext())
        if (can.isNotEmpty()) {
            binding.etUserCAN.isEnabled = false
            binding.etUserCAN.setText(can)
        }
        userPhoto = PreferenceManager.getUserPhoto(requireContext())
        binding.ivUserPhoto.isClickable = false
        if (userPhoto == null) {
            binding.ivUserPhoto.setImageResource(R.drawable.ic_baseline_image_24)
        } else {
            binding.ivUserPhoto.setImageBitmap(userPhoto)
        }
    }

    private fun generateEntry() {

        val embedding = PreferenceManager.getRawEmbeddings(requireContext())
        embeddingEncoded = QREncoder.qrEmbeddingsEncode(dni, embedding)

        if (PreferenceManager.getGenerateNewQrFormat(requireContext())) {
            generateQRCodeImage()
        } else {
            generateQR()
        }
    }

    private fun getDimen(): Int {
        val manager = requireContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = manager.defaultDisplay
        val point = Point()
        display.getSize(point)
        val width = point.x
        val height = point.y
        var dimen = width.coerceAtMost(height)
        dimen = dimen * 3 / 4
        return dimen
    }

    private fun generateQR() {
        qrDataJsonCipher = getQrDataJsonCipher()

        var whiteCanvas: Canvas

        val writer = QRCodeWriter()
        try {
            val bitMatrix =
                writer.encode(qrDataJsonCipher, BarcodeFormat.QR_CODE, qrWidth, qrHeight)

            val foregroundBitmap: Bitmap = if (userPhoto != null)
                getResizedBitmap(userPhoto!!, qrWidth, qrHeight)
            else
                Bitmap.createBitmap(qrWidth, qrHeight, Bitmap.Config.RGB_565)

            if (userPhoto == null) {
                whiteCanvas = Canvas(foregroundBitmap)
                whiteCanvas.drawColor(Color.WHITE)
            }

            val backgroundBitmap = Bitmap.createBitmap(qrWidth, qrHeight, Bitmap.Config.RGB_565)
            whiteCanvas = Canvas(foregroundBitmap)
            whiteCanvas.drawColor(Color.WHITE)

            qrDataEncodedBitmap = bitmapOverlayToCenter(backgroundBitmap, foregroundBitmap)

            for (x in 0 until qrWidth) {
                currentPixelX = x
                for (y in 0 until qrHeight) {
                    currentPixelY = y
                    if (qrDataJsonCipher.length > 100) {
                        // crear un codigo QR estandar con fondo de color blanco
                        if (bitMatrix[x, y]) qrDataEncodedBitmap.setPixel(
                            currentPixelX,
                            currentPixelY,
                            Color.BLACK
                        ) else qrDataEncodedBitmap.setPixel(
                            currentPixelX,
                            currentPixelY,
                            Color.WHITE
                        )
                    } else {
                        // crear un codigo QR con la imagen de fondo
                        if (bitMatrix[x, y] && !initQR) { // se encontro el primer codigo a pintar de negro
                            initQR()
                            qrDataEncodedBitmap.setPixel(currentPixelX, currentPixelY, Color.BLACK)
                            if (!bitMatrix[x - 1, y]) { // si el de la izquierda no es negro, entonces va de blanco
                                qrDataEncodedBitmap.setPixel(
                                    currentPixelX - 1,
                                    currentPixelY,
                                    Color.WHITE
                                )
                            }
                            if (!bitMatrix[x - 2, y]) { // si el 2ble de la izquierda no es negro, entonces va de blanco
                                qrDataEncodedBitmap.setPixel(
                                    currentPixelX - 2,
                                    currentPixelY,
                                    Color.WHITE
                                )
                            }
                        } else {
                            if (x % 2 == 0 && y % 2 == 0) {
                                qrDataEncodedBitmap.setPixel(
                                    currentPixelX,
                                    currentPixelY,
                                    Color.WHITE
                                )
                            } else if (x % 2 != 0 && y % 2 != 0) {
                                qrDataEncodedBitmap.setPixel(
                                    currentPixelX,
                                    currentPixelY,
                                    Color.WHITE
                                )
                            }
                        }
                        if (initQR) {
                            if (currentPixelX >= borderStartX2 && currentPixelX <= borderEndX1 && currentPixelY >= borderStartY2 && currentPixelY <= borderEndY2) {
                                if (bitMatrix[x, y]) {
                                    qrDataEncodedBitmap.setPixel(
                                        currentPixelX,
                                        currentPixelY,
                                        Color.BLACK
                                    )
                                    if (!bitMatrix[x, y - 1]) { // el anterior no es negro, entonces va de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX,
                                            currentPixelY - 1,
                                            Color.WHITE
                                        )
                                    }
                                    if (!bitMatrix[x, y - 2]) { // el 2ble anterior no es negro, entonces va de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX,
                                            currentPixelY - 2,
                                            Color.WHITE
                                        )
                                    }
                                    if (!bitMatrix[x - 1, y]) { // si el de la izquierda no es negro, entonces va de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX - 1,
                                            currentPixelY,
                                            Color.WHITE
                                        )
                                    }
                                    if (!bitMatrix[x - 2, y]) { // si el 2ble de la izquierda no es negro, entonces va de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX - 2,
                                            currentPixelY,
                                            Color.WHITE
                                        )
                                    }
                                } else {
                                    if (bitMatrix[x, y - 1]) { // si el anterior es negro, entonces voy de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX,
                                            currentPixelY,
                                            Color.WHITE
                                        )
                                    }
                                    if (bitMatrix[x, y - 2]) { // si el 2ble anterior es negro, enteonces voy de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX,
                                            currentPixelY,
                                            Color.WHITE
                                        )
                                    }
                                    if (bitMatrix[x - 1, y]) { // si el de la izquierda es negro, entonces voy de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX,
                                            currentPixelY,
                                            Color.WHITE
                                        )
                                    }
                                    if (bitMatrix[x - 2, y]) { // si el 2ble de la izquierda es negro, entonces voy de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX,
                                            currentPixelY,
                                            Color.WHITE
                                        )
                                    }
                                    if (bitMatrix[x + 1, y]) { // si el de la derecha es negro, entonces voy de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX,
                                            currentPixelY,
                                            Color.WHITE
                                        )
                                    }
                                    if (bitMatrix[x + 2, y]) { // si el 2ble de la derecha es negro, entonces voy de blanco
                                        qrDataEncodedBitmap.setPixel(
                                            currentPixelX,
                                            currentPixelY,
                                            Color.WHITE
                                        )
                                    }
                                }
                            } else {
                                if (x % 2 == 0 && y % 2 == 0) {
                                    qrDataEncodedBitmap.setPixel(
                                        currentPixelX,
                                        currentPixelY,
                                        Color.WHITE
                                    )
                                } else if (x % 2 != 0 && y % 2 != 0) {
                                    qrDataEncodedBitmap.setPixel(
                                        currentPixelX,
                                        currentPixelY,
                                        Color.WHITE
                                    )
                                }
                            }
                        }
                    }
                }
            }

            binding.ivEntryQR.setImageBitmap(qrDataEncodedBitmap)
            binding.ivEntryQR.invalidate()
        } catch (e: WriterException) {
        }
    }

    private fun getQrDataJsonCipher(): String {
        val gson = Gson()
        val qrData = if (includeEmbeddings)
            QRData(dni, can, embeddingEncoded)
        else
            QRData(dni, can, "")
        val qrDataJson = gson.toJson(qrData)
        return CryptoManager.cipherVigenere(qrDataJson)
    }

    private fun initQR() {
        initQR = true
        if (currentPixelX < borderWidth) {
            borderWidth = (currentPixelX / 2.0).roundToInt()
        }
        borderStartX1 = currentPixelX - borderWidth
        borderStartX2 = currentPixelX
        borderEndX1 = qrWidth - currentPixelX
        borderEndX2 = borderEndX1 + borderWidth
        borderStartY1 = currentPixelY - borderWidth
        borderStartY2 = currentPixelY
        borderEndY1 = qrHeight - currentPixelY
        borderEndY2 = borderEndY1 + borderWidth

        //paintQrBorders();
    }

    private fun generateQRCodeImage() {
        qrDataJsonCipher = getQrDataJsonCipher()

        val encodingHints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
        encodingHints[EncodeHintType.CHARACTER_SET] = Charsets.UTF_8

        try {
            val code: QRCode =
                Encoder.encode(qrDataJsonCipher, ErrorCorrectionLevel.H, encodingHints)
            val image: Bitmap = renderQRImage(code, qrWidth.toFloat(), qrHeight.toFloat(), 4)

            binding.ivEntryQR.setImageBitmap(image)
            binding.ivEntryQR.invalidate()
        } catch (e: Exception) {
            includeEmbeddings = false
        }

    }

    private fun renderQRImage(code: QRCode, width: Float, height: Float, quietZone: Int): Bitmap {
        var whiteCanvas: Canvas

        val inputByteMatrix: ByteMatrix = code.matrix ?: throw IllegalStateException()

        val inputWidth = inputByteMatrix.width.toFloat()
        val inputHeight = inputByteMatrix.height.toFloat()

        val qrWidth = inputWidth + quietZone * 2
        val qrHeight = inputHeight + quietZone * 2

        val outputWidth = max(width, qrWidth)
        val outputHeight = max(height, qrHeight)

        val multiple = min(outputWidth / qrWidth, outputHeight / qrHeight)

        val leftPadding = (outputWidth - inputWidth * multiple) / 2
        val topPadding = (outputHeight - inputHeight * multiple) / 2

        val circleSize = multiple * CIRCLE_SCALE_DOWN_FACTOR


        val foregroundBitmap: Bitmap = if(userPhoto != null) {
            getResizedBitmap(userPhoto!!, (width - leftPadding * 2).toInt(), (height - topPadding * 2).toInt())
        } else {
            Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.RGB_565)
        }
        if (userPhoto == null) {
            whiteCanvas = Canvas(foregroundBitmap)
            whiteCanvas.drawColor(Color.WHITE)
        }

        val backgroundBitmap =
            Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.RGB_565)
        whiteCanvas = Canvas(backgroundBitmap)
        whiteCanvas.drawColor(Color.WHITE)

        // QR with ovals
        qrDataEncodedBitmap = bitmapOverlayToCenter(backgroundBitmap, foregroundBitmap)
        val canvasOval = Canvas(qrDataEncodedBitmap)

        // Render QR with OVAL
        var inputY = 0
        var outputY = topPadding
        while (inputY < inputHeight) {
            var inputX = 0
            var outputX = leftPadding
            while (inputX < inputWidth) {
                if (inputByteMatrix.get(inputX, inputY).toInt() == 1) {
                    if (!(inputX <= FINDER_PATTERN_SIZE && inputY <= FINDER_PATTERN_SIZE || inputX >= inputWidth - FINDER_PATTERN_SIZE && inputY <= FINDER_PATTERN_SIZE || inputX <= FINDER_PATTERN_SIZE && inputY >= inputHeight - FINDER_PATTERN_SIZE)) {
                        canvasOval.drawOval(
                            outputX,
                            outputY,
                            outputX + circleSize,
                            outputY + circleSize,
                            paintBlack
                        )
                    }
                } else {
                    if (includeWhiteDots && (inputX >= FINDER_PATTERN_SIZE && inputY >= FINDER_PATTERN_SIZE || inputX <= inputWidth - FINDER_PATTERN_SIZE && inputY >= FINDER_PATTERN_SIZE || inputX >= FINDER_PATTERN_SIZE && inputY <= inputHeight - FINDER_PATTERN_SIZE)) {
                        canvasOval.drawOval(
                            outputX,
                            outputY,
                            outputX + circleSize,
                            outputY + circleSize,
                            paintWhite
                        )
                    }
                }
                inputX++
                outputX += multiple
            }
            inputY++
            outputY += multiple
        }
        val circleDiameter = multiple * FINDER_PATTERN_SIZE
        // circulo ezquina superior izquierda - OK
        drawFinderPatternOvalStyle(
            canvasOval,
            leftPadding,
            topPadding,
            circleDiameter
        )
        // circulo ezquina superior derecha
        drawFinderPatternOvalStyle(
            canvasOval,
            leftPadding + (inputWidth - FINDER_PATTERN_SIZE) * multiple,
            topPadding,
            circleDiameter
        )
        // circulo ezquina inferior izquierda
        drawFinderPatternOvalStyle(
            canvasOval,
            leftPadding,
            topPadding + (inputHeight - FINDER_PATTERN_SIZE) * multiple,
            circleDiameter
        )

        return qrDataEncodedBitmap
    }

    private fun drawFinderPatternOvalStyle(
        canvasOval: Canvas,
        x: Float,
        y: Float,
        circleDiameter: Float
    ) {
        val WHITE_CIRCLE_DIAMETER = circleDiameter * 5 / 7
        val WHITE_CIRCLE_OFFSET = circleDiameter / 7
        val MIDDLE_DOT_DIAMETER = circleDiameter * 3 / 7
        val MIDDLE_DOT_OFFSET = circleDiameter * 2 / 7
        canvasOval.drawOval(x, y, x + circleDiameter, y + circleDiameter, paintBlack) // black
        canvasOval.drawOval(
            x + WHITE_CIRCLE_OFFSET,
            y + WHITE_CIRCLE_OFFSET,
            x + WHITE_CIRCLE_OFFSET + WHITE_CIRCLE_DIAMETER,
            y + WHITE_CIRCLE_OFFSET + WHITE_CIRCLE_DIAMETER,
            paintWhite
        ) // white
        canvasOval.drawOval(
            x + MIDDLE_DOT_OFFSET,
            y + MIDDLE_DOT_OFFSET,
            x + MIDDLE_DOT_OFFSET + MIDDLE_DOT_DIAMETER,
            y + MIDDLE_DOT_OFFSET + MIDDLE_DOT_DIAMETER,
            paintBlack
        ) // black
    }

    private fun paintQrBorders() {
        // Para dibujar el borde de la izquierda
        for (x in borderStartX1 until borderStartX2) {
            for (y in borderStartY1 until borderEndY2) {
                qrDataEncodedBitmap.setPixel(x, y, Color.WHITE)
            }
        }
        // Para dibujar el borde de arriba
        for (x in borderStartX1 until borderEndX2) {
            for (y in borderStartY1 until borderStartY2) {
                qrDataEncodedBitmap.setPixel(x, y, Color.WHITE)
            }
        }
        // Para dibujar el borde de la derecha
        for (x in borderEndX1 until borderEndX2) {
            for (y in borderStartY1 until borderEndY2) {
                qrDataEncodedBitmap.setPixel(x, y, Color.WHITE)
            }
        }
        // Para dibujar el borde de abajo
        for (x in borderStartX1 until borderEndX2) {
            for (y in borderEndY1 until borderEndY2) {
                qrDataEncodedBitmap.setPixel(x, y, Color.WHITE)
            }
        }
    }

    private fun paintFirstWhiteColumn(firstBlackX: Int, firstBlackY: Int, height: Int) {
        if (firstBlackX < borderWidth) {
            borderWidth = (firstBlackX / 2.0).roundToInt()
        }
        val firstWhiteX: Int = firstBlackX - borderWidth
        if (firstBlackY < borderWidth) {
            borderWidth = (firstBlackY / 2.0).roundToInt()
        }
        val firstWhiteY: Int = firstBlackY - borderWidth
        val lastWhiteY: Int = height - firstBlackY + borderWidth
        for (x in firstWhiteX until firstBlackX) {
            for (y in firstWhiteY until lastWhiteY) {
                qrDataEncodedBitmap.setPixel(x, y, Color.BLUE)
            }
        }
    }

    private fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        //bm.recycle()
        return resizedBitmap
    }

    private fun bitmapOverlayToCenter(backgroundBitmap: Bitmap, overlayBitmap: Bitmap): Bitmap {
        val bitmap1Width = backgroundBitmap.width
        val bitmap1Height = backgroundBitmap.height
        val bitmap2Width = overlayBitmap.width
        val bitmap2Height = overlayBitmap.height
        val marginLeft = (bitmap1Width * 0.5 - bitmap2Width * 0.5).toFloat()
        val marginTop = (bitmap1Height * 0.5 - bitmap2Height * 0.5).toFloat()
        val finalBitmap = Bitmap.createBitmap(bitmap1Width, bitmap1Height, backgroundBitmap.config)
        val canvas = Canvas(finalBitmap)
        canvas.drawBitmap(backgroundBitmap, Matrix(), null)
        canvas.drawBitmap(overlayBitmap, marginLeft, marginTop, null)
        return finalBitmap
    }

    companion object {
        private const val MARGIN_TOP: Float = 95f
        private const val MARGIN_HORIZONTAL: Float = 45f
        private const val TICKET_WIDTH: Float = 400f
        private const val TICKET_HEIGHT: Float = 600f
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_SAVE_ENTRY_FILE_IMAGE = 11
        private const val REQUEST_CODE_SAVE_ENTRY_FILE_PDF = 12
    }


    private fun saveEntryImage() {
        qrDataEncodedBitmap?.let { bitmap ->
            val appName = requireActivity().applicationInfo.name
            val collection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val contentValues = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "Images_${System.currentTimeMillis()}.jpeg"
                )
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }

            requireContext().contentResolver.apply {
                val uri = insert(collection, contentValues)
                uri?.let {
                    openOutputStream(uri).use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it!!)
                    }
                }
            }

            Toast.makeText(requireContext(), "Imagen guardada correctamente", Toast.LENGTH_LONG).show()
            PreferenceManager.clearUserPref(requireContext())
            dismiss()
        }
    }

}