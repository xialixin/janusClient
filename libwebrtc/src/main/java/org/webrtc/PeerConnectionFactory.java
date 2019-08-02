package org.webrtc;

import android.content.Context;
import java.util.List;

public class PeerConnectionFactory {
   private static volatile boolean nativeLibLoaded;
   private static final String TAG = "PeerConnectionFactory";
   private final long nativeFactory;
   private static Context applicationContext;
   private static Thread networkThread;
   private static Thread workerThread;
   private static Thread signalingThread;
   private EglBase localEglbase;
   private EglBase remoteEglbase;

   public static native void nativeInitializeAndroidGlobals(Context var0, boolean var1);

   public static void initializeAndroidGlobals(Context context, boolean videoHwAcceleration) {
      ContextUtils.initialize(context);
      nativeInitializeAndroidGlobals(context, videoHwAcceleration);
   }

   /** @deprecated */
   @Deprecated
   public static boolean initializeAndroidGlobals(Object context, boolean initializeAudio, boolean initializeVideo, boolean videoHwAcceleration) {
      initializeAndroidGlobals((Context)context, videoHwAcceleration);
      return true;
   }

   public static native void initializeFieldTrials(String var0);

   public static String fieldTrialsFindFullName(String name) {
      return nativeLibLoaded ? nativeFieldTrialsFindFullName(name) : "";
   }

   private static native String nativeFieldTrialsFindFullName(String var0);

   public static native void initializeInternalTracer();

   public static native void shutdownInternalTracer();

   public static native boolean startInternalTracingCapture(String var0);

   public static native void stopInternalTracingCapture();

   /** @deprecated */
   @Deprecated
   public PeerConnectionFactory() {
      this((PeerConnectionFactory.Options)null);
   }

   public PeerConnectionFactory(PeerConnectionFactory.Options options) {
      this.nativeFactory = nativeCreatePeerConnectionFactory(options);
      if (this.nativeFactory == 0L) {
         throw new RuntimeException("Failed to initialize PeerConnectionFactory!");
      }
   }

   public PeerConnection createPeerConnection(PeerConnection.RTCConfiguration rtcConfig, MediaConstraints constraints, PeerConnection.Observer observer) {
      long nativeObserver = nativeCreateObserver(observer);
      if (nativeObserver == 0L) {
         return null;
      } else {
         long nativePeerConnection = nativeCreatePeerConnection(this.nativeFactory, rtcConfig, constraints, nativeObserver);
         return nativePeerConnection == 0L ? null : new PeerConnection(nativePeerConnection, nativeObserver);
      }
   }

   public PeerConnection createPeerConnection(List<PeerConnection.IceServer> iceServers, MediaConstraints constraints, PeerConnection.Observer observer) {
      PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
      return this.createPeerConnection(rtcConfig, constraints, observer);
   }

   public MediaStream createLocalMediaStream(String label) {
      return new MediaStream(nativeCreateLocalMediaStream(this.nativeFactory, label));
   }

   public VideoSource createVideoSource(VideoCapturer capturer) {
      EglBase.Context eglContext = this.localEglbase == null ? null : this.localEglbase.getEglBaseContext();
      long nativeAndroidVideoTrackSource = nativeCreateVideoSource(this.nativeFactory, eglContext, capturer.isScreencast());
      VideoCapturer.CapturerObserver capturerObserver = new AndroidVideoTrackSourceObserver(nativeAndroidVideoTrackSource);
      nativeInitializeVideoCapturer(this.nativeFactory, capturer, nativeAndroidVideoTrackSource, capturerObserver);
      return new VideoSource(nativeAndroidVideoTrackSource);
   }

   public VideoTrack createVideoTrack(String id, VideoSource source) {
      return new VideoTrack(nativeCreateVideoTrack(this.nativeFactory, id, source.nativeSource));
   }

   public AudioSource createAudioSource(MediaConstraints constraints) {
      return new AudioSource(nativeCreateAudioSource(this.nativeFactory, constraints));
   }

   public AudioTrack createAudioTrack(String id, AudioSource source) {
      return new AudioTrack(nativeCreateAudioTrack(this.nativeFactory, id, source.nativeSource));
   }

   public boolean startAecDump(int file_descriptor, int filesize_limit_bytes) {
      return nativeStartAecDump(this.nativeFactory, file_descriptor, filesize_limit_bytes);
   }

   public void stopAecDump() {
      nativeStopAecDump(this.nativeFactory);
   }

   /** @deprecated */
   @Deprecated
   public void setOptions(PeerConnectionFactory.Options options) {
      this.nativeSetOptions(this.nativeFactory, options);
   }

