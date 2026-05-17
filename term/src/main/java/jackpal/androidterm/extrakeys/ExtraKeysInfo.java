package jackpal.androidterm.extrakeys;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses extra keys JSON configuration.
 * Format: array of arrays, each inner array is a row of keys.
 * Each key can be:
 *   - A string: the key name (e.g., "ESC", "TAB")
 *   - An object: {"key": "ESC", "display": "⎋", "popup": "TAB"}
 * Special keys: CTRL, ALT, SHIFT, FN
 */
public class ExtraKeysInfo {

    public interface OnKeyListener {
        void onKey(String keyName, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown);
        void onText(String text);
    }

    private ExtraKeyButton[][] mButtons;

    public ExtraKeysInfo(String json) throws JSONException {
        if (json == null || json.isEmpty()) {
            mButtons = new ExtraKeyButton[0][];
            return;
        }
        JSONArray arr = new JSONArray(json);
        int rows = arr.length();
        mButtons = new ExtraKeyButton[rows][];
        for (int i = 0; i < rows; i++) {
            JSONArray rowArr = arr.getJSONArray(i);
            int cols = rowArr.length();
            mButtons[i] = new ExtraKeyButton[cols];
            for (int j = 0; j < cols; j++) {
                Object item = rowArr.get(j);
                if (item instanceof String) {
                    mButtons[i][j] = new ExtraKeyButton((String) item, null, null, null);
                } else if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    String key = obj.optString("key", "");
                    String display = obj.optString("display", key);
                    String popup = obj.has("popup") ? obj.optString("popup", null) : null;
                    String macro = obj.has("macro") ? obj.optString("macro", null) : null;
                    mButtons[i][j] = new ExtraKeyButton(key, display, popup, macro);
                }
            }
        }
    }

    public ExtraKeyButton[][] getMatrix() {
        return mButtons;
    }

    public int getRowCount() {
        return mButtons.length;
    }

    public static int maxColumns(ExtraKeyButton[][] buttons) {
        int max = 0;
        for (ExtraKeyButton[] row : buttons) {
            if (row.length > max) max = row.length;
        }
        return max;
    }
}
