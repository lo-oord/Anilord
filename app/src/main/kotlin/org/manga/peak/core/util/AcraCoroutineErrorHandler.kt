package org.manga.peak.core.util

import kotlinx.coroutines.CoroutineExceptionHandler
import org.manga.peak.core.util.ext.printStackTraceDebug
import org.manga.peak.core.util.ext.report
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class AcraCoroutineErrorHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
	CoroutineExceptionHandler {

	override fun handleException(context: CoroutineContext, exception: Throwable) {
		exception.printStackTraceDebug()
		exception.report()
	}
}
