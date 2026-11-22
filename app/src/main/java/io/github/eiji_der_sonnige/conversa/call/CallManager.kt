package io.github.eiji_der_sonnige.conversa.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface CallState {
    data object Idle : CallState
    data class Outgoing(val peer: String, val video: Boolean) : CallState
    data class Incoming(val peer: String, val video: Boolean) : CallState
    data class Active(val peer: String, val video: Boolean, val connected: Boolean) : CallState
}

/**
 * Audio-/Videoanrufe direkt Peer-to-Peer über WebRTC (DTLS-SRTP, Ende-zu-Ende verschlüsselt).
 * Die Verbindungsaushandlung (SDP/ICE) läuft verschlüsselt per Gift-Wrap über die Relays,
 * dadurch sind die DTLS-Fingerprints authentifiziert (kein Man-in-the-Middle möglich).
 */
class CallManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val iceConfig: () -> IceConfig,
    private val sendSignal: suspend (peer: String, payload: JSONObject) -> Unit,
) {
    data class IceConfig(val servers: List<PeerConnection.IceServer>, val relayOnly: Boolean)

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val eglBase: EglBase = EglBase.create()

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state
    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo
    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo
    val micOn = MutableStateFlow(true)
    val camOn = MutableStateFlow(true)
    val speakerOn = MutableStateFlow(false)

    private var pc: PeerConnection? = null
    private var peer: String? = null
    private var callId: String? = null
    private var isVideo = false
    private var pendingOffer: String? = null
    private var remoteDescSet = false
    private var iceConnected = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private val earlyCandidates = HashMap<String, MutableList<IceCandidate>>()
    private val outgoingCandidates = mutableListOf<JSONObject>()
    private var candidateFlush: Job? = null

    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localAudio: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null

    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun startCall(peerPub: String, video: Boolean) = scope.launch(dispatcher) {
        if (_state.value != CallState.Idle) return@launch
        peer = peerPub
        callId = io.github.eiji_der_sonnige.conversa.crypto.randomBytes(8).let { io.github.eiji_der_sonnige.conversa.crypto.Hex.encode(it) }
        isVideo = video
        _state.value = CallState.Outgoing(peerPub, video)
        try {
            setupAudio(video)
            val conn = createPeerConnection()
            addLocalTracks(conn, video)
            val offer = conn.createOfferS()
            conn.setLocalS(offer)
            send("offer", JSONObject().put("sdp", offer.description).put("video", video))
            val myCall = callId
            launch {
                delay(45_000)
                if (_state.value is CallState.Outgoing && callId == myCall) hangupInternal()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Anruf fehlgeschlagen", e)
            hangupInternal()
        }
    }

    fun onSignal(from: String, json: JSONObject) = scope.launch(dispatcher) {
        val type = json.optString("type")
        val cid = json.optString("callId")
        try {
            when (type) {
                "offer" -> {
                    if (cid.isEmpty() || (from == peer && cid == callId)) return@launch // Duplikat
                    if (_state.value != CallState.Idle) {
                        sendTo(from, cid, "busy", JSONObject())
                        return@launch
                    }
                    peer = from
                    callId = cid
                    isVideo = json.optBoolean("video")
                    pendingOffer = json.getString("sdp")
                    // Kandidaten, die vor dem Angebot eingetroffen sind, übernehmen.
                    earlyCandidates.remove(from + cid)?.let { pendingRemoteCandidates.addAll(it) }
                    earlyCandidates.clear()
                    _state.value = CallState.Incoming(from, isVideo)
                    launch {
                        delay(45_000)
                        if (_state.value is CallState.Incoming && callId == cid) rejectInternal()
                    }
                }
                "answer" -> if (from == peer && cid == callId && pc != null && !remoteDescSet &&
                    _state.value is CallState.Outgoing
                ) {
                    pc!!.setRemoteS(SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp")))
                    remoteDescSet = true
                    drainRemoteCandidates()
                    becomeActive(from, isVideo)
                }
                "candidates" -> {
                    val arr = json.getJSONArray("list")
                    val list = (0 until minOf(arr.length(), 64)).map { i ->
                        val c = arr.getJSONObject(i)
                        IceCandidate(c.getString("mid"), c.getInt("idx"), c.getString("c"))
                    }
                    if (from == peer && cid == callId) {
                        list.forEach { cand ->
                            if (remoteDescSet && pc != null) pc!!.addIceCandidate(cand) else pendingRemoteCandidates.add(cand)
                        }
                    } else if (_state.value == CallState.Idle && cid.isNotEmpty()) {
                        // Relays garantieren keine Reihenfolge: Kandidaten können vor dem Angebot ankommen.
                        if (earlyCandidates.size > 8) earlyCandidates.clear()
                        earlyCandidates.getOrPut(from + cid) { mutableListOf() }.addAll(list)
                    }
                }
                "hangup", "reject", "busy" -> if (from == peer && cid == callId) cleanup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signal-Fehler", e)
        }
    }

    fun accept() = scope.launch(dispatcher) {
        val st = _state.value as? CallState.Incoming ?: return@launch
        val offer = pendingOffer ?: return@launch
        try {
            setupAudio(st.video)
            val conn = createPeerConnection()
            conn.setRemoteS(SessionDescription(SessionDescription.Type.OFFER, offer))
            remoteDescSet = true
            addLocalTracks(conn, st.video)
            val answer = conn.createAnswerS()
            conn.setLocalS(answer)
            send("answer", JSONObject().put("sdp", answer.description))
            drainRemoteCandidates()
            becomeActive(st.peer, st.video)
        } catch (e: Exception) {
            Log.e(TAG, "Annehmen fehlgeschlagen", e)
            hangupInternal()
        }
    }

    /** Wechselt in den aktiven Zustand; kommt keine Verbindung zustande, wird nach 30 s aufgelegt. */
    private fun becomeActive(peerPub: String, video: Boolean) {
        _state.value = CallState.Active(peerPub, video, iceConnected)
        val myCall = callId
        scope.launch(dispatcher) {
            delay(CONNECT_TIMEOUT_MS)
            val s = _state.value
            if (s is CallState.Active && !s.connected && callId == myCall) {
                Log.w(TAG, "Keine Verbindung zustande gekommen – Anruf beendet")
                hangupInternal()
            }
        }
    }

    fun reject() = scope.launch(dispatcher) { rejectInternal() }
    fun hangup() = scope.launch(dispatcher) { hangupInternal() }

    private fun rejectInternal() {
        send("reject", JSONObject())
        cleanup()
    }

    private fun hangupInternal() {
        if (peer != null) send("hangup", JSONObject())
        cleanup()
    }

    fun toggleMic() {
        micOn.value = !micOn.value
        localAudio?.setEnabled(micOn.value)
    }

    fun toggleCamera() {
        camOn.value = !camOn.value
        localVideoTrack?.setEnabled(camOn.value)
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
    }

    fun toggleSpeaker() {
        speakerOn.value = !speakerOn.value
        routeAudio(speakerOn.value)
    }

    // ---------------------------------------------------------------------

    private fun send(type: String, extra: JSONObject) {
        val p = peer ?: return
        val c = callId ?: return
        sendTo(p, c, type, extra)
    }

    private fun sendTo(p: String, cid: String, type: String, extra: JSONObject) {
        val payload = extra.put("type", type).put("callId", cid)
        scope.launch { sendSignal(p, payload) }
    }

    private fun createPeerConnection(): PeerConnection {
        val ice = iceConfig()
        val cfg = PeerConnection.RTCConfiguration(ice.servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = if (ice.relayOnly) PeerConnection.IceTransportsType.RELAY
            else PeerConnection.IceTransportsType.ALL
        }
        val conn = requireNotNull(factory.createPeerConnection(cfg, observer)) { "PeerConnection fehlgeschlagen" }
        pc = conn
        return conn
    }

    private fun addLocalTracks(conn: PeerConnection, video: Boolean) {
        val aSrc = factory.createAudioSource(MediaConstraints())
        audioSource = aSrc
        localAudio = factory.createAudioTrack("audio0", aSrc).also {
            it.setEnabled(micOn.value)
            conn.addTrack(it, listOf("s0"))
        }
        if (!video) return
        val enumerator = Camera2Enumerator(context)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull() ?: return
        val cap = enumerator.createCapturer(name, null)
        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val src = factory.createVideoSource(false)
        cap.initialize(helper, context, src.capturerObserver)
        cap.startCapture(1280, 720, 30)
        val track = factory.createVideoTrack("video0", src)
        track.setEnabled(camOn.value)
        conn.addTrack(track, listOf("s0"))
        capturer = cap
        surfaceHelper = helper
        videoSource = src
        localVideoTrack = track
        _localVideo.value = track
    }

    private fun drainRemoteCandidates() {
        val conn = pc ?: return
        pendingRemoteCandidates.forEach { conn.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch(dispatcher) {
                outgoingCandidates.add(
                    JSONObject().put("mid", candidate.sdpMid).put("idx", candidate.sdpMLineIndex).put("c", candidate.sdp)
                )
                // Kandidaten bündeln, um wenige Relay-Events zu erzeugen.
                if (candidateFlush?.isActive != true) {
                    candidateFlush = launch {
                        delay(600)
                        if (outgoingCandidates.isNotEmpty()) {
                            val list = JSONArray().apply { outgoingCandidates.forEach { put(it) } }
                            outgoingCandidates.clear()
                            send("candidates", JSONObject().put("list", list))
                        }
                    }
                }
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            scope.launch(dispatcher) {
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        iceConnected = true
                        (_state.value as? CallState.Active)?.let { _state.value = it.copy(connected = true) }
                    }
                    PeerConnection.IceConnectionState.FAILED -> hangupInternal()
                    else -> Unit
                }
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val t = transceiver.receiver.track()
            if (t is VideoTrack) _remoteVideo.value = t
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    private fun setupAudio(video: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        speakerOn.value = video
        routeAudio(video)
    }

    private fun routeAudio(speaker: Boolean) {
        if (speaker) {
            audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { audioManager.setCommunicationDevice(it) }
        } else {
            audioManager.clearCommunicationDevice()
        }
    }

    private fun cleanup() {
        candidateFlush?.cancel()
        outgoingCandidates.clear()
        pendingRemoteCandidates.clear()
        remoteDescSet = false
        iceConnected = false
        pendingOffer = null
        _localVideo.value = null
        _remoteVideo.value = null
        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        capturer = null
        try { pc?.dispose() } catch (_: Exception) {}
        pc = null
        try { localVideoTrack?.dispose() } catch (_: Exception) {}
        try { localAudio?.dispose() } catch (_: Exception) {}
        localVideoTrack = null
        localAudio = null
        try { videoSource?.dispose() } catch (_: Exception) {}
        try { audioSource?.dispose() } catch (_: Exception) {}
        videoSource = null
        audioSource = null
        try { surfaceHelper?.dispose() } catch (_: Exception) {}
        surfaceHelper = null
        try {
            audioManager.clearCommunicationDevice()
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        micOn.value = true
        camOn.value = true
        peer = null
        callId = null
        _state.value = CallState.Idle
    }

    fun release() {
        // Eigener Scope, da der Messenger-Scope direkt danach beendet wird.
        CoroutineScope(dispatcher).launch {
            if (_state.value != CallState.Idle) hangupInternal()
            eglBase.release()
            dispatcher.close()
        }
    }

    // --- Suspend-Helfer für die Callback-API von WebRTC ---

    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    private suspend fun PeerConnection.createOfferS(): SessionDescription = suspendCancellableCoroutine { cont ->
        createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(p0: SessionDescription?) { cont.resume(p0!!) }
            override fun onCreateFailure(p0: String?) { cont.resumeWithException(IllegalStateException(p0)) }
        }, MediaConstraints())
    }

    private suspend fun PeerConnection.createAnswerS(): SessionDescription = suspendCancellableCoroutine { cont ->
        createAnswer(object : SdpAdapter() {
            override fun onCreateSuccess(p0: SessionDescription?) { cont.resume(p0!!) }
            override fun onCreateFailure(p0: String?) { cont.resumeWithException(IllegalStateException(p0)) }
        }, MediaConstraints())
    }

    private suspend fun PeerConnection.setLocalS(sdp: SessionDescription): Unit = suspendCancellableCoroutine { cont ->
        setLocalDescription(object : SdpAdapter() {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(p0: String?) { cont.resumeWithException(IllegalStateException(p0)) }
        }, sdp)
    }

    private suspend fun PeerConnection.setRemoteS(sdp: SessionDescription): Unit = suspendCancellableCoroutine { cont ->
        setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(p0: String?) { cont.resumeWithException(IllegalStateException(p0)) }
        }, sdp)
    }

    companion object {
        private const val TAG = "CallManager"
        private const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
