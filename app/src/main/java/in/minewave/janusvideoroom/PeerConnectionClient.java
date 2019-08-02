package in.minewave.janusvideoroom;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
//import org.webrtc.VideoRenderer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback;
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback;

import org.webrtc.VideoSink;

public class PeerConnectionClient {
  public static final String VIDEO_TRACK_ID = "ARDAMSv0";
  public static final String AUDIO_TRACK_ID = "ARDAMSa0";
  public static final String VIDEO_TRACK_TYPE = "video";
  private static final String TAG = "PCRTCClient";
  private static final String VIDEO_CODEC_VP8 = "VP8";
  private static final String VIDEO_CODEC_VP9 = "VP9";
  private static final String VIDEO_CODEC_H264 = "H264";
  private static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
  private static final String VIDEO_CODEC_H264_HIGH = "H264 High";
  private static final String VIDEO_FLEXFEC_FIELDTRIAL =
          "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
  private static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
  private static final String DISABLE_WEBRTC_AGC_FIELDTRIAL =
          "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";
  private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
  private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
  private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
  private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
  private static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
  private static final int HD_VIDEO_WIDTH = 1280;
  private static final int HD_VIDEO_HEIGHT = 720;

  private static final PeerConnectionClient instance = new PeerConnectionClient();

  private final ScheduledExecutorService executor;
  private EglBase rootEglBase;
  private Context context;
  private PeerConnectionFactory factory;
  private ConcurrentHashMap<BigInteger, JanusConnection> peerConnectionMap;

  PeerConnectionFactory.Options options = null;
  private AudioSource audioSource;
  private VideoSource videoSource;
  @Nullable
  private SurfaceTextureHelper surfaceTextureHelper;
  private String preferredVideoCodec;
  private boolean videoCapturerStopped;
  private boolean isError;
  private Timer statsTimer;
  //private VideoRenderer.Callbacks localRender;
  private VideoSink localRender;
  @Nullable private List<VideoSink> remoteSinks;
  private MediaConstraints pcConstraints;
  private int videoWidth;
  private int videoHeight;
  private int videoFps;
  private MediaConstraints audioConstraints;
  private ParcelFileDescriptor aecDumpFileDescriptor;
  private MediaConstraints sdpMediaConstraints;
  private PeerConnectionParameters peerConnectionParameters;
  private PeerConnectionEvents events;
  private MediaStream mediaStream;
  private VideoCapturer videoCapturer;
  // enableVideo is set to true if video should be rendered and sent.
  private boolean renderVideo;
  private VideoTrack localVideoTrack;
  private VideoTrack remoteVideoTrack;
  private RtpSender localVideoSender;
  // enableAudio is set to true if audio should be sent.
  private boolean enableAudio;
  private AudioTrack localAudioTrack;


  public static class PeerConnectionParameters {
    public final boolean tracing;
    public final int videoWidth;
    public final int videoHeight;
    public final int videoFps;
    public final String videoCodec;
    public final boolean videoCodecHwAcceleration;
    public final boolean videoFlexfecEnabled;
    public final int audioStartBitrate;
    public final String audioCodec;
    public final boolean noAudioProcessing;
    public final boolean useOpenSLES;
    public final boolean disableBuiltInAEC;
    public final boolean disableBuiltInAGC;
    public final boolean disableBuiltInNS;
    public final boolean disableWebRtcAGCAndHPF;

    public PeerConnectionParameters(boolean tracing,
        int videoWidth, int videoHeight, int videoFps, String videoCodec,
        boolean videoCodecHwAcceleration, int audioStartBitrate, String audioCodec,
        boolean noAudioProcessing, boolean useOpenSLES, boolean disableBuiltInAEC,
        boolean disableBuiltInAGC, boolean disableBuiltInNS, boolean videoFlexfecEnabled, boolean disableWebRtcAGCAndHPF) {
      this.tracing = tracing;
      this.videoWidth = videoWidth;
      this.videoHeight = videoHeight;
      this.videoFps = videoFps;
      this.videoCodec = videoCodec;
      this.videoCodecHwAcceleration = videoCodecHwAcceleration;
      this.audioStartBitrate = audioStartBitrate;
      this.audioCodec = audioCodec;
      this.noAudioProcessing = noAudioProcessing;
      this.useOpenSLES = useOpenSLES;
      this.disableBuiltInAEC = disableBuiltInAEC;
      this.disableBuiltInAGC = disableBuiltInAGC;
      this.disableBuiltInNS = disableBuiltInNS;
      this.videoFlexfecEnabled = videoFlexfecEnabled;
      this.disableWebRtcAGCAndHPF = disableWebRtcAGCAndHPF;
    }
  }

