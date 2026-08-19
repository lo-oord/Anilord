package anilord.app.core.ui

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import anilord.app.core.exceptions.resolve.ExceptionResolver
import anilord.app.core.prefs.AppSettings

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BaseActivityEntryPoint {

	val settings: AppSettings

	val exceptionResolverFactory: ExceptionResolver.Factory
}
