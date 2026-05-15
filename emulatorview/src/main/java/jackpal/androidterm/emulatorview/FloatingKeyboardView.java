package jackpal.androidterm.emulatorview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FloatingKeyboardView extends FrameLayout {
    private static final int KEY_STATE_NORMAL = android.R.drawable.btn_default;
    private static final int KEY_STATE_PRESSED = android.R.drawable.btn_default;
    
    private TermSession mSession;
    private TermKeyListener mKeyListener;
    private boolean mShowKeyboard = true;
    private float mKeyHeight;
    private int mDensityDpi;
    
    private Map<TextView, Integer> mKeyMap = new HashMap<>();
    private Map<TextView, Runnable> mKeyLongPressMap = new HashMap<>();
    
    private LinearLayout mMainLayout;
    private LinearLayout mKeyRows[];
    private Handler mHandler = new Handler(Looper.getMainLooper());
    
    private boolean mCtrlActive = false;
    private boolean mAltActive = false;
    private boolean mShiftActive = false;
    
    private OnKeyboardStateChangeListener mKeyboardStateListener;
    
    public interface OnKeyboardStateChangeListener {
        void onKeyboardVisibilityChanged(boolean visible);
    }
    
    public FloatingKeyboardView(Context context) {
        super(context);
        init(context);
    }
    
    public void setOnKeyboardStateChangeListener(OnKeyboardStateChangeListener listener) {
        mKeyboardStateListener = listener;
    }
    
    private void init(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mDensityDpi = metrics.densityDpi;
        mKeyHeight = 48 * metrics.density;
        
        setBackgroundColor(Color.parseColor("#2d2d2d"));
        
        mMainLayout = new LinearLayout(context);
        mMainLayout.setOrientation(LinearLayout.VERTICAL);
        mMainLayout.setLayoutParams(new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        setupKeyRows(context);
        
        addView(mMainLayout);
    }
    
    public void attachToSession(TermSession session, TermKeyListener keyListener) {
        mSession = session;
        mKeyListener = keyListener;
    }
    
    private void setupKeyRows(Context context) {
        mKeyRows = new LinearLayout[3];
        for (int i = 0; i < 3; i++) {
            mKeyRows[i] = new LinearLayout(context);
            mKeyRows[i].setOrientation(LinearLayout.HORIZONTAL);
            mKeyRows[i].setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            mKeyRows[i].setGravity(Gravity.CENTER);
        }
        
        setupRow1(context);
        setupRow2(context);
        setupRow3(context);
        
        for (LinearLayout row : mKeyRows) {
            mMainLayout.addView(row);
        }
    }
    
    private void setupRow1(Context context) {
        LinearLayout row = mKeyRows[0];
        row.addView(createKey(context, "ESC", () -> sendEscape(), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "TAB", () -> sendTab(), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "CTRL", () -> toggleCtrl(), 1.2f, true));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "ALT", () -> toggleAlt(), 1.2f, true));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "⇧", () -> toggleShift(), 1.0f, true, true));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "↑", () -> sendArrow(Keyboard.KEYCODE_UP), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "↓", () -> sendArrow(Keyboard.KEYCODE_DOWN), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "←", () -> sendArrow(Keyboard.KEYCODE_LEFT), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "→", () -> sendArrow(Keyboard.KEYCODE_RIGHT), 1.0f));
    }
    
    private void setupRow2(Context context) {
        LinearLayout row = mKeyRows[1];
        row.addView(createKey(context, "HOME", () -> sendHome(), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "END", () -> sendEnd(), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "PgUp", () -> sendPageUp(), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "PgDn", () -> sendPageDown(), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "Ins", () -> sendInsert(), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "Del", () -> sendDelete(), 1.0f));
    }
    
    private void setupRow3(Context context) {
        LinearLayout row = mKeyRows[2];
        
        TextView textInputKey = createKey(context, "切换文字输入", () -> toggleTextInput(), 2.0f);
        textInputKey.setBackgroundColor(Color.parseColor("#4a4a4a"));
        textInputKey.setTextSize(10);
        row.addView(textInputKey);
        row.addView(createSpace(context, 8));
        
        row.addView(createKey(context, "F1", () -> sendFunctionKey(1), 1.0f));
        row.addView(createSpace(context, 4));
        row.addView(createKey(context, "F2", () -> sendFunctionKey(2), 1.0f));
        row.addView(createSpace(context, 4));
        row.addView(createKey(context, "F3", () -> sendFunctionKey(3), 1.0f));
        row.addView(createSpace(context, 4));
        row.addView(createKey(context, "F4", () -> sendFunctionKey(4), 1.0f));
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "F5", () -> sendFunctionKey(5), 1.0f));
        row.addView(createSpace(context, 4));
        row.addView(createKey(context, "F6", () -> sendFunctionKey(6), 1.0f));
        row.addView(createSpace(context, 4));
        row.addView(createKey(context, "F7", () -> sendFunctionKey(7), 1.0f));
        row.addView(createSpace(context, 4));
        row.addView(createKey(context, "F8", () -> sendFunctionKey(8), 1.0f));
    }
    
    private OnTextInputToggleListener mTextInputToggleListener;
    
    public interface OnTextInputToggleListener {
        void onTextInputToggle();
    }
    
    public void setOnTextInputToggleListener(OnTextInputToggleListener listener) {
        mTextInputToggleListener = listener;
    }
    
    private void toggleTextInput() {
        if (mTextInputToggleListener != null) {
            mTextInputToggleListener.onTextInputToggle();
        }
    }
    
    private TextView createKey(Context context, String label, Runnable action, float weight) {
        return createKey(context, label, action, weight, false);
    }
    
    private TextView createKey(Context context, String label, Runnable action, float weight, boolean isToggle) {
        return createKey(context, label, action, weight, isToggle, false);
    }
    
    private TextView createKey(Context context, String label, Runnable action, float weight, 
                              boolean isToggle, boolean isStateToggle) {
        TextView key = new TextView(context);
        key.setText(label);
        key.setTextColor(Color.WHITE);
        key.setTextSize(12);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setGravity(Gravity.CENTER);
        key.setBackgroundResource(KEY_STATE_NORMAL);
        
        int padding = (int)(8 * context.getResources().getDisplayMetrics().density);
        key.setPadding(padding, padding, padding, padding);
        
        key.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            (int)mKeyHeight,
            weight
        ));
        
        key.setClickable(true);
        key.setFocusable(true);
        
        final boolean toggleKey = isStateToggle;
        
        key.setOnClickListener(v -> {
            playKeyClick();
            if (toggleKey) {
                TextView tv = (TextView)v;
                if (tv.getCurrentTextColor() == Color.parseColor("#ff6b6b")) {
                    tv.setTextColor(Color.WHITE);
                } else {
                    tv.setTextColor(Color.parseColor("#ff6b6b"));
                }
            }
            action.run();
        });
        
        return key;
    }
    
    private View createSpace(Context context, int widthDp) {
        View space = new View(context);
        int widthPx = (int)(widthDp * context.getResources().getDisplayMetrics().density);
        space.setLayoutParams(new LinearLayout.LayoutParams(
            widthPx,
            1
        ));
        return space;
    }
    
    private void playKeyClick() {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }
    }
    
    private void sendEscape() {
        if (mSession == null) return;
        try {
            mSession.write(27);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendTab() {
        if (mSession == null) return;
        try {
            mSession.write('\t');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void toggleCtrl() {
        mCtrlActive = !mCtrlActive;
        if (mKeyListener != null) {
            mKeyListener.handleControlKey(mCtrlActive);
        }
        invalidate();
    }
    
    private void toggleAlt() {
        mAltActive = !mAltActive;
        if (mKeyListener != null) {
            if (mAltActive) {
                mKeyListener.handleKeyCode(android.view.KeyEvent.KEYCODE_ALT_LEFT, null, false);
            } else {
                mKeyListener.handleKeyCode(android.view.KeyEvent.KEYCODE_ALT_LEFT, null, false);
            }
        }
        invalidate();
    }
    
    private void toggleShift() {
        mShiftActive = !mShiftActive;
        invalidate();
    }
    
    private void sendArrow(int keyCode) {
        if (mSession == null) return;
        try {
            mSession.write(27);
            switch (keyCode) {
                case Keyboard.KEYCODE_UP:
                    mSession.write('[');
                    mSession.write('A');
                    break;
                case Keyboard.KEYCODE_DOWN:
                    mSession.write('[');
                    mSession.write('B');
                    break;
                case Keyboard.KEYCODE_RIGHT:
                    mSession.write('[');
                    mSession.write('C');
                    break;
                case Keyboard.KEYCODE_LEFT:
                    mSession.write('[');
                    mSession.write('D');
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendHome() {
        if (mSession == null) return;
        try {
            mSession.write(27);
            mSession.write('[');
            mSession.write('H');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendEnd() {
        if (mSession == null) return;
        try {
            mSession.write(27);
            mSession.write('[');
            mSession.write('F');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendPageUp() {
        if (mSession == null) return;
        try {
            mSession.write(27);
            mSession.write('[');
            mSession.write('5');
            mSession.write('~');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendPageDown() {
        if (mSession == null) return;
        try {
            mSession.write(27);
            mSession.write('[');
            mSession.write('6');
            mSession.write('~');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendInsert() {
        if (mSession == null) return;
        try {
            mSession.write(27);
            mSession.write('[');
            mSession.write('2');
            mSession.write('~');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendDelete() {
        if (mSession == null) return;
        try {
            mSession.write(27);
            mSession.write('[');
            mSession.write('3');
            mSession.write('~');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendFunctionKey(int num) {
        if (mSession == null) return;
        try {
            mSession.write(27);
            mSession.write('O');
            char keyChar;
            switch (num) {
                case 1: keyChar = 'P'; break;
                case 2: keyChar = 'Q'; break;
                case 3: keyChar = 'R'; break;
                case 4: keyChar = 'S'; break;
                case 5: keyChar = 'T'; break;
                case 6: keyChar = 'U'; break;
                case 7: keyChar = 'V'; break;
                case 8: keyChar = 'W'; break;
                default: keyChar = 'P'; break;
            }
            mSession.write(keyChar);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void toggleKeyboard() {
        mShowKeyboard = !mShowKeyboard;
        setVisibility(mShowKeyboard ? View.VISIBLE : View.GONE);
        if (mKeyboardStateListener != null) {
            mKeyboardStateListener.onKeyboardVisibilityChanged(mShowKeyboard);
        }
    }
    
    public boolean isKeyboardVisible() {
        return mShowKeyboard;
    }
    
    public void showKeyboard() {
        if (!mShowKeyboard) {
            toggleKeyboard();
        }
    }
    
    public void hideKeyboard() {
        if (mShowKeyboard) {
            toggleKeyboard();
        }
    }
    
    public int getKeyboardHeight() {
        return mMainLayout.getHeight();
    }
}
