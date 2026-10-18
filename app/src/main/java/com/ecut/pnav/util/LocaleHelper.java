package com.ecut.pnav.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREFS_NAME = "app_preferences";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_LANGUAGE_CHOSEN = "language_chosen";
    private static final String DEFAULT_LANGUAGE = "zh";

    public static Context onAttach(Context context) {
        String languageCode = getPersistedLanguage(context);
        return wrapContext(context, languageCode);
    }

    public static Context wrapContext(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        return context.createConfigurationContext(config);
    }

    public static String getPersistedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE);
    }

    public static void persistLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_APP_LANGUAGE, languageCode).apply();
    }

    /** 用户是否已主动选过语言 —— 用于首次安装只弹一次语言选择 */
    public static boolean hasChosenLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LANGUAGE_CHOSEN, false);
    }

    public static void markLanguageChosen(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LANGUAGE_CHOSEN, true).apply();
    }

    public static String[] getSupportedLanguageCodes() {
        return new String[]{"en", "zh"};
    }
}
