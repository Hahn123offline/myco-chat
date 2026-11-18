package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import com.dsh.trace.Trace;

import java.util.ArrayList;
import java.util.List;

/** adb: DUMP_MOSAIC → 对最近 masked 链走真实 AndroidBlockSource.mosaic，保存前后拼图。 */
public final class MosaicDumpReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.DUMP_MOSAIC";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        try {
            Context app = context.getApplicationContext();
            ChainStore chains = new ChainStore(app);
            long wanted = intent.getIntExtra("chain", 0);
            Cursor c = wanted > 0
                    ? chains.getReadableDatabase().rawQuery(
                    "SELECT id,block_ids FROM chains WHERE id=?", new String[]{String.valueOf(wanted)})
                    : chains.getReadableDatabase().rawQuery(
                    "SELECT id,block_ids FROM chains WHERE text_source='masked'"
                            + " ORDER BY id DESC LIMIT 1", null);
            long id = 0;
            String csv = "";
            if (c.moveToFirst()) { id = c.getLong(0); csv = c.getString(1); }
            c.close();
            if (id == 0 || csv == null || csv.isEmpty()) {
                Log.i("DSHL2", "mosaic_dump: no masked chain");
                return;
            }
            List<Long> ids = new ArrayList<>();
            long onlyBlock = intent.getIntExtra("block", 0);
            if (onlyBlock > 0) {
                ids.add(onlyBlock);
            } else {
                for (String s : csv.split(",")) {
                    try { ids.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
                }
            }
            TraceStore store = new TraceStore(app);
            List<Trace> traces = store.tracesByIds(ids);
            AndroidBlockSource source = new AndroidBlockSource(app, "chain-" + id, traces);
            // 走真实 L2Tools.read_mosaic 工具路径，而不是直接调 mosaic
            com.dsh.agent.l2.L2Tools tools = new com.dsh.agent.l2.L2Tools("chain-" + id, source);
            StringBuilder arg = new StringBuilder("{\"block_ids\":[");
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) arg.append(',');
                arg.append(ids.get(i));
            }
            arg.append("]}");
            com.dsh.agent.ToolResult result = tools.registry().execute("read_mosaic",
                    arg.toString(), new com.dsh.agent.ToolContext(
                            new com.dsh.agent.ImageBudget(1), null));
            Log.i("DSHL2", "mosaic_dump chain=" + id + " blocks=" + ids.size()
                    + " ok=" + (result != null && result.isOk()));
        } catch (Throwable t) {
            Log.e("DSHL2", "mosaic_dump failed", t);
        }
    }
}
