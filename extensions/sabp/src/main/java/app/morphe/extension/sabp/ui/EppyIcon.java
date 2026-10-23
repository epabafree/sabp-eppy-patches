package app.morphe.extension.sabp.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Rounded, outlined icons in the Material Symbols style, drawn from paths on a 24×24 grid so the
 * extension needs no image resources and icons stay crisp at any size.
 */
public final class EppyIcon extends Drawable {
    public static final int PLAY = 1, PAUSE = 2, SKIP_NEXT = 3, SKIP_PREV = 4, REWIND = 5, FORWARD = 6,
            BACK = 7, CHEVRON_DOWN = 8, SEARCH = 9, TUNE = 10, HOME = 11, LIBRARY = 12, BOOKMARK = 13,
            MOON = 14, SPEED = 15, MORE = 16, CLOSE = 17, LIST = 18, GRID = 19, HISTORY = 20, CHART = 21,
            PERSON = 22, FOLDER = 23, PLUS = 24, CHECK = 25, HEADPHONES = 26, QUEUE = 27, CLASSIC = 28,
            BOOKMARK_ADD = 29;

    private final Path stroke = new Path();
    private final Path fill = new Path();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path scratch = new Path();
    private int color;

    public EppyIcon(int icon, int color) {
        this.color = color;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeWidth(2f);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setPathEffect(new CornerPathEffect(1.6f));
        build(icon);
        setColor(color);
    }

    public void setColor(int color) {
        this.color = color;
        strokePaint.setColor(color);
        fillPaint.setColor(color);
        invalidateSelf();
    }

    public int getColor() {
        return color;
    }

