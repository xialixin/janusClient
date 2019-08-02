package org.webrtc;

public class MediaSource {
   final long nativeSource;

   public MediaSource(long nativeSource) {
      this.nativeSource = nativeSource;
   }

   public MediaSource.State state() {
      return nativeState(this.nativeSource);
   }

   public void dispose() {
      free(this.nativeSource);
   }

   private static native MediaSource.State nativeState(long var0);

   private static native void free(long var0);

   public static enum State {
      INITIALIZING,
      LIVE,
      ENDED,
      MUTED;
   }
}
