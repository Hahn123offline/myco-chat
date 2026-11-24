package com.dsh.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小 JSON 工具（只为 OpenAI 兼容协议服务）。
 *
 * 为什么手写：不引第三方依赖，避免 APK 体积膨胀；协议只需要
 * object / array / string / number / true / false / null，一百多行足够。
 * 只被本模块的 codec 与测试使用，不对外。
 */
public final class Json {

    private Json() { }

    // ---------------- 序列化 ----------------

    /** 生成合法的 JSON 字符串字面量（含引号）。非 ASCII 原样输出（body 用 UTF-8）。 */
    public static String quote(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 8);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
        return b.toString();
    }

    // ---------------- 解析 ----------------

    /** 解析成 LinkedHashMap / ArrayList / String / Long|Double / Boolean / null。 */
    public static Object parse(String s) {
        if (s == null) throw new IllegalArgumentException("JSON 为 null");
        Parser p = new Parser(s);
        Object v = p.value();
        p.ws();
        if (!p.eof()) throw p.err("多余字符");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    /** null 安全取对象成员 */
    public static Object get(Object obj, String key) {
        Map<String, Object> m = asObject(obj);
        return m == null ? null : m.get(key);
    }

    public static String string(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) { this.s = s; }

        boolean eof() { return i >= s.length(); }

        IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON 解析失败@" + i + ": " + msg);
        }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }

        Object value() {
            ws();
            if (eof()) throw err("意外的结尾");
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return number();
            }
        }

        void expect(String lit) {
            if (!s.startsWith(lit, i)) throw err("期望 " + lit);
            i += lit.length();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (!eof() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                if (eof() || s.charAt(i) != '"') throw err("期望对象的 key");
                String k = string();
                ws();
                if (eof() || s.charAt(i) != ':') throw err("期望 ':'");
                i++;
                m.put(k, value());
                ws();
                if (eof()) throw err("对象未闭合");
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; return m; }
                throw err("期望 ',' 或 '}'");
            }
        }

        List<Object> array() {
            List<Object> a = new ArrayList<>();
            i++; // [
            ws();
            if (!eof() && s.charAt(i) == ']') { i++; return a; }
            while (true) {
                a.add(value());
                ws();
                if (eof()) throw err("数组未闭合");
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; return a; }
                throw err("期望 ',' 或 ']'");
            }
        }

        String string() {
            i++; // "
            StringBuilder b = new StringBuilder();
            while (true) {
                if (eof()) throw err("字符串未闭合");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                if (eof()) throw err("转义未完成");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw err("\\u 转义不完整");
                        try {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw err("\\u 转义非法");
                        }
                        i += 4;
                        break;
                    default: throw err("未知转义 \\" + e);
                }
            }
        }

        Object number() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.'
                        || c == 'e' || c == 'E') {
                    i++;
                } else {
                    break;
                }
            }
            if (start == i) throw err("非法值");
            String t = s.substring(start, i);
            try {
                if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
                    return Long.parseLong(t);
                }
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                throw err("非法数字 " + t);
            }
        }
    }
}
