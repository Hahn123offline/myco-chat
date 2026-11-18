package com.dsh.v2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DangerSignalsTest {

    @Test public void 卡号_身份证_验证码_手机号提名() {
        assertTrue(DangerSignals.nominate("卡号 6222 0212 3456 7890",
                PageElement.Role.PARAGRAPH, "").contains("card_candidate"));
        assertTrue(DangerSignals.nominate("身份证 11010519491231002X",
                PageElement.Role.PARAGRAPH, "").contains("id_card"));
        assertTrue(DangerSignals.nominate("验证码 123456",
                PageElement.Role.PARAGRAPH, "").contains("sms_code"));
        assertTrue(DangerSignals.nominate("电话 13812345678",
                PageElement.Role.PARAGRAPH, "").contains("contact_phone"));
    }

    @Test public void 金额与输入框提名() {
        assertTrue(DangerSignals.nominate("转账 ¥5,000.00",
                PageElement.Role.PARAGRAPH, "").contains("amount"));
        assertTrue(DangerSignals.nominate("搜索词",
                PageElement.Role.INPUT, "").contains("input_content"));
    }

    @Test public void 普通聊天不误提名() {
        assertFalse(DangerSignals.nominate("今天吃什么",
                PageElement.Role.PARAGRAPH, "聊天").contains("card_candidate"));
    }

    @Test public void 标题支付类整页敏感() {
        assertTrue(DangerSignals.pageSensitive("确认支付"));
        assertTrue(DangerSignals.pageSensitive("订单详情"));
        assertFalse(DangerSignals.pageSensitive("聊天"));
    }
}
