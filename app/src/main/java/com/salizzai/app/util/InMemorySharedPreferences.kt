package com.salizzai.app.util

import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-local fallback used only when Android Keystore encryption is unavailable.
 * Nothing written here reaches disk, preventing an unsafe plaintext credential fallback.
 */
class InMemorySharedPreferences : SharedPreferences {
    private val lock = Any()
    private val values = mutableMapOf<String, Any>()
    private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { values.toMutableMap() }
    override fun getString(key: String, defValue: String?): String? = synchronized(lock) { values[key] as? String ?: defValue }
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = synchronized(lock) {
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues?.toMutableSet()
    }
    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { values[key] as? Int ?: defValue }
    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { values[key] as? Long ?: defValue }
    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) { values[key] as? Float ?: defValue }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { values[key] as? Boolean ?: defValue }
    override fun contains(key: String): Boolean = synchronized(lock) { values.containsKey(key) }
    override fun edit(): SharedPreferences.Editor = MemoryEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = update(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            update(key, values?.toSet())
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = update(key, value)
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = update(key, value)
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = update(key, value)
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = update(key, value)

        private fun update(key: String, value: Any?): SharedPreferences.Editor {
            if (value == null) {
                updates.remove(key)
                removals.add(key)
            } else {
                removals.remove(key)
                updates[key] = value
            }
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            updates.remove(key)
            removals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            updates.clear()
            removals.clear()
            return this
        }

        override fun commit(): Boolean {
            val changed = mutableSetOf<String>()
            synchronized(lock) {
                if (clearRequested) {
                    changed.addAll(values.keys)
                    values.clear()
                }
                removals.forEach { key -> if (values.remove(key) != null) changed.add(key) }
                updates.forEach { (key, value) ->
                    if (value != null && values[key] != value) {
                        values[key] = value
                        changed.add(key)
                    }
                }
            }
            changed.forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) } }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
