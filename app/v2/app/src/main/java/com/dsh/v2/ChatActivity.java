package com.dsh.v2;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.agent.AgentEvent;
import com.dsh.models.Json;

import java.util.List;
import java.util.Map;

/** APP 内单会话 GUI：ScrollView + LinearLayout，不引 RecyclerView。 */
public final class ChatActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private Button send;
    private AgentDbStore store;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new AgentDbStore(this);
        store.ensureSession(ChatSession.ID, "chat", "", "single=1");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(24, 24, 24, 24);
        scroll.addView(messages, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(16, 8, 16, 16);
        input = new EditText(this);
        input.setHint("问它：我昨天下午在干嘛？");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bar.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        send = new Button(this);
        send.setText("发送");
        bar.addView(send, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        send.setOnClickListener(v -> onSend());
        loadHistory();
    }

    private void onSend() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        addBubble(text, true);
        send.setEnabled(false);
        addBubble("……", false);
        ChatSession.send(this, text, (ok, msg) -> ui.post(() -> {
            removeLastBubble();
            addBubble(ok ? msg : "错误：" + msg, false);
            send.setEnabled(true);
            scroll.fullScroll(ScrollView.FOCUS_DOWN);
        }));
    }

    private void loadHistory() {
        List<AgentEvent> events = store.events(ChatSession.ID);
        for (AgentEvent e : events) {
            if ("user".equals(e.type)) addBubble(content(e.payload), true);
            else if ("assistant".equals(e.type)) {
                String c = content(e.payload);
                if (!c.isEmpty()) addBubble(c, false);
            } else if ("error".equals(e.type)) addBubble("错误：" + content(e.payload), false);
        }
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private static String content(String payload) {
        try {
            Map<String, Object> m = Json.asObject(Json.parse(payload));
            String c = Json.string(m == null ? null : m.get("content"));
            return c == null ? payload : c;
        } catch (Throwable t) { return payload == null ? "" : payload; }
    }

    private static String plain(String text) {
        if (text == null) return "";
        return text.replace("**", "").replace("__", "")
                .replaceAll("(?m)^#{1,6}\\s*", "");
    }

    private void addBubble(String text, boolean user) {
        TextView t = new TextView(this);
        t.setText(plain(text));
        t.setTextSize(15);
        t.setPadding(24, 16, 24, 16);
        t.setBackgroundColor(user ? 0xFFDCEBFF : 0xFFF2F2F2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.setMargins(8, 6, 8, 6);
        messages.addView(t, lp);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void removeLastBubble() {
        int n = messages.getChildCount();
        if (n > 0) messages.removeViewAt(n - 1);
    }
}
