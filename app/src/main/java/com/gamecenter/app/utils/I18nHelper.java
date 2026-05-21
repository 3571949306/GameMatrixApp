package com.gamecenter.app.utils;

import android.content.Context;
import android.widget.Toast;
import com.gamecenter.app.R;

/**
 * 国际化（i18n）辅助工具类。
 *
 * <p>简单来说，这个类就像一个"翻译官"——当你需要给用户显示一条提示消息时，
 * 你同时提供中文和英文两个版本，它会自动根据用户手机的系统语言选择合适的版本来显示。</p>
 *
 * <p>提供基于系统语言环境的中英文双语切换能力，主要用于简单的中英文 Toast 提示场景。
 * 根据当前系统语言自动选择中文或英文消息进行展示。
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>采用"同时传入中英文文本"的方式而非字符串资源 ID，适用于少量动态文本的快速国际化</li>
 *   <li>语言判断基于系统 Locale 的主语言代码（如 "zh"、"en"），仅区分中文/非中文两种情况</li>
 *   <li>此类为轻量级工具，不替代 Android 标准的 strings.xml 多语言方案，
 *       后者仍是资源国际化的首选方式</li>
 * </ul>
 */
public class I18nHelper {
    
    /**
     * 根据当前系统语言显示中文或英文 Toast 提示。
     *
     * <p>如果系统语言为中文（语言代码为 "zh"），则显示 zhMessage；
     * 否则显示 enMessage。Toast 显示时长为 SHORT。
     *
     * @param context   上下文，用于获取系统语言配置和显示 Toast
     * @param zhMessage 中文提示文本
     * @param enMessage 英文提示文本
     */
    public static void showToast(Context context, String zhMessage, String enMessage) {
        Toast.makeText(context, isChinese(context) ? zhMessage : enMessage, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 判断当前系统语言是否为中文。
     *
     * <p>通过读取系统资源配置中的首选 Locale，提取其语言代码进行判断。
     * 仅匹配 "zh"，涵盖简体中文（zh-CN）和繁体中文（zh-TW）等所有中文变体。
     *
     * @param context 上下文，用于获取资源配置
     * @return true 如果当前系统语言为中文
     */
    private static boolean isChinese(Context context) {
        // getLocales().get(0) 获取用户首选语言环境
        // Locale 就像手机的"语言身份证"，记录了用户使用的是什么语言
        String lang = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return lang.equals("zh");
    }
}
