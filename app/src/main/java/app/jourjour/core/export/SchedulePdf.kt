package app.jourjour.core.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/**
 * A trip's schedule as a sheet of paper.
 *
 * The page is laid out as the tab is read, because that is the thing people already know
 * how to read: a date across the page, what is booked under it on a ground of its own,
 * and the free stretches between two things. What the screen says by stacking — when it
 * begins over when it ends — the page says by putting them in columns, which is what
 * turns a scrolling list into something that can be glanced down.
 *
 * Drawn with a plain canvas rather than by photographing the screen. A picture of a
 * fortnight is a picture nobody can read; a page breaks where a page should break, keeps
 * its headings at the top, and prints.
 */
object SchedulePdf {

    const val MIME_TYPE = "application/pdf"

    /** A4 in points, which is the unit a PDF page is measured in. */
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 32f

    /** What a block is set in by, and how far its text stands off its own edges. */
    private const val PADDING = 6f
    private const val CORNER = 6f

    /** A hair of space, so that no block runs into the next. */
    private const val BLOCK_GAP = 2f

    private const val TITLE_SIZE = 9.5f
    private const val FIGURE_SIZE = 9f
    private const val NOTES_SIZE = 7.5f
    private const val SHIFT_SIZE = 6.5f
    private const val DAY_SIZE = 10.5f
    private const val HEADER_SIZE = 7.5f

    /** The deeper of the two grounds, which the dates are written across. */
    private const val DAY_GROUND = 0xFFDCE4F2.toInt()

    /** And the lighter one, which what is booked stands on. */
    private const val EVENT_GROUND = 0xFFF1F4FA.toInt()

    /** Minutes claimed twice, in the colours a destructive thing is given. */
    private const val CLASH_GROUND = 0xFFF9DEDC.toInt()
    private const val CLASH_INK = 0xFF8C1D18.toInt()

    private const val INK = 0xFF1A1C1E.toInt()
    private const val QUIET_INK = 0xFF5F6368.toInt()
    private const val RULE = 0xFFC4C7CA.toInt()

    /**
     * The columns, as fractions of the width a page leaves for them.
     *
     * Four of them hold a figure or a word of a known size and are given it; the name,
     * which can be any length at all, takes what is left and wraps inside it. The two
     * clocks are given room for the zone mark beside them rather than under them — it is
     * part of the hour it follows, and an hour that wrapped would stop looking like one.
     */
    private const val START_W = 0.125f
    private const val END_W = 0.125f
    private const val NAME_W = 0.405f
    private const val LENGTH_W = 0.130f
    private const val COST_W = 0.215f

    /**
     * @param headers the five column titles, in column order.
     * @param title what the sheet is of — the trip, and the dates it covers.
     */
    fun write(
        out: OutputStream,
        title: String,
        headers: List<String>,
        zoneMark: String,
        lines: List<PlanLine>,
    ) {
        val document = PdfDocument()
        val sheet = Sheet(document, title, headers, zoneMark)
        lines.forEachIndexed { at, line ->
            // A heading alone at the foot of a page is a heading on the wrong page, so it
            // goes over with the first thing under it.
            val keepWith = (line as? PlanLine.Day)?.let { lines.getOrNull(at + 1) }
            sheet.draw(line, keepWith)
        }
        sheet.finish()
        document.writeTo(out)
        document.close()
    }

