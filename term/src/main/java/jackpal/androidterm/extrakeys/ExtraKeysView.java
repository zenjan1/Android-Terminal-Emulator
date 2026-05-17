package jackpal.androidterm.extrakeys;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Extra keys view using LinearLayout with styled buttons, following Termux approach.
 */
public class ExtraKeysView extends LinearLayout {

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

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setWeightSum(1f);
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

        for (int row = 0; row < buttons.length; row++) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setOrientation(HORIZONTAL);
            rowLayout.setWeightSum(1f);
            rowLayout.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT, 0, 1f / buttons.length));

            for (int col = 0; col < buttons[row].length; col++) {
                final ExtraKeyButton btnInfo = buttons[row][col];

                // Create button with buttonBarButtonStyle like Termux does
                final Button btn = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);

                btn.setText(btnInfo.getDisplay());
                btn.setTextColor(Color.WHITE);
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                btn.setAllCaps(false);
                btn.setPadding(0, 0, 0, 0);

                if (btnInfo.isSpecial()) {
                    btn.setTextColor(Color.LTGRAY);
                }

                final ExtraKeyButton finalBtnInfo = btnInfo;
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (finalBtnInfo.isSpecial()) {
                            boolean newState = !mSpecialButtonStates.getOrDefault(finalBtnInfo.getKey(), false);
                            mSpecialButtonStates.put(finalBtnInfo.getKey(), newState);
                            btn.setTextColor(Color.WHITE);
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
                                btn.setTextColor(Color.WHITE);
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
                                boolean active = mSpecialButtonStates.getOrDefault(finalBtnInfo.getKey(), false);
                                btn.setTextColor(active ? Color.WHITE : Color.LTGRAY);
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

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
                btn.setLayoutParams(params);
                rowLayout.addView(btn);
            }

            addView(rowLayout);
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
}
