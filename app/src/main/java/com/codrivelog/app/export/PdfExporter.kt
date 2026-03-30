package com.codrivelog.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Renderer for Colorado DR 2324 style 2-page sheets.
 *
 * Each generated document always has exactly 2 pages:
 * 1) Instruction page
 * 2) Log-entry page
 *
 * If there are more rows than fit on one log-entry page, the caller should
 * generate additional 2-page documents by splitting rows with [splitIntoSheets].
 */
object PdfExporter {

    const val PAGE_WIDTH = 612
    const val PAGE_HEIGHT = 792

    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 36f
    private const val MARGIN_TOP = 36f

    /** One DR 2324 back-page sheet contains 13 entry blocks. */
    const val ROWS_PER_SHEET = 13

    /** Kept for tests/documentation compatibility. */
    val COL_HEADERS = arrayOf(
        "Date",
        "Verifier's Initials",
        "Driving Time",
        "Night Driving Time",
        "Comments",
    )

    private fun arial(style: Int): Typeface = Typeface.create("Arial", style)

    private val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = arial(Typeface.BOLD)
        textSize = 28f
    }
    private val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = arial(Typeface.BOLD)
        textSize = 13f
    }
    private val paintBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = arial(Typeface.NORMAL)
        textSize = 10.2f
    }
    private val paintBodyBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = arial(Typeface.BOLD)
        textSize = 10.2f
    }
    private val paintSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = arial(Typeface.NORMAL)
        textSize = 8.8f
    }
    private val paintTableText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = arial(Typeface.NORMAL)
        textSize = 8.8f
    }
    private val paintTableTotals = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = arial(Typeface.BOLD)
        textSize = 10f
    }
    private val paintGrid = Paint().apply {
        color = Color.BLACK
        strokeWidth = 0.75f
        style = Paint.Style.STROKE
    }
    private val paintGridBold = Paint().apply {
        color = Color.BLACK
        strokeWidth = 1.2f
        style = Paint.Style.STROKE
    }

    fun write(rows: List<DriveLogRow>, studentName: String = "", outputStream: OutputStream) {
        val doc = buildDocument(rows, studentName)
        doc.writeTo(outputStream)
        outputStream.flush()
        doc.close()
    }

    fun buildDocument(rows: List<DriveLogRow>, studentName: String = ""): PdfDocument {
        val doc = PdfDocument()

        val page1Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page1 = doc.startPage(page1Info)
        drawInstructionPage(page1.canvas, studentName)
        doc.finishPage(page1)

        val page2Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
        val page2 = doc.startPage(page2Info)
        drawLogPage(page2.canvas, rows)
        doc.finishPage(page2)

        return doc
    }

    fun splitIntoSheets(rows: List<DriveLogRow>): List<List<DriveLogRow>> =
        if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(ROWS_PER_SHEET)

    private fun drawInstructionPage(canvas: Canvas, studentName: String) {
        val contentWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val colGap = 12f
        val colWidth = (contentWidth - colGap) / 2f
        val leftX = MARGIN_LEFT
        val rightX = leftX + colWidth + colGap

        var y = MARGIN_TOP + 8f

        canvas.drawText("DR 2324 (02/11/22)", leftX, y, paintSmall)
        y += 12f
        canvas.drawText("COLORADO DEPARTMENT OF REVENUE", leftX, y, paintBodyBold)
        y += 12f
        canvas.drawText("Division of Motor Vehicles", leftX, y, paintBody)

        val title = "Drive Time Log Sheet"
        canvas.drawText(
            title,
            MARGIN_LEFT + (contentWidth - paintTitle.measureText(title)) / 2f,
            MARGIN_TOP + 55f,
            paintTitle,
        )

        val labelsY = MARGIN_TOP + 90f
        val studentLabel = "Student's name:"
        val permitLabel = "Permit number:"
        canvas.drawText(studentLabel, leftX, labelsY, paintHeader)
        canvas.drawLine(leftX + 90f, labelsY + 4f, leftX + colWidth - 4f, labelsY + 4f, paintGrid)
        canvas.drawText(permitLabel, rightX, labelsY, paintHeader)
        canvas.drawLine(rightX + 93f, labelsY + 4f, rightX + colWidth - 4f, labelsY + 4f, paintGrid)

        if (studentName.isNotBlank()) {
            canvas.drawText(studentName, leftX + 92f, labelsY - 2f, paintBody)
        }

        var leftY = labelsY + 28f
        canvas.drawText("Your instruction permit:", leftX, leftY, paintHeader)
        leftY += 18f
        leftY = drawWrappedParagraph(
            canvas,
            "If you are under 18, you must comply with the following requirements:",
            leftX,
            leftY,
            colWidth,
            paintBodyBold,
            13f,
        )
        leftY += 8f

        val leftParagraphs = listOf(
            "You must be 16 years of age to be issued an instruction permit unless you have completed and passed the classroom portion of an approved driver education course then you may be issued an instruction permit at the age of 15 years. You must submit proof of completion, along with your identification, at the time you apply for the permit.",
            "Or, if you have completed a State-approved 4-hour driver awareness course, then you may be issued a permit at 15 years/6 months. You must submit proof of completion, along with your identification, at the time you apply for the permit.",
            "You are required to hold your first instruction permit for at least twelve months and be at least 16 years of age before you can get a driver license in Colorado. This means that if you get your permit on your 15th birthday, you will have to hold the permit until your 16th birthday before you can apply for the license.",
            "You are required, by law, to complete behind-the-wheel training before you can be issued your driver license if you are under the age of 16 years, 6 months at the time you apply for your driver license. The BTW training can be administered two ways: you can take 6 hours with a driving instructor from a department-approved school or, if there isn't a driving school that offers BTW training at least 20 hours per week with an address that is within 30 miles of the permit holder's residence, you may complete 12 hours with a parent, guardian or alternate permit supervisor.",
            "At the time you apply for your driver license, you are also required, by law, to submit a log of your driving experience. The log sheet must show a minimum total of 50 hours, with 10 hours of those 50 hours having been driven at night. The Drive Time Log Sheet is used any time you drive. The appropriate box is filled in by the parent/guardian driving with you or by the driver authorized by your parent/guardian to accompany you while you are driving.",
            "They will fill in the date, the total drive time, the amount of night driving (if any) and their initials. The comments section is optional for licensing purposes, but is useful for you to track your progress.",
            "The Driver Time Log Sheet is the only log sheet acceptable as proof of the required 50 hours of driving time unless the log sheet you are presenting is from a state-approved Commercial Driving School, Driver Education or 3rd-party testing organization. The 50 hour total may include your 6-hour BTW training, if your BTW training was with your Driver education teacher. If you complete 12 hours of BTW training with your parent/guardian/alternate permit supervisor, the 12 hours is IN ADDITION to the 50 hour requirement of the log sheet, for a total of 62 hours. You may make photocopies of the log sheet if you need more than one to complete your 50 hours.",
            "When you have reached your required totals, your parent or guardian or another responsible adult must then verify total driving time and total night driving time on your log sheet(s).",
        )

        for (paragraph in leftParagraphs) {
            leftY = drawWrappedParagraph(canvas, paragraph, leftX, leftY, colWidth, paintBody, 12.3f)
            leftY += 7f
        }

        var rightY = labelsY + 28f
        val rightParagraphs = listOf(
            "These totals are entered on the appropriate lines on the back of the last log sheet. The parent/guardian/responsible adult will then sign and date only the back of the log sheet that has the final completed totals.",
            "Once you have held your instruction permit for at least 12 full months, and you are at least 16 years of age, you are eligible to apply for your license. You must submit the completed Drive Time Log Sheet at the time you apply for your license.",
            "If your parent/guardian/alternate permit supervisor administered the required behind-the-wheel training, it must be included in the total driving time recorded on the log sheet(s).",
        )
        for (paragraph in rightParagraphs) {
            rightY = drawWrappedParagraph(canvas, paragraph, rightX, rightY, colWidth, paintBody, 12.3f)
            rightY += 8f
        }

        rightY = drawWrappedParagraph(
            canvas,
            "Your driver license: When you are issued your driver license, if you are under the age of 18, there are still a few things you need to be aware of. The law does not allow you to carry a passenger under the age of 21 until you have held your license for at least 6 months. And, you can't carry more than one passenger under 21 until you've held your license for at least one year. The exceptions to this are if your parent/guardian is with you, or there is an adult passenger 21 or older who has a valid license and has held that license for at least one year, or the passenger under 21 needs emergency medical assistance or is a member of your immediate family.",
            rightX,
            rightY,
            colWidth,
            paintBody,
            12.3f,
            boldPrefix = "Your driver license:",
        )
        rightY += 8f

        val rightTail = listOf(
            "While you are under 18, you cannot drive between the hours of 12:00 midnight and 5:00 a.m. unless you have held your license for at least one year. The exceptions to this are if your parent/guardian is with you, or there is an adult passenger 21 or older who has a valid license and has held that license for at least one year, or it is an emergency, or you are an emancipated minor with a valid license. You may drive between midnight and 5:00 a.m. if it's to a school or school-authorized activity where the school doesn't provide transportation. You will need a signed statement from the school official showing the date of the activity. And, you may drive between midnight and 5:00 a.m., if it's to and from work. You must carry a signed statement from your employer verifying your employment.",
            "While you are under the age of 18, when you carry any allowed passengers, everyone riding with you must wear their seat belt. Only one passenger can ride in the front seat with you. You can only carry as many passengers in the back seat as there are seat belts.",
            "Colorado law prohibits drivers under 18 years age from using a cell or mobile phone while driving unless it is to contact the police or fire department or it is an emergency. Drivers 18 and older may not use a cell or mobile telephone for text messaging while driving unless it is to contact the police or fire department or it is an emergency. Your license expires 20 days after your 21st birthday.",
            "Have a safe journey and we will see you when you turn 21.",
        )
        for (paragraph in rightTail) {
            rightY = drawWrappedParagraph(canvas, paragraph, rightX, rightY, colWidth, paintBody, 12.3f)
            rightY += 8f
        }
    }

    private fun drawLogPage(canvas: Canvas, rows: List<DriveLogRow>) {
        val contentWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val topY = MARGIN_TOP
        val colWidth = contentWidth / 4f
        val dateX = MARGIN_LEFT
        val initialsX = dateX + colWidth
        val drivingX = initialsX + colWidth
        val nightX = drivingX + colWidth
        val rightEdge = MARGIN_LEFT + contentWidth

        val recordHeight = 40f
        val dataRowHeight = 20f

        var y = topY
        for (i in 0 until ROWS_PER_SHEET) {
            val top = y
            val mid = top + dataRowHeight
            val bottom = top + recordHeight

            canvas.drawRect(MARGIN_LEFT, top, rightEdge, bottom, paintGrid)
            canvas.drawLine(MARGIN_LEFT, mid, rightEdge, mid, paintGrid)
            canvas.drawLine(initialsX, top, initialsX, mid, paintGrid)
            canvas.drawLine(drivingX, top, drivingX, mid, paintGrid)
            canvas.drawLine(nightX, top, nightX, mid, paintGrid)

            canvas.drawText("Date", dateX + 2f, top + 11f, paintTableText)
            canvas.drawText("Verifier's Initials", initialsX + 2f, top + 11f, paintTableText)
            canvas.drawText("Driving Time", drivingX + 2f, top + 11f, paintTableText)
            canvas.drawText("Night Driving Time", nightX + 2f, top + 11f, paintTableText)
            canvas.drawText("Comments", MARGIN_LEFT + 2f, mid + 11f, paintTableText)

            val row = rows.getOrNull(i)
            if (row != null) {
                canvas.drawText(fitText(row.date, colWidth - 6f), dateX + 2f, top + 19f, paintTableText)
                canvas.drawText(fitText(row.supervisorInitials, colWidth - 6f), initialsX + 2f, top + 19f, paintTableText)
                canvas.drawText(fitText(row.drivingTime, colWidth - 6f), drivingX + 2f, top + 19f, paintTableText)
                canvas.drawText(fitText(row.nightDriving, colWidth - 6f), nightX + 2f, top + 19f, paintTableText)
                canvas.drawText(fitText(row.comments, contentWidth - 6f), MARGIN_LEFT + 2f, mid + 19f, paintTableText)
            }

            y = bottom
        }

        val totalsTop = y
        val totalsBottom = totalsTop + 26f
        val labelRight = MARGIN_LEFT + contentWidth / 2f
        canvas.drawRect(MARGIN_LEFT, totalsTop, rightEdge, totalsBottom, paintGridBold)
        canvas.drawLine(labelRight, totalsTop, labelRight, totalsBottom, paintGrid)
        canvas.drawLine(labelRight + colWidth, totalsTop, labelRight + colWidth, totalsBottom, paintGrid)

        val sumTotal = rows.sumOf { it.totalMinutes }
        val sumNight = rows.sumOf { it.nightMinutes }

        val totalsLabel = "Totals for this page"
        canvas.drawText(
            totalsLabel,
            MARGIN_LEFT + (contentWidth / 2f - paintTableTotals.measureText(totalsLabel)) / 2f,
            totalsTop + 17f,
            paintTableTotals,
        )
        canvas.drawText("Driving Time", labelRight + 2f, totalsTop + 11f, paintTableText)
        canvas.drawText("Night Driving Time", labelRight + colWidth + 2f, totalsTop + 11f, paintTableText)
        canvas.drawText(DriveLogRow.formatMinutes(sumTotal), labelRight + 2f, totalsTop + 22f, paintTableText)
        canvas.drawText(DriveLogRow.formatMinutes(sumNight), labelRight + colWidth + 2f, totalsTop + 22f, paintTableText)

        val paragraphY = totalsBottom + 14f
        val paragraph = "To complete your application for a license, your GRAND TOTAL DRIVING TIME and GRAND TOTAL NIGHT DRIVING TIME must be recorded. Your GRAND TOTAL DRIVING TIME must be at least 50 hours and your GRAND TOTAL NIGHT DRIVING TIME must be at least 10 hours. Your application for a license will not be accepted without grand totals of each listed below and must include a signature from a parent, guardian, or responsible adult below."
        val afterParagraphY = drawWrappedParagraph(
            canvas,
            paragraph,
            MARGIN_LEFT,
            paragraphY,
            contentWidth,
            paintBody,
            12.3f,
        ) + 8f

        val boxW = 80f
        val boxH = 20f
        val totalLabel1 = "GRAND TOTAL DRIVING TIME:"
        val totalLabel2 = "GRAND TOTAL NIGHT DRIVING TIME:"
        val labelX = MARGIN_LEFT + 160f
        val boxX = labelX + 175f

        canvas.drawText(totalLabel1, labelX, afterParagraphY + 11f, paintTableTotals)
        canvas.drawRect(boxX, afterParagraphY - 8f, boxX + boxW, afterParagraphY - 8f + boxH, paintGrid)
        canvas.drawText(DriveLogRow.formatMinutes(sumTotal), boxX + 4f, afterParagraphY + 6f, paintBody)
        canvas.drawText("(must be at least 50 hours)", boxX + boxW + 4f, afterParagraphY + 11f, paintBody)

        val y2 = afterParagraphY + 30f
        canvas.drawText(totalLabel2, labelX - 32f, y2 + 11f, paintTableTotals)
        canvas.drawRect(boxX, y2 - 8f, boxX + boxW, y2 - 8f + boxH, paintGrid)
        canvas.drawText(DriveLogRow.formatMinutes(sumNight), boxX + 4f, y2 + 6f, paintBody)
        canvas.drawText("(must be at least 10 hours)", boxX + boxW + 4f, y2 + 11f, paintBody)

        val certifyY = y2 + 29f
        val certify = "Please check all totals prior to signing. By signing below, I certify that the above total hours of driving experience is true and accurate."
        canvas.drawText(certify, MARGIN_LEFT, certifyY, paintBody)

        val signatureY = certifyY + 25f
        canvas.drawText("Name:", MARGIN_LEFT, signatureY, paintBody)
        canvas.drawLine(MARGIN_LEFT + 30f, signatureY + 4f, MARGIN_LEFT + 210f, signatureY + 4f, paintGrid)
        canvas.drawText("Signature:", MARGIN_LEFT + 215f, signatureY, paintBody)
        canvas.drawLine(MARGIN_LEFT + 268f, signatureY + 4f, MARGIN_LEFT + 426f, signatureY + 4f, paintGrid)
        canvas.drawText("Date:", MARGIN_LEFT + 432f, signatureY, paintBody)
        canvas.drawLine(MARGIN_LEFT + 455f, signatureY + 4f, rightEdge, signatureY + 4f, paintGrid)

        canvas.drawText("DR 2324 (02/11/22)", MARGIN_LEFT, PAGE_HEIGHT - 20f, paintSmall)
    }

    private fun drawWrappedParagraph(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        width: Float,
        paint: Paint,
        lineHeight: Float,
        boldPrefix: String? = null,
    ): Float {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= width) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current

        var y = startY
        lines.forEachIndexed { index, line ->
            if (index == 0 && boldPrefix != null && line.startsWith(boldPrefix)) {
                canvas.drawText(boldPrefix, x, y, paintBodyBold)
                val rest = line.removePrefix(boldPrefix)
                canvas.drawText(rest, x + paintBodyBold.measureText(boldPrefix), y, paint)
            } else {
                canvas.drawText(line, x, y, paint)
            }
            y += lineHeight
        }
        return y
    }

    private fun fitText(text: String, maxWidth: Float): String {
        if (text.isEmpty()) return ""
        if (paintTableText.measureText(text) <= maxWidth) return text
        val ellipsis = "..."
        var end = text.length
        while (end > 1 && paintTableText.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
    }
}
