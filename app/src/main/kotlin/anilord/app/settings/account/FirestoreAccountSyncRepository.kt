package anilord.app.settings.account

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import anilord.app.backups.data.model.FavouriteBackup
import anilord.app.backups.data.model.HistoryBackup
import anilord.app.core.db.MangaDatabase
import anilord.app.core.prefs.AppSettings
import javax.inject.Inject

@Reusable
class FirestoreAccountSyncRepository @Inject constructor(
    private val database: MangaDatabase,
    private val settings: AppSettings,
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

    suspend fun saveProfile(user: FirebaseUser, profile: FirebaseAccountActivity.AccountProfile) {
        firestore.collection(USERS).document(user.uid)
            .collection(PROFILE).document(PROFILE)
            .set(mapOf(
                "displayName" to profile.displayName,
                "country" to profile.country,
                "birthDate" to profile.birthDate,
                UPDATED_AT to System.currentTimeMillis(),
            ), SetOptions.merge())
            .await()
        user.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(profile.displayName)
            .build()).await()
    }

    suspend fun sync(user: FirebaseUser): SyncResult {
        val userRoot = firestore.collection(USERS).document(user.uid)
        val remoteFavourites = readPayloads(userRoot.collection(FAVOURITES))
        val remoteHistory = readPayloads(userRoot.collection(HISTORY))
        val settingsDocument = userRoot.collection(SETTINGS).document(SETTINGS).get().await()
        val remoteSettings = (settingsDocument.get(PAYLOAD) as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
            ?.toMap()
            .orEmpty()

        val localFavourites = database.getFavouritesDao().dump().collectToList().map(::FavouriteBackup)
        val localHistory = database.getHistoryDao().dump().collectToList().map(::HistoryBackup)
        val mergedFavourites = mergeFavourites(localFavourites, remoteFavourites)
        val mergedHistory = mergeHistory(localHistory, remoteHistory)

        database.withTransaction {
            mergedFavourites.forEach { restore(it) }
            mergedHistory.forEach { restore(it) }
        }
        if (remoteSettings.isNotEmpty()) settings.upsertAll(remoteSettings)

        val batch = firestore.batch()
        mergedFavourites.forEach { item ->
            val key = "${item.mangaId}_${item.categoryId}"
            batch.set(userRoot.collection(FAVOURITES).document(key), mapOf(
                PAYLOAD to json.encodeToString(FavouriteBackup.serializer(), item),
                UPDATED_AT to item.createdAt,
            ))
        }
        mergedHistory.forEach { item ->
            batch.set(userRoot.collection(HISTORY).document(item.mangaId.toString()), mapOf(
                PAYLOAD to json.encodeToString(HistoryBackup.serializer(), item),
                UPDATED_AT to item.updatedAt,
            ))
        }
        val safeSettings = settings.getAllValues()
            .filterKeys { it !in SENSITIVE_SETTINGS }
            .mapValues { (_, value) -> normalizeFirestoreValue(value) }
        batch.set(userRoot.collection(SETTINGS).document(SETTINGS), mapOf(
            PAYLOAD to safeSettings,
            UPDATED_AT to System.currentTimeMillis(),
        ), SetOptions.merge())
        batch.set(userRoot.collection(META).document(META), mapOf(
            "favoriteCount" to mergedFavourites.size,
            "historyCount" to mergedHistory.size,
            UPDATED_AT to System.currentTimeMillis(),
        ), SetOptions.merge())
        batch.commit().await()

        return SyncResult(mergedFavourites.size, mergedHistory.size)
    }

    private suspend fun restore(item: FavouriteBackup) {
        val tags = item.manga.tags.map { it.toEntity() }
        database.getTagsDao().upsert(tags)
        database.getMangaDao().upsert(item.manga.toEntity(), tags)
        database.getFavouritesDao().upsert(item.toEntity())
    }

    private suspend fun restore(item: HistoryBackup) {
        val tags = item.manga.tags.map { it.toEntity() }
        database.getTagsDao().upsert(tags)
        database.getMangaDao().upsert(item.manga.toEntity(), tags)
        database.getHistoryDao().upsert(item.toEntity())
    }

    private fun mergeFavourites(local: List<FavouriteBackup>, remotePayloads: List<String>): List<FavouriteBackup> {
        return (local + remotePayloads.mapNotNull { decodeFavourite(it) })
            .associateBy { "${it.mangaId}_${it.categoryId}" }
            .values
            .toList()
    }

    private fun mergeHistory(local: List<HistoryBackup>, remotePayloads: List<String>): List<HistoryBackup> {
        return (local + remotePayloads.mapNotNull { decodeHistory(it) })
            .groupBy { it.mangaId }
            .mapNotNull { (_, items) -> items.maxByOrNull { it.updatedAt } }
    }

    private suspend fun readPayloads(collection: CollectionReference): List<String> {
        return collection.get().await().documents.mapNotNull { it.getString(PAYLOAD) }
    }

    private fun decodeFavourite(payload: String): FavouriteBackup? = runCatching {
        json.decodeFromString(FavouriteBackup.serializer(), payload)
    }.getOrNull()

    private fun decodeHistory(payload: String): HistoryBackup? = runCatching {
        json.decodeFromString(HistoryBackup.serializer(), payload)
    }.getOrNull()

    private fun normalizeFirestoreValue(value: Any?): Any? = when (value) {
        is Set<*> -> value.map { normalizeFirestoreValue(it) }
        is Iterable<*> -> value.map { normalizeFirestoreValue(it) }
        is Map<*, *> -> value.entries.associate { it.key.toString() to normalizeFirestoreValue(it.value) }
        is Enum<*> -> value.name
        else -> value
    }

    data class SyncResult(val favorites: Int, val history: Int)

    companion object {
        private const val USERS = "users"
        private const val FAVOURITES = "favourites"
        private const val HISTORY = "history"
        private const val SETTINGS = "settings"
        private const val META = "meta"
        private const val PROFILE = "profile"
        private const val PAYLOAD = "payload"
        private const val UPDATED_AT = "updatedAt"
        private val SENSITIVE_SETTINGS = setOf(
            AppSettings.KEY_APP_PASSWORD,
            AppSettings.KEY_PROXY_PASSWORD,
            AppSettings.KEY_PROXY_LOGIN,
            AppSettings.KEY_INCOGNITO_MODE,
        )
    }
}

private suspend fun <T> Flow<T>.collectToList(): List<T> {
    val result = mutableListOf<T>()
    collect { result += it }
    return result
}
