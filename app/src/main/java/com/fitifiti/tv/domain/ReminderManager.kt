package com.fitifiti.tv.domain

import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.ReminderEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object ReminderManager {
    val currentReminder = MutableStateFlow<ReminderEntity?>(null)

    fun start() {
        kotlinx.coroutines.GlobalScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val targetTime = now + 60000L // 1 minute from now
                val reminders = App.instance.db.reminders().getAll(now) // Wait, getAll(now) returns start >= now. 
                // We want to alert 1 minute before!
                
                val toAlert = reminders.find { it.startTime in now..targetTime }
                if (toAlert != null) {
                    currentReminder.value = toAlert
                    // Delete it so it doesn't trigger again
                    App.instance.db.reminders().delete(toAlert.eventId)
                }
                
                delay(10000) // check every 10 seconds
            }
        }
    }
}
