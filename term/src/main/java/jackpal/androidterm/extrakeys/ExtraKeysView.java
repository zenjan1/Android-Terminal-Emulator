package jackpal.androidterm.extrakeys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simplified extra keys view based on GridLayout.
 * Uses custom KeyButton views to avoid Button theming issues.
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
    private boolean mCtrlDown, mAltDown, mShiftDown, mFnDown;

    private static final int BTN_BG = 0xFF303030;
    private static final int BTN_PRESSED = 0xFF505050;
    private static final int BTN_SPECIAL_ACTIVE = 0xFF606060;
    private static final int BTN_TEXT = 0xFFE0E0E0;
    private static final int BTN_TEXT_ACTIVE = 0xFFFFFFFF;
    private static final float CORNER_RADIUS = 8f;

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
                final KeyButton btn = new KeyButton(getContext());
                btn.setKeyText(btnInfo.getDisplay());
                btn.setKeyBgColor(BTN_BG);
                btn.setKeyTextColor(BTN_TEXT);

                if (btnInfo.isSpecial()) {
                    updateSpecialButtonAppearance(btn, btnInfo.getKey());
                }

                final ExtraKeyButton finalBtnInfo = btnInfo;
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (finalBtnInfo.isSpecial()) {
                            toggleSpecial(finalBtnInfo.getKey(), btn);
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
                                btn.setKeyBgColor(BTN_PRESSED);
                                btn.setKeyTextColor(BTN_TEXT_ACTIVE);
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
                                int bg = isActive(finalBtnInfo.getKey()) ? BTN_SPECIAL_ACTIVE : BTN_BG;
                                btn.setKeyBgColor(bg);
                                btn.setKeyTextColor(isActive(finalBtnInfo.getKey()) ? BTN_TEXT_ACTIVE : BTN_TEXT);
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

    private void toggleSpecial(String key, KeyButton btn) {
        boolean newState = !mSpecialButtonStates.getOrDefault(key, false);
        mSpecialButtonStates.put(key, newState);
        btn.setKeyBgColor(newState ? BTN_SPECIAL_ACTIVE : BTN_BG);
        btn.setKeyTextColor(newState ? BTN_TEXT_ACTIVE : BTN_TEXT);
    }

    private void updateSpecialButtonAppearance(KeyButton btn, String key) {
        boolean active = mSpecialButtonStates.getOrDefault(key, false);
        btn.setKeyBgColor(active ? BTN_SPECIAL_ACTIVE : BTN_BG);
        btn.setKeyTextColor(active ? BTN_TEXT_ACTIVE : BTN_TEXT);
    }

    private boolean isActive(String key) {
        return mSpecialButtonStates.getOrDefault(key, false);
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
     * Custom key button that draws its own background and text to avoid theming issues.
     */
    static class KeyButton extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bgRect = new RectF();
        private String text = "";
        private int bgColor = BTN_BG;
        private int textColor = BTN_TEXT;

        public KeyButton(Context context) {
            super(context);
            bgPaint.setStyle(Paint.Style.FILL);
            textPaint.setTextSize(28f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
            setClickable(true);
        }

        public void setKeyText(String t) {
            text = t;
            invalidate();
        }

        public void setKeyBgColor(int color) {
            bgColor = color;
            invalidate();
        }

        public void setKeyTextColor(int color) {
            textColor = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            bgPaint.setColor(bgColor);
            bgRect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(bgRect, CORNER_RADIUS, CORNER_RADIUS, bgPaint);

            textPaint.setColor(textColor);
            float textX = getWidth() / 2f;
            float textY = getHeight() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(text, textX, textY, textPaint);
        }
    }
}
