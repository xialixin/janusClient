package org.webrtc;

import java.nio.ByteBuffer;

public class VideoRenderer {
   long nativeVideoRenderer;

   public static native void nativeCopyPlane(ByteBuffer var0, int var1, int var2, int var3, ByteBuffer var4, int var5);

   public static void renderFrameDone(VideoRenderer.I420Frame frame) {
      frame.yuvPlanes = null;
      frame.textureId = 0;
      if (frame.nativeFramePointer != 0L) {
         releaseNativeFrame(frame.nativeFramePointer);
         frame.nativeFramePointer = 0L;
      }

   }

   public VideoRenderer(VideoRenderer.Callbacks callbacks) {
      this.nativeVideoRenderer = nativeWrapVideoRenderer(callbacks);
   }

   public void dispose() {
      if (this.nativeVideoRenderer != 0L) {
         freeWrappedVideoRenderer(this.nativeVideoRenderer);
         this.nativeVideoRenderer = 0L;
      }
   }

   private static native long nativeWrapVideoRenderer(VideoRenderer.Callbacks var0);

   private static native void freeWrappedVideoRenderer(long var0);

   private static native void releaseNativeFrame(long var0);

   public interface Callbacks {
      void renderFrame(VideoRenderer.I420Frame var1);
   }

   public static class I420Frame {
      public final int width;
      public final int height;
      public final int[] yuvStrides;
      public ByteBuffer[] yuvPlanes;
      public final boolean yuvFrame;
      public final float[] samplingMatrix;
      public int textureId;
      private long nativeFramePointer;
      public int rotationDegree;

      public I420Frame(int width, int height, int rotationDegree, int[] yuvStrides, ByteBuffer[] yuvPlanes, long nativeFramePointer) {
         this.width = width;
         this.height = height;
         this.yuvStrides = yuvStrides;
         this.yuvPlanes = yuvPlanes;
         this.yuvFrame = true;
         this.rotationDegree = rotationDegree;
         this.nativeFramePointer = nativeFramePointer;
         if (rotationDegree % 90 != 0) {
            throw new IllegalArgumentException("Rotation degree not multiple of 90: " + rotationDegree);
         } else {
            this.samplingMatrix = new float[]{1.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F};
         }
      }

      public I420Frame(int width, int height, int rotationDegree, int textureId, float[] samplingMatrix, long nativeFramePointer) {
         this.width = width;
         this.height = height;
         this.yuvStrides = null;
         this.yuvPlanes = null;
         this.samplingMatrix = samplingMatrix;
         this.textureId = textureId;
         this.yuvFrame = false;
         this.rotationDegree = rotationDegree;
         this.nativeFramePointer = nativeFramePointer;
         if (rotationDegree % 90 != 0) {
            throw new IllegalArgumentException("Rotation degree not multiple of 90: " + rotationDegree);
         }
      }

      public int rotatedWidth() {
         return this.rotationDegree % 180 == 0 ? this.width : this.height;
      }

      public int rotatedHeight() {
         return this.rotationDegree % 180 == 0 ? this.height : this.width;
      }

      public String toString() {
         return this.width + "x" + this.height + ":" + this.yuvStrides[0] + ":" + this.yuvStrides[1] + ":" + this.yuvStrides[2];
      }
   }
}
