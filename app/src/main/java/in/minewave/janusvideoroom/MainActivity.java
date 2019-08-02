package in.minewave.janusvideoroom;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
//import org.webrtc.VideoRenderer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import in.minewave.janusvideoroom.PeerConnectionClient.PeerConnectionParameters;
import in.minewave.janusvideoroom.PeerConnectionClient.PeerConnectionEvents;

public class MainActivity extends AppCompatActivity implements JanusRTCInterface, PeerConnectionEvents {
    private static final String TAG = "MainActivity";

    private static class ProxyVideoSink implements VideoSink {
        private VideoSink target;

        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (target == null) {
                Logging.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }

            target.onFrame(frame);
        }

        synchronized public void setTarget(VideoSink target) {
            this.target = target;
        }
    }

    private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
    private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
    private final List<VideoSink> remoteSinks = new ArrayList<>();

    private PeerConnectionClient peerConnectionClient;
    private PeerConnectionParameters peerConnectionParameters;

    private SurfaceViewRenderer localRender;
    private SurfaceViewRenderer remoteRender;
    private VideoCapturer videoCapturer;
    private EglBase rootEglBase;
    private WebSocketChannel mWebSocketChannel;
    LinearLayout rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rootView = (LinearLayout) findViewById(R.id.activity_main);
        mWebSocketChannel = new WebSocketChannel();
        mWebSocketChannel.initConnection("ws://192.168.100.169:8188");
        //mWebSocketChannel.initConnection("ws://www.zeng060.top:8188");
        mWebSocketChannel.setDelegate(this);

        createLocalRender();
        remoteRender = (SurfaceViewRenderer) findViewById(R.id.remote_video_view);
        remoteRender.init(rootEglBase.getEglBaseContext(), null);
        localProxyVideoSink.setTarget(localRender);
        remoteProxyRenderer.setTarget(remoteRender);
        remoteSinks.add(remoteProxyRenderer);
        peerConnectionParameters  = new PeerConnectionParameters(false, 352, 288, 30, "VP8", false,
                0, "opus", false, false, false, false, false,
                false, false);
        peerConnectionClient = PeerConnectionClient.getInstance();
        peerConnectionClient.createPeerConnectionFactory(this, rootEglBase, peerConnectionParameters, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        peerConnectionClient.startVideoSource();
    }

    private void createLocalRender() {
        localRender = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
        rootEglBase = EglBase.create();
        localRender.init(rootEglBase.getEglBaseContext(), null);
        localRender.setEnableHardwareScaler(true);
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this);
    }

    private boolean captureToTexture() {
        return true;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer = null;
        if (useCamera2()) {
            Log.d(TAG, "Creating capturer using camera2 API.");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            Log.d(TAG, "Creating capturer using camera1 API.");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
        }
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to open camera");
            return null;
        }
        return videoCapturer;
    }


    private void offerPeerConnection(BigInteger handleId) {
        videoCapturer = createVideoCapturer();
        peerConnectionClient.createPeerConnection(localProxyVideoSink, remoteSinks, videoCapturer, handleId);
        peerConnectionClient.createOffer(handleId);
    }

    // interface JanusRTCInterface
    @Override
    public void onPublisherJoined(final BigInteger handleId) {
        offerPeerConnection(handleId);
    }

    @Override
    public void onPublisherRemoteJsep(BigInteger handleId, JSONObject jsep) {
        SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(jsep.optString("type"));
        String sdp = jsep.optString("sdp");
        SessionDescription sessionDescription = new SessionDescription(type, sdp);
        peerConnectionClient.setRemoteDescription(handleId, sessionDescription);
    }

    @Override
    public void subscriberHandleRemoteJsep(BigInteger handleId, JSONObject jsep) {
        SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(jsep.optString("type"));
        String sdp = jsep.optString("sdp");
        SessionDescription sessionDescription = new SessionDescription(type, sdp);
        peerConnectionClient.subscriberHandleRemoteJsep(handleId, sessionDescription);
    }

    @Override
    public void onLeaving(BigInteger handleId) {

    }

    // interface PeerConnectionClient.PeerConnectionEvents
    @Override
    public void onLocalDescription(SessionDescription sdp, BigInteger handleId) {
        Log.e(TAG, sdp.type.toString());
        mWebSocketChannel.publisherCreateOffer(handleId, sdp);
    }

    @Override
    public void onRemoteDescription(SessionDescription sdp, BigInteger handleId) {
        Log.e(TAG, sdp.type.toString());
        mWebSocketChannel.subscriberCreateAnswer(handleId, sdp);
    }

    @Override
    public void onIceCandidate(IceCandidate candidate, BigInteger handleId) {
        Log.e(TAG, "=========onIceCandidate========");
        if (candidate != null) {
            mWebSocketChannel.trickleCandidate(handleId, candidate);
        } else {
            mWebSocketChannel.trickleCandidateComplete(handleId);
        }
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {

    }

    @Override
    public void onIceConnected() {

    }

    @Override
    public void onIceDisconnected() {

    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {

    }

    @Override
    public void onPeerConnectionError(String description) {

    }

    @Override
    public void onRemoteRender(final JanusConnection connection) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //remoteRender = new SurfaceViewRenderer(MainActivity.this);
                //remoteRender.init(rootEglBase.getEglBaseContext(), null);
                LinearLayout.LayoutParams params  = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rootView.addView(remoteRender, params);
//                connection.videoTrack.addRenderer(new VideoRenderer(remoteRender));
            }
        });
    }
}
