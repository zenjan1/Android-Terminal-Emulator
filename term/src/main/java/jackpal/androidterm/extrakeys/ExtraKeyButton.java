package jackpal.androidterm.extrakeys;

/**
 * Data model for a single extra key button.
 */
public class ExtraKeyButton {
    private final String key;
    private final String display;
    private final String popup;
    private final String macro;

    public ExtraKeyButton(String key, String display, String popup, String macro) {
        this.key = key;
        this.display = display != null ? display : key;
        this.popup = popup;
        this.macro = macro;
    }

    public String getKey() { return key; }
    public String getDisplay() { return display; }
    public String getPopup() { return popup; }
    public String getMacro() { return macro; }
    public boolean isMacro() { return macro != null; }
    public boolean isSpecial() {
        return "CTRL".equals(key) || "ALT".equals(key) || "SHIFT".equals(key) || "FN".equals(key);
    }
}
