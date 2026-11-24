package com.dsh.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PageParserRoleTest {

    @Test public void role映射() {
        assertEquals(PageElement.Role.INPUT, PageParser.roleFor(true, false,
                "android.widget.TextView", false, "搜索"));
        assertEquals(PageElement.Role.INPUT, PageParser.roleFor(false, false,
                "android.widget.EditText", false, "搜索"));
        assertEquals(PageElement.Role.TITLE, PageParser.roleFor(false, true,
                "android.widget.TextView", false, "设置"));
        assertEquals(PageElement.Role.BUTTON, PageParser.roleFor(false, false,
                "android.widget.Button", false, "确定"));
        assertEquals(PageElement.Role.BADGE, PageParser.roleFor(false, false,
                "android.widget.TextView", true, "99"));
        assertEquals(PageElement.Role.LIST_ITEM, PageParser.roleFor(false, false,
                "androidx.recyclerview.widget.RecyclerView", false, "item"));
        assertEquals(PageElement.Role.PARAGRAPH, PageParser.roleFor(false, false,
                "android.widget.TextView", false, "一段普通文字"));
    }
}
