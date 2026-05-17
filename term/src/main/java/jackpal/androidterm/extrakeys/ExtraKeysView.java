package jackpal.androidterm.extrakeys;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.PopupWindow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simplified extra keys view based on GridLayout.
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
    private PopupWindow mPopupWindow;
    private boolean mCtrlDown, mAltDown, mShiftDown, mFnDown;

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
                Button btn = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
                btn.setText(btnInfo.getDisplay());
                btn.setTextColor(Color.WHITE);
                btn.setAllCaps(true);
                btn.setPadding(8, 4, 8, 4);
                btn.setTextSize(12);
                btn.setBackgroundColor(Color.argb(60, 128, 128, 128));

                if (btnInfo.isSpecial()) {
                    updateSpecialButtonAppearance(btn, btnInfo.getKey());
                }

                final Button finalBtn = btn;
                final ExtraKeyButton finalBtnInfo = btnInfo;
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (finalBtnInfo.isSpecial()) {
                            toggleSpecial(finalBtnInfo.getKey(), finalBtn);
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
                                v.setBackgroundColor(Color.argb(120, 128, 128, 128));
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
                                v.setBackgroundColor(finalBtnInfo.isSpecial() ? getSpecialBtnColor(finalBtnInfo.getKey()) : Color.argb(60, 128, 128, 128));
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
                params.setMargins(1, 1, 1, 1);
                btn.setLayoutParams(params);
                addView(btn);
            }
        }
    }

    private void toggleSpecial(String key, Button btn) {
        boolean newState = !mSpecialButtonStates.getOrDefault(key, false);
        mSpecialButtonStates.put(key, newState);
        updateSpecialButtonAppearance(btn, key);
    }

    private void updateSpecialButtonAppearance(Button btn, String key) {
        boolean active = mSpecialButtonStates.getOrDefault(key, false);
        btn.setBackgroundColor(active ? Color.argb(180, 128, 128, 128) : Color.argb(60, 128, 128, 128));
    }

    private int getSpecialBtnColor(String key) {
        return mSpecialButtonStates.getOrDefault(key, false) ? Color.argb(180, 128, 128, 128) : Color.argb(60, 128, 128, 128);
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
}
