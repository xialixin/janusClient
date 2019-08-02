package org.webrtc;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

class EglBase10 extends EglBase {
   private static final int EGL_CONTEXT_CLIENT_VERSION = 12440;
   private final EGL10 egl;
   private EGLContext eglContext;
   private EGLConfig eglConfig;
   private EGLDisplay eglDisplay;
   private EGLSurface eglSurface;

   public EglBase10(EglBase10.Context sharedContext, int[] configAttributes) {
      this.eglSurface = EGL10.EGL_NO_SURFACE;
      this.egl = (EGL10)EGLContext.getEGL();
      this.eglDisplay = this.getEglDisplay();
      this.eglConfig = this.getEglConfig(this.eglDisplay, configAttributes);
      this.eglContext = this.createEglContext(sharedContext, this.eglDisplay, this.eglConfig);
   }

   public void createSurface(Surface surface) {
      class FakeSurfaceHolder implements SurfaceHolder {
         private final Surface surface;

         FakeSurfaceHolder(Surface surface) {
            this.surface = surface;
         }

         public void addCallback(Callback callback) {
         }

         public void removeCallback(Callback callback) {
         }

         public boolean isCreating() {
            return false;
         }

         /** @deprecated */
         @Deprecated
         public void setType(int i) {
         }

         public void setFixedSize(int i, int i2) {
         }

         public void setSizeFromLayout() {
         }

         public void setFormat(int i) {
         }

         public void setKeepScreenOn(boolean b) {
         }

         public Canvas lockCanvas() {
            return null;
         }

         public Canvas lockCanvas(Rect rect) {
            return null;
         }

         public void unlockCanvasAndPost(Canvas canvas) {
         }

         public Rect getSurfaceFrame() {
            return null;
         }

         public Surface getSurface() {
            return this.surface;
         }
      }

      this.createSurfaceInternal(new FakeSurfaceHolder(surface));
   }

   public void createSurface(SurfaceTexture surfaceTexture) {
      this.createSurfaceInternal(surfaceTexture);
   }

   private void createSurfaceInternal(Object nativeWindow) {
      if (!(nativeWindow instanceof SurfaceHolder) && !(nativeWindow instanceof SurfaceTexture)) {
         throw new IllegalStateException("Input must be either a SurfaceHolder or SurfaceTexture");
      } else {
         this.checkIsNotReleased();
         if (this.eglSurface != EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("Already has an EGLSurface");
         } else {
            int[] surfaceAttribs = new int[]{12344};
            this.eglSurface = this.egl.eglCreateWindowSurface(this.eglDisplay, this.eglConfig, nativeWindow, surfaceAttribs);
            if (this.eglSurface == EGL10.EGL_NO_SURFACE) {
               throw new RuntimeException("Failed to create window surface: 0x" + Integer.toHexString(this.egl.eglGetError()));
            }
         }
      }
   }

   public void createDummyPbufferSurface() {
      this.createPbufferSurface(1, 1);
   }

   public void createPbufferSurface(int width, int height) {
      this.checkIsNotReleased();
      if (this.eglSurface != EGL10.EGL_NO_SURFACE) {
         throw new RuntimeException("Already has an EGLSurface");
      } else {
         int[] surfaceAttribs = new int[]{12375, width, 12374, height, 12344};
         this.eglSurface = this.egl.eglCreatePbufferSurface(this.eglDisplay, this.eglConfig, surfaceAttribs);
         if (this.eglSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("Failed to create pixel buffer surface with size " + width + "x" + height + ": 0x" + Integer.toHexString(this.egl.eglGetError()));
         }
      }
   }

   public EglBase.Context getEglBaseContext() {
      return new EglBase10.Context(this.eglContext);
   }

   public boolean hasSurface() {
      return this.eglSurface != EGL10.EGL_NO_SURFACE;
   }

   public int surfaceWidth() {
      int[] widthArray = new int[1];
      this.egl.eglQuerySurface(this.eglDisplay, this.eglSurface, 12375, widthArray);
      return widthArray[0];
   }

   public int surfaceHeight() {
      int[] heightArray = new int[1];
      this.egl.eglQuerySurface(this.eglDisplay, this.eglSurface, 12374, heightArray);
      return heightArray[0];
   }

