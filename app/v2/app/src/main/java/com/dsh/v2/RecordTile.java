package com.dsh.v2;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings：一键暂停/恢复记录。 */
public final class RecordTile extends TileService {

    @Override public void onStartListening() {
        super.onStartListening();
        boolean paused = RecordSwitch.paused(this);
        Tile t = getQsTile();
        if (t != null) {
            t.setState(paused ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.setLabel(paused ? "记录已暂停" : "记录中");
            t.updateTile();
        }
    }

    @Override public void onClick() {
        final boolean paused = !RecordSwitch.paused(this);
        new Thread(() -> RecordSwitch.setPaused(getApplicationContext(), paused),
                "record-tile").start();
        Tile t = getQsTile();
        if (t != null) {
            t.setState(paused ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.setLabel(paused ? "记录已暂停" : "记录中");
            t.updateTile();
        }
    }
}
