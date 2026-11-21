package io.github.eiji_der_sonnige.conversa

import android.app.Application
import io.github.eiji_der_sonnige.conversa.core.Notifier

class ConversaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
    }
}
