package org.webrtc;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Build.VERSION;
import android.view.Surface;

@TargetApi(18)
class EglBase14 extends EglBase {
   private static final String TAG = "EglBase14";
   private static final int EGLExt_SDK_VERSION = 18;
   private static final int CURRENT_SDK_VERSION;
   private EGLContext eglContext;
   private EGLConfig eglConfig;
   private EGLDisplay eglDisplay;
   private EGLSurface eglSurface;

   public static boolean isEGL14Supported() {
      Logging.d("EglBase14", "SDK version: " + CURRENT_SDK_VERSION + ". isEGL14Supported: " + (CURRENT_SDK_VERSION >= 18));
      return CURRENT_SDK_VERSION >= 18;
   }

   public EglBase14(EglBase14.Context sharedContext, int[] configAttributes) {
      this.eglSurface = EGL14.EGL_NO_SURFACE;
      this.eglDisplay = getEglDisplay();
      this.eglConfig = getEglConfig(this.eglDisplay, configAttributes);
      this.eglContext = createEglContext(sharedContext, this.eglDisplay, this.eglConfig);
   }

   public void createSurface(Surface surface) {
      this.createSurfaceInternal(surface);
   }

   public void createSurface(SurfaceTexture surfaceTexture) {
      this.createSurfaceInternal(surfaceTexture);
   }

