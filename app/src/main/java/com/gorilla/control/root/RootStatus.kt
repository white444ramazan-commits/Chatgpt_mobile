package com.gorilla.control.root

object RootStatus {
    fun check(): Boolean = RootController.hasRoot()
}
