package org.newnewpipe.app.sync

import android.content.Context
import org.newnewpipe.app.NewPipeDatabase
import org.newnewpipe.app.database.subscription.SubscriptionEntity
import org.newnewpipe.extractor.subscription.SubscriptionItem

/**
 * Storage seam for subscriptions, so [SubscriptionSyncCodec] is testable on
 * the JVM without Room.
 */
interface SubscriptionStore {
    fun all(): List<SubscriptionItem>

    /** Replaces the local subscriptions with the merged snapshot (upsert by serviceId+url). */
    fun replaceAll(items: List<SubscriptionItem>)
}

/**
 * Reference codec for the subscriptions entity (plan 022 S7): the local
 * snapshot and the merged apply use the S6 [SubscriptionMerger] format
 * (per-item `updated_at_ms`), so last-write-wins keeps working across sync
 * runs. The same pattern applies to the history/playlists codecs (follow-up
 * step: they need the stream find-or-create glue).
 */
class SubscriptionSyncCodec(private val store: SubscriptionStore) : SyncCodec {

    override val fileName = "subscriptions.json"

    override fun localSnapshot(): String? {
        val items = store.all()
        if (items.isEmpty()) return null
        val now = System.currentTimeMillis()
        return SubscriptionMerger.serialize(
            items.map { SyncSubscription(it.serviceId, it.url, it.name, updatedAtMs = now) },
        )
    }

    override fun merge(local: String?, remote: String?): String? =
        SubscriptionMerger.merge(local, remote)

    override fun apply(merged: String?) {
        if (merged == null) return
        store.replaceAll(
            SubscriptionMerger.parse(merged)
                .map { SubscriptionItem(it.serviceId, it.url, it.name) },
        )
    }
}

/** Room-backed [SubscriptionStore]. */
class SubscriptionStoreImpl(context: Context) : SubscriptionStore {
    private val dao = NewPipeDatabase.getInstance(context.applicationContext).subscriptionDAO()

    override fun all(): List<SubscriptionItem> =
        dao.getAll().blockingFirst(emptyList())
            .map { SubscriptionItem(it.serviceId, it.url, it.name) }

    override fun replaceAll(items: List<SubscriptionItem>) {
        val entities = items.map { item ->
            SubscriptionEntity().apply {
                serviceId = item.serviceId
                url = item.url
                name = item.name
            }
        }
        dao.upsertAll(entities)
    }
}

/**
 * The entity codecs wired to the app's database. History and playlists
 * codecs are the documented follow-up (their apply step needs the
 * stream find-or-create glue).
 */
fun appSyncCodecs(context: Context): List<SyncCodec> =
    listOf(SubscriptionSyncCodec(SubscriptionStoreImpl(context)))
