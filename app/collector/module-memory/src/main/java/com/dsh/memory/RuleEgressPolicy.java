package com.dsh.memory;

import com.dsh.host.api.Config;
import com.dsh.host.api.memory.EgressPolicy;
import com.dsh.host.api.memory.EgressTier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 纯规则门控：决定一条记忆能不能出设备。**不引模型、不联网、不猜语义。**
 *
 * 规则优先级（保守优先）：
 *   1. 包名在黑名单 → BLACK（无论标题/正文是什么）；
 *   2. 标题或脱敏正文命中敏感词 → GRAY（**白名单包名也降级**）；
 *   3. 包名在白名单 → WHITE；
 *   4. 其余（未知应用） → GRAY。
 *
 * 关键事故防护：B站/小红书/浏览器里出现"支付/订单/银行卡/验证码"时，
 * 绝不能因为"包名是白名单"就判 WHITE —— 必须先过敏感词降级。
 *
 * 规则来自配置（每次调用重新读 = 热更新）；代码里只放一份保守默认值。
 */
public final class RuleEgressPolicy implements EgressPolicy {

    // ---------------- 默认值（保守） ----------------

    static final String DEF_BLACK_PKGS = String.join(",",
            // IM / 短信
            "com.tencent.mm", "com.tencent.mobileqq", "com.android.mms",
            // 支付 / 银行
            "com.eg.android.AlipayGphone", "com.unionpay", "com.icbc", "com.cmbchina",
            "com.chinamworld.main", "com.bankcomm.Bankcomm",
            // 证件 / 密码器 / 邮箱
            "com.android.contacts", "com.android.email", "com.google.android.gm",
            // 健康 / 位置
            "com.xiaomi.health", "com.android.vending", "com.google.android.apps.maps",
            "com.baidu.BaiduMap", "com.autonavi.minimap");

    static final String DEF_WHITE_PKGS = String.join(",",
            // 视频 / 社区
            "tv.danmaku.bili", "com.xingin.xhs", "com.zhihu.android", "com.ss.android.ugc.aweme",
            // 浏览器 / 资讯 / 搜索
            "com.android.browser", "com.android.chrome", "com.ss.android.article.news",
            "com.tencent.news", "com.netease.newsreader.activity",
            // 购物（公开商品页）
            "com.taobao.taobao", "com.jingdong.app.mall", "com.xunmeng.pinduoduo");

    static final String DEF_GRAY_HINTS = String.join(",",
            "支付", "订单", "收货", "银行卡", "身份证", "验证码", "余额", "账单", "地址",
            "密码", "转账", "实名", "证件",
            // 真机数据补漏（com.miui.personalassistant 的「付款码」曾被判 GRAY）：
            // 付款码/扫码页/理财/医疗/住址 都应按"需要脱敏/不出设备"处理
            "付款", "收款", "扫码", "二维码", "红包", "理财", "基金", "股票",
            "医保", "社保", "体检", "病历", "医院", "住址");

    // ---------------- 配置 key ----------------

    static final String KEY_BLACK = "egress.black.pkgs";
    static final String KEY_WHITE = "egress.white.pkgs";
    static final String KEY_TITLE_HINTS = "egress.gray.titleHints";
    static final String KEY_TEXT_HINTS = "egress.gray.textHints";

    private final Supplier<Config> configs;

    public RuleEgressPolicy(Supplier<Config> configs) {
        this.configs = configs;
    }

    @Override
    public EgressTier tierFor(String pkg, String activity, String title, String maskedText) {
        String p = norm(pkg);
        if (p.isEmpty()) return EgressTier.GRAY;   // 不知道来源 → 保守

        Config c = configs == null ? null : configs.get();
        List<String> black = csv(c, KEY_BLACK, DEF_BLACK_PKGS);
        List<String> white = csv(c, KEY_WHITE, DEF_WHITE_PKGS);
        List<String> titleHints = csv(c, KEY_TITLE_HINTS, DEF_GRAY_HINTS);
        List<String> textHints = csv(c, KEY_TEXT_HINTS, DEF_GRAY_HINTS);

        if (contains(black, p)) return EgressTier.BLACK;

        boolean sensitive = containsAny(title, titleHints) || containsAny(maskedText, textHints);
        if (contains(white, p)) return sensitive ? EgressTier.GRAY : EgressTier.WHITE;

        // 未知应用：即使没有命中敏感词也默认 GRAY（保守）
        return EgressTier.GRAY;
    }

    // ---------------- 纯工具（包内可见，单测直接测） ----------------

    static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** 配置里有这个 key 就用它（空值 = 显式禁用该名单）；没有才用默认值 */
    static List<String> csv(Config c, String key, String def) {
        String raw;
        if (c != null && c.has(key)) raw = c.getString(key, "");
        else raw = def;
        return split(raw);
    }

    static List<String> split(String csv) {
        if (csv == null || csv.trim().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String s = norm(part);
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    static boolean contains(List<String> list, String value) {
        return list != null && list.contains(value);
    }

    /** 中文/英文子串匹配；英文不区分大小写 */
    static boolean containsAny(String text, List<String> hints) {
        if (text == null || text.isEmpty() || hints == null || hints.isEmpty()) return false;
        String t = text.toLowerCase(Locale.ROOT);
        for (String h : hints) {
            if (!h.isEmpty() && t.contains(h)) return true;
        }
        return false;
    }
}
