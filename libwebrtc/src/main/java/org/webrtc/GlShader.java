package org.webrtc;

import android.opengl.GLES20;
import java.nio.FloatBuffer;

public class GlShader {
   private static final String TAG = "GlShader";
   private int program;

   private static int compileShader(int shaderType, String source) {
      int shader = GLES20.glCreateShader(shaderType);
      if (shader == 0) {
         throw new RuntimeException("glCreateShader() failed. GLES20 error: " + GLES20.glGetError());
      } else {
         GLES20.glShaderSource(shader, source);
         GLES20.glCompileShader(shader);
         int[] compileStatus = new int[]{0};
         GLES20.glGetShaderiv(shader, 35713, compileStatus, 0);
         if (compileStatus[0] != 1) {
            Logging.e("GlShader", "Could not compile shader " + shaderType + ":" + GLES20.glGetShaderInfoLog(shader));
            throw new RuntimeException(GLES20.glGetShaderInfoLog(shader));
         } else {
            GlUtil.checkNoGLES2Error("compileShader");
            return shader;
         }
      }
   }

   public GlShader(String vertexSource, String fragmentSource) {
      int vertexShader = compileShader(35633, vertexSource);
      int fragmentShader = compileShader(35632, fragmentSource);
      this.program = GLES20.glCreateProgram();
      if (this.program == 0) {
         throw new RuntimeException("glCreateProgram() failed. GLES20 error: " + GLES20.glGetError());
      } else {
         GLES20.glAttachShader(this.program, vertexShader);
         GLES20.glAttachShader(this.program, fragmentShader);
         GLES20.glLinkProgram(this.program);
         int[] linkStatus = new int[]{0};
         GLES20.glGetProgramiv(this.program, 35714, linkStatus, 0);
         if (linkStatus[0] != 1) {
            Logging.e("GlShader", "Could not link program: " + GLES20.glGetProgramInfoLog(this.program));
            throw new RuntimeException(GLES20.glGetProgramInfoLog(this.program));
         } else {
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            GlUtil.checkNoGLES2Error("Creating GlShader");
         }
      }
   }

   public int getAttribLocation(String label) {
      if (this.program == -1) {
         throw new RuntimeException("The program has been released");
      } else {
         int location = GLES20.glGetAttribLocation(this.program, label);
         if (location < 0) {
            throw new RuntimeException("Could not locate '" + label + "' in program");
         } else {
            return location;
         }
      }
   }

   public void setVertexAttribArray(String label, int dimension, FloatBuffer buffer) {
      if (this.program == -1) {
         throw new RuntimeException("The program has been released");
      } else {
         int location = this.getAttribLocation(label);
         GLES20.glEnableVertexAttribArray(location);
         GLES20.glVertexAttribPointer(location, dimension, 5126, false, 0, buffer);
         GlUtil.checkNoGLES2Error("setVertexAttribArray");
      }
   }

   public int getUniformLocation(String label) {
      if (this.program == -1) {
         throw new RuntimeException("The program has been released");
      } else {
         int location = GLES20.glGetUniformLocation(this.program, label);
         if (location < 0) {
            throw new RuntimeException("Could not locate uniform '" + label + "' in program");
         } else {
            return location;
         }
      }
   }

   public void useProgram() {
      if (this.program == -1) {
         throw new RuntimeException("The program has been released");
      } else {
         GLES20.glUseProgram(this.program);
         GlUtil.checkNoGLES2Error("glUseProgram");
      }
   }

   public void release() {
      Logging.d("GlShader", "Deleting shader.");
      if (this.program != -1) {
         GLES20.glDeleteProgram(this.program);
         this.program = -1;
      }

   }
}
