package org.webrtc;

interface CameraSession {
   void stop();

   public interface Events {
      void onCameraOpening();

      void onCameraError(CameraSession var1, String var2);

      void onCameraDisconnected(CameraSession var1);

      void onCameraClosed(CameraSession var1);

      void onByteBufferFrameCaptured(CameraSession var1, byte[] var2, int var3, int var4, int var5, long var6);

      void onTextureFrameCaptured(CameraSession var1, int var2, int var3, int var4, float[] var5, int var6, long var7);
   }

   public interface CreateSessionCallback {
      void onDone(CameraSession var1);

      void onFailure(CameraSession.FailureType var1, String var2);
   }

   public static enum FailureType {
      ERROR,
      DISCONNECTED;
   }
}
