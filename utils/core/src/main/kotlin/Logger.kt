/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.utils.core

import java.util.concurrent.ConcurrentHashMap

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.kotlin.KotlinLogger
import org.apache.logging.log4j.kotlin.asLog4jSupplier
import org.apache.logging.log4j.kotlin.loggerOf
import org.apache.logging.log4j.message.Message

/**
 * Global map of loggers for classes so only one logger needs to be instantiated per class.
 */
val loggerOfClass = ConcurrentHashMap<Any, KotlinLogger>()

/**
 * An extension property for adding a log instance to any (unique) class.
 */
val Any.log: KotlinLogger
    inline get() = loggerOfClass.getOrPut(this::class.java) {
        this::class.qualifiedName?.let { name ->
            require(isOrtClass(this::class.java)) {
                "Logging is only allowed on ORT classes, but '$name' is used."
            }
        }

        loggerOf(this::class.java)
    }

/** The base package of ORT. This is used to determine whether a specific class belongs to ORT. */
const val ORT_PACKAGE = "org.ossreviewtoolkit."

/**
 * Check whether [cls] represents a class or interface that belongs to ORT. This is the case if [cls] belongs to a
 * package in the ORT namespace (as defined by [ORT_PACKAGE]) or extends an ORT class or interface.
 */
fun isOrtClass(cls: Class<*>?): Boolean =
    when {
        cls == null -> false
        cls.name.startsWith(ORT_PACKAGE) -> true
        else -> cls.interfaces.any { it.name.startsWith(ORT_PACKAGE) } || isOrtClass(cls.superclass)
    }

val KotlinLogger.statements by lazy { mutableSetOf<Triple<Any, Level, String>>() }

/**
 * A convenience function to log a specific statement only once with this class instance.
 */
inline fun <reified T : Any> T.logOnce(level: Level, supplier: () -> String) {
    val statement = Triple(this, level, supplier())
    if (statement !in log.statements) {
        log.statements += statement
        log.log(statement.second, statement.third)
    }
}

/**
 * A log [Level] for logging performance information. The int value of the level is between [Level.INFO] and
 * [Level.DEBUG].
 */
val PERFORMANCE: Level = Level.forName("PERFORMANCE", 450)

fun KotlinLogger.perf(marker: Marker, msg: Message) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, marker, msg, null)
}

fun KotlinLogger.perf(marker: Marker, msg: Message, t: Throwable?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, marker, msg, t)
}

fun KotlinLogger.perf(marker: Marker, msg: CharSequence) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, marker, msg, null)
}

fun KotlinLogger.perf(marker: Marker, msg: CharSequence, t: Throwable?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, marker, msg, t)
}

fun KotlinLogger.perf(marker: Marker, msg: Any) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, marker, msg, null)
}

fun KotlinLogger.perf(marker: Marker, msg: Any, t: Throwable?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, marker, msg, t)
}

fun KotlinLogger.perf(msg: Message) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, null, msg, null)
}

fun KotlinLogger.perf(msg: Message, t: Throwable?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, null, msg, t)
}

fun KotlinLogger.perf(msg: CharSequence) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, null, msg, null)
}

fun KotlinLogger.perf(msg: CharSequence, t: Throwable?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, null, msg, t)
}

fun KotlinLogger.perf(msg: Any) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, null, msg, null)
}

fun KotlinLogger.perf(msg: Any, t: Throwable?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, null, msg, t)
}

fun KotlinLogger.perf(supplier: () -> Any?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, null, supplier.asLog4jSupplier(), null)
}

fun KotlinLogger.perf(t: Throwable, supplier: () -> Any?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, null, supplier.asLog4jSupplier(), t)
}

fun KotlinLogger.perf(marker: Marker, supplier: () -> Any?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, marker, supplier.asLog4jSupplier(), null)
}

fun KotlinLogger.perf(marker: Marker, t: Throwable?, supplier: () -> Any?) {
    delegate.logIfEnabled(KotlinLogger.FQCN, PERFORMANCE, marker, supplier.asLog4jSupplier(), t)
}