    private void build(int icon) {
        Path s = stroke, f = fill;
        switch (icon) {
            case PLAY:
                f.moveTo(8, 5);
                f.lineTo(19, 12);
                f.lineTo(8, 19);
                f.close();
                break;
            case PAUSE:
                f.addRoundRect(new RectF(6, 5, 10, 19), 1.5f, 1.5f, Path.Direction.CW);
                f.addRoundRect(new RectF(14, 5, 18, 19), 1.5f, 1.5f, Path.Direction.CW);
                break;
            case SKIP_NEXT:
                f.moveTo(5, 6);
                f.lineTo(15, 12);
                f.lineTo(5, 18);
                f.close();
                f.addRoundRect(new RectF(16.5f, 6, 19, 18), 1f, 1f, Path.Direction.CW);
                break;
            case SKIP_PREV:
                f.moveTo(19, 6);
                f.lineTo(9, 12);
                f.lineTo(19, 18);
                f.close();
                f.addRoundRect(new RectF(5, 6, 7.5f, 18), 1f, 1f, Path.Direction.CW);
                break;
            case REWIND:
                // Circle centred on (12,12); arrow head at the top; "10"-style marks centred.
                s.addArc(new RectF(4, 4, 20, 20), -100, -250);
                s.moveTo(12.6f, 1.6f);
                s.lineTo(10.2f, 4.0f);
                s.lineTo(12.6f, 6.4f);
                s.moveTo(9.5f, 9.5f);
                s.lineTo(9.5f, 14.5f);
                s.addRoundRect(new RectF(12, 9.5f, 15, 14.5f), 1.5f, 1.5f, Path.Direction.CW);
                break;
            case FORWARD:
                s.addArc(new RectF(4, 4, 20, 20), -80, 250);
                s.moveTo(11.4f, 1.6f);
                s.lineTo(13.8f, 4.0f);
                s.lineTo(11.4f, 6.4f);
                s.moveTo(9.5f, 9.5f);
                s.lineTo(9.5f, 14.5f);
                s.addRoundRect(new RectF(12, 9.5f, 15, 14.5f), 1.5f, 1.5f, Path.Direction.CW);
                break;
            case BACK:
                s.moveTo(19, 12);
                s.lineTo(5, 12);
                s.moveTo(11, 6);
                s.lineTo(5, 12);
                s.lineTo(11, 18);
                break;
            case CHEVRON_DOWN:
                s.moveTo(6, 9);
                s.lineTo(12, 15);
                s.lineTo(18, 9);
                break;
            case SEARCH:
                s.addCircle(10.5f, 10.5f, 6, Path.Direction.CW);
                s.moveTo(15, 15);
                s.lineTo(20, 20);
                break;
            case TUNE:
                s.moveTo(4, 7);
                s.lineTo(13, 7);
                s.moveTo(19, 7);
                s.lineTo(20, 7);
                s.addCircle(16, 7, 2.5f, Path.Direction.CW);
                s.moveTo(4, 17);
                s.lineTo(5, 17);
                s.moveTo(11, 17);
                s.lineTo(20, 17);
                s.addCircle(8, 17, 2.5f, Path.Direction.CW);
                break;
            case HOME:
                s.moveTo(4, 10.5f);
                s.lineTo(12, 4);
                s.lineTo(20, 10.5f);
                s.lineTo(20, 19);
                s.quadTo(20, 20, 19, 20);
                s.lineTo(5, 20);
                s.quadTo(4, 20, 4, 19);
                s.close();
                s.moveTo(10, 16);
                s.lineTo(14, 16);
                break;
            case LIBRARY:
                s.moveTo(12, 6.5f);
                s.cubicTo(10, 5, 6.5f, 4.5f, 3.5f, 5.5f);
                s.lineTo(3.5f, 18.5f);
                s.cubicTo(6.5f, 17.5f, 10, 18, 12, 19.5f);
                s.cubicTo(14, 18, 17.5f, 17.5f, 20.5f, 18.5f);
                s.lineTo(20.5f, 5.5f);
                s.cubicTo(17.5f, 4.5f, 14, 5, 12, 6.5f);
                s.close();
                s.moveTo(12, 6.5f);
                s.lineTo(12, 19.5f);
                break;
            case BOOKMARK:
                s.moveTo(7, 4);
                s.lineTo(17, 4);
                s.lineTo(17, 20);
                s.lineTo(12, 16);
                s.lineTo(7, 20);
                s.close();
                break;
            case BOOKMARK_ADD:
                // Same 10×16 bookmark as BOOKMARK, shifted left so bookmark + plus are centred.
                s.moveTo(11, 4);
                s.lineTo(5, 4);
                s.lineTo(5, 20);
                s.lineTo(10, 16);
                s.lineTo(15, 20);
                s.lineTo(15, 11);
                s.moveTo(17, 2.5f);
                s.lineTo(17, 7.5f);
                s.moveTo(14.5f, 5);
                s.lineTo(19.5f, 5);
                break;
            case MOON: {
                Path a = new Path();
                a.addCircle(12, 12, 8, Path.Direction.CW);
                Path b = new Path();
                b.addCircle(16.5f, 8.5f, 6.5f, Path.Direction.CW);
                s.op(a, b, Path.Op.DIFFERENCE);
                break;
            }
            case SPEED:
                s.addArc(new RectF(4, 6, 20, 22), 150, 240);
                s.moveTo(12, 14);
                s.lineTo(16, 10);
                break;
            case MORE:
                f.addCircle(12, 5.5f, 1.8f, Path.Direction.CW);
                f.addCircle(12, 12, 1.8f, Path.Direction.CW);
                f.addCircle(12, 18.5f, 1.8f, Path.Direction.CW);
                break;
            case CLOSE:
                s.moveTo(6, 6);
                s.lineTo(18, 18);
                s.moveTo(18, 6);
                s.lineTo(6, 18);
                break;
            case LIST:
                s.moveTo(9, 6);
                s.lineTo(20, 6);
                s.moveTo(9, 12);
                s.lineTo(20, 12);
                s.moveTo(9, 18);
                s.lineTo(20, 18);
                f.addCircle(5, 6, 1.4f, Path.Direction.CW);
                f.addCircle(5, 12, 1.4f, Path.Direction.CW);
                f.addCircle(5, 18, 1.4f, Path.Direction.CW);
                break;
            case GRID:
                s.addRoundRect(new RectF(4, 4, 11, 11), 2, 2, Path.Direction.CW);
                s.addRoundRect(new RectF(13, 4, 20, 11), 2, 2, Path.Direction.CW);
                s.addRoundRect(new RectF(4, 13, 11, 20), 2, 2, Path.Direction.CW);
                s.addRoundRect(new RectF(13, 13, 20, 20), 2, 2, Path.Direction.CW);
                break;
            case HISTORY:
                s.addCircle(12, 12, 8, Path.Direction.CW);
                s.moveTo(12, 8);
                s.lineTo(12, 12);
                s.lineTo(15, 14);
                break;
            case CHART:
                s.moveTo(6, 19);
                s.lineTo(6, 12);
                s.moveTo(12, 19);
                s.lineTo(12, 6);
                s.moveTo(18, 19);
                s.lineTo(18, 14);
                break;
            case PERSON:
                s.addCircle(12, 8, 4, Path.Direction.CW);
                s.moveTo(4.5f, 20);
                s.cubicTo(5, 16, 8, 14.5f, 12, 14.5f);
                s.cubicTo(16, 14.5f, 19, 16, 19.5f, 20);
                break;
            case FOLDER:
                s.moveTo(3.5f, 6.5f);
                s.quadTo(3.5f, 5, 5, 5);
                s.lineTo(9, 5);
                s.lineTo(11, 7);
                s.lineTo(19, 7);
                s.quadTo(20.5f, 7, 20.5f, 8.5f);
                s.lineTo(20.5f, 17.5f);
                s.quadTo(20.5f, 19, 19, 19);
                s.lineTo(5, 19);
                s.quadTo(3.5f, 19, 3.5f, 17.5f);
                s.close();
                break;
            case PLUS:
                s.moveTo(12, 5);
                s.lineTo(12, 19);
                s.moveTo(5, 12);
                s.lineTo(19, 12);
                break;
            case CHECK:
                s.moveTo(5, 12.5f);
                s.lineTo(10, 17);
                s.lineTo(19, 7);
                break;
            case HEADPHONES:
                s.moveTo(4, 15);
                s.lineTo(4, 12);
                s.cubicTo(4, 7.5f, 7.5f, 4, 12, 4);
                s.cubicTo(16.5f, 4, 20, 7.5f, 20, 12);
                s.lineTo(20, 15);
                s.addRoundRect(new RectF(4, 13, 8, 20), 2, 2, Path.Direction.CW);
                s.addRoundRect(new RectF(16, 13, 20, 20), 2, 2, Path.Direction.CW);
                break;
            case QUEUE:
                s.moveTo(4, 6);
                s.lineTo(16, 6);
                s.moveTo(4, 11);
                s.lineTo(16, 11);
                s.moveTo(4, 16);
                s.lineTo(10, 16);
                f.moveTo(14, 14);
                f.lineTo(20, 17);
                f.lineTo(14, 20);
                f.close();
                break;
            case CLASSIC:
                s.addRoundRect(new RectF(5, 3, 19, 21), 3, 3, Path.Direction.CW);
                s.addCircle(12, 14, 3.5f, Path.Direction.CW);
                s.moveTo(9, 7);
                s.lineTo(15, 7);
                break;
            default:
                break;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float scale = Math.min(b.width(), b.height()) / 24f;
        int save = canvas.save();
        canvas.translate(b.left + (b.width() - 24 * scale) / 2f, b.top + (b.height() - 24 * scale) / 2f);
        canvas.scale(scale, scale);
        if (!fill.isEmpty()) canvas.drawPath(fill, fillPaint);
        if (!stroke.isEmpty()) canvas.drawPath(stroke, strokePaint);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        strokePaint.setAlpha(alpha);
        fillPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        strokePaint.setColorFilter(cf);
        fillPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return -1;
    }
}
