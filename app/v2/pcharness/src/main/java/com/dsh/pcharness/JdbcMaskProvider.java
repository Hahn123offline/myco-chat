package com.dsh.pcharness;

import com.dsh.guard.Guard;
import com.dsh.guard.GuardResult;
import com.dsh.masker.MaskBlock;
import com.dsh.masker.MaskElement;
import com.dsh.masker.MaskPolicy;
import com.dsh.masker.Masker;
import com.dsh.masker.MaskedBlock;
import com.dsh.memory.MaskProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** 桌面运行时 Masker：page_elements（trace.db）→ Masker；拿不到返回 null。 */
public final class JdbcMaskProvider implements MaskProvider {

    private final String traceDb;
    private final Masker masker = new Masker();
    private final Guard guard = new Guard();

    public JdbcMaskProvider(String traceDb) {
        this.traceDb = traceDb;
    }

    @Override public com.dsh.memory.Masked maskedTextFor(long itemId, String pkg,
                                                          long t0, long t1, String rawText) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + traceDb);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT elem_id,text,left,top,right,bottom,role,signals FROM page_elements "
                             + "WHERE pkg=? AND t0 BETWEEN ? AND ? ORDER BY id")) {
            ps.setString(1, pkg == null ? "" : pkg);
            ps.setLong(2, t0 - 2000);
            ps.setLong(3, t1 + 2000);
            List<MaskElement> elems = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    elems.add(new MaskElement(rs.getString(1), rs.getString(2),
                            rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                            rs.getString(7), java.util.Collections.emptyList()));
                }
            }
            if (!elems.isEmpty()) {
                MaskedBlock mb = masker.mask(new MaskBlock("", rawText, elems),
                        MaskPolicy.defaults());
                GuardResult gr = guard.check(mb.text == null ? "" : mb.text);
                if (gr.isBlocked()) return com.dsh.memory.Masked.none();
                return new com.dsh.memory.Masked(mb.text, com.dsh.memory.MaskMode.FULL);
            }
            // 无 page_elements：纯文本降级
            MaskedBlock mb = masker.mask(MaskBlock.ofText(rawText), MaskPolicy.defaults());
            GuardResult gr = guard.check(mb.text == null ? "" : mb.text);
            if (gr.isBlocked()) return com.dsh.memory.Masked.none();
            return new com.dsh.memory.Masked(mb.text, com.dsh.memory.MaskMode.TEXT_ONLY);
        } catch (Throwable t) {
            return com.dsh.memory.Masked.none();
        }
    }
}
