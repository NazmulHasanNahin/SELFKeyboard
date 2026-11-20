package com.mykb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** A flat, rounded key that draws a main label and an optional small hint in the corner. */
class KeyView(ctx: Context) : View(ctx) {
    var label = ""
    var hint = ""
    var fill = 0
    var fillPressed = 0
    var textColor = 0
    var hintColor = 0
    var textSizePx = 0f
    var hintSizePx = 0f
    var radius = 0f
    var bold = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val pad = resources.displayMetrics.density * 4f

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        p.style = Paint.Style.FILL
        p.color = if (isPressed) fillPressed else fill
        canvas.drawRoundRect(rect, radius, radius, p)
        if (label.isNotEmpty()) {
            p.color = textColor
            p.textAlign = Paint.Align.CENTER
            p.isFakeBoldText = bold
            var ts = textSizePx
            p.textSize = ts
            val maxW = width - pad * 2
            val w = p.measureText(label)
            if (w > maxW && w > 0f) {
                ts = ts * maxW / w
                p.textSize = ts
            }
            val y = height / 2f - (p.descent() + p.ascent()) / 2f
            canvas.drawText(label, width / 2f, y, p)
            p.isFakeBoldText = false
        }
        if (hint.isNotEmpty()) {
            p.color = hintColor
            p.textSize = hintSizePx
            p.textAlign = Paint.Align.RIGHT
            canvas.drawText(hint, width - pad, hintSizePx + pad * 0.5f, p)
        }
    }
}
