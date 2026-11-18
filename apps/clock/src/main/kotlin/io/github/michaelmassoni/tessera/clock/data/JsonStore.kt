package io.github.michaelmassoni.tessera.clock.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * A small JSON document on disk with an in-memory [StateFlow] copy. Writes are synchronous and
 * atomic (temp file + rename): data is tiny, and receivers that run while an alarm fires need it
 * on disk before they return. A corrupt file is moved aside and replaced by [default].
 */
class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: T,
) {
    private val lock = Any()
    private val flow = MutableStateFlow(read())

    val state: StateFlow<T> = flow.asStateFlow()

    val value: T get() = flow.value

    fun update(transform: (T) -> T): T =
        synchronized(lock) {
            val next = transform(flow.value)
            if (next != flow.value) {
                write(next)
                flow.value = next
            }
            next
        }

    private fun read(): T {
        if (!file.exists()) return default
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: SerializationException) {
            file.renameTo(File(file.parentFile, file.name + ".corrupt"))
            default
        } catch (e: IllegalArgumentException) {
            file.renameTo(File(file.parentFile, file.name + ".corrupt"))
            default
        } catch (e: IOException) {
            default
        }
    }

    private fun write(value: T) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(serializer, value))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private companion object {
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
