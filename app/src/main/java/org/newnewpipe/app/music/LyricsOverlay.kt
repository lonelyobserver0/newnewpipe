package org.newnewpipe.app.music

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.newnewpipe.app.R

/**
 * Overlay dei testi sincronizzati nel player (022-S11).
 *
 * Mostra le righe LRC (da [LyricsClient]/[LrcParser]) con la riga corrente
 * evidenziata in bianco e grassetto e scorrimento automatico che la tiene
 * centrata. Il calcolo della riga corrente è delegato a [LyricsIndex]
 * (puro, coperto dagli unit test); qui c'è solo la resa: spannable
 * evidenziato + scroll.
 *
 * Lo scroll automatico si disattiva appena l'utente tocca la lista (lettura
 * manuale) e si riattiva dopo [AUTO_SCROLL_RESUME_DELAY_MS] di inattività o
 * quando la riga corrente esce dalla banda centrale di visibilità.
 */
class LyricsOverlay(
    private val root: ViewGroup,
    private val scrollView: ScrollView,
    private val textView: TextView,
) {

    companion object {
        private const val AUTO_SCROLL_RESUME_DELAY_MS = 3_000L
        /** Mezza larghezza della banda centrale (fuori dalla banda si ri-centra). */
        private const val CENTER_BAND_FRACTION = 0.30f
    }

    @ColorInt
    private val highlightColor: Int = ContextCompat.getColor(root.context, R.color.white)

    @ColorInt
    private val dimColor: Int = ContextCompat.getColor(root.context, R.color.white_secondary)

    private val mainHandler = Handler(Looper.getMainLooper())

    private var lines: List<LrcLine> = emptyList()
    private var index = LyricsIndex(emptyList())
    private var lineStarts = IntArray(0)
    private var currentLine = -1
    private var lastPositionMs = 0L
    private var autoScrollEnabled = true

    private val resumeAutoScroll = Runnable { autoScrollEnabled = true }

    init {
        // Toccare la lista = lettura manuale: pausa allo scroll automatico.
        scrollView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN
                || event.actionMasked == MotionEvent.ACTION_MOVE
            ) {
                autoScrollEnabled = false
                mainHandler.removeCallbacks(resumeAutoScroll)
                mainHandler.postDelayed(resumeAutoScroll, AUTO_SCROLL_RESUME_DELAY_MS)
            }
            false
        }
    }

    val isVisible: Boolean
        get() = root.visibility == View.VISIBLE

    val hasLines: Boolean
        get() = index.size > 0

    fun show() {
        root.visibility = View.VISIBLE
    }

    fun hide() {
        root.visibility = View.GONE
    }

    /** Reset a contenuto vuoto (es. al cambio brano). */
    fun clear() {
        lines = emptyList()
        index = LyricsIndex(emptyList())
        lineStarts = IntArray(0)
        currentLine = -1
        textView.text = ""
    }

    fun setLoading() {
        renderMessage(textView.context.getString(R.string.lyrics_loading))
    }

    fun setEmpty() {
        renderMessage(textView.context.getString(R.string.lyrics_not_found))
    }

    fun setError() {
        renderMessage(textView.context.getString(R.string.lyrics_error))
    }

    fun setLines(newLines: List<LrcLine>) {
        lines = newLines
        index = LyricsIndex(newLines)
        currentLine = -1
        lineStarts = buildLineStarts(newLines)
        // Ripristina la resa delle righe (dopo eventuale messaggio di stato).
        textView.typeface = Typeface.DEFAULT
        textView.gravity = Gravity.CENTER_HORIZONTAL
        textView.setTextColor(dimColor)
        textView.text = buildSpannable(newLines, -1)
        // Il Layout della TextView esiste solo dopo il layout pass: il primo
        // posizionamento (riga corrente + scroll) va rimandato.
        mainHandler.post { updatePosition(lastPositionMs) }
    }

    /**
     * Aggiorna la riga corrente dalla posizione di riproduzione (ms).
     * Chiamata dal tick di progresso del player (main thread).
     */
    fun updatePosition(positionMs: Long) {
        lastPositionMs = positionMs
        if (!isVisible || !hasLines) {
            return
        }
        val newLine = index.lineIndexAt(positionMs)
        if (newLine != currentLine) {
            currentLine = newLine
            textView.text = buildSpannable(lines, newLine)
            maybeAutoScroll()
        }
    }

    private fun renderMessage(message: String) {
        lines = emptyList()
        index = LyricsIndex(emptyList())
        lineStarts = IntArray(0)
        currentLine = -1
        textView.typeface = Typeface.DEFAULT
        textView.gravity = Gravity.CENTER
        textView.setTextColor(dimColor)
        textView.text = message
        scrollView.scrollTo(0, 0)
    }

    private fun buildSpannable(lyrics: List<LrcLine>, highlight: Int): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        lyrics.forEachIndexed { i, line ->
            if (i > 0) {
                sb.append('\n')
            }
            val start = sb.length
            sb.append(line.text)
            if (i == highlight) {
                sb.setSpan(ForegroundColorSpan(highlightColor), start, sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    /** Offset di inizio (nel testo completo) di ogni riga lyrics. */
    private fun buildLineStarts(lyrics: List<LrcLine>): IntArray {
        val starts = IntArray(lyrics.size)
        var offset = 0
        lyrics.forEachIndexed { i, line ->
            starts[i] = offset
            offset += line.text.length + 1 // +1 per il separatore '\n'
        }
        return starts
    }

    /**
     * Scroll automatico: centra la riga corrente solo se è fuori dalla banda
     * centrale di visibilità (così la lettura manuale non viene contrastata).
     */
    private fun maybeAutoScroll() {
        if (!autoScrollEnabled || currentLine < 0 || currentLine >= lineStarts.size) {
            return
        }
        val layout = textView.layout ?: return
        val lineStart = lineStarts[currentLine]
        val layoutLine = layout.getLineForOffset(lineStart)
        val lineTop = layout.getLineTop(layoutLine)
        val lineBottom = layout.getLineBottom(layoutLine)
        val visibleHeight = scrollView.height
        if (visibleHeight <= 0) {
            return
        }
        val scrollY = scrollView.scrollY
        val bandTop = scrollY + visibleHeight * CENTER_BAND_FRACTION
        val bandBottom = scrollY + visibleHeight * (1f - CENTER_BAND_FRACTION)
        if (lineTop >= bandTop && lineBottom <= bandBottom) {
            return
        }
        val target = (lineTop - visibleHeight / 2f + (lineBottom - lineTop) / 2f)
                .toInt()
                .coerceAtLeast(0)
        scrollView.smoothScrollTo(0, target)
    }
}
