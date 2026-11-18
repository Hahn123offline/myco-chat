package com.dsh.v2;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 三层契约拼装：persona（可空）/ sim（必有）/ facts（程序生成，order 高）。 */
public final class PromptAssembler {

    private PromptAssembler() { }

    public static String loadAsset(Context ctx, String path) {
        try (InputStream in = ctx.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** persona 不存在/为空 → 整段跳过；空人格系统必须照常工作。 */
    public static String assemble(String persona, String sim, String facts) {
        return com.dsh.agent.PromptAssembler.assemble(persona, sim, facts);
    }
}
