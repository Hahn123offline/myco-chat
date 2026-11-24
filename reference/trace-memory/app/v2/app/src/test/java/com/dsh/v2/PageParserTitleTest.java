package com.dsh.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class PageParserTitleTest {

    @Test public void 从居中BUTTON识别群名() {
        String title = PageParser.inferTitle(Arrays.asList(
                new PageParser.TitleCandidate("返回消息未读22", 30, 159, 102, 231,
                        PageElement.Role.PARAGRAPH),
                new PageParser.TitleCandidate("零杯（）", 201, 160, 405, 229,
                        PageElement.Role.BUTTON),
                new PageParser.TitleCandidate("(4)", 405, 160, 465, 229,
                        PageElement.Role.PARAGRAPH),
                new PageParser.TitleCandidate("聊天设置", 1080, 159, 1152, 231,
                        PageElement.Role.BUTTON)
        ), 1200);
        assertEquals("零杯（）", title);
    }

    @Test public void 列表行不当标题() {
        String title = PageParser.inferTitle(Arrays.asList(
                new PageParser.TitleCandidate("零杯网页设计大赛决赛群, ,不吃香菜加入了群聊,18:12",
                        0, 0, 1200, 200, PageElement.Role.PARAGRAPH)
        ), 1200);
        assertEquals("", title);
    }
}
