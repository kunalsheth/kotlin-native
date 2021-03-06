/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package konan.internal

import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

@kotlin.internal.InlineOnly
@PublishedApi
internal inline suspend fun <T> suspendCoroutineUninterceptedOrReturn(crossinline block: (Continuation<T>) -> Any?): T =
        returnIfSuspended<T>(block(getContinuation<T>()))

@kotlin.internal.InlineOnly
@PublishedApi
internal inline fun <T> Continuation<T>.intercepted(): Continuation<T> =
        normalizeContinuation<T>(this)

@Intrinsic
@PublishedApi
internal external fun <T> getContinuation(): Continuation<T>

@kotlin.internal.InlineOnly
@PublishedApi
internal inline suspend fun getCoroutineContext(): CoroutineContext =
        getContinuation<Any?>().context

@Intrinsic
@PublishedApi
internal external suspend fun <T> returnIfSuspended(@Suppress("UNUSED_PARAMETER") argument: Any?): T


@ExportForCompiler
internal fun <T> interceptContinuationIfNeeded(
        context: CoroutineContext,
        continuation: Continuation<T>
) = context[ContinuationInterceptor]?.interceptContinuation(continuation) ?: continuation

/**
 * @suppress
 */
@ExportForCompiler
@PublishedApi
internal fun <T> normalizeContinuation(continuation: Continuation<T>): Continuation<T> =
        (continuation as? CoroutineImpl)?.facade ?: continuation

/**
 * @suppress
 */
@ExportForCompiler
abstract internal class CoroutineImpl(
        protected var completion: Continuation<Any?>?
) : Continuation<Any?> {

    // label == -1 when coroutine cannot be started (it is just a factory object) or has already finished execution
    // label == 0 in initial part of the coroutine
    protected var label: NativePtr = if (completion != null) NativePtr.NULL else NativePtr.NULL + (-1L)

    private val _context: CoroutineContext? = completion?.context

    override val context: CoroutineContext
        get() = _context!!

    private var _facade: Continuation<Any?>? = null

    val facade: Continuation<Any?> get() {
        if (_facade == null) _facade = interceptContinuationIfNeeded(_context!!, this)
        return _facade!!
    }

    override fun resume(value: Any?) {
        processBareContinuationResume(completion!!) {
            doResume(value, null)
        }
    }

    override fun resumeWithException(exception: Throwable) {
        processBareContinuationResume(completion!!) {
            doResume(null, exception)
        }
    }

    protected abstract fun doResume(data: Any?, exception: Throwable?): Any?

    open fun create(completion: Continuation<*>): Continuation<Unit> {
        throw IllegalStateException("create(Continuation) has not been overridden")
    }

    open fun create(value: Any?, completion: Continuation<*>): Continuation<Unit> {
        throw IllegalStateException("create(Any?;Continuation) has not been overridden")
    }
}
