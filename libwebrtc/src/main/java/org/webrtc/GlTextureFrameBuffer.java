package org.webrtc;

import android.opengl.GLES20;
import java.nio.Buffer;

public class GlTextureFrameBuffer {
   private final int frameBufferId;
   private final int textureId;
   private final int pixelFormat;
   private int width;
   private int height;

   public GlTextureFrameBuffer(int pixelFormat) {
      switch(pixelFormat) {
      case 6407:
      case 6408:
      case 6409:
         this.pixelFormat = pixelFormat;
         this.textureId = GlUtil.generateTexture(3553);
         this.width = 0;
         this.height = 0;
         int[] frameBuffers = new int[1];
         GLES20.glGenFramebuffers(1, frameBuffers, 0);
         this.frameBufferId = frameBuffers[0];
         return;
      default:
         throw new IllegalArgumentException("Invalid pixel format: " + pixelFormat);
      }
   }

   public void setSize(int width, int height) {
      if (width != 0 && height != 0) {
         if (width != this.width || height != this.height) {
            this.width = width;
            this.height = height;
            GLES20.glActiveTexture(33984);
            GLES20.glBindTexture(3553, this.textureId);
            GLES20.glTexImage2D(3553, 0, this.pixelFormat, width, height, 0, this.pixelFormat, 5121, (Buffer)null);
            GLES20.glBindTexture(3553, 0);
            GlUtil.checkNoGLES2Error("GlTextureFrameBuffer setSize");
            GLES20.glBindFramebuffer(36160, this.frameBufferId);
            GLES20.glFramebufferTexture2D(36160, 36064, 3553, this.textureId, 0);
            int status = GLES20.glCheckFramebufferStatus(36160);
            if (status != 36053) {
               throw new IllegalStateException("Framebuffer not complete, status: " + status);
            } else {
               GLES20.glBindFramebuffer(36160, 0);
            }
         }
      } else {
         throw new IllegalArgumentException("Invalid size: " + width + "x" + height);
      }
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public int getFrameBufferId() {
      return this.frameBufferId;
   }

   public int getTextureId() {
      return this.textureId;
   }

   public void release() {
      GLES20.glDeleteTextures(1, new int[]{this.textureId}, 0);
      GLES20.glDeleteFramebuffers(1, new int[]{this.frameBufferId}, 0);
      this.width = 0;
      this.height = 0;
   }
}
