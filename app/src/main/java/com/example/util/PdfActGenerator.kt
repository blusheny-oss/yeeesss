package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.ChecklistItem
import com.example.data.model.HandoverAct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfActGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN_X = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_X * 2
    private const val FOOTER_Y = 812f

    suspend fun generateActPdf(
        context: Context,
        act: HandoverAct,
        items: List<ChecklistItem>,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "acts_pdf").apply { mkdirs() }
        val outFile = File(dir, "Akt_${act.id}.pdf")

        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = 44f

        val navyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(19, 42, 74)
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(19, 42, 74)
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(31, 41, 55)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }
        val fillHeaderPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        fun drawFooter(c: Canvas, pNum: Int) {
            c.drawLine(MARGIN_X, FOOTER_Y - 12f, PAGE_WIDTH - MARGIN_X, FOOTER_Y - 12f, linePaint)
            c.drawText(
                "АктЖилья • ID: ${act.id} • Простая ЭЦП (63-ФЗ) • Стр. $pNum",
                MARGIN_X,
                FOOTER_Y,
                mutedPaint
            )
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > FOOTER_Y - 22f) {
                drawFooter(canvas, pageNumber)
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 44f
            }
        }

        fun drawWrappedText(text: String, paint: TextPaint, width: Float = CONTENT_WIDTH, extraGap: Float = 6f) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .build()
            ensureSpace(layout.height + extraGap)
            canvas.save()
            canvas.translate(MARGIN_X, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + extraGap
        }

        val dateFormatted = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU")).format(Date())
        val rubFormat = NumberFormat.getIntegerInstance(Locale("ru", "RU"))

        // Header banner
        canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 58f, fillHeaderPaint)
        canvas.drawText("АКТ ПРИЕМА-ПЕРЕДАЧИ ЖИЛОГО ПОМЕЩЕНИЯ", MARGIN_X + 12f, y + 22f, navyPaint)
        canvas.drawText(
            "№ ${act.shareToken.ifBlank { act.id.takeLast(8).uppercase() }} от $dateFormatted",
            MARGIN_X + 12f,
            y + 39f,
            boldBodyPaint
        )
        val gpsSummary = if (act.latitude != 0.0 || act.longitude != 0.0) {
            String.format(Locale.US, "GPS: %.5f, %.5f", act.latitude, act.longitude)
        } else {
            "Геолокация: ${act.locationLabel.ifBlank { "подтверждена при фотофиксации" }}"
        }
        canvas.drawText(gpsSummary, MARGIN_X + 12f, y + 52f, mutedPaint)
        y += 72f

        // Property & Parties
        drawWrappedText("1. СТОРОНЫ И ОБЪЕКТ АРЕНДЫ", sectionPaint, extraGap = 4f)
        val aptSuffix = if (act.apartmentNumber.isNotBlank()) ", кв. ${act.apartmentNumber}" else ""
        drawWrappedText("Объект: ${act.address}$aptSuffix (${act.title})", boldBodyPaint, extraGap = 3f)
        drawWrappedText(
            "Арендодатель (Собственник): ${act.landlordName.ifBlank { "Не указан" }} • тел.: ${act.landlordPhone.ifBlank { "—" }}",
            bodyPaint,
            extraGap = 3f
        )
        drawWrappedText(
            "Наниматель (Арендатор): ${act.tenantName.ifBlank { "Не указан" }} • тел.: ${act.tenantPhone.ifBlank { "—" }}",
            bodyPaint,
            extraGap = 3f
        )
        drawWrappedText(
            "Обеспечительный платеж (Залог): ${rubFormat.format(act.depositAmountRub)} ₽ • Ежемесячная плата: ${rubFormat.format(act.monthlyRentRub)} ₽",
            boldBodyPaint,
            extraGap = 10f
        )

        // Legal clauses protecting deposit
        drawWrappedText("2. ЮРИДИЧЕСКИЕ УСЛОВИЯ И ЗАЩИТА ЗАЛОГА", sectionPaint, extraGap = 4f)
        drawWrappedText(
            "Настоящий Акт составлен в соответствии со ст. 671, 678, 681 ГК РФ и фиксирует фактическое техническое состояние помещения, отделки, мебели и бытовой техники на момент передачи ключей. Зафиксированные в разделе 4 недостатки, сколы и дефекты существовали до момента заселения; Наниматель не несет материальной ответственности за указанные дефекты и за естественный износ помещения при возврате обеспечительного платежа (залога).",
            bodyPaint,
            extraGap = 10f
        )

        // Utility Meters
        drawWrappedText("3. ПОКАЗАНИЯ ПРИБОРОВ УЧЕТА (СЧЕТЧИКИ ЖКУ)", sectionPaint, extraGap = 4f)
        drawWrappedText(
            "ХВС (холодная вода): ${act.coldWaterMeter.ifBlank { "—" }}   |   ГВС (горячая вода): ${act.hotWaterMeter.ifBlank { "—" }}",
            bodyPaint,
            extraGap = 3f
        )
        drawWrappedText(
            "Электроэнергия (кВт·ч): ${act.electricityMeter.ifBlank { "—" }}   |   Газ: ${act.gasMeter.ifBlank { "—" }}",
            bodyPaint,
            extraGap = 10f
        )

        // Checklist items
        drawWrappedText("4. ВЕДОМОСТЬ ОСМОТРА ПОМЕЩЕНИЯ И ДЕФЕКТОВ", sectionPaint, extraGap = 6f)
        items.forEachIndexed { idx, item ->
            val statusLabel = when (item.condition) {
                ChecklistItem.CONDITION_OK -> "[ИСРАВНО]"
                ChecklistItem.CONDITION_DEFECT -> "[ДЕФЕКТ]"
                ChecklistItem.CONDITION_NOTE -> "[ЗАМЕЧАНИЕ]"
                else -> "[НЕ ПРОВЕРЕНО]"
            }
            val commentPart = if (item.comment.isNotBlank()) " — ${item.comment}" else ""
            val photoPart = if (item.photoSha256.isNotBlank()) " (Фото SHA256: ${item.photoSha256.take(10)}…)" else ""
            drawWrappedText(
                "${idx + 1}. ${item.zone}: ${item.title} $statusLabel$commentPart$photoPart",
                if (item.condition == ChecklistItem.CONDITION_DEFECT) boldBodyPaint else bodyPaint,
                extraGap = 4f
            )
        }
        y += 6f

        // Embedded Live Photos
        val photoItems = items.filter { it.photoLocalPath.isNotBlank() && File(it.photoLocalPath).exists() }
        if (photoItems.isNotEmpty()) {
            drawWrappedText("5. ФОТОФИКСАЦИЯ (ЖИВАЯ СЪЕМКА В ПРИЛОЖЕНИИ)", sectionPaint, extraGap = 6f)
            val colWidth = (CONTENT_WIDTH - 12f) / 2f
            val imgHeight = 155f
            val blockHeight = imgHeight + 42f

            var idx = 0
            while (idx < photoItems.size) {
                ensureSpace(blockHeight)
                for (col in 0..1) {
                    val currentIdx = idx + col
                    if (currentIdx >= photoItems.size) break
                    val pItem = photoItems[currentIdx]
                    val left = MARGIN_X + col * (colWidth + 12f)
                    val bmp = loadScaledBitmap(pItem.photoLocalPath, 600, 450)
                    if (bmp != null) {
                        val dst = RectF(left, y, left + colWidth, y + imgHeight)
                        canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dst, null)
                        bmp.recycle()
                    }
                    canvas.drawText("${pItem.zone.take(26)}", left, y + imgHeight + 12f, boldBodyPaint)
                    canvas.drawText(
                        "${pItem.photoCapturedAtIso} • ${pItem.photoSha256.take(10)}",
                        left,
                        y + imgHeight + 24f,
                        mutedPaint
                    )
                }
                y += blockHeight
                idx += 2
            }
        }

        // Digital Signature Block
        ensureSpace(125f)
        y += 6f
        drawWrappedText("6. ЦИФРОВАЯ ПОДПИСЬ СТОРОН (ПЭП ПО СМС-КОДУ, 63-ФЗ)", sectionPaint, extraGap = 6f)
        val fmtSign = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru", "RU"))
        val creatorSignLine = if (act.creatorSignedAtMillis > 0L) {
            "Инициатор (${if (act.creatorRole == HandoverAct.ROLE_TENANT) "Наниматель" else "Арендодатель"}): ПОДПИСАНО ${fmtSign.format(Date(act.creatorSignedAtMillis))} • Хэш ПЭП: ${act.creatorSmsCodeHash.take(16)}"
        } else {
            "Инициатор: Ожидается подпись по СМС-коду"
        }
        val cpSignLine = if (act.counterpartyConfirmed && act.counterpartySignedAtMillis > 0L) {
            "Вторая сторона (${act.counterpartyName}, ${act.counterpartyPhone}): ПОДТВЕРЖДЕНО ${fmtSign.format(Date(act.counterpartySignedAtMillis))} • Хэш ПЭП: ${act.counterpartySmsCodeHash.take(16)}"
        } else {
            "Вторая сторона: Ожидается подтверждение по ссылке / СМС-коду (${act.shareToken})"
        }
        drawWrappedText(creatorSignLine, bodyPaint, extraGap = 4f)
        drawWrappedText(cpSignLine, bodyPaint, extraGap = 8f)

        drawFooter(canvas, pageNumber)
        pdf.finishPage(page)

        FileOutputStream(outFile).use { fos ->
            pdf.writeTo(fos)
        }
        pdf.close()
        outFile
    }

    private fun loadScaledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > reqWidth * 2 || opts.outHeight / sample > reqHeight * 2) {
                sample *= 2
            }
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        }
    }
}
