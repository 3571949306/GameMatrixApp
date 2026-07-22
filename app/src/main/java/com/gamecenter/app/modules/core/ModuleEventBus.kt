package com.gamecenter.app.modules.core

import java.util.concurrent.CopyOnWriteArrayList

object ModuleEventBus {
    private val observers = CopyOnWriteArrayList<(ModuleCoreEvent) -> Unit>()

    fun addObserver(observer: (ModuleCoreEvent) -> Unit) {
        observers.addIfAbsent(observer)
    }

    fun removeObserver(observer: (ModuleCoreEvent) -> Unit) {
        observers.remove(observer)
    }

    fun publish(event: ModuleCoreEvent) {
        observers.forEach { observer -> runCatching { observer(event) } }
    }
}
