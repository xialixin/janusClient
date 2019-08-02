package org.webrtc;

public class AudioTrack extends MediaStreamTrack {
   public AudioTrack(long nativeTrack) {
      super(nativeTrack);
   }

   public void setVolume(double volume) {
      nativeSetVolume(super.nativeTrack, volume);
   }

   private static native void nativeSetVolume(long var0, double var2);
}
