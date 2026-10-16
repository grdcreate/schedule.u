package com.controlscheduler.app;

/**
 * A single control that a schedule can drive.
 *
 *  DIRECT  - the app performs it silently, no special access needed
 *  PRIV    - needs root or Shizuku (ADB shell); otherwise it falls back
 *            to the kill switch (for network actions) or to the settings screen
 *  VPN     - handled by the built-in local VPN (works on every phone, no root)
 *  SCREEN  - just opens the matching system screen
 *  CMD     - custom shell command (root / Shizuku)
 */
public enum Action {

    // ---- silent, no special access ----
    FLASHLIGHT_ON("Flashlight on", "FL", "Toggles", Kind.DIRECT, false, null),
    FLASHLIGHT_OFF("Flashlight off", "FL", "Toggles", Kind.DIRECT, false, null),
    DND_ON("Do Not Disturb on", "DD", "Toggles", Kind.DIRECT, false, "Needs Do Not Disturb access (Setup)."),
    DND_OFF("Do Not Disturb off", "DD", "Toggles", Kind.DIRECT, false, "Needs Do Not Disturb access (Setup)."),
    ROTATE_ON("Auto-rotate on", "AR", "Toggles", Kind.DIRECT, false, "Needs Modify system settings (Setup)."),
    ROTATE_OFF("Auto-rotate off", "AR", "Toggles", Kind.DIRECT, false, "Needs Modify system settings (Setup)."),

    // ---- sound & screen ----
    RINGER_SILENT("Ringer: silent", "SL", "Sound & screen", Kind.DIRECT, false, null),
    RINGER_VIBRATE("Ringer: vibrate", "VB", "Sound & screen", Kind.DIRECT, false, null),
    RINGER_NORMAL("Ringer: normal", "RN", "Sound & screen", Kind.DIRECT, false, null),
    VOLUME("Set media volume", "VO", "Sound & screen", Kind.DIRECT, true, null),
    BRIGHTNESS("Set brightness", "BR", "Sound & screen", Kind.DIRECT, true, "Needs Modify system settings (Setup)."),

    // ---- network radios (root / Shizuku; auto-falls back to kill switch) ----
    WIFI_OFF("Wi-Fi off", "WF", "Network", Kind.PRIV, false, "Root or Shizuku switches Wi-Fi off for real."),
    WIFI_ON("Wi-Fi on", "WF", "Network", Kind.PRIV, false, "Root or Shizuku switches Wi-Fi on for real."),
    DATA_OFF("Mobile data off", "MD", "Network", Kind.PRIV, false, "No root/Shizuku? The kill switch blocks all internet instead."),
    DATA_ON("Mobile data on", "MD", "Network", Kind.PRIV, false, null),
    AIRPLANE_ON("Airplane mode on", "AP", "Network", Kind.PRIV, false, "No root/Shizuku? The kill switch blocks all internet instead."),
    AIRPLANE_OFF("Airplane mode off", "AP", "Network", Kind.PRIV, false, null),
    BT_OFF("Bluetooth off", "BT", "Network", Kind.PRIV, false, null),
    BT_ON("Bluetooth on", "BT", "Network", Kind.PRIV, false, null),
    HOTSPOT_OFF("Hotspot off", "HS", "Network", Kind.PRIV, false, null),
    HOTSPOT_ON("Hotspot on", "HS", "Network", Kind.PRIV, false, null),
    LOCATION_OFF("Location off", "LO", "Network", Kind.PRIV, false, null),
    LOCATION_ON("Location on", "LO", "Network", Kind.PRIV, false, null),
    NFC_OFF("NFC off", "NF", "Network", Kind.PRIV, false, null),
    NFC_ON("NFC on", "NF", "Network", Kind.PRIV, false, null),

    // ---- built-in kill switch (no root needed) ----
    INTERNET_OFF("Internet off (kill switch)", "IO", "Internet", Kind.VPN, false, "Blocks all internet on the phone until you switch it back on."),
    INTERNET_ON("Internet on", "IO", "Internet", Kind.VPN, false, null),

