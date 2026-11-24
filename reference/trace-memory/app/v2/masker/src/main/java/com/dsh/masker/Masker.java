package com.dsh.masker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 独立掩码器：输入一块纯数据，输出掩码后文本 + span + 图片 bbox + 审计分类。
 * 不依赖 Trace / HostContext / 网络 / Android。
 */
public final class Masker {

    private final MaskJudge judge;

    public Masker() { this(new RuleMaskJudge()); }

    public Masker(MaskJudge judge) {
        this.judge = judge == null ? new RuleMaskJudge() : judge;
    }

    public static MaskedBlock apply(MaskBlock block, MaskPolicy policy) {
        return new Masker().mask(block, policy);
    }

    public MaskedBlock mask(MaskBlock block, MaskPolicy policy) {
        MaskPolicy p = policy == null ? MaskPolicy.defaults() : policy;
        List<MaskElement> elements = block.elements;
        if (elements == null || elements.isEmpty()) {
            elements = new ArrayList<>();
            elements.add(new MaskElement("text", block.text, 0, 0, 0, 0,
                    "PARAGRAPH", new ArrayList<>()));
        }
        List<ElementSpan> candidates = judge.judge(elements);
        List<MaskSpan> spans = new ArrayList<>();
        List<MaskBBox> boxes = new ArrayList<>();
        List<MaskClassification> classes = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        int globalOffset = 0;
        for (int ei = 0; ei < elements.size(); ei++) {
            MaskElement e = elements.get(ei);
            List<ElementSpan> own = new ArrayList<>();
            for (ElementSpan s : candidates) {
                if (e.id.equals(s.elementId)) own.add(s);
            }
            own.sort(Comparator.comparingInt(s -> s.start));
            TextResult tr = maskText(e.text, own, p, e);
            if (ei > 0) {
                out.append('\n');
                globalOffset += 1;
            }
            int base = globalOffset;
            out.append(tr.text);
            for (MaskSpan rs : tr.spans) {
                spans.add(new MaskSpan(base + rs.start, base + rs.end, rs.kind));
                if (e.right > e.left && e.bottom > e.top) {
                    boxes.add(new MaskBBox(e.left - p.bboxPadding, e.top - p.bboxPadding,
                            (e.right - e.left) + p.bboxPadding * 2,
                            (e.bottom - e.top) + p.bboxPadding * 2, rs.kind));
                }
            }
            classes.addAll(tr.classifications);
            globalOffset += tr.text.length();
        }
        String finalText = (block.elements == null || block.elements.isEmpty())
                ? out.toString() : out.toString();
        return new MaskedBlock(finalText, spans, boxes, classes);
    }

    private static final class TextResult {
        final String text;
        final List<MaskSpan> spans;
        final List<MaskClassification> classifications;
        TextResult(String t, List<MaskSpan> s, List<MaskClassification> c) {
            text = t; spans = s; classifications = c;
        }
    }

    private static TextResult maskText(String text, List<ElementSpan> candidates,
                                       MaskPolicy p, MaskElement e) {
        List<MaskSpan> spans = new ArrayList<>();
        List<MaskClassification> classes = new ArrayList<>();
        if (text == null || text.isEmpty() || candidates.isEmpty()) {
            if (!candidates.isEmpty()) {
                for (ElementSpan s : candidates) {
                    classes.add(new MaskClassification(e.id, s.start, s.end, s.kind,
                            false, "无文本"));
                }
            }
            return new TextResult(text == null ? "" : text, spans, classes);
        }
        StringBuilder b = new StringBuilder();
        int cursor = 0;
        for (ElementSpan s : candidates) {
            int start = Math.max(cursor, Math.min(text.length(), s.start));
            int end = Math.max(start, Math.min(text.length(), s.end));
            if (start > cursor) b.append(text, cursor, start);
            String src = text.substring(start, end);
            boolean enabled = enabled(s.kind, p);
            String masked = enabled ? maskKind(src, s.kind, p) : src;
            int outStart = b.length();
            b.append(masked);
            int outEnd = b.length();
            if (enabled) {
                spans.add(new MaskSpan(outStart, outEnd, s.kind));
            }
            classes.add(new MaskClassification(e.id, start, end, s.kind, enabled,
                    enabled ? "rule:" + s.kind : "policy." + s.kind + "=false"));
            cursor = end;
        }
        if (cursor < text.length()) b.append(text, cursor, text.length());
        return new TextResult(b.toString(), spans, classes);
    }

    private static boolean enabled(String kind, MaskPolicy p) {
        switch (kind) {
            case "card": return p.maskCard;
            case "id_card": return p.maskIdCard;
            case "phone": return p.maskPhone;
            case "code": return p.maskCode;
            case "email": return p.maskEmail;
            case "amount": return p.maskAmount;
            case "name": return p.maskName;
            default: return false;
        }
    }

    static String maskKind(String src, String kind, MaskPolicy p) {
        switch (kind) {
            case "card": return maskDigits(src, -1, p.keepCardTail, '*');
            case "id_card": return maskDigits(src, p.keepIdHead, p.keepIdTail, '*');
            case "phone": return maskDigits(src, p.keepPhoneHead, p.keepPhoneTail, '*');
            case "code": return maskDigits(src, 0, 0, '█');
            case "email": return maskEmail(src);
            case "amount": return maskDigits(src, 0, 0, '█');
            default: return src;
        }
    }

    /** 保留头 head 个数字和尾 tail 个数字（按数字序号，分隔符原样保留），其余换 fill。 */
    static String maskDigits(String src, int head, int tail, char fill) {
        int digitCount = 0;
        for (int i = 0; i < src.length(); i++) if (Character.isDigit(src.charAt(i))) digitCount++;
        StringBuilder b = new StringBuilder(src.length());
        int idx = 0;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (!Character.isDigit(c)) { b.append(c); continue; }
            idx++;
            boolean keep = (head >= 0 && idx <= head)
                    || (tail > 0 && idx > digitCount - tail);
            b.append(keep ? c : fill);
        }
        return b.toString();
    }

    static String maskEmail(String src) {
        int at = src.indexOf('@');
        if (at <= 0) return src;
        String local = src.substring(0, at);
        String domain = src.substring(at + 1);
        String maskedLocal = local.isEmpty() ? "***" : local.substring(0, 1) + "***";
        return maskedLocal + "@" + domain;
    }
}
