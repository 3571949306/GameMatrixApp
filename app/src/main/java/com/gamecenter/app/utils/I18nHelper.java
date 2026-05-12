package com.gamecenter.app.utils;

import android.content.Context;
import android.widget.Toast;
import com.gamecenter.app.R;

public class I18nHelper {
    
    public static void showToast(Context context, String zhMessage, String enMessage) {
        Toast.makeText(context, isChinese(context) ? zhMessage : enMessage, Toast.LENGTH_SHORT).show();
    }
    
    private static boolean isChinese(Context context) {
        String lang = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return lang.equals("zh");
    }
}
