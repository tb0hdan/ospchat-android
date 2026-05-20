package com.ospchat.android.media

import android.content.Context
import com.ospchat.shared.media.AudioCallSession
import com.ospchat.shared.media.AudioCallSessionFactory
import com.ospchat.shared.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android actual of [AudioCallSession] backed by `io.getstream:stream-webrtc-android`
 * (Stream's actively-maintained Android fork of libwebrtc, keeping the
 * `org.webrtc.*` namespace).
 *
 * Audio-only — exactly one local audio track. ICE servers empty: LAN-only,
 * host candidates only. libwebrtc invokes observer callbacks on its own
 * signaling thread; we republish state and ICE via Flows so the rest of
 * the app stays on Kotlin coroutines.
 */
class AndroidAudioCallSession(
    factory: PeerConnectionFactory,
) : AudioCallSession {
    private val _state = MutableStateFlow(AudioCallSession.State.NEW)
    override val state: StateFlow<AudioCallSession.State> = _state.asStateFlow()

    private val iceFlow =
        MutableSharedFlow<AudioCallSession.IceCandidate>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val localIceCandidates: Flow<AudioCallSession.IceCandidate> = iceFlow.asSharedFlow()

    private val closed = AtomicBoolean(false)

    private val audioSource: AudioSource = factory.createAudioSource(MediaConstraints())
    private val audioTrack: AudioTrack = factory.createAudioTrack("ospchat-audio", audioSource)

    private val peer: PeerConnection =
        requireNotNull(
            factory.createPeerConnection(
                PeerConnection.RTCConfiguration(emptyList()),
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        iceFlow.tryEmit(
                            AudioCallSession.IceCandidate(
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex,
                                candidate = candidate.sdp,
                            ),
                        )
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                        _state.value =
                            when (newState) {
                                PeerConnection.PeerConnectionState.NEW -> AudioCallSession.State.NEW
                                PeerConnection.PeerConnectionState.CONNECTING -> AudioCallSession.State.NEGOTIATING
                                PeerConnection.PeerConnectionState.CONNECTED -> AudioCallSession.State.CONNECTED
                                PeerConnection.PeerConnectionState.DISCONNECTED -> AudioCallSession.State.NEGOTIATING
                                PeerConnection.PeerConnectionState.FAILED -> AudioCallSession.State.FAILED
                                PeerConnection.PeerConnectionState.CLOSED -> AudioCallSession.State.CLOSED
                            }
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                        Log.d(TAG, "ICE connection state: $state")
                    }

                    // Required no-op overrides
                    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

                    override fun onAddStream(stream: MediaStream) = Unit

                    override fun onRemoveStream(stream: MediaStream) = Unit

                    override fun onDataChannel(dc: DataChannel) = Unit

                    override fun onRenegotiationNeeded() = Unit

                    override fun onAddTrack(
                        receiver: RtpReceiver,
                        mediaStreams: Array<out MediaStream>,
                    ) = Unit
                },
            ),
        ) { "createPeerConnection returned null" }

    init {
        peer.addTrack(audioTrack, listOf("ospchat-stream"))
    }

    override suspend fun createOffer(): String {
        val sdp = awaitCreate { observer -> peer.createOffer(observer, MediaConstraints()) }
        awaitSet { observer -> peer.setLocalDescription(observer, sdp) }
        _state.value = AudioCallSession.State.NEGOTIATING
        return sdp.description
    }

    override suspend fun acceptOffer(remoteSdp: String): String {
        val remote = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
        awaitSet { observer -> peer.setRemoteDescription(observer, remote) }
        val answer = awaitCreate { observer -> peer.createAnswer(observer, MediaConstraints()) }
        awaitSet { observer -> peer.setLocalDescription(observer, answer) }
        _state.value = AudioCallSession.State.NEGOTIATING
        return answer.description
    }

    override suspend fun setRemoteAnswer(sdp: String) {
        val remote = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        awaitSet { observer -> peer.setRemoteDescription(observer, remote) }
    }

    override suspend fun addRemoteIce(candidate: AudioCallSession.IceCandidate) {
        peer.addIceCandidate(IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate))
    }

    override fun setMuted(muted: Boolean) {
        audioTrack.setEnabled(!muted)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Order matters: close + dispose the peer connection BEFORE the track
        // and source. The native PeerConnection holds references to its
        // attached tracks; disposing the track first creates a use-after-free
        // window in the C++ layer on some libwebrtc versions.
        runCatching { peer.close() }.onFailure { Log.w(TAG, "peer.close failed", it) }
        runCatching { peer.dispose() }
        runCatching { audioTrack.dispose() }
        runCatching { audioSource.dispose() }
        _state.value = AudioCallSession.State.CLOSED
    }

    private suspend fun awaitCreate(start: (SdpObserver) -> Unit): SessionDescription =
        suspendCancellableCoroutine { cont ->
            start(
                object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        cont.resume(sdp)
                    }

                    override fun onCreateFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException("WebRTC create SDP failed: $error"))
                    }

                    override fun onSetSuccess() = Unit

                    override fun onSetFailure(error: String?) = Unit
                },
            )
        }

    private suspend fun awaitSet(start: (SdpObserver) -> Unit): Unit =
        suspendCancellableCoroutine { cont ->
            start(
                object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) = Unit

                    override fun onCreateFailure(error: String?) = Unit

                    override fun onSetSuccess() {
                        cont.resume(Unit)
                    }

                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException("WebRTC setSDP failed: $error"))
                    }
                },
            )
        }

    private companion object {
        const val TAG = "AndroidAudioCallSession"
    }
}

/**
 * Holds the single shared [PeerConnectionFactory] and initializes the native
 * libwebrtc library on first use. One factory per process; cheap session
 * instances per call.
 */
@Singleton
class AndroidAudioCallSessionFactory
    @Inject
    constructor(
        private val context: Context,
    ) : AudioCallSessionFactory {
        private val factory: PeerConnectionFactory by lazy {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(context)
                    .createInitializationOptions(),
            )
            PeerConnectionFactory.builder().createPeerConnectionFactory()
        }

        override fun create(): AudioCallSession = AndroidAudioCallSession(factory)

        fun shutdown() {
            runCatching { factory.dispose() }
        }
    }
