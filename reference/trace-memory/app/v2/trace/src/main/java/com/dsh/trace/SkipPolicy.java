package com.dsh.trace;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 结构性跳过：哪些包名不是"用户在看的**内容**"。
 *
 * 这不是语义判断/专家系统，是结构性事实：系统 UI（通知栏/弹层）、launcher、输入法、
 * 以及我们自己的设置页，都不构成"痕迹"。（系统设置本身**保留**——用户真的会翻设置。）
 *
 * 列表由 app 层用 Android 结构查询构造：CATEGORY_HOME 反查 launcher、
 * InputMethodManager 取已启用输入法、固定 systemui 包名 + 自己包名。
 */
public final class SkipPolicy {

    private final Set<String> packages;

    private SkipPolicy(Set<String> packages) {
        this.packages = Collections.unmodifiableSet(new HashSet<>(packages));
    }

    public static SkipPolicy none() { return new SkipPolicy(Collections.emptySet()); }

    public static SkipPolicy of(String... pkgs) {
        Set<String> s = new HashSet<>();
        if (pkgs != null) for (String p : pkgs) if (p != null && !p.isEmpty()) s.add(p);
        return new SkipPolicy(s);
    }

    public static SkipPolicy of(Set<String> pkgs) {
        return new SkipPolicy(pkgs == null ? Collections.emptySet() : pkgs);
    }

    public boolean shouldSkip(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return packages.contains(pkg);
    }

    public Set<String> packages() { return packages; }

    public int size() { return packages.size(); }
}
