package com.talwinter.bptracker.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.talwinter.bptracker.clinical.Analysis
import com.talwinter.bptracker.clinical.Clinical
import com.talwinter.bptracker.clinical.Guideline
import com.talwinter.bptracker.clinical.MeasurementSetting
import com.talwinter.bptracker.clinical.Protocol722
import com.talwinter.bptracker.data.Arm
import com.talwinter.bptracker.data.Occasion
import com.talwinter.bptracker.data.Reading
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A PDF to hand to a doctor.
 *
 * This is the app's output. Everything else — logging, photos, reminders — exists to feed
 * it. The brief is therefore narrow: a clinician should get the answer in about thirty
 * seconds, without being handed a spreadsheet to interpret.
 *
 * So the page leads with the 7-day average and its category, states plainly which
 * guideline and which setting produced that category, and only then shows supporting
 * detail. Printed in black and white on purpose — colour-coded risk badges are for the
 * app; a page that will meet a mono printer or a photocopier should not depend on hue.
 */
object ReportGenerator {

    // A4 at 72dpi, the natural unit for PdfDocument.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 44f

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
    private val DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM  HH:mm", Locale.UK)

    fun generate(
        readings: List<Reading>,
        guideline: Guideline,
        setting: MeasurementSetting,
        outputFile: File,
        zone: ZoneId = ZoneId.systemDefault()
    ): File {
        val doc = PdfDocument()
        val assessment = Protocol722.assessCurrentWindow(readings, guideline, setting, zone = zone)
        val windowReadings = readings
            .filter {
                val d = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
                !d.isBefore(assessment.windowStart) && !d.isAfter(assessment.windowEnd)
            }
            .sortedByDescending { it.timestamp }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        y = drawHeader(canvas, y, assessment)
        y = drawHeadline(canvas, y, assessment, guideline, setting)
        y = drawSessions(canvas, y, assessment)
        y = drawChart(canvas, y, windowReadings.reversed())
        y = drawFindings(canvas, y, readings, windowReadings)
        y = drawTableHeader(canvas, y + 6f)

        for (r in windowReadings) {
            if (y > PAGE_H - MARGIN - 70f) {
                drawFooter(canvas, pageNumber)
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
                canvas = page.canvas
                y = drawTableHeader(canvas, MARGIN)
            }
            y = drawTableRow(canvas, y, r, guideline, setting, zone)
        }

        drawDisclaimer(canvas, PAGE_H - MARGIN - 26f)
        drawFooter(canvas, pageNumber)
        doc.finishPage(page)

        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { doc.writeTo(it) }
        doc.close()
        return outputFile
    }

    // ---- Sections ----

    private fun drawHeader(canvas: Canvas, top: Float, assessment: Protocol722.Assessment): Float {
        var y = top + 14f
        canvas.drawText("Home blood pressure record", MARGIN, y, title)
        y += 18f
        canvas.drawText(
            "${assessment.windowStart.format(DATE)} to ${assessment.windowEnd.format(DATE)}" +
                "   ·   self-measured at home with an upper-arm cuff",
            MARGIN, y, small
        )
        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        return y + 24f
    }

    private fun drawHeadline(
        canvas: Canvas,
        top: Float,
        assessment: Protocol722.Assessment,
        guideline: Guideline,
        setting: MeasurementSetting
    ): Float {
        var y = top
        canvas.drawText("7-DAY AVERAGE", MARGIN, y, eyebrow)
        y += 32f

        val overall = assessment.overall
        if (overall != null && assessment.hasEnoughForResult) {
            val value = "${overall.systolic}/${overall.diastolic}"
            canvas.drawText(value, MARGIN, y, huge)
            canvas.drawText("mmHg", MARGIN + huge.measureText(value) + 8f, y, small)

            y += 20f
            assessment.category?.let { category ->
                canvas.drawText(category.displayName, MARGIN, y, bodyBold)
                y += 13f
                val threshold = if (setting == MeasurementSetting.HOME) "135/85" else "140/90"
                canvas.drawText(
                    "by ${guideline.shortName}, ${setting.name.lowercase(Locale.UK)} thresholds " +
                        "(hypertension from $threshold)",
                    MARGIN, y, small
                )
            }
            overall.pulse?.let {
                y += 13f
                canvas.drawText("Average pulse $it bpm", MARGIN, y, small)
            }
        } else {
            canvas.drawText("Not enough readings", MARGIN, y, headingBold)
            y += 16f
            canvas.drawText(
                "${assessment.daysWithData} of ${Protocol722.MINIMUM_DAYS_FOR_RESULT} days minimum. " +
                    "An average of fewer than three days is not reported.",
                MARGIN, y, small
            )
        }
        return y + 26f
    }

