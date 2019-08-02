package org.webrtc;

public class VideoSource extends MediaSource {
   public VideoSource(long nativeSource) {
      super(nativeSource);
   }

   public void adaptOutputFormat(int width, int height, int fps) {
      nativeAdaptOutputFormat(this.nativeSource, width, height, fps);
   }

   private static native void nativeAdaptOutputFormat(long var0, int var2, int var3, int var4);
}