  /**
   * Peer connection events.
   */
  public interface PeerConnectionEvents {
    /**
     * Callback fired once local SDP is created and set.
     */
    void onLocalDescription(final SessionDescription sdp, final BigInteger handleId);


    void onRemoteDescription(final SessionDescription sdp, final BigInteger handleId);

    /**
     * Callback fired once local Ice candidate is generated.
     */
    void onIceCandidate(final IceCandidate candidate, final BigInteger handleId);

    /**
     * Callback fired once local ICE candidates are removed.
     */
    void onIceCandidatesRemoved(final IceCandidate[] candidates);

    /**
     * Callback fired once connection is established (IceConnectionState is
     * CONNECTED).
     */
    void onIceConnected();

    /**
     * Callback fired once connection is closed (IceConnectionState is
     * DISCONNECTED).
     */
    void onIceDisconnected();

    /**
     * Callback fired once peer connection is closed.
     */
    void onPeerConnectionClosed();

    /**
     * Callback fired once peer connection statistics is ready.
     */
    void onPeerConnectionStatsReady(final StatsReport[] reports);

    /**
     * Callback fired once peer connection error happened.
     */
    void onPeerConnectionError(final String description);

    void onRemoteRender(JanusConnection connection);
  }

  private PeerConnectionClient() {
    // Executor thread is started once in private ctor and is used for all
    // peer connection API calls to ensure new peer connection factory is
    // created on the same thread as previously destroyed factory.
    executor = Executors.newSingleThreadScheduledExecutor();
    peerConnectionMap = new ConcurrentHashMap<>();
  }

  public static PeerConnectionClient getInstance() {
    return instance;
  }

  public void setPeerConnectionFactoryOptions(PeerConnectionFactory.Options options) {
    this.options = options;
  }

  public void createPeerConnectionFactory(final Context context, EglBase rootEglBase,
      final PeerConnectionParameters peerConnectionParameters, final PeerConnectionEvents events) {
    this.peerConnectionParameters = peerConnectionParameters;
    this.events = events;
    this.rootEglBase = rootEglBase;
    // Reset variables to initial states.
    this.context = null;
    factory = null;
    videoCapturerStopped = false;
    isError = false;
    mediaStream = null;
    videoCapturer = null;
    renderVideo = true;
    localVideoTrack = null;
    remoteVideoTrack = null;
    localVideoSender = null;
    enableAudio = true;
    localAudioTrack = null;
    statsTimer = new Timer();

    Log.d(TAG, "Preferred video codec: " + getSdpVideoCodecName(peerConnectionParameters));

    final String fieldTrials = getFieldTrials(peerConnectionParameters);
    executor.execute(() -> {
      Log.d(TAG, "Initialize WebRTC. Field trials: " + fieldTrials);
      PeerConnectionFactory.initialize(
              PeerConnectionFactory.InitializationOptions.builder(context)
                      .setFieldTrials(fieldTrials)
                      .setEnableInternalTracer(true)
                      .createInitializationOptions());
    });

    executor.execute(new Runnable() {
      @Override
      public void run() {
        createPeerConnectionFactoryInternal(context);
      }
    });
  }

