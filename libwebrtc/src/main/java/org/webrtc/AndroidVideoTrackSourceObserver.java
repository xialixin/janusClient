package org.webrtc;

class AndroidVideoTrackSourceObserver implements VideoCapturer.CapturerObserver {
   private final long nativeSource;

   public AndroidVideoTrackSourceObserver(long nativeSource) {
      this.nativeSource = nativeSource;
   }

   public void onCapturerStarted(boolean success) {
      this.nativeCapturerStarted(this.nativeSource, success);
   }

   public void onCapturerStopped() {
      this.nativeCapturerStopped(this.nativeSource);
   }

   public void onByteBufferFrameCaptured(byte[] data, int width, int height, int rotation, long timeStamp) {
      this.nativeOnByteBufferFrameCaptured(this.nativeSource, data, data.length, width, height, rotation, timeStamp);
   }

   public void onTextureFrameCaptured(int width, int height, int oesTextureId, float[] transformMatrix, int rotation, long timestamp) {
      this.nativeOnTextureFrameCaptured(this.nativeSource, width, height, oesTextureId, transformMatrix, rotation, timestamp);
   }

   private native void nativeCapturerStarted(long var1, boolean var3);

   private native void nativeCapturerStopped(long var1);

   private native void nativeOnByteBufferFrameCaptured(long var1, byte[] var3, int var4, int var5, int var6, int var7, long var8);

   private native void nativeOnTextureFrameCaptured(long var1, int var3, int var4, int var5, float[] var6, int var7, long var8);
}
