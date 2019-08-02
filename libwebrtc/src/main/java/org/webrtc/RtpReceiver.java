package org.webrtc;

public class RtpReceiver {
   final long nativeRtpReceiver;
   private long nativeObserver;
   private MediaStreamTrack cachedTrack;

   public RtpReceiver(long nativeRtpReceiver) {
      this.nativeRtpReceiver = nativeRtpReceiver;
      long track = nativeGetTrack(nativeRtpReceiver);
      this.cachedTrack = new MediaStreamTrack(track);
   }

   public MediaStreamTrack track() {
      return this.cachedTrack;
   }

   public boolean setParameters(RtpParameters parameters) {
      return nativeSetParameters(this.nativeRtpReceiver, parameters);
   }

   public RtpParameters getParameters() {
      return nativeGetParameters(this.nativeRtpReceiver);
   }

   public String id() {
      return nativeId(this.nativeRtpReceiver);
   }

   public void dispose() {
      this.cachedTrack.dispose();
      if (this.nativeObserver != 0L) {
         nativeUnsetObserver(this.nativeRtpReceiver, this.nativeObserver);
         this.nativeObserver = 0L;
      }

      free(this.nativeRtpReceiver);
   }

   public void SetObserver(RtpReceiver.Observer observer) {
      if (this.nativeObserver != 0L) {
         nativeUnsetObserver(this.nativeRtpReceiver, this.nativeObserver);
      }

      this.nativeObserver = nativeSetObserver(this.nativeRtpReceiver, observer);
   }

   private static native long nativeGetTrack(long var0);

   private static native boolean nativeSetParameters(long var0, RtpParameters var2);

   private static native RtpParameters nativeGetParameters(long var0);

   private static native String nativeId(long var0);

   private static native void free(long var0);

   private static native long nativeSetObserver(long var0, RtpReceiver.Observer var2);

   private static native long nativeUnsetObserver(long var0, long var2);

   public interface Observer {
      void onFirstPacketReceived(MediaStreamTrack.MediaType var1);
   }
}
