package com.example.myplayer.playback

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.myplayer.data.Track

const val EXTRA_SOURCE = "source"
const val EXTRA_MODE_ID = "mode_id"

private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

/**
 * Zamienia [Track] na element kolejki Media3.
 * mediaId = stabilny klucz utworu; w extras zapisujemy źródło kolejki i (opcjonalnie) id trybu,
 * żeby usługa wiedziała, skąd pochodzi aktualna kolejka.
 */
fun Track.toMediaItem(source: String, modeId: String? = null): MediaItem {
    val extras = Bundle().apply {
        putString(EXTRA_SOURCE, source)
        if (modeId != null) putString(EXTRA_MODE_ID, modeId)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist.ifBlank { null })
        .setAlbumTitle(album.ifBlank { null })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
        .apply {
            if (albumId > 0) setArtworkUri(ContentUris.withAppendedId(ALBUM_ART_BASE, albumId))
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(key)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}