  private static String getSdpVideoCodecName(PeerConnectionParameters parameters) {
    switch (parameters.videoCodec) {
      case VIDEO_CODEC_VP8:
        return VIDEO_CODEC_VP8;
      case VIDEO_CODEC_VP9:
        return VIDEO_CODEC_VP9;
      case VIDEO_CODEC_H264_HIGH:
      case VIDEO_CODEC_H264_BASELINE:
        return VIDEO_CODEC_H264;
      default:
        return VIDEO_CODEC_VP8;
    }
  }
  private static String getFieldTrials(PeerConnectionParameters peerConnectionParameters) {
    String fieldTrials = "";
    if (peerConnectionParameters.videoFlexfecEnabled) {
      fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
      Log.d(TAG, "Enable FlexFEC field trial.");
    }
    fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
    if (peerConnectionParameters.disableWebRtcAGCAndHPF) {
      fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;
      Log.d(TAG, "Disable WebRTC AGC field trial.");
    }
    return fieldTrials;
  }

  /*public void createPeerConnection(final EglBase.Context renderEGLContext,
                                   final VideoRenderer.Callbacks localRender,
                                   final VideoCapturer videoCapturer, final BigInteger handleId) {
    if (peerConnectionParameters == null) {
      Log.e(TAG, "Creating peer connection without initializing factory.");
      return;
    }
    this.localRender = localRender;
    this.videoCapturer = videoCapturer;
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          createMediaConstraintsInternal();
          createPeerConnectionInternal(renderEGLContext, handleId);
        } catch (Exception e) {
          reportError("Failed to create peer connection: " + e.getMessage());
          throw e;
        }
      }
    });
  }*/

  public void createPeerConnection(final VideoSink localRender, final List<VideoSink> remoteSinks,
                                   final VideoCapturer videoCapturer, final BigInteger handleId) {
    if (peerConnectionParameters == null) {
      Log.e(TAG, "Creating peer connection without initializing factory.");
      return;
    }
    this.localRender = localRender;
    this.remoteSinks = remoteSinks;
    this.videoCapturer = videoCapturer;
    executor.execute(() -> {
      try {
        createMediaConstraintsInternal();
        createPeerConnectionInternal(handleId);
      } catch (Exception e) {
        reportError("Failed to create peer connection: " + e.getMessage());
        throw e;
      }
    });
  }

