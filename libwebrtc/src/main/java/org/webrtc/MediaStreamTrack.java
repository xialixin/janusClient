package org.webrtc;

public class MediaStreamTrack {
   final long nativeTrack;

   public MediaStreamTrack(long nativeTrack) {
      this.nativeTrack = nativeTrack;
   }

   public String id() {
      return nativeId(this.nativeTrack);
   }

   public String kind() {
      return nativeKind(this.nativeTrack);
   }

   public boolean enabled() {
      return nativeEnabled(this.nativeTrack);
   }

   public boolean setEnabled(boolean enable) {
      return nativeSetEnabled(this.nativeTrack, enable);
   }

   public MediaStreamTrack.State state() {
      return nativeState(this.nativeTrack);
   }

   public void dispose() {
      free(this.nativeTrack);
   }

   private static native String nativeId(long var0);

   private static native String nativeKind(long var0);

   private static native boolean nativeEnabled(long var0);

   private static native boolean nativeSetEnabled(long var0, boolean var2);

   private static native MediaStreamTrack.State nativeState(long var0);

   private static native void free(long var0);

   public static enum MediaType {
      MEDIA_TYPE_AUDIO,
      MEDIA_TYPE_VIDEO;
   }

   public static enum State {
      LIVE,
      ENDED;
   }
}
