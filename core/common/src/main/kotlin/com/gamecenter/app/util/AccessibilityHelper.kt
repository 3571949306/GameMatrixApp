package com.gamecenter.app.util

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes

object AccessibilityHelper {
    
    @JvmStatic
    fun setDescription(view: View, description: String) {
        view.contentDescription = description
    }
    
    @JvmStatic
    fun setDescription(view: View, @StringRes resId: Int) {
        view.contentDescription = view.context.getString(resId)
    }
    
    @JvmStatic
    fun setButtonRole(view: View, label: String) {
        view.contentDescription = label
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    
    @JvmStatic
    fun setImageDescription(imageView: ImageView, description: String) {
        imageView.contentDescription = description
    }
    
    @JvmStatic
    fun setImageButtonDescription(button: ImageButton, description: String) {
        button.contentDescription = description
    }
    
    @JvmStatic
    fun announceForAccessibility(view: View, announcement: String) {
        view.announceForAccessibility(announcement)
    }
    
    @JvmStatic
    fun setHeading(view: TextView) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            view.isAccessibilityHeading = true
        }
    }
    
    @JvmStatic
    fun setClickableWithAnnouncement(view: View, clickLabel: String, announcement: String) {
        view.contentDescription = clickLabel
        view.setOnClickListener {
            it.announceForAccessibility(announcement)
        }
    }
    
    @JvmStatic
    fun enableAccessibility(view: View) {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    
    @JvmStatic
    fun disableAccessibility(view: View) {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    
    @JvmStatic
    fun setScreenReaderFocus(view: View) {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.requestFocus()
    }
}

fun View.setAccessibilityDescription(description: String) {
    contentDescription = description
}

fun View.setAccessibilityDescription(@StringRes resId: Int) {
    contentDescription = context.getString(resId)
}

fun View.announce(announcement: String) {
    announceForAccessibility(announcement)
}

fun TextView.setAsHeading() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        isAccessibilityHeading = true
    }
}
