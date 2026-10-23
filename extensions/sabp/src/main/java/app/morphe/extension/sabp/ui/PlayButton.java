package app.morphe.extension.sabp.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

import app.morphe.extension.sabp.EppyTheme;

/**
 * The big Material 3 Expressive play/pause control. Paused: a circle with a play glyph.
 * Playing: morphs to a rounded square with a pause glyph (shape change is the state cue, not
 * colour alone). Morph is skipped when reduced motion is on.
 */
public class PlayButton extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private EppyIcon play, pause;
    private boolean playing;
    private float morph; // 0 = circle (paused), 1 = rounded square (playing)
    private ValueAnimator anim;
    private EppyTheme t;

    public PlayButton(Context c) {
        super(c);
        setClickable(true);
        setFocusable(true);
    }

    public void apply(EppyTheme t) {
        this.t = t;
        bg.setColor(t.primary);
        play = new EppyIcon(EppyIcon.PLAY, t.onPrimary);
        pause = new EppyIcon(EppyIcon.PAUSE, t.onPrimary);
        updateDescription();
        invalidate();
    }

    public void setPlaying(boolean p, boolean animate) {
        if (p == playing && anim == null) return;
        playing = p;
        updateDescription();
        float target = p ? 1f : 0f;
        if (anim != null) anim.cancel();
        if (!animate || (t != null && t.reduceMotion)) {
            morph = target;
            anim = null;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(morph, target);
        anim.setDuration(350);
        anim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        anim.addUpdateListener(a -> {
            morph = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private void updateDescription() {
        setContentDescription(playing ? "Pause" : "Play");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (play == null) return;
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        // Playing state is slightly wider: an expressive "pill-square".
        float hw = size / 2f * (1f + 0.12f * morph);
        float hh = size / 2f;
        float radius = hh * (1f - 0.42f * morph);
        r.set(cx - hw, cy - hh, cx + hw, cy + hh);
        if (isPressed()) bg.setAlpha(225);
        else bg.setAlpha(255);
        canvas.drawRoundRect(r, radius, radius, bg);
        int icon = Math.round(size * 0.42f);
        EppyIcon g = morph > 0.5f ? pause : play;
        g.setBounds((int) (cx - icon / 2f), (int) (cy - icon / 2f), (int) (cx + icon / 2f), (int) (cy + icon / 2f));
        g.draw(canvas);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }
}
