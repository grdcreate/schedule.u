package com.controlscheduler.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

/** Shared styling helpers so every screen keeps the same premium dark look. */
public class Ui {

    public static final int ACCENT = 0xFFFF2D6F;
    public static final int TRACK_OFF = 0xFF23232A;
    public static final int THUMB_OFF = 0xFF6E6E78;
    public static final int TEXT = 0xFFF5F5F7;
    public static final int TEXT2 = 0xFF8A8A93;
    public static final int TEXT3 = 0xFF6A6A73;
    public static final int SUCCESS = 0xFFFF2D6F;
    public static final int DANGER = 0xFFFF5A5A;
    public static final int WARN = 0xFFE8B24A;

    /** Script-ish italic used for the accent word, like the reference artwork. */
    public static android.graphics.Typeface script() {
        return android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC);
    }

    public static void applyScript(android.widget.TextView tv) {
        tv.setTypeface(script());
    }

    public static void tintSwitch(android.widget.Switch sw) {
        if (sw == null) return;
        ColorStateList thumb = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.WHITE, THUMB_OFF});
        ColorStateList track = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ACCENT, TRACK_OFF});
        sw.setThumbTintList(thumb);
        sw.setTrackTintList(track);
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** "11:30 PM" or "23:30", whichever format the user picked. */
    public static String time(Context c, int hour, int minute) {
        return TimeFmt.label(hour, minute, Prefs.twelve(c));
    }

    /**
     * Big clock face: "11:30" with a small rose "PM" after it in 12-hour mode,
     * or plain "23:30" in 24-hour mode.
     */
    public static CharSequence bigTime(Context c, int hour, int minute, float suffixScale) {
        if (!Prefs.twelve(c)) return TimeFmt.h24(hour, minute);
        SpannableStringBuilder sb = new SpannableStringBuilder(TimeFmt.clockPart(hour, minute));
        int from = sb.length() + 1;
        sb.append(" ").append(TimeFmt.meridiem(hour));
        sb.setSpan(new RelativeSizeSpan(suffixScale), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(ACCENT), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }
}
