package org.webrtc;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GlUtil {
   private GlUtil() {
   }

   public static void checkNoGLES2Error(String msg) {
      int error = GLES20.glGetError();
      if (error != 0) {
         throw new RuntimeException(msg + ": GLES20 error: " + error);
      }
   }

   public static FloatBuffer createFloatBuffer(float[] coords) {
      ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * 4);
      bb.order(ByteOrder.nativeOrder());
      FloatBuffer fb = bb.asFloatBuffer();
      fb.put(coords);
      fb.position(0);
      return fb;
   }

   public static int generateTexture(int target) {
      int[] textureArray = new int[1];
      GLES20.glGenTextures(1, textureArray, 0);
      int textureId = textureArray[0];
      GLES20.glBindTexture(target, textureId);
      GLES20.glTexParameterf(target, 10241, 9729.0F);
      GLES20.glTexParameterf(target, 10240, 9729.0F);
      GLES20.glTexParameterf(target, 10242, 33071.0F);
      GLES20.glTexParameterf(target, 10243, 33071.0F);
      checkNoGLES2Error("generateTexture");
      return textureId;
   }
}
