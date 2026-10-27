package com.cyberstock.common;

import java.util.List;
import java.util.Locale;

/** Resolves an {@code Accept-Language} header to one of the supported app locales. */
public final class Locales {

    public static final String EN_US = "en-US";
    public static final String ZH_HK = "zh-HK";
    public static final String ZH_TW = "zh-TW";
    public static final String ZH_CN = "zh-CN";
    public static final List<String> SUPPORTED = List.of(ZH_HK, ZH_TW, ZH_CN, EN_US);

    private Locales() {}

    public static String resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return EN_US;
        }
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
                String mapped = map(range.getRange());
                if (mapped != null) {
                    return mapped;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // malformed header -> default
        }
        return EN_US;
    }

    /** Maps a BCP-47 tag to a supported locale, or {@code null} if unsupported. */
    static String map(String tag) {
        String t = tag.toLowerCase(Locale.ROOT);
        if (t.equals("zh-tw") || t.startsWith("zh-hant-tw")) {
            return ZH_TW;
        }
        if (t.equals("zh-hk") || t.equals("zh-mo") || t.startsWith("zh-hant")) {
            return ZH_HK; // generic Traditional Chinese defaults to Hong Kong
        }
        if (t.equals("zh") || t.equals("zh-cn") || t.equals("zh-sg") || t.startsWith("zh-hans")) {
            return ZH_CN;
        }
        if (t.startsWith("en")) {
            return EN_US;
        }
        return null;
    }
}
