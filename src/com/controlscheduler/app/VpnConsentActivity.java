package com.controlscheduler.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

/** The one-time "allow this app to act as a VPN" screen needed for the kill switch. */
public class VpnConsentActivity extends Activity {

    private static final int REQ = 9941;
    /** only true when the user asked for internet-off right now (the switch, the tile) */
    public static final String EXTRA_BLOCK_NOW = "block_now";

    public static Intent intent(Context c) {
        return new Intent(c, VpnConsentActivity.class);
    }

    public static Intent intent(Context c, boolean blockNow) {
        return intent(c).putExtra(EXTRA_BLOCK_NOW, blockNow);
    }

    private boolean blockNow() {
        return getIntent() != null && getIntent().getBooleanExtra(EXTRA_BLOCK_NOW, false);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_consent);
        findViewById(R.id.btn_consent).setOnClickListener(v -> ask());
        findViewById(R.id.btn_later).setOnClickListener(v -> finish());
    }

    private void ask() {
        try {
            Intent prepare = VpnService.prepare(this);
            if (prepare == null) {
                onConsented();
            } else {
                startActivityForResult(prepare, REQ);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "VPN consent not available on this device", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ) {
            if (result == RESULT_OK) onConsented();
            else {
                Toast.makeText(this, "Without this, internet actions fall back to opening screens", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void onConsented() {
        Prefs.setVpnConsent(this, true);
        if (blockNow()) {
            EngineService.start(this, EngineService.ACTION_BLOCK);
            Toast.makeText(this, "Kill switch ready \u2014 internet is off now", Toast.LENGTH_SHORT).show();
        } else {
            // consent for scheduled use: arm the engine, but leave the user online right now
            EngineService.start(this, EngineService.ACTION_ARM);
            Toast.makeText(this, "Kill switch ready \u2014 internet-off steps will now work at their time",
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
