package com.dsh.v2;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.dsh.models.ApiKeyProvider;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * API Key 私有存储：Android Keystore AES/GCM 加密后存 app 私有 SharedPreferences。
 *
 * 安全约束（task-23）：
 *  - 明文 key 只在本类里短暂存在；不写文件、不进日志、不进崩溃报告
 *  - UI 只显示掩码
 *  - 密文格式：Base64(iv(12) || ciphertext)
 */
public final class SecretStore implements ApiKeyProvider {

    private static final String PREFS = "dsh_secrets";
    private static final String KEY_ALIAS = "dsh_api_key_v1";
    private static final String KEY_PREFIX = "key_";

    private final SharedPreferences prefs;

    public SecretStore(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override public String keyFor(String providerId) {
        try {
            String stored = prefs.getString(KEY_PREFIX + providerId, null);
            if (stored == null || stored.isEmpty()) return "";
            byte[] blob = Base64.decode(stored, Base64.NO_WRAP);
            if (blob.length < 13) return "";
            byte[] iv = new byte[12];
            byte[] ct = new byte[blob.length - 12];
            System.arraycopy(blob, 0, iv, 0, 12);
            System.arraycopy(blob, 12, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    public boolean save(String providerId, String key) {
        if (key == null || key.trim().isEmpty()) return false;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(key.trim().getBytes(StandardCharsets.UTF_8));
            byte[] blob = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(ct, 0, blob, iv.length, ct.length);
            return prefs.edit().putString(KEY_PREFIX + providerId,
                    Base64.encodeToString(blob, Base64.NO_WRAP)).commit();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean has(String providerId) {
        return !keyFor(providerId).isEmpty();
    }

    /** 删除已保存的 key（UI「清除」按钮） */
    public boolean clear(String providerId) {
        try {
            return prefs.edit().remove(KEY_PREFIX + providerId).commit();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 只用于 UI 展示，不落日志：sk-****abcd */
    public static String mask(String key) {
        if (key == null || key.isEmpty()) return "未填写";
        String k = key.trim();
        if (k.length() <= 8) return "****";
        return k.substring(0, 3) + "****" + k.substring(k.length() - 4);
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }
}