    private fun drawSessions(canvas: Canvas, top: Float, assessment: Protocol722.Assessment): Float {
        var y = top
        canvas.drawText("MORNING AND EVENING", MARGIN, y, eyebrow)
        y += 18f

        val morning = assessment.morning?.let { "${it.systolic}/${it.diastolic}" } ?: "—"
        val evening = assessment.evening?.let { "${it.systolic}/${it.diastolic}" } ?: "—"
        canvas.drawText("Morning  $morning          Evening  $evening", MARGIN, y, body)
        y += 14f

        assessment.morningEveningSystolicDelta?.let { delta ->
            canvas.drawText(
                when {
                    delta > 10 -> "Mornings average $delta mmHg higher than evenings."
                    delta < -10 -> "Evenings average ${-delta} mmHg higher than mornings."
                    else -> "Morning and evening averages agree within 10 mmHg."
                },
                MARGIN, y, small
            )
            y += 13f
        }

        val days = assessment.daysWithData
        val discarded = assessment.readingsDiscardedFromFirstDay
        canvas.drawText(
            buildString {
                append("Protocol: ${assessment.readingsUsed} of ${Protocol722.EXPECTED_READINGS} readings ")
                append("across $days ${if (days == 1) "day" else "days"}.")
                // Only mention the day-one rule when it actually discarded something;
                // "(0 readings)" is noise on a page meant to be read in thirty seconds.
                if (discarded > 0) {
                    append(" Day one excluded by protocol ")
                    append("($discarded ${if (discarded == 1) "reading" else "readings"}).")
                }
            },
            MARGIN, y, small
        )
        return y + 26f
    }

    /** Greyscale throughout, so the chart survives a mono printer. */
    private fun drawChart(canvas: Canvas, top: Float, chronological: List<Reading>): Float {
        val height = 148f
        val left = MARGIN + 26f
        val right = PAGE_W - MARGIN
        var y = top

        canvas.drawText("EVERY READING IN THE PERIOD", MARGIN, y, eyebrow)
        y += 14f
        val chartTop = y
        val chartBottom = y + height

        if (chronological.size < 2) {
            canvas.drawText("Not enough readings to chart.", MARGIN, chartTop + 20f, small)
            return chartTop + 40f
        }

        val minV = minOf(60, chronological.minOf { it.diastolic } - 10)
        val maxV = maxOf(170, chronological.maxOf { it.systolic } + 10)
        val span = (maxV - minV).toFloat()

        fun yFor(v: Number) = chartBottom - (v.toFloat() - minV) / span * height
        fun xFor(i: Int) = left + (right - left) * i / (chronological.size - 1).toFloat()

        var guide = ((minV + 19) / 20) * 20
        while (guide <= maxV) {
            val gy = yFor(guide)
            canvas.drawLine(left, gy, right, gy, faintRule)
            canvas.drawText(guide.toString(), MARGIN - 2f, gy + 3f, tiny)
            guide += 20
        }

        canvas.drawLine(left, yFor(135), right, yFor(135), dashRule)
        canvas.drawLine(left, yFor(85), right, yFor(85), dashRule)

        fun series(values: List<Int>) {
            for (i in 0 until values.size - 1) {
                canvas.drawLine(xFor(i), yFor(values[i]), xFor(i + 1), yFor(values[i + 1]), seriesThin)
            }
            values.forEachIndexed { i, v -> canvas.drawCircle(xFor(i), yFor(v), 1.5f, dot) }
        }
        series(chronological.map { it.systolic })
        series(chronological.map { it.diastolic })

        val rolled = Analysis.rollingAverage(chronological)
        for (i in 0 until rolled.size - 1) {
            canvas.drawLine(xFor(i), yFor(rolled[i].first), xFor(i + 1), yFor(rolled[i + 1].first), seriesBold)
            canvas.drawLine(xFor(i), yFor(rolled[i].second), xFor(i + 1), yFor(rolled[i + 1].second), seriesBold)
        }

        canvas.drawText(
            "Thin lines with dots are individual readings; heavy lines are a 5-reading rolling " +
                "average. Dashed lines mark the home thresholds, 135 and 85 mmHg.",
            MARGIN, chartBottom + 14f, tiny
        )
        return chartBottom + 30f
    }

    private fun drawFindings(
        canvas: Canvas,
        top: Float,
        allReadings: List<Reading>,
        windowReadings: List<Reading>
    ): Float {
        val arms = Analysis.compareArms(allReadings)
        val sd = Analysis.systolicStandardDeviation(windowReadings)
        if (arms == null && sd == null) return top

        var y = top
        canvas.drawText("NOTES FROM THE DATA", MARGIN, y, eyebrow)
        y += 18f

        if (arms != null) {
            val higher = if (arms.higherArm == Arm.LEFT) "left" else "right"
            canvas.drawText(
                "Left arm ${arms.leftSystolic}/${arms.leftDiastolic} over ${arms.leftCount} readings; " +
                    "right arm ${arms.rightSystolic}/${arms.rightDiastolic} over ${arms.rightCount}.",
                MARGIN, y, body
            )
            y += 13f
            canvas.drawText(
                if (arms.isNotable)
                    "Inter-arm systolic difference ${arms.systolicDifference} mmHg, $higher arm higher — " +
                        "at or above the ${Analysis.NOTABLE_ARM_DIFFERENCE} mmHg level usually considered " +
                        "worth reviewing."
                else
                    "Inter-arm systolic difference ${arms.systolicDifference} mmHg.",
                MARGIN, y, small
            )
            y += 15f
        }

        sd?.let {
            canvas.drawText("Systolic variability across the period: SD ${it.roundToInt()} mmHg.", MARGIN, y, small)
            y += 15f
        }
        return y + 6f
    }

