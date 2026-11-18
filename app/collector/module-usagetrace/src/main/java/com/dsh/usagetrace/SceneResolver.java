package com.dsh.usagetrace;

/**
 * 场景解析器链：activity → 节点标题 → OCR 标题 → 画面哈希。
 * 纯逻辑、无 Android 依赖，可单测。
 */
public final class SceneResolver {

    public static Scene resolve(String pkg,
                                String activity,
                                String nodeTitle,
                                String ocrTitle,
                                String frameHash) {
        String act = activity == null ? "" : activity;
        if (!act.isEmpty() && !act.startsWith(pkg)) act = "";   // 上个应用的残留

        String nt = TitleNormalizer.normalize(nodeTitle);
        String ot = TitleNormalizer.normalize(ocrTitle);

        // 1) 节点树标题最可信
        if (!nt.isEmpty()) {
            String key = !act.isEmpty() ? pkg + "|A|" + act + "|T|" + nt : pkg + "|T|" + nt;
            return new Scene(pkg, act, nt, "node", key, 0.95f);
        }
        // 2) OCR 标题（微信这类节点树不可用的应用靠这层）
        if (!ot.isEmpty()) {
            return new Scene(pkg, act, ot, "ocr", pkg + "|O|" + ot, 0.70f);
        }
        // 3) 只有 activity
        if (!act.isEmpty()) {
            return new Scene(pkg, act, "", "none", pkg + "|A|" + act, 0.50f);
        }
        // 4) 兜底：画面哈希（同一画面视为同一场景）
        String h = frameHash == null ? "0" : frameHash;
        return new Scene(pkg, "", "", "none", pkg + "|H|" + h, 0.30f);
    }

    /** 两个场景是否应视为同一段（抖动抑制的最后一关） */
    public static boolean sameScene(Scene a, Scene b) {
        if (a == null || b == null) return false;
        if (a.key.equals(b.key)) return true;
        if (!a.pkg.equals(b.pkg)) return false;
        // 同应用、同 activity、标题近似 → 同一页
        if (a.activity.equals(b.activity)
                && TitleNormalizer.sameTitle(a.title, b.title)) return true;
        // 一方没标题、另一方有，且 activity 相同 → 更可能是同一页的识别抖动
        if (a.activity.equals(b.activity)
                && (a.title.isEmpty() || b.title.isEmpty())) return true;
        return false;
    }
}
