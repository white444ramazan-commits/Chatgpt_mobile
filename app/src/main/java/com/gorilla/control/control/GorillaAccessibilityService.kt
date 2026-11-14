package com.gorilla.control.control
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
class GorillaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
