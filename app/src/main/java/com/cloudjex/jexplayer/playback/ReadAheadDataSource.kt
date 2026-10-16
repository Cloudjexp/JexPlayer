package com.cloudjex.jexplayer.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import kotlin.math.min

@OptIn(UnstableApi::class)
internal class ReadAheadDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val bufferSizeBytes: Int = DEFAULT_BUFFER_SIZE_BYTES
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        ReadAheadDataSource(upstreamFactory.createDataSource(), bufferSizeBytes)

    private companion object {
        const val DEFAULT_BUFFER_SIZE_BYTES = 64 * 1024
    }
}

@OptIn(UnstableApi::class)
private class ReadAheadDataSource(
    private val upstream: DataSource,
    bufferSizeBytes: Int
) : DataSource {

    private val bufferSizeBytes = bufferSizeBytes.coerceAtLeast(MIN_BUFFER_SIZE_BYTES)
    private var buffer = ByteArray(0)
    private var bufferReadOffset = 0
    private var bufferByteCount = 0
    private var remainingBytes = C.LENGTH_UNSET.toLong()
    private var endOfInput = false
    private var upstreamOpen = false
    private var useReadAhead = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        if (upstreamOpen) close()
        resetBuffer()

        useReadAhead = dataSpec.uri.lastPathSegment
            ?.endsWith(FLAC_EXTENSION, ignoreCase = true) == true
        if (useReadAhead) buffer = ByteArray(bufferSizeBytes)

        upstreamOpen = true
        val resolvedLength = upstream.open(dataSpec)
        remainingBytes = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            resolvedLength != C.LENGTH_UNSET.toLong() -> resolvedLength
            else -> C.LENGTH_UNSET.toLong()
        }
        return resolvedLength
    }

    @Throws(IOException::class)
    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remainingBytes == 0L) return C.RESULT_END_OF_INPUT
        if (!useReadAhead) return readUpstream(target, offset, length)

        if (bufferReadOffset < bufferByteCount) {
            return copyBufferedBytes(target, offset, length)
        }
        if (endOfInput) return C.RESULT_END_OF_INPUT

        bufferReadOffset = 0
        bufferByteCount = 0

        if (length >= READ_AHEAD_REQUEST_LIMIT_BYTES) {
            return readUpstream(target, offset, length)
        }

        fillBuffer()
        if (bufferByteCount == 0) return C.RESULT_END_OF_INPUT
        return copyBufferedBytes(target, offset, length)
    }

    private fun copyBufferedBytes(target: ByteArray, offset: Int, length: Int): Int {
        var bytesToCopy = min(length, bufferByteCount - bufferReadOffset)
        if (remainingBytes != C.LENGTH_UNSET.toLong()) {
            bytesToCopy = min(bytesToCopy.toLong(), remainingBytes).toInt()
        }
        if (bytesToCopy <= 0) return C.RESULT_END_OF_INPUT

        System.arraycopy(buffer, bufferReadOffset, target, offset, bytesToCopy)
        bufferReadOffset += bytesToCopy
        decreaseRemainingBytes(bytesToCopy)
        return bytesToCopy
    }

    @Throws(IOException::class)
    private fun fillBuffer() {
        var bytesFetched = 0
        while (bytesFetched < buffer.size) {
            val requestedBytes = if (remainingBytes == C.LENGTH_UNSET.toLong()) {
                buffer.size - bytesFetched
            } else {
                min((buffer.size - bytesFetched).toLong(), remainingBytes - bytesFetched).toInt()
            }
            if (requestedBytes <= 0) break

            val bytesRead = try {
                readUpstreamBytes(buffer, bytesFetched, requestedBytes)
            } catch (error: IOException) {
                resetBuffer()
                throw error
            }

            when {
                bytesRead == C.RESULT_END_OF_INPUT -> {
                    endOfInput = true
                    break
                }
                else -> bytesFetched += bytesRead
            }
        }
        bufferByteCount = bytesFetched
    }

    @Throws(IOException::class)
    private fun readUpstream(target: ByteArray, offset: Int, length: Int): Int {
        val requestedBytes = if (remainingBytes == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            min(length.toLong(), remainingBytes).toInt()
        }
        if (requestedBytes <= 0) return C.RESULT_END_OF_INPUT

        val bytesRead = readUpstreamBytes(target, offset, requestedBytes)
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            endOfInput = true
        } else if (bytesRead > 0) {
            decreaseRemainingBytes(bytesRead)
        }
        return bytesRead
    }

    @Throws(IOException::class)
    private fun readUpstreamBytes(target: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(target, offset, length)
        if (bytesRead == 0) {
            throw IOException("Upstream returned 0 bytes for a non-empty read")
        }
        return bytesRead
    }

    private fun decreaseRemainingBytes(bytesRead: Int) {
        if (remainingBytes != C.LENGTH_UNSET.toLong()) {
            remainingBytes -= bytesRead
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    @Throws(IOException::class)
    override fun close() {
        try {
            if (upstreamOpen) upstream.close()
        } finally {
            upstreamOpen = false
            useReadAhead = false
            remainingBytes = C.LENGTH_UNSET.toLong()
            endOfInput = false
            resetBuffer()
            buffer = ByteArray(0)
        }
    }

    private fun resetBuffer() {
        bufferReadOffset = 0
        bufferByteCount = 0
    }

    private companion object {
        const val FLAC_EXTENSION = ".flac"
        const val MIN_BUFFER_SIZE_BYTES = 1
        const val READ_AHEAD_REQUEST_LIMIT_BYTES = 4 * 1024
    }
}
