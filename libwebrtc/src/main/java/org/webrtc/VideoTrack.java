package org.webrtc;

import java.util.LinkedList;

public class VideoTrack extends MediaStreamTrack {
   private final LinkedList<VideoRenderer> renderers = new LinkedList();

   public VideoTrack(long nativeTrack) {
      super(nativeTrack);
   }

   public void addRenderer(VideoRenderer renderer) {
      this.renderers.add(renderer);
      nativeAddRenderer(this.nativeTrack, renderer.nativeVideoRenderer);
   }

   public void removeRenderer(VideoRenderer renderer) {
      if (this.renderers.remove(renderer)) {
         nativeRemoveRenderer(this.nativeTrack, renderer.nativeVideoRenderer);
         renderer.dispose();
      }
   }

   public void dispose() {
      while(!this.renderers.isEmpty()) {
         this.removeRenderer((VideoRenderer)this.renderers.getFirst());
      }

      super.dispose();
   }

   private static native void nativeAddRenderer(long var0, long var2);

   private static native void nativeRemoveRenderer(long var0, long var2);
}
