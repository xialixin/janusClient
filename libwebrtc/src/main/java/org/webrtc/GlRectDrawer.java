package org.webrtc;

import android.opengl.GLES20;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public class GlRectDrawer implements RendererCommon.GlDrawer {
   private static final String VERTEX_SHADER_STRING = "varying vec2 interp_tc;\nattribute vec4 in_pos;\nattribute vec4 in_tc;\n\nuniform mat4 texMatrix;\n\nvoid main() {\n    gl_Position = in_pos;\n    interp_tc = (texMatrix * in_tc).xy;\n}\n";
   private static final String YUV_FRAGMENT_SHADER_STRING = "precision mediump float;\nvarying vec2 interp_tc;\n\nuniform sampler2D y_tex;\nuniform sampler2D u_tex;\nuniform sampler2D v_tex;\n\nvoid main() {\n  float y = texture2D(y_tex, interp_tc).r;\n  float u = texture2D(u_tex, interp_tc).r - 0.5;\n  float v = texture2D(v_tex, interp_tc).r - 0.5;\n  gl_FragColor = vec4(y + 1.403 * v,                       y - 0.344 * u - 0.714 * v,                       y + 1.77 * u, 1);\n}\n";
   private static final String RGB_FRAGMENT_SHADER_STRING = "precision mediump float;\nvarying vec2 interp_tc;\n\nuniform sampler2D rgb_tex;\n\nvoid main() {\n  gl_FragColor = texture2D(rgb_tex, interp_tc);\n}\n";
   private static final String OES_FRAGMENT_SHADER_STRING = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 interp_tc;\n\nuniform samplerExternalOES oes_tex;\n\nvoid main() {\n  gl_FragColor = texture2D(oes_tex, interp_tc);\n}\n";
   private static final FloatBuffer FULL_RECTANGLE_BUF = GlUtil.createFloatBuffer(new float[]{-1.0F, -1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F});
   private static final FloatBuffer FULL_RECTANGLE_TEX_BUF = GlUtil.createFloatBuffer(new float[]{0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F});
   private final Map<String, GlRectDrawer.Shader> shaders = new IdentityHashMap();

   public void drawOes(int oesTextureId, float[] texMatrix, int frameWidth, int frameHeight, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
      this.prepareShader("#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 interp_tc;\n\nuniform samplerExternalOES oes_tex;\n\nvoid main() {\n  gl_FragColor = texture2D(oes_tex, interp_tc);\n}\n", texMatrix);
      GLES20.glActiveTexture(33984);
      GLES20.glBindTexture(36197, oesTextureId);
      this.drawRectangle(viewportX, viewportY, viewportWidth, viewportHeight);
      GLES20.glBindTexture(36197, 0);
   }

   public void drawRgb(int textureId, float[] texMatrix, int frameWidth, int frameHeight, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
      this.prepareShader("precision mediump float;\nvarying vec2 interp_tc;\n\nuniform sampler2D rgb_tex;\n\nvoid main() {\n  gl_FragColor = texture2D(rgb_tex, interp_tc);\n}\n", texMatrix);
      GLES20.glActiveTexture(33984);
      GLES20.glBindTexture(3553, textureId);
      this.drawRectangle(viewportX, viewportY, viewportWidth, viewportHeight);
      GLES20.glBindTexture(3553, 0);
   }

   public void drawYuv(int[] yuvTextures, float[] texMatrix, int frameWidth, int frameHeight, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
      this.prepareShader("precision mediump float;\nvarying vec2 interp_tc;\n\nuniform sampler2D y_tex;\nuniform sampler2D u_tex;\nuniform sampler2D v_tex;\n\nvoid main() {\n  float y = texture2D(y_tex, interp_tc).r;\n  float u = texture2D(u_tex, interp_tc).r - 0.5;\n  float v = texture2D(v_tex, interp_tc).r - 0.5;\n  gl_FragColor = vec4(y + 1.403 * v,                       y - 0.344 * u - 0.714 * v,                       y + 1.77 * u, 1);\n}\n", texMatrix);

      int i;
      for(i = 0; i < 3; ++i) {
         GLES20.glActiveTexture('蓀' + i);
         GLES20.glBindTexture(3553, yuvTextures[i]);
      }

      this.drawRectangle(viewportX, viewportY, viewportWidth, viewportHeight);

      for(i = 0; i < 3; ++i) {
         GLES20.glActiveTexture('蓀' + i);
         GLES20.glBindTexture(3553, 0);
      }

   }

   private void drawRectangle(int x, int y, int width, int height) {
      GLES20.glViewport(x, y, width, height);
      GLES20.glDrawArrays(5, 0, 4);
   }

   private void prepareShader(String fragmentShader, float[] texMatrix) {
      GlRectDrawer.Shader shader;
      if (this.shaders.containsKey(fragmentShader)) {
         shader = (GlRectDrawer.Shader)this.shaders.get(fragmentShader);
      } else {
         shader = new GlRectDrawer.Shader(fragmentShader);
         this.shaders.put(fragmentShader, shader);
         shader.glShader.useProgram();
         if (fragmentShader == "precision mediump float;\nvarying vec2 interp_tc;\n\nuniform sampler2D y_tex;\nuniform sampler2D u_tex;\nuniform sampler2D v_tex;\n\nvoid main() {\n  float y = texture2D(y_tex, interp_tc).r;\n  float u = texture2D(u_tex, interp_tc).r - 0.5;\n  float v = texture2D(v_tex, interp_tc).r - 0.5;\n  gl_FragColor = vec4(y + 1.403 * v,                       y - 0.344 * u - 0.714 * v,                       y + 1.77 * u, 1);\n}\n") {
            GLES20.glUniform1i(shader.glShader.getUniformLocation("y_tex"), 0);
            GLES20.glUniform1i(shader.glShader.getUniformLocation("u_tex"), 1);
            GLES20.glUniform1i(shader.glShader.getUniformLocation("v_tex"), 2);
         } else if (fragmentShader == "precision mediump float;\nvarying vec2 interp_tc;\n\nuniform sampler2D rgb_tex;\n\nvoid main() {\n  gl_FragColor = texture2D(rgb_tex, interp_tc);\n}\n") {
            GLES20.glUniform1i(shader.glShader.getUniformLocation("rgb_tex"), 0);
         } else {
            if (fragmentShader != "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 interp_tc;\n\nuniform samplerExternalOES oes_tex;\n\nvoid main() {\n  gl_FragColor = texture2D(oes_tex, interp_tc);\n}\n") {
               throw new IllegalStateException("Unknown fragment shader: " + fragmentShader);
            }

            GLES20.glUniform1i(shader.glShader.getUniformLocation("oes_tex"), 0);
         }

         GlUtil.checkNoGLES2Error("Initialize fragment shader uniform values.");
         shader.glShader.setVertexAttribArray("in_pos", 2, FULL_RECTANGLE_BUF);
         shader.glShader.setVertexAttribArray("in_tc", 2, FULL_RECTANGLE_TEX_BUF);
      }

      shader.glShader.useProgram();
      GLES20.glUniformMatrix4fv(shader.texMatrixLocation, 1, false, texMatrix, 0);
   }

   public void release() {
      Iterator var1 = this.shaders.values().iterator();

      while(var1.hasNext()) {
         GlRectDrawer.Shader shader = (GlRectDrawer.Shader)var1.next();
         shader.glShader.release();
      }

      this.shaders.clear();
   }

   private static class Shader {
      public final GlShader glShader;
      public final int texMatrixLocation;

      public Shader(String fragmentShader) {
         this.glShader = new GlShader("varying vec2 interp_tc;\nattribute vec4 in_pos;\nattribute vec4 in_tc;\n\nuniform mat4 texMatrix;\n\nvoid main() {\n    gl_Position = in_pos;\n    interp_tc = (texMatrix * in_tc).xy;\n}\n", fragmentShader);
         this.texMatrixLocation = this.glShader.getUniformLocation("texMatrix");
      }
   }
}
