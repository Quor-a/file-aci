/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.fileproperties.audio

import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.ai.assistance.quro.file.R
import com.ai.assistance.quro.file.file.FileItem
import com.ai.assistance.quro.file.file.format
import com.ai.assistance.quro.file.file.isAudio
import com.ai.assistance.quro.file.fileproperties.FilePropertiesTabFragment
import com.ai.assistance.quro.file.util.ParcelableArgs
import com.ai.assistance.quro.file.util.ParcelableParceler
import com.ai.assistance.quro.file.util.Stateful
import com.ai.assistance.quro.file.util.args
import com.ai.assistance.quro.file.util.isMediaMetadataRetrieverCompatible
import com.ai.assistance.quro.file.util.viewModels

class FilePropertiesAudioTabFragment : FilePropertiesTabFragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { FilePropertiesAudioTabViewModel(args.path) } }

    override fun onResume() {
        super.onResume()

        viewModel.audioInfoLiveData.observe(viewLifecycleOwner) { onAudioInfoChanged(it) }
    }

    override fun refresh() {
        viewModel.reload()
    }

    private fun onAudioInfoChanged(stateful: Stateful<AudioInfo>) {
        bindView(stateful) { audioInfo ->
            if (audioInfo.title != null) {
                addItemView(R.string.file_properties_media_title, audioInfo.title)
            }
            if (audioInfo.artist != null) {
                addItemView(R.string.file_properties_audio_artist, audioInfo.artist)
            }
            if (audioInfo.album != null) {
                addItemView(R.string.file_properties_audio_album, audioInfo.album)
            }
            if (audioInfo.albumArtist != null) {
                addItemView(R.string.file_properties_audio_album_artist, audioInfo.albumArtist)
            }
            if (audioInfo.composer != null) {
                addItemView(R.string.file_properties_audio_composer, audioInfo.composer)
            }
            if (audioInfo.discNumber != null) {
                addItemView(R.string.file_properties_audio_disc_number, audioInfo.discNumber)
            }
            if (audioInfo.trackNumber != null) {
                addItemView(R.string.file_properties_audio_track_number, audioInfo.trackNumber)
            }
            if (audioInfo.year != null) {
                addItemView(R.string.file_properties_audio_year, audioInfo.year)
            }
            if (audioInfo.genre != null) {
                addItemView(R.string.file_properties_audio_genre, audioInfo.genre)
            }
            if (audioInfo.duration != null) {
                addItemView(R.string.file_properties_media_duration, audioInfo.duration.format())
            }
            if (audioInfo.bitRate != null) {
                addItemView(
                    R.string.file_properties_media_bit_rate, getString(
                        R.string.file_properties_media_bit_rate_format, audioInfo.bitRate / 1000
                    )
                )
            }
            if (audioInfo.sampleRate != null) {
                addItemView(
                    R.string.file_properties_audio_sample_rate, getString(
                        R.string.file_properties_audio_sample_rate_format, audioInfo.sampleRate
                    )
                )
            }
        }
    }

    companion object {
        fun isAvailable(file: FileItem): Boolean =
            file.mimeType.isAudio && file.path.isMediaMetadataRetrieverCompatible
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
