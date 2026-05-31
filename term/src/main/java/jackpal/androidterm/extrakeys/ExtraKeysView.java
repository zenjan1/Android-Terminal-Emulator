package jackpal.androidterm.extrakeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Extra keys view - single custom view that draws all buttons with Canvas.
 * This avoids all Button/TextView theming issues.
 */
public class ExtraKeysView extends View {

    public interface OnKeyListener {
        void onExtraKeyClick(String keyName);
    }

    private static final List<String> REPETITIVE_KEYS = Arrays.asList(
        "UP", "DOWN", "LEFT", "RIGHT", "BKSP", "DEL", "PGUP", "PGDN");

    private static final List<String> SPECIAL_KEYS = Arrays.asList("CTRL", "ALT", "SHIFT", "FN");

    private ExtraKeysInfo mExtraKeysInfo;
    private OnKeyListener mListener;
    private Map<String, Boolean> mSpecialButtonStates = new HashMap<>();
    private ScheduledExecutorService mScheduler;
    private Handler mHandler;
    private int mLongPressCount;

    private Paint bgPaint;
    private Paint textPaint;
    private RectF bgRect;

    private ArrayList<ButtonInfo> mButtons = new ArrayList<>();

    private static class ButtonInfo {
        final String key;
        final String display;
        final boolean isSpecial;
        final boolean isMacro;
        final String macro;
        boolean pressed;
        boolean active;
        float x, y, w, h;

        ButtonInfo(String key, String display, boolean isSpecial, boolean isMacro, String macro) {
            this.key = key;
            this.display = display;
            this.isSpecial = isSpecial;
            this.isMacro = isMacro;
            this.macro = macro;
            this.pressed = false;
            this.active = false;
        }
    }

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        mHandler = new Handler(Looper.getMainLooper());
        for (String k : SPECIAL_KEYS) mSpecialButtonStates.put(k, false);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(36f);
        textPaint.setFakeBoldText(true);
        bgRect = new RectF();
    }

    public void setOnKeyListener(OnKeyListener listener) {
        mListener = listener;
    }

    public void reload(ExtraKeysInfo info) {
        mExtraKeysInfo = info;
        mButtons.clear();
        if (info == null) {
            invalidate();
            return;
        }

        ExtraKeyButton[][] buttons = info.getMatrix();
        for (ExtraKeyButton[] row : buttons) {
            for (ExtraKeyButton btn : row) {
                mButtons.add(new ButtonInfo(
                    btn.getKey(),
                    btn.getDisplay(),
                    btn.isSpecial(),
                    btn.isMacro(),
                    btn.getMacro()
                ));
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        if (mButtons.isEmpty() || width == 0 || height == 0) return;

        // Calculate grid
        int rows = mExtraKeysInfo != null ? mExtraKeysInfo.getMatrix().length : 1;
        int rowH = height / rows;

        int idx = 0;
        ExtraKeyButton[][] matrix = mExtraKeysInfo != null ? mExtraKeysInfo.getMatrix() : null;
        if (matrix == null) return;

        for (int row = 0; row < rows; row++) {
            int cols = matrix[row].length;
            int colW = width / cols;
            for (int col = 0; col < cols; col++) {
                if (idx >= mButtons.size()) break;
                ButtonInfo bi = mButtons.get(idx);
                bi.x = col * colW;
                bi.y = row * rowH;
                bi.w = colW;
                bi.h = rowH;

                // Draw button background only when pressed or active
                if (bi.pressed || bi.active) {
                    bgPaint.setColor(0xFF404040);
                    bgRect.set(bi.x + 2, bi.y + 2, bi.x + bi.w - 2, bi.y + bi.h - 2);
                    canvas.drawRoundRect(bgRect, 6f, 6f, bgPaint);
                }

                // Draw button text
                textPaint.setColor(0xFFFFFFFF);
                float cy = bi.y + bi.h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
                canvas.drawText(bi.display, bi.x + bi.w / 2f, cy, textPaint);

                idx++;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        ButtonInfo hit = null;
        for (ButtonInfo bi : mButtons) {
            if (x >= bi.x && x <= bi.x + bi.w && y >= bi.y && y <= bi.y + bi.h) {
                hit = bi;
                break;
            }
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (hit != null) {
                    hit.pressed = true;
                    invalidate();
                    if (REPETITIVE_KEYS.contains(hit.key) && !hit.isSpecial) {
                        mLongPressCount = 0;
                        mScheduler = Executors.newSingleThreadScheduledExecutor();
                        final String repetitiveKey = hit.key;
                        mScheduler.scheduleWithFixedDelay(new Runnable() {
                            @Override
                            public void run() {
                                mLongPressCount++;
                                fireKey(repetitiveKey);
                            }
                        }, 400, 80, TimeUnit.MILLISECONDS);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (hit != null) {
                    hit.pressed = false;
                    invalidate();
                    if (mScheduler != null) {
                        mScheduler.shutdownNow();
                        mScheduler = null;
                    }
                    mLongPressCount = 0;
                    if (hit.isSpecial) {
                        boolean newState = !mSpecialButtonStates.getOrDefault(hit.key, false);
                        mSpecialButtonStates.put(hit.key, newState);
                        hit.active = newState;
                        invalidate();
                    } else if (hit.isMacro) {
                        fireText(hit.macro);
                    } else {
                        fireKey(hit.key);
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                for (ButtonInfo bi : mButtons) {
                    bi.pressed = false;
                }
                if (mScheduler != null) {
                    mScheduler.shutdownNow();
                    mScheduler = null;
                }
                mLongPressCount = 0;
                invalidate();
                return true;
        }
        return false;
    }

    private void fireKey(String keyName) {
        if (mListener != null) mListener.onExtraKeyClick(keyName);
    }

    private void fireText(String text) {
        if (mListener != null) mListener.onExtraKeyClick(text);
    }

    public boolean isCtrlDown() { return mSpecialButtonStates.getOrDefault("CTRL", false); }
    public boolean isAltDown() { return mSpecialButtonStates.getOrDefault("ALT", false); }
    public boolean isShiftDown() { return mSpecialButtonStates.getOrDefault("SHIFT", false); }
    public boolean isFnDown() { return mSpecialButtonStates.getOrDefault("FN", false); }
}
