package com.gamecenter.app.utils

import android.content.Context
import android.widget.Toast

object I18nHelper {

    @JvmStatic
    fun showToast(context: Context, zhMessage: String, enMessage: String) {
        Toast.makeText(context, if (isChinese(context)) zhMessage else enMessage, Toast.LENGTH_SHORT).show()
    }

    private fun isChinese(context: Context): Boolean {
        val lang = context.resources.configuration.locales[0].language
        return lang == "zh"
    }
}