    // ---- just open the matching system screen ----
    OPEN_WIFI_SETTINGS("Open Wi-Fi screen", "WF", "Screens", Kind.SCREEN, false, null),
    OPEN_INTERNET_PANEL("Open internet panel", "IP", "Screens", Kind.SCREEN, false, null),
    OPEN_DATA_SETTINGS("Open mobile data screen", "MD", "Screens", Kind.SCREEN, false, null),
    OPEN_AIRPLANE_SETTINGS("Open airplane screen", "AP", "Screens", Kind.SCREEN, false, null),
    OPEN_LOCATION_SETTINGS("Open location screen", "LO", "Screens", Kind.SCREEN, false, null),
    OPEN_NFC_SETTINGS("Open NFC screen", "NF", "Screens", Kind.SCREEN, false, null),
    OPEN_HOTSPOT_SETTINGS("Open hotspot screen", "HS", "Screens", Kind.SCREEN, false, null),
    OPEN_BLUETOOTH_SETTINGS("Open Bluetooth screen", "BT", "Screens", Kind.SCREEN, false, null),

    // ---- advanced ----
    COMMAND("Run shell command", "RC", "Advanced", Kind.CMD, false, "Runs through root or Shizuku, e.g. svc data disable");

    public enum Kind { DIRECT, PRIV, VPN, SCREEN, CMD }

    public final String label;
    public final String tag;
    public final String group;
    public final Kind kind;
    public final boolean needsLevel;
    public final String note;

    Action(String label, String tag, String group, Kind kind, boolean needsLevel, String note) {
        this.label = label; this.tag = tag; this.group = group;
        this.kind = kind; this.needsLevel = needsLevel; this.note = note;
    }

    public static Action of(String name) {
        if (name == null) return FLASHLIGHT_ON;
        for (Action a : values()) if (a.name().equals(name)) return a;
        return legacy(name);
    }

    /**
     * Names used by v1 of this app (and a few friendly aliases) so old saved
     * schedules keep working after an upgrade instead of silently resetting.
     */
    private static Action legacy(String name) {
        switch (name) {
            case "WIFI": case "WIFI_OFF": return WIFI_OFF;
            case "WIFI_SETTINGS": return OPEN_WIFI_SETTINGS;
            case "INTERNET_PANEL": return OPEN_INTERNET_PANEL;
            case "MOBILE_DATA": return DATA_OFF;
            case "AIRPLANE": return AIRPLANE_ON;
            case "BLUETOOTH": return BT_OFF;
            case "FLASHLIGHT": return FLASHLIGHT_ON;
            case "DO_NOT_DISTURB": return DND_ON;
            case "LOCATION": return LOCATION_OFF;
            case "NFC": return NFC_OFF;
            case "HOTSPOT": return HOTSPOT_OFF;
            case "SETTINGS": case "OPEN_SETTINGS": return OPEN_WIFI_SETTINGS;
            case "CUSTOM_COMMAND": return COMMAND;
            default: return FLASHLIGHT_ON;
        }
    }

    /** The settings screen that corresponds to a radio action (used as last-resort fallback). */
    public Action fallbackScreen() {
        switch (this) {
            case WIFI_OFF: case WIFI_ON: return OPEN_WIFI_SETTINGS;
            case DATA_OFF: case DATA_ON: return OPEN_DATA_SETTINGS;
            case AIRPLANE_ON: case AIRPLANE_OFF: return OPEN_AIRPLANE_SETTINGS;
            case LOCATION_OFF: case LOCATION_ON: return OPEN_LOCATION_SETTINGS;
            case NFC_OFF: case NFC_ON: return OPEN_NFC_SETTINGS;
            case HOTSPOT_OFF: case HOTSPOT_ON: return OPEN_HOTSPOT_SETTINGS;
            case BT_OFF: case BT_ON: return OPEN_BLUETOOTH_SETTINGS;
            default: return null;
        }
    }
}