   private void createSurfaceInternal(Object surface) {
      if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
         throw new IllegalStateException("Input must be either a Surface or SurfaceTexture");
      } else {
         this.checkIsNotReleased();
         if (this.eglSurface != EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("Already has an EGLSurface");
         } else {
            int[] surfaceAttribs = new int[]{12344};
            this.eglSurface = EGL14.eglCreateWindowSurface(this.eglDisplay, this.eglConfig, surface, surfaceAttribs, 0);
            if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
               throw new RuntimeException("Failed to create window surface: 0x" + Integer.toHexString(EGL14.eglGetError()));
            }
         }
      }
   }

   public void createDummyPbufferSurface() {
      this.createPbufferSurface(1, 1);
   }

   public void createPbufferSurface(int width, int height) {
      this.checkIsNotReleased();
      if (this.eglSurface != EGL14.EGL_NO_SURFACE) {
         throw new RuntimeException("Already has an EGLSurface");
      } else {
         int[] surfaceAttribs = new int[]{12375, width, 12374, height, 12344};
         this.eglSurface = EGL14.eglCreatePbufferSurface(this.eglDisplay, this.eglConfig, surfaceAttribs, 0);
         if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("Failed to create pixel buffer surface with size " + width + "x" + height + ": 0x" + Integer.toHexString(EGL14.eglGetError()));
         }
      }
   }

   public EglBase14.Context getEglBaseContext() {
      return new EglBase14.Context(this.eglContext);
   }

   public boolean hasSurface() {
      return this.eglSurface != EGL14.EGL_NO_SURFACE;
   }

   public int surfaceWidth() {
      int[] widthArray = new int[1];
      EGL14.eglQuerySurface(this.eglDisplay, this.eglSurface, 12375, widthArray, 0);
      return widthArray[0];
   }

   public int surfaceHeight() {
      int[] heightArray = new int[1];
      EGL14.eglQuerySurface(this.eglDisplay, this.eglSurface, 12374, heightArray, 0);
      return heightArray[0];
   }

   public void releaseSurface() {
      if (this.eglSurface != EGL14.EGL_NO_SURFACE) {
         EGL14.eglDestroySurface(this.eglDisplay, this.eglSurface);
         this.eglSurface = EGL14.EGL_NO_SURFACE;
      }

   }

   private void checkIsNotReleased() {
      if (this.eglDisplay == EGL14.EGL_NO_DISPLAY || this.eglContext == EGL14.EGL_NO_CONTEXT || this.eglConfig == null) {
         throw new RuntimeException("This object has been released");
      }
   }

   public void release() {
      this.checkIsNotReleased();
      this.releaseSurface();
      this.detachCurrent();
      EGL14.eglDestroyContext(this.eglDisplay, this.eglContext);
      EGL14.eglReleaseThread();
      EGL14.eglTerminate(this.eglDisplay);
      this.eglContext = EGL14.EGL_NO_CONTEXT;
      this.eglDisplay = EGL14.EGL_NO_DISPLAY;
      this.eglConfig = null;
   }

   public void makeCurrent() {
      this.checkIsNotReleased();
      if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
         throw new RuntimeException("No EGLSurface - can't make current");
      } else {
         synchronized(EglBase.lock) {
            if (!EGL14.eglMakeCurrent(this.eglDisplay, this.eglSurface, this.eglSurface, this.eglContext)) {
               throw new RuntimeException("eglMakeCurrent failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
            }
         }
      }
   }

   public void detachCurrent() {
      synchronized(EglBase.lock) {
         if (!EGL14.eglMakeCurrent(this.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            throw new RuntimeException("eglDetachCurrent failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
         }
      }
   }

   public void swapBuffers() {
      this.checkIsNotReleased();
      if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
         throw new RuntimeException("No EGLSurface - can't swap buffers");
      } else {
         synchronized(EglBase.lock) {
            EGL14.eglSwapBuffers(this.eglDisplay, this.eglSurface);
         }
      }
   }

   public void swapBuffers(long timeStampNs) {
      this.checkIsNotReleased();
      if (this.eglSurface == EGL14.EGL_NO_SURFACE) {
         throw new RuntimeException("No EGLSurface - can't swap buffers");
      } else {
         synchronized(EglBase.lock) {
            EGLExt.eglPresentationTimeANDROID(this.eglDisplay, this.eglSurface, timeStampNs);
            EGL14.eglSwapBuffers(this.eglDisplay, this.eglSurface);
         }
      }
   }

   private static EGLDisplay getEglDisplay() {
      EGLDisplay eglDisplay = EGL14.eglGetDisplay(0);
      if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
         throw new RuntimeException("Unable to get EGL14 display: 0x" + Integer.toHexString(EGL14.eglGetError()));
      } else {
         int[] version = new int[2];
         if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw new RuntimeException("Unable to initialize EGL14: 0x" + Integer.toHexString(EGL14.eglGetError()));
         } else {
            return eglDisplay;
         }
      }
   }

   private static EGLConfig getEglConfig(EGLDisplay eglDisplay, int[] configAttributes) {
      EGLConfig[] configs = new EGLConfig[1];
      int[] numConfigs = new int[1];
      if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, configs.length, numConfigs, 0)) {
         throw new RuntimeException("eglChooseConfig failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
      } else if (numConfigs[0] <= 0) {
         throw new RuntimeException("Unable to find any matching EGL config");
      } else {
         EGLConfig eglConfig = configs[0];
         if (eglConfig == null) {
            throw new RuntimeException("eglChooseConfig returned null");
         } else {
            return eglConfig;
         }
      }
   }

   private static EGLContext createEglContext(EglBase14.Context sharedContext, EGLDisplay eglDisplay, EGLConfig eglConfig) {
      if (sharedContext != null && sharedContext.egl14Context == EGL14.EGL_NO_CONTEXT) {
         throw new RuntimeException("Invalid sharedContext");
      } else {
         int[] contextAttributes = new int[]{12440, 2, 12344};
         EGLContext rootContext = sharedContext == null ? EGL14.EGL_NO_CONTEXT : sharedContext.egl14Context;
         EGLContext eglContext;
         synchronized(EglBase.lock) {
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, rootContext, contextAttributes, 0);
         }

         if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("Failed to create EGL context: 0x" + Integer.toHexString(EGL14.eglGetError()));
         } else {
            return eglContext;
         }
      }
   }

   static {
      CURRENT_SDK_VERSION = VERSION.SDK_INT;
   }

   public static class Context extends EglBase.Context {
      private final EGLContext egl14Context;

      public Context(EGLContext eglContext) {
         this.egl14Context = eglContext;
      }
   }
}
