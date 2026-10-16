package com.controlscheduler.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick settings tile: tap = block/unblock all internet instantly. */
public class QuickTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean blocked = Prefs.internetBlocked(this);
        t.setState(blocked ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(blocked ? "Internet off" : "Internet on");
        t.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean blocked = !Prefs.internetBlocked(this);
        Prefs.setInternetBlocked(this, blocked);

        // first time the tile is used we still need the one-off VPN consent
        if (blocked && !Prefs.vpnConsent(this)) {
            Intent consent = VpnConsentActivity.intent(this);
            consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 1, consent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(consent);
            }
            refresh();
            return;
        }

        EngineService.start(this, blocked ? EngineService.ACTION_BLOCK : EngineService.ACTION_UNBLOCK);
        refresh();
    }
}
