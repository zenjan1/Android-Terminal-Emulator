package jackpal.androidterm.emulatorview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TextInputKeyboardView extends FrameLayout {
    private TermSession mSession;
    private TermKeyListener mKeyListener;
    private boolean mShowKeyboard = true;
    private float mKeyHeight;
    private int mDensityDpi;
    
    private LinearLayout mMainLayout;
    private LinearLayout mKeyRows[];
    
    private boolean mShiftActive = false;
    private boolean mCapsLock = false;
    
    private OnKeyboardStateChangeListener mKeyboardStateListener;
    
    public interface OnKeyboardStateChangeListener {
        void onKeyboardVisibilityChanged(boolean visible);
    }
    
    public TextInputKeyboardView(Context context) {
        super(context);
        init(context);
    }
    
    public void setOnKeyboardStateChangeListener(OnKeyboardStateChangeListener listener) {
        mKeyboardStateListener = listener;
    }
    
    private void init(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mDensityDpi = metrics.densityDpi;
        mKeyHeight = 44 * metrics.density;
        
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
        mKeyRows = new LinearLayout[4];
        for (int i = 0; i < 4; i++) {
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
        setupRow4(context);
        
        for (LinearLayout row : mKeyRows) {
            mMainLayout.addView(row);
        }
    }
    
    private void setupRow1(Context context) {
        LinearLayout row = mKeyRows[0];
        row.addView(createKey(context, "q", () -> sendChar('q'), 1.0f));
        row.addView(createKey(context, "w", () -> sendChar('w'), 1.0f));
        row.addView(createKey(context, "e", () -> sendChar('e'), 1.0f));
        row.addView(createKey(context, "r", () -> sendChar('r'), 1.0f));
        row.addView(createKey(context, "t", () -> sendChar('t'), 1.0f));
        row.addView(createKey(context, "y", () -> sendChar('y'), 1.0f));
        row.addView(createKey(context, "u", () -> sendChar('u'), 1.0f));
        row.addView(createKey(context, "i", () -> sendChar('i'), 1.0f));
        row.addView(createKey(context, "o", () -> sendChar('o'), 1.0f));
        row.addView(createKey(context, "p", () -> sendChar('p'), 1.0f));
    }
    
    private void setupRow2(Context context) {
        LinearLayout row = mKeyRows[1];
        row.addView(createSpace(context, 16));
        row.addView(createKey(context, "a", () -> sendChar('a'), 1.0f));
        row.addView(createKey(context, "s", () -> sendChar('s'), 1.0f));
        row.addView(createKey(context, "d", () -> sendChar('d'), 1.0f));
        row.addView(createKey(context, "f", () -> sendChar('f'), 1.0f));
        row.addView(createKey(context, "g", () -> sendChar('g'), 1.0f));
        row.addView(createKey(context, "h", () -> sendChar('h'), 1.0f));
        row.addView(createKey(context, "j", () -> sendChar('j'), 1.0f));
        row.addView(createKey(context, "k", () -> sendChar('k'), 1.0f));
        row.addView(createKey(context, "l", () -> sendChar('l'), 1.0f));
        row.addView(createSpace(context, 16));
    }
    
    private void setupRow3(Context context) {
        LinearLayout row = mKeyRows[2];
        
        TextView shiftKey = createKey(context, "⇧", () -> toggleShift(), 1.5f, true);
        shiftKey.setTextSize(16);
        row.addView(shiftKey);
        
        row.addView(createKey(context, "z", () -> sendChar('z'), 1.0f));
        row.addView(createKey(context, "x", () -> sendChar('x'), 1.0f));
        row.addView(createKey(context, "c", () -> sendChar('c'), 1.0f));
        row.addView(createKey(context, "v", () -> sendChar('v'), 1.0f));
        row.addView(createKey(context, "b", () -> sendChar('b'), 1.0f));
        row.addView(createKey(context, "n", () -> sendChar('n'), 1.0f));
        row.addView(createKey(context, "m", () -> sendChar('m'), 1.0f));
        
        TextView delKey = createKey(context, "⌫", () -> sendDelete(), 1.5f);
        delKey.setTextSize(16);
        row.addView(delKey);
    }
    
    private void setupRow4(Context context) {
        LinearLayout row = mKeyRows[3];
        
        TextView toggleKey = createKey(context, "功能键", () -> toggleFunctionKeyboard(), 1.5f);
        toggleKey.setBackgroundColor(Color.parseColor("#4a4a4a"));
        toggleKey.setTextSize(10);
        row.addView(toggleKey);
        
        row.addView(createSpace(context, 8));
        row.addView(createKey(context, "-", () -> sendChar('-'), 1.0f));
        row.addView(createKey(context, "/", () -> sendChar('/'), 1.0f));
        row.addView(createKey(context, ":", () -> sendChar(':'), 1.0f));
        row.addView(createKey(context, ";", () -> sendChar(';'), 1.0f));
        row.addView(createKey(context, "(", () -> sendChar('('), 1.0f));
        row.addView(createKey(context, ")", () -> sendChar(')'), 1.0f));
        row.addView(createKey(context, "$", () -> sendChar('$'), 1.0f));
        row.addView(createKey(context, "&", () -> sendChar('&'), 1.0f));
        row.addView(createKey(context, "@", () -> sendChar('@'), 1.0f));
        row.addView(createKey(context, "\"", () -> sendChar('"'), 1.0f));
        
        row.addView(createSpace(context, 8));
        TextView enterKey = createKey(context, "⏎", () -> sendEnter(), 1.5f);
        enterKey.setTextSize(16);
        enterKey.setBackgroundColor(Color.parseColor("#4a4a4a"));
        row.addView(enterKey);
    }
    
    private OnFunctionKeyboardToggleListener mFunctionKeyboardToggleListener;
    
    public interface OnFunctionKeyboardToggleListener {
        void onFunctionKeyboardToggle();
    }
    
    public void setOnFunctionKeyboardToggleListener(OnFunctionKeyboardToggleListener listener) {
        mFunctionKeyboardToggleListener = listener;
    }
    
    private void toggleFunctionKeyboard() {
        if (mFunctionKeyboardToggleListener != null) {
            mFunctionKeyboardToggleListener.onFunctionKeyboardToggle();
        }
    }
    
    private TextView createKey(Context context, String label, Runnable action, float weight) {
        return createKey(context, label, action, weight, false);
    }
    
    private TextView createKey(Context context, String label, Runnable action, float weight, boolean isToggle) {
        TextView key = new TextView(context);
        key.setText(label);
        key.setTextColor(Color.WHITE);
        key.setTextSize(14);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setGravity(Gravity.CENTER);
        key.setBackgroundColor(Color.parseColor("#3d3d3d"));
        
        int padding = (int)(6 * context.getResources().getDisplayMetrics().density);
        key.setPadding(padding, padding, padding, padding);
        
        key.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            (int)mKeyHeight,
            weight
        ));
        
        key.setClickable(true);
        key.setFocusable(true);
        
        final boolean toggleKey = isToggle;
        final String normalLabel = label;
        
        key.setOnClickListener(v -> {
            playKeyClick();
            if (toggleKey) {
                TextView tv = (TextView)v;
                if (tv.getCurrentTextColor() == Color.parseColor("#ff6b6b")) {
                    tv.setTextColor(Color.WHITE);
                    mShiftActive = false;
                } else {
                    tv.setTextColor(Color.parseColor("#ff6b6b"));
                    mShiftActive = true;
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
    
    private void sendChar(char ch) {
        if (mSession == null) return;
        char toSend = mShiftActive ? Character.toUpperCase(ch) : ch;
        try {
            mSession.write(toSend);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mShiftActive && !mCapsLock) {
            mShiftActive = false;
        }
    }
    
    private void sendDelete() {
        if (mSession == null) return;
        try {
            mSession.write(127);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void sendEnter() {
        if (mSession == null) return;
        try {
            mSession.write('\n');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void toggleShift() {
        if (mShiftActive) {
            mCapsLock = !mCapsLock;
        } else {
            mShiftActive = true;
            mCapsLock = false;
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