  public void close() {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        closeInternal();
      }
    });
  }

  private void createPeerConnectionFactoryInternal(Context context) {
    //PeerConnectionFactory.initializeInternalTracer();
    if (peerConnectionParameters.tracing) {
      PeerConnectionFactory.startInternalTracingCapture(
          Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
          + "webrtc-trace.txt");
    }
    Log.d(TAG,
        "Create peer connection factory. Use video: true");
    isError = false;

    // Initialize field trials.
    //PeerConnectionFactory.initializeFieldTrials("");

    // Check preferred video codec.
    preferredVideoCodec = VIDEO_CODEC_VP8;
    if (peerConnectionParameters.videoCodec != null) {
      if (peerConnectionParameters.videoCodec.equals(VIDEO_CODEC_VP9)) {
        preferredVideoCodec = VIDEO_CODEC_VP9;
      } else if (peerConnectionParameters.videoCodec.equals(VIDEO_CODEC_H264)) {
        preferredVideoCodec = VIDEO_CODEC_H264;
      }
    }
    Log.d(TAG, "Pereferred video codec: " + preferredVideoCodec);

    // Enable/disable OpenSL ES playback.
    if (!peerConnectionParameters.useOpenSLES) {
      Log.d(TAG, "Disable OpenSL ES audio even if device supports it");
      WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true /* enable */);
    } else {
      Log.d(TAG, "Allow OpenSL ES audio if device supports it");
      WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false);
    }

    if (peerConnectionParameters.disableBuiltInAEC) {
      Log.d(TAG, "Disable built-in AEC even if device supports it");
      WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
    } else {
      Log.d(TAG, "Enable built-in AEC if device supports it");
      WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(false);
    }

    if (peerConnectionParameters.disableBuiltInAGC) {
      Log.d(TAG, "Disable built-in AGC even if device supports it");
      WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true);
    } else {
      Log.d(TAG, "Enable built-in AGC if device supports it");
      WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(false);
    }

    if (peerConnectionParameters.disableBuiltInNS) {
      Log.d(TAG, "Disable built-in NS even if device supports it");
      WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);
    } else {
      Log.d(TAG, "Enable built-in NS if device supports it");
      WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(false);
    }

    // Create peer connection factory.
    //if (!PeerConnectionFactory.initializeAndroidGlobals(
   //         context, true, true, peerConnectionParameters.videoCodecHwAcceleration)) {
    //  events.onPeerConnectionError("Failed to initializeAndroidGlobals");
    //}
    if (options != null) {
      Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
    }
    this.context = context;
    //factory = new PeerConnectionFactory(options);

    final AudioDeviceModule adm = createJavaAudioDevice();

    final boolean enableH264HighProfile = false;
    final VideoEncoderFactory encoderFactory;
    final VideoDecoderFactory decoderFactory;

    if (peerConnectionParameters.videoCodecHwAcceleration) {
      encoderFactory = new DefaultVideoEncoderFactory(
              rootEglBase.getEglBaseContext(), true /* enableIntelVp8Encoder */, enableH264HighProfile);
      decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
    } else {
      encoderFactory = new SoftwareVideoEncoderFactory();
      decoderFactory = new SoftwareVideoDecoderFactory();
    }

    Log.d(TAG, "factory create call prepare finish ! factory is");
    factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory();
    Log.d(TAG, "factory create call finish ! factory is");
    adm.release();
    Log.d(TAG, "Peer connection factory created.");
  }

  AudioDeviceModule createJavaAudioDevice() {
    // Enable/disable OpenSL ES playback.
    if (!peerConnectionParameters.useOpenSLES) {
      Log.w(TAG, "External OpenSLES ADM not implemented yet.");
      // TODO(magjed): Add support for external OpenSLES ADM.
    }

    // Set audio record error callbacks.
    AudioRecordErrorCallback audioRecordErrorCallback = new AudioRecordErrorCallback() {
      @Override
      public void onWebRtcAudioRecordInitError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordInitError: " + errorMessage);
        reportError(errorMessage);
      }

      @Override
      public void onWebRtcAudioRecordStartError(
              JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
        reportError(errorMessage);
      }

      @Override
      public void onWebRtcAudioRecordError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioRecordError: " + errorMessage);
        reportError(errorMessage);
      }
    };

    AudioTrackErrorCallback audioTrackErrorCallback = new AudioTrackErrorCallback() {
      @Override
      public void onWebRtcAudioTrackInitError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
        reportError(errorMessage);
      }

      @Override
      public void onWebRtcAudioTrackStartError(
              JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
        reportError(errorMessage);
      }

      @Override
      public void onWebRtcAudioTrackError(String errorMessage) {
        Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
        reportError(errorMessage);
      }
    };

    return JavaAudioDeviceModule.builder(context)
            .setSamplesReadyCallback(null)
            .setUseHardwareAcousticEchoCanceler(!peerConnectionParameters.disableBuiltInAEC)
            .setUseHardwareNoiseSuppressor(!peerConnectionParameters.disableBuiltInNS)
            .setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .createAudioDeviceModule();
  }

  private void createMediaConstraintsInternal() {
    // Create peer connection constraints.
    pcConstraints = new MediaConstraints();
    pcConstraints.optional.add(
            new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));

    // Create video constraints if video call is enabled.
    videoWidth = peerConnectionParameters.videoWidth;
    videoHeight = peerConnectionParameters.videoHeight;
    videoFps = peerConnectionParameters.videoFps;

    // If video resolution is not specified, default to HD.
    if (videoWidth == 0 || videoHeight == 0) {
      videoWidth = HD_VIDEO_WIDTH;
      videoHeight = HD_VIDEO_HEIGHT;
    }

    // If fps is not specified, default to 30.
    if (videoFps == 0) {
      videoFps = 30;
    }
    Logging.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps);

    // Create audio constraints.
    audioConstraints = new MediaConstraints();
    // added for audio performance measurements
    if (peerConnectionParameters.noAudioProcessing) {
      Log.d(TAG, "Disabling audio processing");
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
    }
    // Create SDP constraints.
    sdpMediaConstraints = new MediaConstraints();
    sdpMediaConstraints.mandatory.add(
            new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    sdpMediaConstraints.mandatory.add(
            new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
  }

  private PeerConnection createPeerConnection(BigInteger handleId, boolean type) {
    Log.d(TAG, "Create peer connection.");
    PeerConnection.IceServer iceServer = new PeerConnection.IceServer("turn:192.168.100.169:3478", "dds", "123456");
    List<PeerConnection.IceServer> iceServers = new ArrayList<>();
    iceServers.add(iceServer);
    PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
    //rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
    rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;

    PCObserver pcObserver = new PCObserver();
    SDPObserver sdpObserver = new SDPObserver();
    PeerConnection peerConnection = factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);

    JanusConnection janusConnection = new JanusConnection();
    janusConnection.handleId = handleId;
    janusConnection.sdpObserver = sdpObserver;
    janusConnection.peerConnection = peerConnection;
    janusConnection.type = type;

    peerConnectionMap.put(handleId, janusConnection);
    pcObserver.setConnection(janusConnection);
    sdpObserver.setConnection(janusConnection);
    Log.d(TAG, "Peer connection created.");
    return peerConnection;
  }


  private void createPeerConnectionInternal(BigInteger handleId) {
    if (factory == null || isError) {
      Log.e(TAG, "Peerconnection factory is not created");
      return;
    }

    Log.d(TAG, "PCConstraints: " + pcConstraints.toString());

    //factory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);

    PeerConnection peerConnection = createPeerConnection(handleId, true);

    mediaStream = factory.createLocalMediaStream("ARDAMS");
    mediaStream.addTrack(createVideoTrack(videoCapturer));

    mediaStream.addTrack(createAudioTrack());
    peerConnection.addStream(mediaStream);
    findVideoSender(handleId);
  }

  private void closeInternal() {
    Log.d(TAG, "Closing peer connection.");
    statsTimer.cancel();

    if (peerConnectionMap != null) {
      for (Map.Entry<BigInteger, JanusConnection> entry: peerConnectionMap.entrySet()) {
        if (entry.getValue().peerConnection != null) {
          entry.getValue().peerConnection.dispose();
        }
      }
    }
    Log.d(TAG, "Closing audio source.");
    if (audioSource != null) {
      audioSource.dispose();
      audioSource = null;
    }
    Log.d(TAG, "Stopping capture.");
    if (videoCapturer != null) {
      try {
        videoCapturer.stopCapture();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      videoCapturerStopped = true;
      videoCapturer.dispose();
      videoCapturer = null;
    }
    Log.d(TAG, "Closing video source.");
    if (videoSource != null) {
      videoSource.dispose();
      videoSource = null;
    }
    Log.d(TAG, "Closing peer connection factory.");
    if (factory != null) {
      factory.dispose();
      factory = null;
    }
    options = null;
    Log.d(TAG, "Closing peer connection done.");
    events.onPeerConnectionClosed();
    PeerConnectionFactory.stopInternalTracingCapture();
    PeerConnectionFactory.shutdownInternalTracer();
  }

  public boolean isHDVideo() {
    return videoWidth * videoHeight >= 1280 * 720;
  }

  private void getStats(final BigInteger handleId) {
    PeerConnection peerConnection = peerConnectionMap.get(handleId).peerConnection;
    boolean success = peerConnection.getStats(new StatsObserver() {
      @Override
      public void onComplete(final StatsReport[] reports) {
        events.onPeerConnectionStatsReady(reports);
      }
    }, null);
    if (!success) {
      Log.e(TAG, "getStats() returns false!");
    }
  }

  public void enableStatsEvents(boolean enable, int periodMs, final BigInteger handleId) {
    if (enable) {
      try {
        statsTimer.schedule(new TimerTask() {
          @Override
          public void run() {
            executor.execute(new Runnable() {
              @Override
              public void run() {
                getStats(handleId);
              }
            });
          }
        }, 0, periodMs);
      } catch (Exception e) {
        Log.e(TAG, "Can not schedule statistics timer", e);
      }
    } else {
      statsTimer.cancel();
    }
  }

  public void setAudioEnabled(final boolean enable) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        enableAudio = enable;
        if (localAudioTrack != null) {
          localAudioTrack.setEnabled(enableAudio);
        }
      }
    });
  }

  public void setVideoEnabled(final boolean enable) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        renderVideo = enable;
        if (localVideoTrack != null) {
          localVideoTrack.setEnabled(renderVideo);
        }
        if (remoteVideoTrack != null) {
          remoteVideoTrack.setEnabled(renderVideo);
        }
      }
    });
  }

  public void createOffer(final BigInteger handleId) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        JanusConnection connection = peerConnectionMap.get(handleId);
        PeerConnection peerConnection = connection.peerConnection;
        if (peerConnection != null && !isError) {
          Log.d(TAG, "PC Create OFFER");
          peerConnection.createOffer(connection.sdpObserver, sdpMediaConstraints);
        }
      }
    });
  }

  public void setRemoteDescription(final BigInteger handleId, final SessionDescription sdp) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        PeerConnection peerConnection = peerConnectionMap.get(handleId).peerConnection;
        SDPObserver sdpObserver = peerConnectionMap.get(handleId).sdpObserver;
        if (peerConnection == null || isError) {
          return;
        }
        peerConnection.setRemoteDescription(sdpObserver, sdp);
      }
    });
  }

  public void subscriberHandleRemoteJsep(final BigInteger handleId, final SessionDescription sdp) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          PeerConnection peerConnection = createPeerConnection(handleId, false);
          SDPObserver sdpObserver = peerConnectionMap.get(handleId).sdpObserver;
          if (peerConnection == null || isError) {
            return;
          }
          JanusConnection connection = peerConnectionMap.get(handleId);
          peerConnection.setRemoteDescription(sdpObserver, sdp);
          Log.d(TAG, "PC create ANSWER");
          peerConnection.createAnswer(connection.sdpObserver, sdpMediaConstraints);
        }
      });
  }

  public void stopVideoSource() {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        if (videoCapturer != null && !videoCapturerStopped) {
          Log.d(TAG, "Stop video source.");
          try {
            videoCapturer.stopCapture();
          } catch (InterruptedException e) {
          }
          videoCapturerStopped = true;
        }
      }
    });
  }

  public void startVideoSource() {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        if (videoCapturer != null && videoCapturerStopped) {
          Log.d(TAG, "Restart video source.");
          videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
          videoCapturerStopped = false;
        }
      }
    });
  }

  private void reportError(final String errorMessage) {
    Log.e(TAG, "Peerconnection error: " + errorMessage);
    executor.execute(new Runnable() {
      @Override
      public void run() {
        if (!isError) {
          events.onPeerConnectionError(errorMessage);
          isError = true;
        }
      }
    });
  }

  private AudioTrack createAudioTrack() {
    audioSource = factory.createAudioSource(audioConstraints);
    localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
    localAudioTrack.setEnabled(enableAudio);
    return localAudioTrack;
  }

  @Nullable
  private VideoTrack createVideoTrack(VideoCapturer capturer) {
    surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
    videoSource = factory.createVideoSource(capturer.isScreencast());
    capturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
    capturer.startCapture(videoWidth, videoHeight, videoFps);

    localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
    localVideoTrack.setEnabled(renderVideo);
    localVideoTrack.addSink(localRender);
    return localVideoTrack;
  }

  private void findVideoSender(final BigInteger handleId) {
    PeerConnection peerConnection = peerConnectionMap.get(handleId).peerConnection;
    for (RtpSender sender : peerConnection.getSenders()) {
      if (sender.track() != null) {
        String trackType = sender.track().kind();
        if (trackType.equals(VIDEO_TRACK_TYPE)) {
          Log.d(TAG, "Found video sender.");
          localVideoSender = sender;
        }
      }
    }
  }

  private void switchCameraInternal() {
    if (videoCapturer instanceof CameraVideoCapturer) {
      Log.d(TAG, "Switch camera");
      CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
      cameraVideoCapturer.switchCamera(null);
    } else {
      Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
    }
  }

  public void switchCamera() {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        switchCameraInternal();
      }
    });
  }

  public void changeCaptureFormat(final int width, final int height, final int framerate) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        changeCaptureFormatInternal(width, height, framerate);
      }
    });
  }

  private void changeCaptureFormatInternal(int width, int height, int framerate) {
    if (isError || videoCapturer == null) {
      Log.e(TAG,
          "Failed to change capture format. Video: true. Error : " + isError);
      return;
    }
    Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
    videoSource.adaptOutputFormat(width, height, framerate);
  }

  // Implementation detail: observe ICE & stream changes and react accordingly.
  private class PCObserver implements PeerConnection.Observer {
    private JanusConnection connection;
    private PeerConnection peerConnection;
    public void setConnection(JanusConnection connection) {
      this.connection = connection;
      this.peerConnection = connection.peerConnection;
    }
    @Override
    public void onIceCandidate(final IceCandidate candidate) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          events.onIceCandidate(candidate, connection.handleId);
        }
      });
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          events.onIceCandidatesRemoved(candidates);
        }
      });
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
      Log.d(TAG, "SignalingState: " + newState);
    }

    @Override
    public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          Log.d(TAG, "IceConnectionState: " + newState);
          if (newState == IceConnectionState.CONNECTED) {
            events.onIceConnected();
          } else if (newState == IceConnectionState.DISCONNECTED) {
            events.onIceDisconnected();
          } else if (newState == IceConnectionState.FAILED) {
            reportError("ICE connection failed.");
          }
        }
      });
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
      Log.d(TAG, "IceGatheringState: " + newState);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
      Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
    }

    @Override
    public void onAddStream(final MediaStream stream) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          if (peerConnection == null || isError) {
            return;
          }
          Log.d(TAG, "=========== onAddStream ==========");
          if (stream.videoTracks.size() == 1) {
            remoteVideoTrack = stream.videoTracks.get(0);
            remoteVideoTrack.setEnabled(true);
            connection.videoTrack = remoteVideoTrack;
            events.onRemoteRender(connection);
          }
        }
      });
    }

    @Override
    public void onRemoveStream(final MediaStream stream) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          remoteVideoTrack = null;
        }
      });
    }

    @Override
    public void onDataChannel(final DataChannel dc) {
      Log.d(TAG, "New Data channel " + dc.label());

    }

    @Override
    public void onRenegotiationNeeded() {
      // No need to do anything; AppRTC follows a pre-agreed-upon
      // signaling/negotiation protocol.
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

    }
  }

  class SDPObserver implements SdpObserver {
    private PeerConnection peerConnection;
    private SDPObserver sdpObserver;
    private BigInteger handleId;
    private SessionDescription localSdp;
    private boolean type;
    public void setConnection(JanusConnection connection) {
      this.peerConnection = connection.peerConnection;
      this.sdpObserver = connection.sdpObserver;
      this.handleId = connection.handleId;
      this.type = connection.type;
    }
    @Override
    public void onCreateSuccess(final SessionDescription origSdp) {
      Log.e(TAG, "SDP on create success");
      final SessionDescription sdp = new SessionDescription(origSdp.type, origSdp.description);
      localSdp = sdp;
      executor.execute(new Runnable() {
        @Override
        public void run() {
          if (peerConnection != null && !isError) {
            Log.d(TAG, "Set local SDP from " + sdp.type);
            peerConnection.setLocalDescription(sdpObserver, sdp);
          }
        }
      });
    }

    @Override
    public void onSetSuccess() {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          if (peerConnection == null || isError) {
            return;
          }
          if (type) {
            if (peerConnection.getRemoteDescription() == null) {
              Log.d(TAG, "Local SDP set succesfully");
              events.onLocalDescription(localSdp, handleId);
            } else {
              Log.d(TAG, "Remote SDP set succesfully");
            }
          } else {
            if (peerConnection.getLocalDescription() != null) {
              Log.d(TAG, "answer Local SDP set succesfully");
              events.onRemoteDescription(localSdp, handleId);
            } else {
              Log.d(TAG, "answer Remote SDP set succesfully");
            }
          }
        }
      });
    }

    @Override
    public void onCreateFailure(final String error) {
      reportError("createSDP error: " + error);
    }

    @Override
    public void onSetFailure(final String error) {
      reportError("setSDP error: " + error);
    }
  }
}
