/*
 * Copyright (C) 2019 iFLYTEK CO.,LTD.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iflytek.cyber.evs.sdk.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.dash.manifest.DashManifest
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.util.Util
import java.io.IOException

class MediaSourceFactory internal constructor(
    private val mContext: Context, private val mName: String
) {
    private val mMainHandler: Handler
    private val mPlaylistParser = PlaylistParser()
    private val mMediaSourceListener = MediaSourceListener()
    private val mFileDataSourceFactory = FileDataSource.Factory()
    private val mHttpDataSourceFactory: DataSource.Factory

    init {
        mHttpDataSourceFactory = buildHttpDataSourceFactory(mContext)

        mMainHandler = Handler()
    }//        mLogger = logger;

    private fun buildHttpDataSourceFactory(context: Context): HttpDataSource.Factory {
        val userAgent = Util.getUserAgent(context, sUserAgentName)
        // Some streams may see a long response time to begin data transfer from server after
        // connection. Use default 8 second connection timeout and increased 20 second read timeout
        // to catch this case and avoid reattempts to connect that will continue to time out.
        // May perceive long "dead time" in cases where data read takes a long time
        return DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
    }

    @Throws(Exception::class)
    internal fun createFileMediaSource(uri: Uri): MediaSource {
        return createMediaSource(
            uri, mFileDataSourceFactory, mMediaSourceListener, mMainHandler,
            mPlaylistParser
        )
    }

    @Throws(Exception::class)
    internal fun createHttpMediaSource(uri: Uri): MediaSource {
        return createMediaSource(
            uri, mHttpDataSourceFactory, mMediaSourceListener, mMainHandler,
            mPlaylistParser
        )
    }

    //
    // Media types for creating an ExoPlayer MediaSource
    //
    enum class MediaType private constructor(val type: Int) {
        DASH(C.CONTENT_TYPE_DASH),
        SMOOTH_STREAMING(C.CONTENT_TYPE_SS),
        HLS(C.CONTENT_TYPE_HLS),
        OTHER(C.CONTENT_TYPE_OTHER),
        M3U(4),
        PLS(5);


        companion object {

            fun inferContentType(fileExtension: String?): MediaType {
                if (fileExtension == null) {
                    return OTHER
                } else if (fileExtension.endsWith(".ashx") || fileExtension.endsWith(".m3u")) {
                    return M3U
                } else if (fileExtension.endsWith(".pls")) {
                    return PLS
                } else {
                    val type = Util.inferContentType(fileExtension)
                    for (mediaType in MediaType.values()) {
                        if (mediaType.type == type) return mediaType
                    }
                    return OTHER
                }
            }
        }
    }

    //
    // Media Source event listener
    //
    private inner class MediaSourceListener : MediaSourceEventListener {

        private var mRetryCount = 0

        override fun onLoadStarted(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            super.onLoadStarted(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData)
            mRetryCount = 1
        }

//        override fun onLoadStarted(
//            windowIndex: Int,
//            mediaPeriodId: MediaSource.MediaPeriodId?,
//            loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
//            mediaLoadData: MediaSourceEventListener.MediaLoadData?
//        ) {
//            mRetryCount = 1
//        }

        override fun onLoadCompleted(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            super.onLoadCompleted(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData)
            mRetryCount = 0
        }

//        override fun onLoadCompleted(
//            windowIndex: Int,
//            mediaPeriodId: MediaSource.MediaPeriodId?,
//            loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
//            mediaLoadData: MediaSourceEventListener.MediaLoadData?
//        ) {
//            mRetryCount = 0
//        }

        override fun onLoadCanceled(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            super.onLoadCanceled(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData)
            mRetryCount = 0
        }

//        override fun onLoadCanceled(
//            windowIndex: Int,
//            mediaPeriodId: MediaSource.MediaPeriodId?,
//            loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
//            mediaLoadData: MediaSourceEventListener.MediaLoadData?
//        ) {
//            mRetryCount = 0
//        }

        override fun onLoadError(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean
        ) {
            super.onLoadError(
                windowIndex,
                mediaPeriodId,
                loadEventInfo,
                mediaLoadData,
                error,
                wasCanceled
            )
            mRetryCount++
        }
//        override fun onLoadError(
//            windowIndex: Int,
//            mediaPeriodId: MediaSource.MediaPeriodId?,
//            loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
//            mediaLoadData: MediaSourceEventListener.MediaLoadData?,
//            error: IOException?,
//            wasCanceled: Boolean
//        ) {
//            mRetryCount++
//        }

    }

    companion object {
        private val sTag = "MediaSourceFactory"
        private const val sUserAgentName = "com.iflytek.sampleapp"

        @Throws(Exception::class)
        private fun createMediaSource(
            uri: Uri,
            dataSourceFactory: DataSource.Factory,
            mediaSourceListener: MediaSourceEventListener,
            handler: Handler,
            playlistParser: PlaylistParser
        ): MediaSource {
            when (val type = MediaType.inferContentType(uri.lastPathSegment)) {
                MediaType.DASH -> {
                    val ms = DashMediaSource.Factory(
                        DefaultDashChunkSource.Factory(dataSourceFactory),
                        dataSourceFactory
                    ).createMediaSource(
                        MediaItem.Builder()
                            .setUri(uri)
                            .build()
                    )
                    ms.addEventListener(handler, mediaSourceListener)
                    return ms
                }
                MediaType.SMOOTH_STREAMING -> {
                    val ms = SsMediaSource.Factory(
                        DefaultSsChunkSource.Factory(dataSourceFactory),
                        dataSourceFactory
                    ).createMediaSource(
                        MediaItem.Builder()
                            .setUri(uri)
                            .build()
                    )
                    ms.addEventListener(handler, mediaSourceListener)
                    return ms
                }
                MediaType.HLS -> {
                    val ms = HlsMediaSource.Factory(
                        dataSourceFactory
                    ).createMediaSource(
                        MediaItem.Builder()
                            .setUri(uri)
                            .build()
                    )
                    ms.addEventListener(handler, mediaSourceListener)
                    return ms
                }
                MediaType.M3U, MediaType.PLS -> {
                    val parsedUri = playlistParser.parseUri(uri)
                    return createMediaSource(
                        parsedUri, dataSourceFactory, mediaSourceListener,
                        handler, playlistParser
                    )
                }
                MediaType.OTHER -> {
                    val ms = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(
                            MediaItem.Builder()
                                .setUri(uri)
                                .build()
                        )
                    ms.addEventListener(handler, mediaSourceListener)
                    return ms
                }
                else -> {
                    throw IllegalStateException("Unsupported type: $type")
                }
            }
        }
    }
}
