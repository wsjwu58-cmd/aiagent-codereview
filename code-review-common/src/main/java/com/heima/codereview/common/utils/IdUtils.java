package com.heima.codereview.common.utils;

import java.util.UUID;

public final class IdUtils {

    private IdUtils() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String withPrefix(String prefix) {
        return prefix + "-" + uuid();
    }

    public static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String compactWithPrefix(String prefix, int maxLength) {
        String normalizedPrefix = prefix == null ? "" : prefix.trim();
        String compact = compactUuid();
        if (normalizedPrefix.isBlank()) {
            int safeLength = Math.max(1, Math.min(compact.length(), maxLength));
            return compact.substring(0, safeLength);
        }
        int effectiveMaxLength = Math.max(normalizedPrefix.length() + 2, maxLength);
        int suffixLength = Math.max(1, Math.min(compact.length(), effectiveMaxLength - normalizedPrefix.length() - 1));
        return normalizedPrefix + "-" + compact.substring(0, suffixLength);
    }
}