    // ---- Readings table ----

    private val columns = floatArrayOf(0f, 128f, 190f, 232f, 288f, 336f)

    private fun drawTableHeader(canvas: Canvas, top: Float): Float {
        var y = top + 8f
        canvas.drawText("ALL READINGS", MARGIN, y, eyebrow)
        y += 16f
        listOf("When", "Reading", "Pulse", "Session", "Arm", "Notes").forEachIndexed { i, label ->
            canvas.drawText(label, MARGIN + columns[i], y, tinyBold)
        }
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        return y + 13f
    }

    private fun drawTableRow(
        canvas: Canvas,
        top: Float,
        r: Reading,
        guideline: Guideline,
        setting: MeasurementSetting,
        zone: ZoneId
    ): Float {
        val category = runCatching {
            Clinical.classify(r.systolic, r.diastolic, guideline, setting)
        }.getOrNull()

        val flags = buildList {
            if (r.excludeFromAverages) add("not in average")
            if (r.irregularHeartbeat) add("irregular beat")
            if (category?.severity == 4) add("crisis range")
            r.notes?.let { add(it.take(26)) }
        }.joinToString(", ")

        canvas.drawText(Instant.ofEpochMilli(r.timestamp).atZone(zone).format(DAY_TIME), MARGIN + columns[0], top, tiny)
        canvas.drawText("${r.systolic}/${r.diastolic}", MARGIN + columns[1], top, tinyBold)
        canvas.drawText(r.pulse?.toString() ?: "—", MARGIN + columns[2], top, tiny)
        canvas.drawText(
            when (r.occasion) {
                Occasion.MORNING -> "morning"
                Occasion.EVENING -> "evening"
                Occasion.OTHER -> "other"
            },
            MARGIN + columns[3], top, tiny
        )
        canvas.drawText(if (r.arm == Arm.LEFT) "left" else "right", MARGIN + columns[4], top, tiny)
        if (flags.isNotEmpty()) canvas.drawText(flags, MARGIN + columns[5], top, tiny)
        return top + 13f
    }

    // ---- Chrome ----

    private fun drawDisclaimer(canvas: Canvas, y: Float) {
        canvas.drawLine(MARGIN, y - 12f, PAGE_W - MARGIN, y - 12f, rule)
        canvas.drawText(
            "Self-recorded with a home upper-arm cuff and logged in a personal app. " +
                "Not a medical device, and not a diagnosis.",
            MARGIN, y, tiny
        )
        canvas.drawText(
            "Home readings run lower than clinic readings; the categories above use home thresholds.",
            MARGIN, y + 11f, tiny
        )
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        canvas.drawText("Page $pageNumber", PAGE_W - MARGIN - 34f, PAGE_H - 20f, tiny)
    }

    // ---- Paints ----

    private val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansBold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val mono: Typeface = Typeface.create("monospace", Typeface.BOLD)

    private val title = Paint().apply { typeface = sansBold; textSize = 17f; color = Color.BLACK; isAntiAlias = true }
    private val headingBold = Paint().apply { typeface = sansBold; textSize = 14f; color = Color.BLACK; isAntiAlias = true }
    private val huge = Paint().apply { typeface = mono; textSize = 34f; color = Color.BLACK; isAntiAlias = true }
    private val body = Paint().apply { typeface = sans; textSize = 10f; color = Color.BLACK; isAntiAlias = true }
    private val bodyBold = Paint().apply { typeface = sansBold; textSize = 11f; color = Color.BLACK; isAntiAlias = true }
    private val small = Paint().apply { typeface = sans; textSize = 8.5f; color = Color.rgb(55, 55, 55); isAntiAlias = true }
    private val tiny = Paint().apply { typeface = sans; textSize = 7.5f; color = Color.rgb(70, 70, 70); isAntiAlias = true }
    private val tinyBold = Paint().apply { typeface = sansBold; textSize = 7.5f; color = Color.BLACK; isAntiAlias = true }
    private val eyebrow = Paint().apply {
        typeface = sansBold
        textSize = 7.5f
        color = Color.rgb(110, 110, 110)
        letterSpacing = 0.14f
        isAntiAlias = true
    }

    private val rule = Paint().apply { color = Color.rgb(150, 150, 150); strokeWidth = 0.7f }
    private val faintRule = Paint().apply { color = Color.rgb(224, 224, 224); strokeWidth = 0.5f }
    private val dashRule = Paint().apply {
        color = Color.rgb(120, 120, 120)
        strokeWidth = 0.7f
        pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
    }
    private val seriesThin = Paint().apply { color = Color.rgb(125, 125, 125); strokeWidth = 0.8f; isAntiAlias = true }
    private val seriesBold = Paint().apply { color = Color.BLACK; strokeWidth = 1.8f; isAntiAlias = true }
    private val dot = Paint().apply { color = Color.rgb(90, 90, 90); isAntiAlias = true }
}
