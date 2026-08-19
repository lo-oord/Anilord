package anilord.app.scrobbling

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import okhttp3.OkHttpClient
import anilord.app.BuildConfig
import anilord.app.core.db.MangaDatabase
import anilord.app.core.network.BaseHttpClient
import anilord.app.core.network.CurlLoggingInterceptor
import anilord.app.scrobbling.anilist.data.AniListAuthenticator
import anilord.app.scrobbling.anilist.data.AniListInterceptor
import anilord.app.scrobbling.anilist.domain.AniListScrobbler
import anilord.app.scrobbling.common.data.ScrobblerStorage
import anilord.app.scrobbling.common.domain.Scrobbler
import anilord.app.scrobbling.common.domain.model.ScrobblerService
import anilord.app.scrobbling.common.domain.model.ScrobblerType
import anilord.app.scrobbling.kitsu.data.KitsuAuthenticator
import anilord.app.scrobbling.kitsu.data.KitsuInterceptor
import anilord.app.scrobbling.kitsu.data.KitsuRepository
import anilord.app.scrobbling.kitsu.domain.KitsuScrobbler
import anilord.app.scrobbling.mal.data.MALAuthenticator
import anilord.app.scrobbling.mal.data.MALInterceptor
import anilord.app.scrobbling.mal.domain.MALScrobbler
import anilord.app.scrobbling.shikimori.data.ShikimoriAuthenticator
import anilord.app.scrobbling.shikimori.data.ShikimoriInterceptor
import anilord.app.scrobbling.shikimori.domain.ShikimoriScrobbler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScrobblingModule {

	@Provides
	@Singleton
	@ScrobblerType(ScrobblerService.SHIKIMORI)
	fun provideShikimoriHttpClient(
		@BaseHttpClient baseHttpClient: OkHttpClient,
		authenticator: ShikimoriAuthenticator,
		@ScrobblerType(ScrobblerService.SHIKIMORI) storage: ScrobblerStorage,
	): OkHttpClient = baseHttpClient.newBuilder().apply {
		authenticator(authenticator)
		addInterceptor(ShikimoriInterceptor(storage))
	}.build()

	@Provides
	@Singleton
	@ScrobblerType(ScrobblerService.MAL)
	fun provideMALHttpClient(
		@BaseHttpClient baseHttpClient: OkHttpClient,
		authenticator: MALAuthenticator,
		@ScrobblerType(ScrobblerService.MAL) storage: ScrobblerStorage,
	): OkHttpClient = baseHttpClient.newBuilder().apply {
		authenticator(authenticator)
		addInterceptor(MALInterceptor(storage))
	}.build()

	@Provides
	@Singleton
	@ScrobblerType(ScrobblerService.ANILIST)
	fun provideAniListHttpClient(
		@BaseHttpClient baseHttpClient: OkHttpClient,
		authenticator: AniListAuthenticator,
		@ScrobblerType(ScrobblerService.ANILIST) storage: ScrobblerStorage,
	): OkHttpClient = baseHttpClient.newBuilder().apply {
		authenticator(authenticator)
		addInterceptor(AniListInterceptor(storage))
	}.build()

	@Provides
	@Singleton
	fun provideKitsuRepository(
		@ApplicationContext context: Context,
		@ScrobblerType(ScrobblerService.KITSU) storage: ScrobblerStorage,
		database: MangaDatabase,
		authenticator: KitsuAuthenticator,
	): KitsuRepository {
		val okHttp = OkHttpClient.Builder().apply {
			authenticator(authenticator)
			addInterceptor(KitsuInterceptor(storage))
			if (BuildConfig.DEBUG) {
				addInterceptor(CurlLoggingInterceptor())
			}
		}.build()
		return KitsuRepository(context, okHttp, storage, database)
	}

	@Provides
	@Singleton
	@ScrobblerType(ScrobblerService.ANILIST)
	fun provideAniListStorage(
		@ApplicationContext context: Context,
	): ScrobblerStorage = ScrobblerStorage(context, ScrobblerService.ANILIST)

	@Provides
	@Singleton
	@ScrobblerType(ScrobblerService.SHIKIMORI)
	fun provideShikimoriStorage(
		@ApplicationContext context: Context,
	): ScrobblerStorage = ScrobblerStorage(context, ScrobblerService.SHIKIMORI)

	@Provides
	@Singleton
	@ScrobblerType(ScrobblerService.MAL)
	fun provideMALStorage(
		@ApplicationContext context: Context,
	): ScrobblerStorage = ScrobblerStorage(context, ScrobblerService.MAL)

	@Provides
	@Singleton
	@ScrobblerType(ScrobblerService.KITSU)
	fun provideKitsuStorage(
		@ApplicationContext context: Context,
	): ScrobblerStorage = ScrobblerStorage(context, ScrobblerService.KITSU)

	@Provides
	@ElementsIntoSet
	fun provideScrobblers(
		shikimoriScrobbler: ShikimoriScrobbler,
		aniListScrobbler: AniListScrobbler,
		malScrobbler: MALScrobbler,
		kitsuScrobbler: KitsuScrobbler
	): Set<@JvmSuppressWildcards Scrobbler> = setOf(shikimoriScrobbler, aniListScrobbler, malScrobbler, kitsuScrobbler)
}
