package org.newnewpipe.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistMergerTest {

    @Test
    fun soloLocale_ritornaPlaylistInOrdine() {
        val local = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":"https://thumb/a","updated_at_ms":1000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=1","title":"Uno","join_index":0,"updated_at_ms":1000},
                {"service_id":0,"url":"https://youtube.com/watch?v=2","title":"Due","join_index":1,"updated_at_ms":1000}]},
            {"name":"Da vedere","thumbnail_url":null,"updated_at_ms":2000,"streams":[]}]}"""

        val merged = PlaylistMerger.merge(local, null)

        assertEquals(
            listOf(
                SyncPlaylist(
                    name = "Preferiti",
                    thumbnailUrl = "https://thumb/a",
                    updatedAtMs = 1000,
                    streams = listOf(
                        SyncPlaylistItem(0, "https://youtube.com/watch?v=1", "Uno", 0, 1000),
                        SyncPlaylistItem(0, "https://youtube.com/watch?v=2", "Due", 1, 1000),
                    ),
                ),
                SyncPlaylist("Da vedere", null, 2000, emptyList()),
            ),
            PlaylistMerger.parse(merged),
        )
    }

    @Test
    fun soloRemoto_ritornaPlaylistRemote() {
        val remote = """{"playlists":[
            {"name":"Remota","thumbnail_url":null,"updated_at_ms":3000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=9","title":"Nove","join_index":0,"updated_at_ms":3000}]}]}"""

        val merged = PlaylistMerger.merge(null, remote)

        assertEquals(
            listOf(
                SyncPlaylist(
                    name = "Remota",
                    thumbnailUrl = null,
                    updatedAtMs = 3000,
                    streams = listOf(
                        SyncPlaylistItem(0, "https://youtube.com/watch?v=9", "Nove", 0, 3000),
                    ),
                ),
            ),
            PlaylistMerger.parse(merged),
        )
    }

    @Test
    fun entrambiSenzaConflitto_unionePlaylistLocaliPoiRemote() {
        val local = """{"playlists":[
            {"name":"Locale","thumbnail_url":null,"updated_at_ms":1000,"streams":[]}]}"""
        val remote = """{"playlists":[
            {"name":"Remota","thumbnail_url":null,"updated_at_ms":2000,"streams":[]}]}"""

        val merged = PlaylistMerger.merge(local, remote)

        assertEquals(
            listOf(
                SyncPlaylist("Locale", null, 1000, emptyList()),
                SyncPlaylist("Remota", null, 2000, emptyList()),
            ),
            PlaylistMerger.parse(merged),
        )
    }

    @Test
    fun conflittoLww_playlistRemotaPiuRecente_vinceMetadatiMaNonPerdeItemLocali() {
        val local = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":"https://thumb/old","updated_at_ms":1000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=1","title":"Uno","join_index":0,"updated_at_ms":1000}]}]}"""
        val remote = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":"https://thumb/new","updated_at_ms":2000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=1","title":"Uno","join_index":0,"updated_at_ms":1000}]}]}"""

        val merged = PlaylistMerger.merge(local, remote)

        // I metadati vincono dal remoto (più recente), ma l'item presente solo
        // sul lato locale sopravvive al merge a livello di playlist.
        assertEquals(
            listOf(
                SyncPlaylist(
                    name = "Preferiti",
                    thumbnailUrl = "https://thumb/new",
                    updatedAtMs = 2000,
                    streams = listOf(
                        SyncPlaylistItem(0, "https://youtube.com/watch?v=1", "Uno", 0, 1000),
                    ),
                ),
            ),
            PlaylistMerger.parse(merged),
        )
    }

    @Test
    fun conflittoLww_itemPiuRecente_vinceConIlSuoJoinIndex() {
        val local = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":null,"updated_at_ms":1000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=1","title":"TitoloVecchio","join_index":0,"updated_at_ms":1000}]}]}"""
        val remote = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":null,"updated_at_ms":1000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=1","title":"TitoloNuovo","join_index":4,"updated_at_ms":2000}]}]}"""

        val merged = PlaylistMerger.merge(local, remote)

        assertEquals(
            listOf(
                SyncPlaylist(
                    name = "Preferiti",
                    thumbnailUrl = null,
                    updatedAtMs = 1000,
                    streams = listOf(
                        SyncPlaylistItem(0, "https://youtube.com/watch?v=1", "TitoloNuovo", 4, 2000),
                    ),
                ),
            ),
            PlaylistMerger.parse(merged),
        )
    }

    @Test
    fun itemSoloSuUnLato_ordineLocalePoiRemotoAppended() {
        val local = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":null,"updated_at_ms":1000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=1","title":"Uno","join_index":0,"updated_at_ms":1000}]}]}"""
        val remote = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":null,"updated_at_ms":1000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=2","title":"Due","join_index":0,"updated_at_ms":2000}]}]}"""

        val merged = PlaylistMerger.merge(local, remote)

        assertEquals(
            listOf(
                SyncPlaylist(
                    name = "Preferiti",
                    thumbnailUrl = null,
                    updatedAtMs = 1000,
                    streams = listOf(
                        SyncPlaylistItem(0, "https://youtube.com/watch?v=1", "Uno", 0, 1000),
                        SyncPlaylistItem(0, "https://youtube.com/watch?v=2", "Due", 0, 2000),
                    ),
                ),
            ),
            PlaylistMerger.parse(merged),
        )
    }

    @Test
    fun timestampUguali_preferisceLocale() {
        val local = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":null,"updated_at_ms":1000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=1","title":"Locale","join_index":0,"updated_at_ms":1000}]}]}"""
        val remote = """{"playlists":[
            {"name":"Preferiti","thumbnail_url":null,"updated_at_ms":1000,"streams":[
                {"service_id":0,"url":"https://youtube.com/watch?v=1","title":"Remoto","join_index":0,"updated_at_ms":1000}]}]}"""

        val merged = PlaylistMerger.merge(local, remote)

        assertEquals(
            listOf(
                SyncPlaylist(
                    name = "Preferiti",
                    thumbnailUrl = null,
                    updatedAtMs = 1000,
                    streams = listOf(
                        SyncPlaylistItem(0, "https://youtube.com/watch?v=1", "Locale", 0, 1000),
                    ),
                ),
            ),
            PlaylistMerger.parse(merged),
        )
    }

    @Test
    fun jsonCorrotto_trattatoComeVuoto_nonPerdeLAltroLato() {
        val local = "###nope###"
        val remote = """{"playlists":[
            {"name":"Remota","thumbnail_url":null,"updated_at_ms":2000,"streams":[]}]}"""

        val merged = PlaylistMerger.merge(local, remote)

        assertEquals(
            listOf(SyncPlaylist("Remota", null, 2000, emptyList())),
            PlaylistMerger.parse(merged),
        )
    }

    @Test
    fun entrambiVuoti_ritornaNull() {
        assertNull(PlaylistMerger.merge(null, null))
        assertNull(PlaylistMerger.merge(null, ""))
    }
}
