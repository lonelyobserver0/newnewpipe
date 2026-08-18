package org.newnewpipe.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionMergerTest {

    @Test
    fun soloLocale_returnsLocalItemsInOrder() {
        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"A","updated_at_ms":1000},
            {"service_id":0,"url":"https://youtube.com/@b","name":"B","updated_at_ms":2000}]}"""

        val merged = SubscriptionMerger.merge(local, null)

        assertEquals(
            listOf(
                SyncSubscription(0, "https://youtube.com/@a", "A", 1000),
                SyncSubscription(0, "https://youtube.com/@b", "B", 2000),
            ),
            SubscriptionMerger.parse(merged),
        )
    }

    @Test
    fun soloRemoto_returnsRemoteItemsInOrder() {
        val remote = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@c","name":"C","updated_at_ms":3000}]}"""

        val merged = SubscriptionMerger.merge(null, remote)

        assertEquals(
            listOf(SyncSubscription(0, "https://youtube.com/@c", "C", 3000)),
            SubscriptionMerger.parse(merged),
        )
    }

    @Test
    fun entrambiSenzaConflitto_unioneLocalPoiRemoto() {
        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"A","updated_at_ms":1000}]}"""
        val remote = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@b","name":"B","updated_at_ms":2000}]}"""

        val merged = SubscriptionMerger.merge(local, remote)

        assertEquals(
            listOf(
                SyncSubscription(0, "https://youtube.com/@a", "A", 1000),
                SyncSubscription(0, "https://youtube.com/@b", "B", 2000),
            ),
            SubscriptionMerger.parse(merged),
        )
    }

    @Test
    fun conflittoLww_remotoPiuRecente_vinceRemoto() {
        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Vecchio","updated_at_ms":1000}]}"""
        val remote = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Nuovo","updated_at_ms":2000}]}"""

        val merged = SubscriptionMerger.merge(local, remote)

        assertEquals(
            listOf(SyncSubscription(0, "https://youtube.com/@a", "Nuovo", 2000)),
            SubscriptionMerger.parse(merged),
        )
    }

    @Test
    fun conflittoLww_localePiuRecente_vinceLocale() {
        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Nuovo","updated_at_ms":2000}]}"""
        val remote = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Vecchio","updated_at_ms":1000}]}"""

        val merged = SubscriptionMerger.merge(local, remote)

        assertEquals(
            listOf(SyncSubscription(0, "https://youtube.com/@a", "Nuovo", 2000)),
            SubscriptionMerger.parse(merged),
        )
    }

    @Test
    fun timestampUguali_preferisceLocale() {
        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Locale","updated_at_ms":1000}]}"""
        val remote = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Remoto","updated_at_ms":1000}]}"""

        val merged = SubscriptionMerger.merge(local, remote)

        assertEquals(
            listOf(SyncSubscription(0, "https://youtube.com/@a", "Locale", 1000)),
            SubscriptionMerger.parse(merged),
        )
    }

    @Test
    fun legacySenzaTimestamp_riusaParserImport_eTimestampedVince() {
        // Payload scritto da ImportExportJsonHelper (export dell'app): nessun
        // updated_at_ms per voce. Il merger lo riusa come snapshot (ts=0).
        val local = """{"app_version":"x","app_version_int":1,"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"CanaleLegacy"}]}"""
        val remote = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Aggiornato","updated_at_ms":5000}]}"""

        val merged = SubscriptionMerger.merge(local, remote)

        assertEquals(
            listOf(SyncSubscription(0, "https://youtube.com/@a", "Aggiornato", 5000)),
            SubscriptionMerger.parse(merged),
        )
    }

    @Test
    fun jsonCorrotto_trattatoComeVuoto_nonPerdeLAltroLato() {
        val local = "not json at all"
        val remote = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@b","name":"B","updated_at_ms":2000}]}"""

        val merged = SubscriptionMerger.merge(local, remote)

        assertEquals(
            listOf(SyncSubscription(0, "https://youtube.com/@b", "B", 2000)),
            SubscriptionMerger.parse(merged),
        )
    }

    @Test
    fun entrambiVuoti_ritornaNull() {
        assertNull(SubscriptionMerger.merge(null, null))
        assertNull(SubscriptionMerger.merge("", "  "))
    }
}
