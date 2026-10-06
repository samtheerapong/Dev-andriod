package com.example.carbrowser

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point bound by the Android Auto host. Declared in the manifest with the
 * NAVIGATION category so the host offers us a raw drawing Surface.
 */
class MyCarAppService : CarAppService() {

    // Personal sideloaded build: accept any host. A published app would allowlist
    // Google's Android Auto host signatures instead.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = MySession()
}
