package com.campus.events.auth;

import com.campus.events.foundation.ApiException;
import java.nio.charset.StandardCharsets;

/** NFR-08的新建密码规则；不把新建表单限制反向施加到既有账号登录。 */
public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void validateNew(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 8)
            throw ApiException.invalid("新建密码至少8个字符");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.invalid("密码UTF-8长度不能超过72字节");
    }
}
