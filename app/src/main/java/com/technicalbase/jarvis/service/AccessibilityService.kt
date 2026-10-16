package com.technicalbase.jarvis.service

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService as BaseAccessibilityService

class AccessibilityService : BaseAccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