    /**
     * One run of pages, filled from the top down.
     *
     * Every page opens with the column titles. A sheet read on the second page has nothing
     * else to say which figure is which, and a reader should not have to go back for it.
     */
    private class Sheet(
        private val document: PdfDocument,
        private val title: String,
        private val headers: List<String>,
        private val zoneMark: String,
    ) {
        private val content = PAGE_WIDTH - 2 * MARGIN
        private val columns = listOf(START_W, END_W, NAME_W, LENGTH_W, COST_W).map { it * content }
        private val bottom = PAGE_HEIGHT - MARGIN

        private val ground = Paint().apply { isAntiAlias = true }
        private val rule = Paint().apply {
            isAntiAlias = true
            color = RULE
            strokeWidth = 0.5f
        }
        private val ink = textPaint(FIGURE_SIZE, INK)
        private val quiet = textPaint(NOTES_SIZE, QUIET_INK)
        private val shift = textPaint(SHIFT_SIZE, QUIET_INK)
        private val name = textPaint(TITLE_SIZE, INK)
        private val day = textPaint(DAY_SIZE, INK).apply { isFakeBoldText = true }
        private val summary = textPaint(NOTES_SIZE, QUIET_INK)
        private val header = textPaint(HEADER_SIZE, QUIET_INK).apply { isFakeBoldText = true }
        private val clash = textPaint(FIGURE_SIZE, CLASH_INK)

        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private var number = 0

        /**
         * The day being filled, so that a day running over a page break says again which
         * day it is. A column of hours with no date over it is a column of hours.
         */
        private var carriedDay: PlanLine.Day? = null

        fun draw(line: PlanLine, keepWith: PlanLine?) {
            // Out of the way before the page is chosen: a break that lands on a new date
            // must not carry the old one over with it.
            if (line is PlanLine.Day) carriedDay = null
            val height = heightOf(line) + (keepWith?.let { heightOf(it) } ?: 0f)
            if (page == null || y + height > bottom) newPage()
            val canvas = requireNotNull(canvas)
            when (line) {
                is PlanLine.Day -> drawDay(canvas, line)
                is PlanLine.Event -> drawEvent(canvas, line)
                is PlanLine.Gap -> drawGap(canvas, line)
                is PlanLine.Overlap -> drawOverlap(canvas, line)
            }
            if (line is PlanLine.Day) carriedDay = line
        }

        fun finish() {
            page?.let(document::finishPage)
            page = null
        }

        private fun newPage() {
            finish()
            number += 1
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
            val started = document.startPage(info)
            page = started
            canvas = started.canvas
            y = MARGIN
            drawTop(started.canvas)
            carriedDay?.let { drawDay(started.canvas, it) }
        }

        /** What the sheet is of, once, and the column titles, on every page. */
        private fun drawTop(canvas: Canvas) {
            if (number == 1) {
                val heading = textPaint(DAY_SIZE + 1f, INK).apply { isFakeBoldText = true }
                val laid = layout(title, heading, content)
                canvas.withTranslation(MARGIN, y) { laid.draw(it) }
                y += laid.height + 10f
            }
            // The length column is headed over the middle, because that is where the
            // figures under it stand: a title against the left edge of a centred column
            // reads as the title of something else.
            val titles = headers.mapIndexed { at, text ->
                layout(text, header, columns[at] - 2 * PADDING, if (at == 3) MIDDLE else START)
            }
            val height = titles.maxOf { it.height }.toFloat()
            var x = MARGIN
            titles.forEachIndexed { at, laid ->
                canvas.withTranslation(x + PADDING, y) { laid.draw(it) }
                x += columns[at]
            }
            y += height + 3f
            canvas.drawLine(MARGIN, y, MARGIN + content, y, rule)
            y += 5f
        }

        private fun drawDay(canvas: Canvas, line: PlanLine.Day) {
            val date = layout(line.date, day, content * 0.5f)
            val said = layout(line.summary, summary, content * 0.5f - PADDING)
            val height = maxOf(date.height, said.height) + 2 * PADDING
            ground.color = DAY_GROUND
            canvas.drawRect(MARGIN, y, MARGIN + content, y + height, ground)
            canvas.withTranslation(MARGIN + PADDING, y + PADDING) { date.draw(it) }
            if (line.summary.isNotBlank()) {
                // Against the far edge, as the heading on the screen has it: the date is
                // what a column of these is scanned by, and the line about the day is read
                // only once the date has been found.
                val x = MARGIN + content - PADDING - said.width
                canvas.withTranslation(x, y + PADDING) { said.draw(it) }
            }
            y += height + BLOCK_GAP
        }

        private fun drawEvent(canvas: Canvas, line: PlanLine.Event) {
            val cells = cellsOf(line)
            val height = cells.maxOf { it.height }.toFloat() + 2 * PADDING
            ground.color = EVENT_GROUND
            canvas.drawRoundRect(
                RectF(MARGIN, y, MARGIN + content, y + height),
                line.corners,
                ground,
            )
            var x = MARGIN
            cells.forEachIndexed { at, cell ->
                canvas.withTranslation(x + PADDING, y + PADDING) { cell.draw(it) }
                x += columns[at]
            }
            y += height + BLOCK_GAP
        }

        /** One figure, in the column the lengths run down, and nothing else. */
        private fun drawGap(canvas: Canvas, line: PlanLine.Gap) {
            val laid = layout(line.length, quiet, columns[3] - 2 * PADDING, MIDDLE)
            val height = laid.height + PADDING
            val x = MARGIN + columns[0] + columns[1] + columns[2] + PADDING
            canvas.withTranslation(x, y + PADDING / 2) { laid.draw(it) }
            y += height + BLOCK_GAP
        }

        private fun drawOverlap(canvas: Canvas, line: PlanLine.Overlap) {
            val text = layout(line.text, clash, columns[2] - 2 * PADDING)
            val figure = layout(line.length, clash, columns[3] - 2 * PADDING, MIDDLE)
            val height = maxOf(text.height, figure.height) + 2 * PADDING
            ground.color = CLASH_GROUND
            // Square against both events that claim these minutes, so that the three of
            // them read as one run rather than as three separate things.
            canvas.drawRect(MARGIN, y, MARGIN + content, y + height, ground)
            val name = MARGIN + columns[0] + columns[1] + PADDING
            canvas.withTranslation(name, y + PADDING) { text.draw(it) }
            canvas.withTranslation(name + columns[2], y + PADDING) { figure.draw(it) }
            y += height + BLOCK_GAP
        }

        /**
         * The five cells of an event, each laid out in its own column.
         *
         * The name carries whatever was written about it underneath, set smaller: it is
         * the same thing continued, and a note in its own column would be a column empty
         * on every other line.
         */
        private fun cellsOf(line: PlanLine.Event): List<StaticLayout> = listOf(
            clockCell(line.start, line.startShift, columns[0]),
            clockCell(line.end, line.endShift, columns[1]),
            titleCell(line),
            layout(line.length, ink, columns[3] - 2 * PADDING, MIDDLE),
            layout(line.cost, quiet, columns[4] - 2 * PADDING),
        )

        /**
         * An hour, with how far the clocks moved at it where they did.
         *
         * The two are one cell and not two: the figure belongs to that hour, and a column
         * of its own would be a column that said nothing on nine lines in ten.
         */
        private fun clockCell(at: String, moved: String, width: Float): StaticLayout {
            if (at.isBlank()) return layout("", ink, width - 2 * PADDING)
            if (moved.isBlank()) return layout(at, ink, width - 2 * PADDING)
            // Named, not only signed. On the screen a raised figure beside an hour is read
            // off a list a person is already holding; on a page handed to somebody else it
            // is a number with no idea what it counts.
            val text = android.text.SpannableString("$at $zoneMark$moved").apply {
                setSpan(
                    android.text.style.AbsoluteSizeSpan(SHIFT_SIZE.toInt() + 1, true),
                    at.length + 1,
                    length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    android.text.style.ForegroundColorSpan(QUIET_INK),
                    at.length + 1,
                    length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            return layout(text, ink, width - 2 * PADDING)
        }

        private fun titleCell(line: PlanLine.Event): StaticLayout {
            val width = columns[2] - 2 * PADDING
            if (line.notes.isBlank()) return layout(line.title, name, width)
            val text = android.text.SpannableString("${line.title}\n${line.notes}").apply {
                val from = line.title.length + 1
                setSpan(
                    android.text.style.AbsoluteSizeSpan(NOTES_SIZE.toInt() + 1, true),
                    from,
                    length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    android.text.style.ForegroundColorSpan(QUIET_INK),
                    from,
                    length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            return layout(text, name, width)
        }

        /** How tall a line stands, asked before the page it would stand on is chosen. */
        private fun heightOf(line: PlanLine): Float = when (line) {
            is PlanLine.Day -> maxOf(
                layout(line.date, day, content * 0.5f).height,
                layout(line.summary, summary, content * 0.5f - PADDING).height,
            ) + 2 * PADDING + BLOCK_GAP
            is PlanLine.Event ->
                cellsOf(line).maxOf { it.height } + 2 * PADDING + BLOCK_GAP
            is PlanLine.Gap ->
                layout(line.length, quiet, columns[3] - 2 * PADDING, MIDDLE).height + PADDING + BLOCK_GAP
            is PlanLine.Overlap -> maxOf(
                layout(line.text, clash, columns[2] - 2 * PADDING).height,
                layout(line.length, clash, columns[3] - 2 * PADDING, MIDDLE).height,
            ) + 2 * PADDING + BLOCK_GAP
        }
    }

    private fun textPaint(size: Float, colour: Int) = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        color = colour
    }

    /** Against the edge it is read from, which is where almost everything on a page goes. */
    private val START = Layout.Alignment.ALIGN_NORMAL

    /** Over the middle of its column, which is where a column of bare figures belongs. */
    private val MIDDLE = Layout.Alignment.ALIGN_CENTER

    private fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Float,
        alignment: Layout.Alignment = START,
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()

    /**
     * A block rounded where it ends and cut square where whatever is next carries on from
     * it, which is the same rule the screen draws by.
     *
     * A rounded rectangle has one radius for all four corners, so a block square at one
     * end is drawn rounded and then squared off again with the corner that has to go.
     */
    private fun Canvas.drawRoundRect(rect: RectF, corners: PlanCorners, paint: Paint) {
        drawRoundRect(rect, CORNER, CORNER, paint)
        if (!corners.top) {
            drawRect(rect.left, rect.top, rect.right, rect.top + CORNER, paint)
        }
        if (!corners.bottom) {
            drawRect(rect.left, rect.bottom - CORNER, rect.right, rect.bottom, paint)
        }
    }

    private inline fun Canvas.withTranslation(x: Float, y: Float, block: (Canvas) -> Unit) {
        val saved = save()
        translate(x, y)
        block(this)
        restoreToCount(saved)
    }
}
