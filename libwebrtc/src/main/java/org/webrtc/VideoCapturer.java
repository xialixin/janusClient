package org.webrtc;

import android.content.Context;

public interface VideoCapturer {
   void initialize(SurfaceTextureHelper var1, Context var2, VideoCapturer.CapturerObserver var3);

   void startCapture(int var1, int var2, int var3);

   void stopCapture() throws InterruptedException;

   void changeCaptureFormat(int var1, int var2, int var3);

   void dispose();

   boolean isScreencast();

   public interface CapturerObserver {
      void onCapturerStarted(boolean var1);

      void onCapturerStopped();

      void onByteBufferFrameCaptured(byte[] var1, int var2, int var3, int var4, long var5);

      void onTextureFrameCaptured(int var1, int var2, int var3, float[] var4, int var5, long var6);
   }
}
