package org.newnewpipe.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMergerTest {

    @Test
    fun soloLocale_ritornaVociLocali() {
        val local = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=a","title":"A","access_date_ms":2000,"repeat_count":1},
            {"service_id":0,"url":"https://youtube.com/watch?v=b","title":"B","access_date_ms":1000,"repeat_count":2}]}"""

        val merged = HistoryMerger.merge(local, null)

        assertEquals(
            listOf(
                SyncHistoryEntry(0, "https://youtube.com/watch?v=a", "A", 2000, 1),
                SyncHistoryEntry(0, "https://youtube.com/watch?v=b", "B", 1000, 2),
            ),
            HistoryMerger.parse(merged),
        )
    }

    @Test
    fun soloRemoto_ritornaVociRemote() {
        val remote = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=c","title":"C","access_date_ms":3000,"repeat_count":5}]}"""

        val merged = HistoryMerger.merge(null, remote)

        assertEquals(
            listOf(SyncHistoryEntry(0, "https://youtube.com/watch?v=c", "C", 3000, 5)),
            HistoryMerger.parse(merged),
        )
    }

    @Test
    fun entrambiSenzaConflitto_unioneOrdinataPerDataDesc() {
        val local = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=a","title":"A","access_date_ms":1000,"repeat_count":1}]}"""
        val remote = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=b","title":"B","access_date_ms":2000,"repeat_count":2}]}"""

        val merged = HistoryMerger.merge(local, remote)

        assertEquals(
            listOf(
                SyncHistoryEntry(0, "https://youtube.com/watch?v=b", "B", 2000, 2),
                SyncHistoryEntry(0, "https://youtube.com/watch?v=a", "A", 1000, 1),
            ),
            HistoryMerger.parse(merged),
        )
    }

    @Test
    fun conflittoLww_accessoPiuRecente_vince() {
        val local = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=a","title":"TitoloVecchio","access_date_ms":1000,"repeat_count":1}]}"""
        val remote = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=a","title":"TitoloNuovo","access_date_ms":2000,"repeat_count":3}]}"""

        val merged = HistoryMerger.merge(local, remote)

        assertEquals(
            listOf(SyncHistoryEntry(0, "https://youtube.com/watch?v=a", "TitoloNuovo", 2000, 3)),
            HistoryMerger.parse(merged),
        )
    }

    @Test
    fun conflittoLww_localePiuRecente_vinceLocale() {
        val local = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=a","title":"TitoloNuovo","access_date_ms":2000,"repeat_count":3}]}"""
        val remote = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=a","title":"TitoloVecchio","access_date_ms":1000,"repeat_count":1}]}"""

        val merged = HistoryMerger.merge(local, remote)

        assertEquals(
            listOf(SyncHistoryEntry(0, "https://youtube.com/watch?v=a", "TitoloNuovo", 2000, 3)),
            HistoryMerger.parse(merged),
        )
    }

    @Test
    fun timestampUguali_preferisceLocale() {
        val local = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=a","title":"Locale","access_date_ms":1000,"repeat_count":1}]}"""
        val remote = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=a","title":"Remoto","access_date_ms":1000,"repeat_count":1}]}"""

        val merged = HistoryMerger.merge(local, remote)

        assertEquals(
            listOf(SyncHistoryEntry(0, "https://youtube.com/watch?v=a", "Locale", 1000, 1)),
            HistoryMerger.parse(merged),
        )
    }

    @Test
    fun jsonCorrotto_trattatoComeVuoto_nonPerdeLAltroLato() {
        val local = "{broken"
        val remote = """{"history":[
            {"service_id":0,"url":"https://youtube.com/watch?v=b","title":"B","access_date_ms":2000,"repeat_count":2}]}"""

        val merged = HistoryMerger.merge(local, remote)

        assertEquals(
            listOf(SyncHistoryEntry(0, "https://youtube.com/watch?v=b", "B", 2000, 2)),
            HistoryMerger.parse(merged),
        )
    }

    @Test
    fun entrambiVuoti_ritornaNull() {
        assertNull(HistoryMerger.merge(null, null))
        assertNull(HistoryMerger.merge("", null))
    }
}
