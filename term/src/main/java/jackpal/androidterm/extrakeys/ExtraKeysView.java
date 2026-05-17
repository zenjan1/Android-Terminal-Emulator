package jackpal.androidterm.extrakeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simplified extra keys view based on GridLayout.
 * Uses custom-drawn KeyButton views to avoid Android theming issues.
 */
public class ExtraKeysView extends GridLayout {

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

    private static final int BTN_BG = Color.rgb(48, 48, 48);
    private static final int BTN_PRESSED = Color.rgb(96, 96, 96);
    private static final int BTN_SPECIAL_ACTIVE = Color.rgb(96, 96, 96);
    private static final int BTN_TEXT = Color.rgb(224, 224, 224);
    private static final int BTN_TEXT_ACTIVE = Color.WHITE;

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHandler = new Handler(Looper.getMainLooper());
        for (String k : SPECIAL_KEYS) mSpecialButtonStates.put(k, false);
    }

    public void setOnKeyListener(OnKeyListener listener) {
        mListener = listener;
    }

    public void reload(ExtraKeysInfo info) {
        mExtraKeysInfo = info;
        removeAllViews();
        if (info == null) return;

        ExtraKeyButton[][] buttons = info.getMatrix();
        setRowCount(buttons.length);
        int maxCol = ExtraKeysInfo.maxColumns(buttons);
        setColumnCount(maxCol);

        for (int row = 0; row < buttons.length; row++) {
            for (int col = 0; col < buttons[row].length; col++) {
                final ExtraKeyButton btnInfo = buttons[row][col];
                final KeyButton btn = new KeyButton(getContext(), btnInfo.getDisplay());

                if (btnInfo.isSpecial()) {
                    btn.setActive(mSpecialButtonStates.getOrDefault(btnInfo.getKey(), false));
                }

                final ExtraKeyButton finalBtnInfo = btnInfo;
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (finalBtnInfo.isSpecial()) {
                            boolean newState = !mSpecialButtonStates.getOrDefault(finalBtnInfo.getKey(), false);
                            mSpecialButtonStates.put(finalBtnInfo.getKey(), newState);
                            btn.setActive(newState);
                        } else if (finalBtnInfo.isMacro()) {
                            fireText(finalBtnInfo.getMacro());
                        } else {
                            fireKey(finalBtnInfo.getKey());
                        }
                    }
                });

                btn.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                btn.setPressed(true);
                                if (REPETITIVE_KEYS.contains(finalBtnInfo.getKey()) && !finalBtnInfo.isSpecial()) {
                                    mLongPressCount = 0;
                                    mScheduler = Executors.newSingleThreadScheduledExecutor();
                                    mScheduler.scheduleWithFixedDelay(new Runnable() {
                                        @Override
                                        public void run() {
                                            mLongPressCount++;
                                            fireKey(finalBtnInfo.getKey());
                                        }
                                    }, 400, 80, TimeUnit.MILLISECONDS);
                                }
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                btn.setPressed(false);
                                if (mScheduler != null) {
                                    mScheduler.shutdownNow();
                                    mScheduler = null;
                                }
                                mLongPressCount = 0;
                                return true;
                        }
                        return false;
                    }
                });

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 0;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1f);
                params.rowSpec = GridLayout.spec(row, GridLayout.FILL, 1f);
                params.setMargins(2, 2, 2, 2);
                btn.setLayoutParams(params);
                addView(btn);
            }
        }
    }

    private void fireKey(String keyName) {
        if (mListener != null) {
            mListener.onExtraKeyClick(keyName);
        }
    }

    private void fireText(String text) {
        if (mListener != null) {
            mListener.onExtraKeyClick(text);
        }
    }

    public boolean isCtrlDown() { return mSpecialButtonStates.getOrDefault("CTRL", false); }
    public boolean isAltDown() { return mSpecialButtonStates.getOrDefault("ALT", false); }
    public boolean isShiftDown() { return mSpecialButtonStates.getOrDefault("SHIFT", false); }
    public boolean isFnDown() { return mSpecialButtonStates.getOrDefault("FN", false); }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mScheduler != null) mScheduler.shutdownNow();
    }

    /**
     * A button that draws itself, completely bypassing Android theming.
     */
    static class KeyButton extends View {
        private final Paint bgPaint;
        private final Paint textPaint;
        private final Rect textBounds;
        private final String text;
        private boolean isPressed;
        private boolean isActive;

        public KeyButton(Context context, String text) {
            super(context);
            this.text = text;
            this.isPressed = false;
            this.isActive = false;

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(26f);
            textPaint.setFakeBoldText(true);

            textBounds = new Rect();
            textPaint.getTextBounds(text, 0, text.length(), textBounds);

            setWillNotDraw(false);
            setClickable(true);
        }

        public void setPressed(boolean pressed) {
            this.isPressed = pressed;
            invalidate();
        }

        public void setActive(boolean active) {
            this.isActive = active;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();

            // Draw background
            if (isPressed) {
                bgPaint.setColor(BTN_PRESSED);
            } else if (isActive) {
                bgPaint.setColor(BTN_SPECIAL_ACTIVE);
            } else {
                bgPaint.setColor(BTN_BG);
            }
            RectF rect = new RectF(0, 0, width, height);
            canvas.drawRoundRect(rect, 8f, 8f, bgPaint);

            // Draw text
            textPaint.setColor(isPressed ? BTN_TEXT_ACTIVE : BTN_TEXT);
            float cx = width / 2f;
            float cy = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(text, cx, cy, textPaint);
        }
    }
}