   public void setVideoHwAccelerationOptions(EglBase.Context localEglContext, EglBase.Context remoteEglContext) {
      if (this.localEglbase != null) {
         Logging.w("PeerConnectionFactory", "Egl context already set.");
         this.localEglbase.release();
      }

      if (this.remoteEglbase != null) {
         Logging.w("PeerConnectionFactory", "Egl context already set.");
         this.remoteEglbase.release();
      }

      this.localEglbase = EglBase.create(localEglContext);
      this.remoteEglbase = EglBase.create(remoteEglContext);
      nativeSetVideoHwAccelerationOptions(this.nativeFactory, this.localEglbase.getEglBaseContext(), this.remoteEglbase.getEglBaseContext());
   }

   public void dispose() {
      nativeFreeFactory(this.nativeFactory);
      networkThread = null;
      workerThread = null;
      signalingThread = null;
      if (this.localEglbase != null) {
         this.localEglbase.release();
      }

      if (this.remoteEglbase != null) {
         this.remoteEglbase.release();
      }

   }

   public void threadsCallbacks() {
      nativeThreadsCallbacks(this.nativeFactory);
   }

   private static void printStackTrace(Thread thread, String threadName) {
      if (thread != null) {
         StackTraceElement[] stackTraces = thread.getStackTrace();
         if (stackTraces.length > 0) {
            Logging.d("PeerConnectionFactory", threadName + " stacks trace:");
            StackTraceElement[] var3 = stackTraces;
            int var4 = stackTraces.length;

            for(int var5 = 0; var5 < var4; ++var5) {
               StackTraceElement stackTrace = var3[var5];
               Logging.d("PeerConnectionFactory", stackTrace.toString());
            }
         }
      }

   }

   public static void printStackTraces() {
      printStackTrace(networkThread, "Network thread");
      printStackTrace(workerThread, "Worker thread");
      printStackTrace(signalingThread, "Signaling thread");
   }

   private static void onNetworkThreadReady() {
      networkThread = Thread.currentThread();
      Logging.d("PeerConnectionFactory", "onNetworkThreadReady");
   }

   private static void onWorkerThreadReady() {
      workerThread = Thread.currentThread();
      Logging.d("PeerConnectionFactory", "onWorkerThreadReady");
   }

   private static void onSignalingThreadReady() {
      signalingThread = Thread.currentThread();
      Logging.d("PeerConnectionFactory", "onSignalingThreadReady");
   }

   private static native long nativeCreatePeerConnectionFactory(PeerConnectionFactory.Options var0);

   private static native long nativeCreateObserver(PeerConnection.Observer var0);

   private static native long nativeCreatePeerConnection(long var0, PeerConnection.RTCConfiguration var2, MediaConstraints var3, long var4);

   private static native long nativeCreateLocalMediaStream(long var0, String var2);

   private static native long nativeCreateVideoSource(long var0, EglBase.Context var2, boolean var3);

   private static native void nativeInitializeVideoCapturer(long var0, VideoCapturer var2, long var3, VideoCapturer.CapturerObserver var5);

   private static native long nativeCreateVideoTrack(long var0, String var2, long var3);

   private static native long nativeCreateAudioSource(long var0, MediaConstraints var2);

   private static native long nativeCreateAudioTrack(long var0, String var2, long var3);

   private static native boolean nativeStartAecDump(long var0, int var2, int var3);

   private static native void nativeStopAecDump(long var0);

   /** @deprecated */
   @Deprecated
   public native void nativeSetOptions(long var1, PeerConnectionFactory.Options var3);

   private static native void nativeSetVideoHwAccelerationOptions(long var0, Object var2, Object var3);

   private static native void nativeThreadsCallbacks(long var0);

   private static native void nativeFreeFactory(long var0);

   static {
      try {
         System.loadLibrary("jingle_peerconnection_so");
         nativeLibLoaded = true;
      } catch (UnsatisfiedLinkError var1) {
         nativeLibLoaded = false;
      }

   }

   public static class Options {
      static final int ADAPTER_TYPE_UNKNOWN = 0;
      static final int ADAPTER_TYPE_ETHERNET = 1;
      static final int ADAPTER_TYPE_WIFI = 2;
      static final int ADAPTER_TYPE_CELLULAR = 4;
      static final int ADAPTER_TYPE_VPN = 8;
      static final int ADAPTER_TYPE_LOOPBACK = 16;
      public int networkIgnoreMask;
      public boolean disableEncryption;
      public boolean disableNetworkMonitor;
   }
}
