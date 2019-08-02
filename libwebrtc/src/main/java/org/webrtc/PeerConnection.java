package org.webrtc;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class PeerConnection {
   private final List<MediaStream> localStreams;
   private final long nativePeerConnection;
   private final long nativeObserver;
   private List<RtpSender> senders;
   private List<RtpReceiver> receivers;

   PeerConnection(long nativePeerConnection, long nativeObserver) {
      this.nativePeerConnection = nativePeerConnection;
      this.nativeObserver = nativeObserver;
      this.localStreams = new LinkedList();
      this.senders = new LinkedList();
      this.receivers = new LinkedList();
   }

   public native SessionDescription getLocalDescription();

   public native SessionDescription getRemoteDescription();

   public native DataChannel createDataChannel(String var1, DataChannel.Init var2);

   public native void createOffer(SdpObserver var1, MediaConstraints var2);

   public native void createAnswer(SdpObserver var1, MediaConstraints var2);

   public native void setLocalDescription(SdpObserver var1, SessionDescription var2);

   public native void setRemoteDescription(SdpObserver var1, SessionDescription var2);

   public boolean setConfiguration(PeerConnection.RTCConfiguration config) {
      return this.nativeSetConfiguration(config, this.nativeObserver);
   }

   public boolean addIceCandidate(IceCandidate candidate) {
      return this.nativeAddIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
   }

   public boolean removeIceCandidates(IceCandidate[] candidates) {
      return this.nativeRemoveIceCandidates(candidates);
   }

   public boolean addStream(MediaStream stream) {
      boolean ret = this.nativeAddLocalStream(stream.nativeStream);
      if (!ret) {
         return false;
      } else {
         this.localStreams.add(stream);
         return true;
      }
   }

   public void removeStream(MediaStream stream) {
      this.nativeRemoveLocalStream(stream.nativeStream);
      this.localStreams.remove(stream);
   }

   public RtpSender createSender(String kind, String stream_id) {
      RtpSender new_sender = this.nativeCreateSender(kind, stream_id);
      if (new_sender != null) {
         this.senders.add(new_sender);
      }

      return new_sender;
   }

   public List<RtpSender> getSenders() {
      Iterator var1 = this.senders.iterator();

      while(var1.hasNext()) {
         RtpSender sender = (RtpSender)var1.next();
         sender.dispose();
      }

      this.senders = this.nativeGetSenders();
      return Collections.unmodifiableList(this.senders);
   }

   public List<RtpReceiver> getReceivers() {
      Iterator var1 = this.receivers.iterator();

      while(var1.hasNext()) {
         RtpReceiver receiver = (RtpReceiver)var1.next();
         receiver.dispose();
      }

      this.receivers = this.nativeGetReceivers();
      return Collections.unmodifiableList(this.receivers);
   }

   /** @deprecated */
   @Deprecated
   public boolean getStats(StatsObserver observer, MediaStreamTrack track) {
      return this.nativeOldGetStats(observer, track == null ? 0L : track.nativeTrack);
   }

   public void getStats(RTCStatsCollectorCallback callback) {
      this.nativeNewGetStats(callback);
   }

   public boolean startRtcEventLog(int file_descriptor, int max_size_bytes) {
      return this.nativeStartRtcEventLog(file_descriptor, max_size_bytes);
   }

   public void stopRtcEventLog() {
      this.nativeStopRtcEventLog();
   }

   public native PeerConnection.SignalingState signalingState();

   public native PeerConnection.IceConnectionState iceConnectionState();

   public native PeerConnection.IceGatheringState iceGatheringState();

   public native void close();

   public void dispose() {
      this.close();
      Iterator var1 = this.localStreams.iterator();

      while(var1.hasNext()) {
         MediaStream stream = (MediaStream)var1.next();
         this.nativeRemoveLocalStream(stream.nativeStream);
         stream.dispose();
      }

      this.localStreams.clear();
      var1 = this.senders.iterator();

      while(var1.hasNext()) {
         RtpSender sender = (RtpSender)var1.next();
         sender.dispose();
      }

      this.senders.clear();
      var1 = this.receivers.iterator();

      while(var1.hasNext()) {
         RtpReceiver receiver = (RtpReceiver)var1.next();
         receiver.dispose();
      }

      this.receivers.clear();
      freePeerConnection(this.nativePeerConnection);
      freeObserver(this.nativeObserver);
   }

   private static native void freePeerConnection(long var0);

   private static native void freeObserver(long var0);

   public native boolean nativeSetConfiguration(PeerConnection.RTCConfiguration var1, long var2);

   private native boolean nativeAddIceCandidate(String var1, int var2, String var3);

   private native boolean nativeRemoveIceCandidates(IceCandidate[] var1);

   private native boolean nativeAddLocalStream(long var1);

   private native void nativeRemoveLocalStream(long var1);

   private native boolean nativeOldGetStats(StatsObserver var1, long var2);

   private native void nativeNewGetStats(RTCStatsCollectorCallback var1);

   private native RtpSender nativeCreateSender(String var1, String var2);

   private native List<RtpSender> nativeGetSenders();

   private native List<RtpReceiver> nativeGetReceivers();

   private native boolean nativeStartRtcEventLog(int var1, int var2);

   private native void nativeStopRtcEventLog();

   static {
      System.loadLibrary("jingle_peerconnection_so");
   }

   public static class RTCConfiguration {
      public PeerConnection.IceTransportsType iceTransportsType;
      public List<PeerConnection.IceServer> iceServers;
      public PeerConnection.BundlePolicy bundlePolicy;
      public PeerConnection.RtcpMuxPolicy rtcpMuxPolicy;
      public PeerConnection.TcpCandidatePolicy tcpCandidatePolicy;
      public PeerConnection.CandidateNetworkPolicy candidateNetworkPolicy;
      public int audioJitterBufferMaxPackets;
      public boolean audioJitterBufferFastAccelerate;
      public int iceConnectionReceivingTimeout;
      public int iceBackupCandidatePairPingInterval;
      public PeerConnection.KeyType keyType;
      public PeerConnection.ContinualGatheringPolicy continualGatheringPolicy;
      public int iceCandidatePoolSize;
      public boolean pruneTurnPorts;
      public boolean presumeWritableWhenFullyRelayed;
      public Integer iceCheckMinInterval;
      public boolean disableIPv6OnWifi;

      public RTCConfiguration(List<PeerConnection.IceServer> iceServers) {
         this.iceTransportsType = PeerConnection.IceTransportsType.ALL;
         this.bundlePolicy = PeerConnection.BundlePolicy.BALANCED;
         this.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
         this.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
         PeerConnection.CandidateNetworkPolicy var10001 = this.candidateNetworkPolicy;
         this.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL;
         this.iceServers = iceServers;
         this.audioJitterBufferMaxPackets = 50;
         this.audioJitterBufferFastAccelerate = false;
         this.iceConnectionReceivingTimeout = -1;
         this.iceBackupCandidatePairPingInterval = -1;
         this.keyType = PeerConnection.KeyType.ECDSA;
         this.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
         this.iceCandidatePoolSize = 0;
         this.pruneTurnPorts = false;
         this.presumeWritableWhenFullyRelayed = false;
         this.iceCheckMinInterval = null;
         this.disableIPv6OnWifi = false;
      }
   }

   public static enum ContinualGatheringPolicy {
      GATHER_ONCE,
      GATHER_CONTINUALLY;
   }

   public static enum KeyType {
      RSA,
      ECDSA;
   }

   public static enum CandidateNetworkPolicy {
      ALL,
      LOW_COST;
   }

   public static enum TcpCandidatePolicy {
      ENABLED,
      DISABLED;
   }

   public static enum RtcpMuxPolicy {
      NEGOTIATE,
      REQUIRE;
   }

   public static enum BundlePolicy {
      BALANCED,
      MAXBUNDLE,
      MAXCOMPAT;
   }

   public static enum IceTransportsType {
      NONE,
      RELAY,
      NOHOST,
      ALL;
   }

   public static class IceServer {
      public final String uri;
      public final String username;
      public final String password;
      public final PeerConnection.TlsCertPolicy tlsCertPolicy;

      public IceServer(String uri) {
         this(uri, "", "");
      }

      public IceServer(String uri, String username, String password) {
         this(uri, username, password, PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_SECURE);
      }

      public IceServer(String uri, String username, String password, PeerConnection.TlsCertPolicy tlsCertPolicy) {
         this.uri = uri;
         this.username = username;
         this.password = password;
         this.tlsCertPolicy = tlsCertPolicy;
      }

      public String toString() {
         return this.uri + " [" + this.username + ":" + this.password + "] [" + this.tlsCertPolicy + "]";
      }
   }

   public interface Observer {
      void onSignalingChange(PeerConnection.SignalingState var1);

      void onIceConnectionChange(PeerConnection.IceConnectionState var1);

      void onIceConnectionReceivingChange(boolean var1);

      void onIceGatheringChange(PeerConnection.IceGatheringState var1);

      void onIceCandidate(IceCandidate var1);

      void onIceCandidatesRemoved(IceCandidate[] var1);

      void onAddStream(MediaStream var1);

      void onRemoveStream(MediaStream var1);

      void onDataChannel(DataChannel var1);

      void onRenegotiationNeeded();

      void onAddTrack(RtpReceiver var1, MediaStream[] var2);
   }

   public static enum SignalingState {
      STABLE,
      HAVE_LOCAL_OFFER,
      HAVE_LOCAL_PRANSWER,
      HAVE_REMOTE_OFFER,
      HAVE_REMOTE_PRANSWER,
      CLOSED;
   }

   public static enum TlsCertPolicy {
      TLS_CERT_POLICY_SECURE,
      TLS_CERT_POLICY_INSECURE_NO_CHECK;
   }

   public static enum IceConnectionState {
      NEW,
      CHECKING,
      CONNECTED,
      COMPLETED,
      FAILED,
      DISCONNECTED,
      CLOSED;
   }

   public static enum IceGatheringState {
      NEW,
      GATHERING,
      COMPLETE;
   }
}
