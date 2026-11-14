package com.v2ray.ang.ui.hardvpn

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Vector icon set transcribed from the supplied HardVPN design. Every glyph is drawn on a
 * canvas so the app gains no icon dependency and the stroke weight stays consistent with
 * the original composition.
 */
enum class AppIcon {
    Shield,
    Download,
    Upload,
    Globe,
    Lock,
    Bolt,
    Person,
    Chevron,
    Back,
    Search,
    Close,
    Home,
    Plus,
    Trash,
    Edit,
    Refresh,
    Check,
    Heart,
    Info,
    Send,
}

@Composable
fun DrawIcon(icon: AppIcon, color: Color, modifier: Modifier = Modifier) {
    val filledPath = remember(icon) {
        when (icon) {
            AppIcon.Shield -> Path().apply {
                moveTo(0.5f, 0.08f)
                lineTo(0.84f, 0.22f)
                lineTo(0.8f, 0.57f)
                quadraticBezierTo(0.75f, 0.8f, 0.5f, 0.93f)
                quadraticBezierTo(0.25f, 0.8f, 0.2f, 0.57f)
                lineTo(0.16f, 0.22f)
                close()
            }

            AppIcon.Bolt -> Path().apply {
                moveTo(0.59f, 0.06f)
                lineTo(0.23f, 0.56f)
                lineTo(0.47f, 0.56f)
                lineTo(0.4f, 0.94f)
                lineTo(0.79f, 0.41f)
                lineTo(0.55f, 0.41f)
                close()
            }

            AppIcon.Person -> Path().apply {
                moveTo(0.2f, 0.88f)
                cubicTo(0.19f, 0.47f, 0.81f, 0.47f, 0.8f, 0.88f)
                close()
            }

            AppIcon.Home -> Path().apply {
                moveTo(0.12f, 0.48f)
                lineTo(0.5f, 0.14f)
                lineTo(0.88f, 0.48f)
                moveTo(0.24f, 0.44f)
                lineTo(0.24f, 0.88f)
                lineTo(0.76f, 0.88f)
                lineTo(0.76f, 0.44f)
            }

            AppIcon.Heart -> Path().apply {
                moveTo(0.5f, 0.86f)
                cubicTo(0.12f, 0.6f, 0.14f, 0.26f, 0.36f, 0.24f)
                cubicTo(0.46f, 0.23f, 0.5f, 0.33f, 0.5f, 0.33f)
                cubicTo(0.5f, 0.33f, 0.54f, 0.23f, 0.64f, 0.24f)
                cubicTo(0.86f, 0.26f, 0.88f, 0.6f, 0.5f, 0.86f)
                close()
            }

            else -> null
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lineWidth = size.minDimension * 0.085f
        val stroke = Stroke(width = lineWidth, cap = StrokeCap.Round)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = color,
                start = Offset(w * x1, h * y1),
                end = Offset(w * x2, h * y2),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
        }

        fun normalizedPath(iconPath: Path, filled: Boolean) {
            drawContext.canvas.save()
            drawContext.canvas.scale(w, h)
            if (filled) {
                drawPath(iconPath, color)
            } else {
                drawPath(
                    iconPath,
                    color,
                    style = Stroke(width = 0.085f, cap = StrokeCap.Round)
                )
            }
            drawContext.canvas.restore()
        }

        when (icon) {
            AppIcon.Shield -> {
                filledPath?.let { normalizedPath(it, filled = false) }
                line(0.34f, 0.49f, 0.46f, 0.61f)
                line(0.46f, 0.61f, 0.69f, 0.37f)
            }

            AppIcon.Download, AppIcon.Upload -> {
                val down = icon == AppIcon.Download
                val tail = if (down) 0.18f else 0.82f
                val tip = if (down) 0.82f else 0.18f
                val shoulder = if (down) 0.57f else 0.43f
                line(0.5f, tail, 0.5f, tip)
                line(0.26f, shoulder, 0.5f, tip)
                line(0.74f, shoulder, 0.5f, tip)
            }

            AppIcon.Globe -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.39f,
                    style = stroke
                )
                drawOval(
                    color = color,
                    topLeft = Offset(w * 0.32f, h * 0.11f),
                    size = Size(w * 0.36f, h * 0.78f),
                    style = stroke
                )
                line(0.12f, 0.5f, 0.88f, 0.5f)
            }

            AppIcon.Lock -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.23f, h * 0.43f),
                    size = Size(w * 0.54f, h * 0.45f),
                    cornerRadius = CornerRadius(w * 0.07f),
                    style = stroke
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.33f, h * 0.12f),
                    size = Size(w * 0.34f, h * 0.51f),
                    style = stroke
                )
                line(0.5f, 0.6f, 0.5f, 0.72f)
            }

            AppIcon.Bolt -> filledPath?.let { normalizedPath(it, filled = true) }

            AppIcon.Person -> {
                drawCircle(
                    color = color,
                    radius = w * 0.16f,
                    center = Offset(w * 0.5f, h * 0.28f),
                    style = stroke
                )
                filledPath?.let { normalizedPath(it, filled = false) }
            }

            AppIcon.Home -> filledPath?.let { normalizedPath(it, filled = false) }

            AppIcon.Heart -> filledPath?.let { normalizedPath(it, filled = true) }

            AppIcon.Chevron -> {
                line(0.37f, 0.2f, 0.65f, 0.5f)
                line(0.65f, 0.5f, 0.37f, 0.8f)
            }

            AppIcon.Back -> {
                line(0.65f, 0.18f, 0.33f, 0.5f)
                line(0.33f, 0.5f, 0.65f, 0.82f)
            }

            AppIcon.Search -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.25f,
                    center = Offset(w * 0.43f, h * 0.43f),
                    style = stroke
                )
                line(0.62f, 0.62f, 0.84f, 0.84f)
            }

            AppIcon.Close -> {
                line(0.25f, 0.25f, 0.75f, 0.75f)
                line(0.75f, 0.25f, 0.25f, 0.75f)
            }

            AppIcon.Plus -> {
                line(0.5f, 0.2f, 0.5f, 0.8f)
                line(0.2f, 0.5f, 0.8f, 0.5f)
            }

            AppIcon.Check -> {
                line(0.2f, 0.52f, 0.42f, 0.74f)
                line(0.42f, 0.74f, 0.82f, 0.28f)
            }

            AppIcon.Trash -> {
                line(0.2f, 0.28f, 0.8f, 0.28f)
                line(0.4f, 0.28f, 0.4f, 0.18f)
                line(0.4f, 0.18f, 0.6f, 0.18f)
                line(0.6f, 0.18f, 0.6f, 0.28f)
                line(0.29f, 0.28f, 0.34f, 0.86f)
                line(0.34f, 0.86f, 0.66f, 0.86f)
                line(0.66f, 0.86f, 0.71f, 0.28f)
            }

            AppIcon.Edit -> {
                line(0.18f, 0.82f, 0.8f, 0.2f)
                line(0.66f, 0.16f, 0.84f, 0.34f)
                line(0.18f, 0.82f, 0.3f, 0.8f)
                line(0.3f, 0.8f, 0.18f, 0.82f)
            }

            AppIcon.Refresh -> {
                drawArc(
                    color = color,
                    startAngle = 40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = Stroke(width = lineWidth, cap = StrokeCap.Round)
                )
                line(0.66f, 0.12f, 0.86f, 0.22f)
                line(0.86f, 0.22f, 0.76f, 0.4f)
            }

            AppIcon.Send -> {
                // Paper-plane glyph used for the (not yet configured) Telegram bot entry.
                line(0.12f, 0.5f, 0.88f, 0.14f)
                line(0.88f, 0.14f, 0.62f, 0.86f)
                line(0.62f, 0.86f, 0.42f, 0.6f)
                line(0.42f, 0.6f, 0.12f, 0.5f)
                line(0.42f, 0.6f, 0.88f, 0.14f)
            }

            AppIcon.Info -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.39f,
                    style = stroke
                )
                line(0.5f, 0.44f, 0.5f, 0.72f)
                drawCircle(
                    color = color,
                    radius = lineWidth * 0.55f,
                    center = Offset(w * 0.5f, h * 0.31f)
                )
            }
        }
    }
}

/** Sparkle glyph used by the Smart Auto Select row. */
@Composable
fun SmartSymbol(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val star = Path().apply {
            moveTo(w * 0.48f, h * 0.04f)
            quadraticBezierTo(w * 0.55f, h * 0.39f, w * 0.91f, h * 0.51f)
            quadraticBezierTo(w * 0.55f, h * 0.61f, w * 0.48f, h * 0.96f)
            quadraticBezierTo(w * 0.40f, h * 0.61f, w * 0.06f, h * 0.51f)
            quadraticBezierTo(w * 0.40f, h * 0.39f, w * 0.48f, h * 0.04f)
            close()
        }
        drawPath(star, Color.White)
        drawCircle(
            color = Color(0xFFBDE1FF),
            radius = w * 0.075f,
            center = Offset(w * 0.84f, h * 0.13f)
        )
    }
}