   public void releaseSurface() {
      if (this.eglSurface != EGL10.EGL_NO_SURFACE) {
         this.egl.eglDestroySurface(this.eglDisplay, this.eglSurface);
         this.eglSurface = EGL10.EGL_NO_SURFACE;
      }

   }

   private void checkIsNotReleased() {
      if (this.eglDisplay == EGL10.EGL_NO_DISPLAY || this.eglContext == EGL10.EGL_NO_CONTEXT || this.eglConfig == null) {
         throw new RuntimeException("This object has been released");
      }
   }

   public void release() {
      this.checkIsNotReleased();
      this.releaseSurface();
      this.detachCurrent();
      this.egl.eglDestroyContext(this.eglDisplay, this.eglContext);
      this.egl.eglTerminate(this.eglDisplay);
      this.eglContext = EGL10.EGL_NO_CONTEXT;
      this.eglDisplay = EGL10.EGL_NO_DISPLAY;
      this.eglConfig = null;
   }

   public void makeCurrent() {
      this.checkIsNotReleased();
      if (this.eglSurface == EGL10.EGL_NO_SURFACE) {
         throw new RuntimeException("No EGLSurface - can't make current");
      } else {
         synchronized(EglBase.lock) {
            if (!this.egl.eglMakeCurrent(this.eglDisplay, this.eglSurface, this.eglSurface, this.eglContext)) {
               throw new RuntimeException("eglMakeCurrent failed: 0x" + Integer.toHexString(this.egl.eglGetError()));
            }
         }
      }
   }

   public void detachCurrent() {
      synchronized(EglBase.lock) {
         if (!this.egl.eglMakeCurrent(this.eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)) {
            throw new RuntimeException("eglDetachCurrent failed: 0x" + Integer.toHexString(this.egl.eglGetError()));
         }
      }
   }

   public void swapBuffers() {
      this.checkIsNotReleased();
      if (this.eglSurface == EGL10.EGL_NO_SURFACE) {
         throw new RuntimeException("No EGLSurface - can't swap buffers");
      } else {
         synchronized(EglBase.lock) {
            this.egl.eglSwapBuffers(this.eglDisplay, this.eglSurface);
         }
      }
   }

   private EGLDisplay getEglDisplay() {
      EGLDisplay eglDisplay = this.egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
      if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
         throw new RuntimeException("Unable to get EGL10 display: 0x" + Integer.toHexString(this.egl.eglGetError()));
      } else {
         int[] version = new int[2];
         if (!this.egl.eglInitialize(eglDisplay, version)) {
            throw new RuntimeException("Unable to initialize EGL10: 0x" + Integer.toHexString(this.egl.eglGetError()));
         } else {
            return eglDisplay;
         }
      }
   }

   private EGLConfig getEglConfig(EGLDisplay eglDisplay, int[] configAttributes) {
      EGLConfig[] configs = new EGLConfig[1];
      int[] numConfigs = new int[1];
      if (!this.egl.eglChooseConfig(eglDisplay, configAttributes, configs, configs.length, numConfigs)) {
         throw new RuntimeException("eglChooseConfig failed: 0x" + Integer.toHexString(this.egl.eglGetError()));
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

   private EGLContext createEglContext(EglBase10.Context sharedContext, EGLDisplay eglDisplay, EGLConfig eglConfig) {
      if (sharedContext != null && sharedContext.eglContext == EGL10.EGL_NO_CONTEXT) {
         throw new RuntimeException("Invalid sharedContext");
      } else {
         int[] contextAttributes = new int[]{12440, 2, 12344};
         EGLContext rootContext = sharedContext == null ? EGL10.EGL_NO_CONTEXT : sharedContext.eglContext;
         EGLContext eglContext;
         synchronized(EglBase.lock) {
            eglContext = this.egl.eglCreateContext(eglDisplay, eglConfig, rootContext, contextAttributes);
         }

         if (eglContext == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException("Failed to create EGL context: 0x" + Integer.toHexString(this.egl.eglGetError()));
         } else {
            return eglContext;
         }
      }
   }

   public static class Context extends EglBase.Context {
      private final EGLContext eglContext;

      public Context(EGLContext eglContext) {
         this.eglContext = eglContext;
      }
   }
}
