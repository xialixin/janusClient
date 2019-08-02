package org.webrtc;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Build.VERSION;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TargetApi(19)
public class MediaCodecVideoEncoder {
   private static final String TAG = "MediaCodecVideoEncoder";
   private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
   private static final int DEQUEUE_TIMEOUT = 0;
   private static final int BITRATE_ADJUSTMENT_FPS = 30;
   private static final int MAXIMUM_INITIAL_FPS = 30;
   private static final double BITRATE_CORRECTION_SEC = 3.0D;
   private static final double BITRATE_CORRECTION_MAX_SCALE = 4.0D;
   private static final int BITRATE_CORRECTION_STEPS = 20;
   private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 25000L;
   private static final long QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000L;
   private static MediaCodecVideoEncoder runningInstance = null;
   private static MediaCodecVideoEncoder.MediaCodecVideoEncoderErrorCallback errorCallback = null;
   private static int codecErrors = 0;
   private static Set<String> hwEncoderDisabledTypes = new HashSet();
   private Thread mediaCodecThread;
   private MediaCodec mediaCodec;
   private ByteBuffer[] outputBuffers;
   private EglBase14 eglBase;
   private int width;
   private int height;
   private Surface inputSurface;
   private GlRectDrawer drawer;
   private static final String VP8_MIME_TYPE = "video/x-vnd.on2.vp8";
   private static final String VP9_MIME_TYPE = "video/x-vnd.on2.vp9";
   private static final String H264_MIME_TYPE = "video/avc";
   private static final MediaCodecVideoEncoder.MediaCodecProperties qcomVp8HwProperties;
   private static final MediaCodecVideoEncoder.MediaCodecProperties exynosVp8HwProperties;
   private static final MediaCodecVideoEncoder.MediaCodecProperties intelVp8HwProperties;
   private static final MediaCodecVideoEncoder.MediaCodecProperties qcomVp9HwProperties;
   private static final MediaCodecVideoEncoder.MediaCodecProperties exynosVp9HwProperties;
   private static final MediaCodecVideoEncoder.MediaCodecProperties[] vp9HwList;
   private static final MediaCodecVideoEncoder.MediaCodecProperties qcomH264HwProperties;
   private static final MediaCodecVideoEncoder.MediaCodecProperties exynosH264HwProperties;
   private static final MediaCodecVideoEncoder.MediaCodecProperties[] h264HwList;
   private static final String[] H264_HW_EXCEPTION_MODELS;
   private static final int VIDEO_ControlRateConstant = 2;
   private static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 2141391876;
   private static final int[] supportedColorList;
   private static final int[] supportedSurfaceColorList;
   private MediaCodecVideoEncoder.VideoCodecType type;
   private int colorFormat;
   private MediaCodecVideoEncoder.BitrateAdjustmentType bitrateAdjustmentType;
   private double bitrateAccumulator;
   private double bitrateAccumulatorMax;
   private double bitrateObservationTimeMs;
   private int bitrateAdjustmentScaleExp;
   private int targetBitrateBps;
   private int targetFps;
   private long forcedKeyFrameMs;
   private long lastKeyFrameMs;
   private ByteBuffer configData;

   public MediaCodecVideoEncoder() {
      this.bitrateAdjustmentType = MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT;
      this.configData = null;
   }

   private static MediaCodecVideoEncoder.MediaCodecProperties[] vp8HwList() {
      ArrayList<MediaCodecVideoEncoder.MediaCodecProperties> supported_codecs = new ArrayList();
      supported_codecs.add(qcomVp8HwProperties);
      supported_codecs.add(exynosVp8HwProperties);
      if (PeerConnectionFactory.fieldTrialsFindFullName("WebRTC-IntelVP8").equals("Enabled")) {
         supported_codecs.add(intelVp8HwProperties);
      }

      return (MediaCodecVideoEncoder.MediaCodecProperties[])supported_codecs.toArray(new MediaCodecVideoEncoder.MediaCodecProperties[supported_codecs.size()]);
   }

   public static void setErrorCallback(MediaCodecVideoEncoder.MediaCodecVideoEncoderErrorCallback errorCallback) {
      Logging.d("MediaCodecVideoEncoder", "Set error callback");
      MediaCodecVideoEncoder.errorCallback = errorCallback;
   }

   public static void disableVp8HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "VP8 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/x-vnd.on2.vp8");
   }

   public static void disableVp9HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "VP9 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/x-vnd.on2.vp9");
   }

   public static void disableH264HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "H.264 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/avc");
   }

   public static boolean isVp8HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp8") && findHwEncoder("video/x-vnd.on2.vp8", vp8HwList(), supportedColorList) != null;
   }

   public static MediaCodecVideoEncoder.EncoderProperties vp8HwEncoderProperties() {
      return hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp8") ? null : findHwEncoder("video/x-vnd.on2.vp8", vp8HwList(), supportedColorList);
   }

   public static boolean isVp9HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp9") && findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, supportedColorList) != null;
   }

   public static boolean isH264HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/avc") && findHwEncoder("video/avc", h264HwList, supportedColorList) != null;
   }

   public static boolean isVp8HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp8") && findHwEncoder("video/x-vnd.on2.vp8", vp8HwList(), supportedSurfaceColorList) != null;
   }

   public static boolean isVp9HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp9") && findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, supportedSurfaceColorList) != null;
   }

   public static boolean isH264HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/avc") && findHwEncoder("video/avc", h264HwList, supportedSurfaceColorList) != null;
   }

   private static MediaCodecVideoEncoder.EncoderProperties findHwEncoder(String mime, MediaCodecVideoEncoder.MediaCodecProperties[] supportedHwCodecProperties, int[] colorList) {
      if (VERSION.SDK_INT < 19) {
         return null;
      } else {
         for(int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
            MediaCodecInfo info = null;

            try {
               info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException var17) {
               Logging.e("MediaCodecVideoEncoder", "Cannot retrieve encoder codec info", var17);
            }

            if (info != null && info.isEncoder()) {
               String name = null;
               String[] var6 = info.getSupportedTypes();
               int var7 = var6.length;

               for(int var8 = 0; var8 < var7; ++var8) {
                  String mimeType = var6[var8];
                  if (mimeType.equals(mime)) {
                     name = info.getName();
                     break;
                  }
               }

               if (name != null) {
                  Logging.v("MediaCodecVideoEncoder", "Found candidate encoder " + name);
                  boolean supportedCodec = false;
                  MediaCodecVideoEncoder.BitrateAdjustmentType bitrateAdjustmentType = MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT;
                  MediaCodecVideoEncoder.MediaCodecProperties[] var21 = supportedHwCodecProperties;
                  int var23 = supportedHwCodecProperties.length;

                  int var10;
                  for(var10 = 0; var10 < var23; ++var10) {
                     MediaCodecVideoEncoder.MediaCodecProperties codecProperties = var21[var10];
                     if (name.startsWith(codecProperties.codecPrefix)) {
                        if (VERSION.SDK_INT >= codecProperties.minSdk) {
                           if (codecProperties.bitrateAdjustmentType != MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT) {
                              bitrateAdjustmentType = codecProperties.bitrateAdjustmentType;
                              Logging.w("MediaCodecVideoEncoder", "Codec " + name + " requires bitrate adjustment: " + bitrateAdjustmentType);
                           }

                           supportedCodec = true;
                           break;
                        }

                        Logging.w("MediaCodecVideoEncoder", "Codec " + name + " is disabled due to SDK version " + VERSION.SDK_INT);
                     }
                  }

                  if (!supportedCodec) {
                     bitrateAdjustmentType = MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT;
                  }

                  CodecCapabilities capabilities;
                  try {
                     capabilities = info.getCapabilitiesForType(mime);
                  } catch (IllegalArgumentException var18) {
                     Logging.e("MediaCodecVideoEncoder", "Cannot retrieve encoder capabilities", var18);
                     continue;
                  }

                  int[] var24 = capabilities.colorFormats;
                  var10 = var24.length;

                  int supportedColorFormat;
                  int var25;
                  for(var25 = 0; var25 < var10; ++var25) {
                     supportedColorFormat = var24[var25];
                     Logging.v("MediaCodecVideoEncoder", "   Color: 0x" + Integer.toHexString(supportedColorFormat));
                  }

                  var24 = colorList;
                  var10 = colorList.length;

                  for(var25 = 0; var25 < var10; ++var25) {
                     supportedColorFormat = var24[var25];
                     int[] var13 = capabilities.colorFormats;
                     int var14 = var13.length;

                     for(int var15 = 0; var15 < var14; ++var15) {
                        int codecColorFormat = var13[var15];
                        if (codecColorFormat == supportedColorFormat) {
                           Logging.d("MediaCodecVideoEncoder", "Found target encoder for mime " + mime + " : " + name + ". Color: 0x" + Integer.toHexString(codecColorFormat) + ". Bitrate adjustment: " + bitrateAdjustmentType);
                           return new MediaCodecVideoEncoder.EncoderProperties(name, codecColorFormat, bitrateAdjustmentType);
                        }
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private void checkOnMediaCodecThread() {
      if (this.mediaCodecThread.getId() != Thread.currentThread().getId()) {
         throw new RuntimeException("MediaCodecVideoEncoder previously operated on " + this.mediaCodecThread + " but is now called on " + Thread.currentThread());
      }
   }

   public static void printStackTrace() {
      if (runningInstance != null && runningInstance.mediaCodecThread != null) {
         StackTraceElement[] mediaCodecStackTraces = runningInstance.mediaCodecThread.getStackTrace();
         if (mediaCodecStackTraces.length > 0) {
            Logging.d("MediaCodecVideoEncoder", "MediaCodecVideoEncoder stacks trace:");
            StackTraceElement[] var1 = mediaCodecStackTraces;
            int var2 = mediaCodecStackTraces.length;

            for(int var3 = 0; var3 < var2; ++var3) {
               StackTraceElement stackTrace = var1[var3];
               Logging.d("MediaCodecVideoEncoder", stackTrace.toString());
            }
         }
      }

   }

   static MediaCodec createByCodecName(String codecName) {
      try {
         return MediaCodec.createByCodecName(codecName);
      } catch (Exception var2) {
         return null;
      }
   }

   boolean initEncode(MediaCodecVideoEncoder.VideoCodecType type, int width, int height, int kbps, int fps, EglBase14.Context sharedContext) {
      boolean useSurface = sharedContext != null;
      Logging.d("MediaCodecVideoEncoder", "Java initEncode: " + type + " : " + width + " x " + height + ". @ " + kbps + " kbps. Fps: " + fps + ". Encode from texture : " + useSurface);
      this.width = width;
      this.height = height;
      if (this.mediaCodecThread != null) {
         throw new RuntimeException("Forgot to release()?");
      } else {
         MediaCodecVideoEncoder.EncoderProperties properties = null;
         String mime = null;
         int keyFrameIntervalSec = 0;
         if (type == MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP8) {
            mime = "video/x-vnd.on2.vp8";
            properties = findHwEncoder("video/x-vnd.on2.vp8", vp8HwList(), useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 100;
         } else if (type == MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP9) {
            mime = "video/x-vnd.on2.vp9";
            properties = findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 100;
         } else if (type == MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_H264) {
            mime = "video/avc";
            properties = findHwEncoder("video/avc", h264HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 20;
         }

         if (properties == null) {
            throw new RuntimeException("Can not find HW encoder for " + type);
         } else {
            runningInstance = this;
            this.colorFormat = properties.colorFormat;
            this.bitrateAdjustmentType = properties.bitrateAdjustmentType;
            if (this.bitrateAdjustmentType == MediaCodecVideoEncoder.BitrateAdjustmentType.FRAMERATE_ADJUSTMENT) {
               fps = 30;
            } else {
               fps = Math.min(fps, 30);
            }

            this.forcedKeyFrameMs = 0L;
            this.lastKeyFrameMs = -1L;
            if (type == MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_VP8 && properties.codecName.startsWith(qcomVp8HwProperties.codecPrefix)) {
               if (VERSION.SDK_INT == 23) {
                  this.forcedKeyFrameMs = 25000L;
               } else if (VERSION.SDK_INT > 23) {
                  this.forcedKeyFrameMs = 15000L;
               }
            }

            Logging.d("MediaCodecVideoEncoder", "Color format: " + this.colorFormat + ". Bitrate adjustment: " + this.bitrateAdjustmentType + ". Key frame interval: " + this.forcedKeyFrameMs + " . Initial fps: " + fps);
            this.targetBitrateBps = 1000 * kbps;
            this.targetFps = fps;
            this.bitrateAccumulatorMax = (double)this.targetBitrateBps / 8.0D;
            this.bitrateAccumulator = 0.0D;
            this.bitrateObservationTimeMs = 0.0D;
            this.bitrateAdjustmentScaleExp = 0;
            this.mediaCodecThread = Thread.currentThread();

            try {
               MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
               format.setInteger("bitrate", this.targetBitrateBps);
               format.setInteger("bitrate-mode", 2);
               format.setInteger("color-format", properties.colorFormat);
               format.setInteger("frame-rate", this.targetFps);
               format.setInteger("i-frame-interval", keyFrameIntervalSec);
               Logging.d("MediaCodecVideoEncoder", "  Format: " + format);
               this.mediaCodec = createByCodecName(properties.codecName);
               this.type = type;
               if (this.mediaCodec == null) {
                  Logging.e("MediaCodecVideoEncoder", "Can not create media encoder");
                  this.release();
                  return false;
               } else {
                  this.mediaCodec.configure(format, (Surface)null, (MediaCrypto)null, 1);
                  if (useSurface) {
                     this.eglBase = new EglBase14(sharedContext, EglBase.CONFIG_RECORDABLE);
                     this.inputSurface = this.mediaCodec.createInputSurface();
                     this.eglBase.createSurface(this.inputSurface);
                     this.drawer = new GlRectDrawer();
                  }

                  this.mediaCodec.start();
                  this.outputBuffers = this.mediaCodec.getOutputBuffers();
                  Logging.d("MediaCodecVideoEncoder", "Output buffers: " + this.outputBuffers.length);
                  return true;
               }
            } catch (IllegalStateException var12) {
               Logging.e("MediaCodecVideoEncoder", "initEncode failed", var12);
               this.release();
               return false;
            }
         }
      }
   }

   ByteBuffer[] getInputBuffers() {
      ByteBuffer[] inputBuffers = this.mediaCodec.getInputBuffers();
      Logging.d("MediaCodecVideoEncoder", "Input buffers: " + inputBuffers.length);
      return inputBuffers;
   }

   void checkKeyFrameRequired(boolean requestedKeyFrame, long presentationTimestampUs) {
      long presentationTimestampMs = (presentationTimestampUs + 500L) / 1000L;
      if (this.lastKeyFrameMs < 0L) {
         this.lastKeyFrameMs = presentationTimestampMs;
      }

      boolean forcedKeyFrame = false;
      if (!requestedKeyFrame && this.forcedKeyFrameMs > 0L && presentationTimestampMs > this.lastKeyFrameMs + this.forcedKeyFrameMs) {
         forcedKeyFrame = true;
      }

      if (requestedKeyFrame || forcedKeyFrame) {
         if (requestedKeyFrame) {
            Logging.d("MediaCodecVideoEncoder", "Sync frame request");
         } else {
            Logging.d("MediaCodecVideoEncoder", "Sync frame forced");
         }

         Bundle b = new Bundle();
         b.putInt("request-sync", 0);
         this.mediaCodec.setParameters(b);
         this.lastKeyFrameMs = presentationTimestampMs;
      }

   }

   boolean encodeBuffer(boolean isKeyframe, int inputBuffer, int size, long presentationTimestampUs) {
      this.checkOnMediaCodecThread();

      try {
         this.checkKeyFrameRequired(isKeyframe, presentationTimestampUs);
         this.mediaCodec.queueInputBuffer(inputBuffer, 0, size, presentationTimestampUs, 0);
         return true;
      } catch (IllegalStateException var7) {
         Logging.e("MediaCodecVideoEncoder", "encodeBuffer failed", var7);
         return false;
      }
   }

   boolean encodeTexture(boolean isKeyframe, int oesTextureId, float[] transformationMatrix, long presentationTimestampUs) {
      this.checkOnMediaCodecThread();

      try {
         this.checkKeyFrameRequired(isKeyframe, presentationTimestampUs);
         this.eglBase.makeCurrent();
         GLES20.glClear(16384);
         this.drawer.drawOes(oesTextureId, transformationMatrix, this.width, this.height, 0, 0, this.width, this.height);
         this.eglBase.swapBuffers(TimeUnit.MICROSECONDS.toNanos(presentationTimestampUs));
         return true;
      } catch (RuntimeException var7) {
         Logging.e("MediaCodecVideoEncoder", "encodeTexture failed", var7);
         return false;
      }
   }

   void release() {
      Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder");
      this.checkOnMediaCodecThread();

      class CaughtException {
         Exception e;
      }

      final CaughtException caughtException = new CaughtException();
      boolean stopHung = false;
      if (this.mediaCodec != null) {
         final CountDownLatch releaseDone = new CountDownLatch(1);
         Runnable runMediaCodecRelease = new Runnable() {
            public void run() {
               Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder on release thread");

               try {
                  MediaCodecVideoEncoder.this.mediaCodec.stop();
               } catch (Exception var3) {
                  Logging.e("MediaCodecVideoEncoder", "Media encoder stop failed", var3);
               }

               try {
                  MediaCodecVideoEncoder.this.mediaCodec.release();
               } catch (Exception var2) {
                  Logging.e("MediaCodecVideoEncoder", "Media encoder release failed", var2);
                  caughtException.e = var2;
               }

               Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder on release thread done");
               releaseDone.countDown();
            }
         };
         (new Thread(runMediaCodecRelease)).start();
         if (!ThreadUtils.awaitUninterruptibly(releaseDone, 5000L)) {
            Logging.e("MediaCodecVideoEncoder", "Media encoder release timeout");
            stopHung = true;
         }

         this.mediaCodec = null;
      }

      this.mediaCodecThread = null;
      if (this.drawer != null) {
         this.drawer.release();
         this.drawer = null;
      }

      if (this.eglBase != null) {
         this.eglBase.release();
         this.eglBase = null;
      }

      if (this.inputSurface != null) {
         this.inputSurface.release();
         this.inputSurface = null;
      }

      runningInstance = null;
      if (stopHung) {
         ++codecErrors;
         if (errorCallback != null) {
            Logging.e("MediaCodecVideoEncoder", "Invoke codec error callback. Errors: " + codecErrors);
            errorCallback.onMediaCodecVideoEncoderCriticalError(codecErrors);
         }

         throw new RuntimeException("Media encoder release timeout.");
      } else if (caughtException.e != null) {
         RuntimeException runtimeException = new RuntimeException(caughtException.e);
         runtimeException.setStackTrace(ThreadUtils.concatStackTraces(caughtException.e.getStackTrace(), runtimeException.getStackTrace()));
         throw runtimeException;
      } else {
         Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder done");
      }
   }

   private boolean setRates(int kbps, int frameRate) {
      this.checkOnMediaCodecThread();
      int codecBitrateBps = 1000 * kbps;
      if (this.bitrateAdjustmentType == MediaCodecVideoEncoder.BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         this.bitrateAccumulatorMax = (double)codecBitrateBps / 8.0D;
         if (this.targetBitrateBps > 0 && codecBitrateBps < this.targetBitrateBps) {
            this.bitrateAccumulator = this.bitrateAccumulator * (double)codecBitrateBps / (double)this.targetBitrateBps;
         }
      }

      this.targetBitrateBps = codecBitrateBps;
      this.targetFps = frameRate;
      if (this.bitrateAdjustmentType == MediaCodecVideoEncoder.BitrateAdjustmentType.FRAMERATE_ADJUSTMENT && this.targetFps > 0) {
         codecBitrateBps = 30 * this.targetBitrateBps / this.targetFps;
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " -> " + codecBitrateBps / 1000 + " kbps. Fps: " + this.targetFps);
      } else if (this.bitrateAdjustmentType == MediaCodecVideoEncoder.BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " kbps. Fps: " + this.targetFps + ". ExpScale: " + this.bitrateAdjustmentScaleExp);
         if (this.bitrateAdjustmentScaleExp != 0) {
            codecBitrateBps = (int)((double)codecBitrateBps * this.getBitrateScale(this.bitrateAdjustmentScaleExp));
         }
      } else {
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " kbps. Fps: " + this.targetFps);
      }

      try {
         Bundle params = new Bundle();
         params.putInt("video-bitrate", codecBitrateBps);
         this.mediaCodec.setParameters(params);
         return true;
      } catch (IllegalStateException var5) {
         Logging.e("MediaCodecVideoEncoder", "setRates failed", var5);
         return false;
      }
   }

   int dequeueInputBuffer() {
      this.checkOnMediaCodecThread();

      try {
         return this.mediaCodec.dequeueInputBuffer(0L);
      } catch (IllegalStateException var2) {
         Logging.e("MediaCodecVideoEncoder", "dequeueIntputBuffer failed", var2);
         return -2;
      }
   }

   MediaCodecVideoEncoder.OutputBufferInfo dequeueOutputBuffer() {
      this.checkOnMediaCodecThread();

      try {
         BufferInfo info = new BufferInfo();
         int result = this.mediaCodec.dequeueOutputBuffer(info, 0L);
         if (result >= 0) {
            boolean isConfigFrame = (info.flags & 2) != 0;
            if (isConfigFrame) {
               Logging.d("MediaCodecVideoEncoder", "Config frame generated. Offset: " + info.offset + ". Size: " + info.size);
               this.configData = ByteBuffer.allocateDirect(info.size);
               this.outputBuffers[result].position(info.offset);
               this.outputBuffers[result].limit(info.offset + info.size);
               this.configData.put(this.outputBuffers[result]);
               this.mediaCodec.releaseOutputBuffer(result, false);
               result = this.mediaCodec.dequeueOutputBuffer(info, 0L);
            }
         }

         if (result >= 0) {
            ByteBuffer outputBuffer = this.outputBuffers[result].duplicate();
            outputBuffer.position(info.offset);
            outputBuffer.limit(info.offset + info.size);
            this.reportEncodedFrame(info.size);
            boolean isKeyFrame = (info.flags & 1) != 0;
            if (isKeyFrame) {
               Logging.d("MediaCodecVideoEncoder", "Sync frame generated");
            }

            if (isKeyFrame && this.type == MediaCodecVideoEncoder.VideoCodecType.VIDEO_CODEC_H264) {
               Logging.d("MediaCodecVideoEncoder", "Appending config frame of size " + this.configData.capacity() + " to output buffer with offset " + info.offset + ", size " + info.size);
               ByteBuffer keyFrameBuffer = ByteBuffer.allocateDirect(this.configData.capacity() + info.size);
               this.configData.rewind();
               keyFrameBuffer.put(this.configData);
               keyFrameBuffer.put(outputBuffer);
               keyFrameBuffer.position(0);
               return new MediaCodecVideoEncoder.OutputBufferInfo(result, keyFrameBuffer, isKeyFrame, info.presentationTimeUs);
            } else {
               return new MediaCodecVideoEncoder.OutputBufferInfo(result, outputBuffer.slice(), isKeyFrame, info.presentationTimeUs);
            }
         } else if (result == -3) {
            this.outputBuffers = this.mediaCodec.getOutputBuffers();
            return this.dequeueOutputBuffer();
         } else if (result == -2) {
            return this.dequeueOutputBuffer();
         } else if (result == -1) {
            return null;
         } else {
            throw new RuntimeException("dequeueOutputBuffer: " + result);
         }
      } catch (IllegalStateException var6) {
         Logging.e("MediaCodecVideoEncoder", "dequeueOutputBuffer failed", var6);
         return new MediaCodecVideoEncoder.OutputBufferInfo(-1, (ByteBuffer)null, false, -1L);
      }
   }

   private double getBitrateScale(int bitrateAdjustmentScaleExp) {
      return Math.pow(4.0D, (double)bitrateAdjustmentScaleExp / 20.0D);
   }

   private void reportEncodedFrame(int size) {
      if (this.targetFps != 0 && this.bitrateAdjustmentType == MediaCodecVideoEncoder.BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         double expectedBytesPerFrame = (double)this.targetBitrateBps / (8.0D * (double)this.targetFps);
         this.bitrateAccumulator += (double)size - expectedBytesPerFrame;
         this.bitrateObservationTimeMs += 1000.0D / (double)this.targetFps;
         double bitrateAccumulatorCap = 3.0D * this.bitrateAccumulatorMax;
         this.bitrateAccumulator = Math.min(this.bitrateAccumulator, bitrateAccumulatorCap);
         this.bitrateAccumulator = Math.max(this.bitrateAccumulator, -bitrateAccumulatorCap);
         if (this.bitrateObservationTimeMs > 3000.0D) {
            Logging.d("MediaCodecVideoEncoder", "Acc: " + (int)this.bitrateAccumulator + ". Max: " + (int)this.bitrateAccumulatorMax + ". ExpScale: " + this.bitrateAdjustmentScaleExp);
            boolean bitrateAdjustmentScaleChanged = false;
            int bitrateAdjustmentInc;
            if (this.bitrateAccumulator > this.bitrateAccumulatorMax) {
               bitrateAdjustmentInc = (int)(this.bitrateAccumulator / this.bitrateAccumulatorMax + 0.5D);
               this.bitrateAdjustmentScaleExp -= bitrateAdjustmentInc;
               this.bitrateAccumulator = this.bitrateAccumulatorMax;
               bitrateAdjustmentScaleChanged = true;
            } else if (this.bitrateAccumulator < -this.bitrateAccumulatorMax) {
               bitrateAdjustmentInc = (int)(-this.bitrateAccumulator / this.bitrateAccumulatorMax + 0.5D);
               this.bitrateAdjustmentScaleExp += bitrateAdjustmentInc;
               this.bitrateAccumulator = -this.bitrateAccumulatorMax;
               bitrateAdjustmentScaleChanged = true;
            }

            if (bitrateAdjustmentScaleChanged) {
               this.bitrateAdjustmentScaleExp = Math.min(this.bitrateAdjustmentScaleExp, 20);
               this.bitrateAdjustmentScaleExp = Math.max(this.bitrateAdjustmentScaleExp, -20);
               Logging.d("MediaCodecVideoEncoder", "Adjusting bitrate scale to " + this.bitrateAdjustmentScaleExp + ". Value: " + this.getBitrateScale(this.bitrateAdjustmentScaleExp));
               this.setRates(this.targetBitrateBps / 1000, this.targetFps);
            }

            this.bitrateObservationTimeMs = 0.0D;
         }

      }
   }

   boolean releaseOutputBuffer(int index) {
      this.checkOnMediaCodecThread();

      try {
         this.mediaCodec.releaseOutputBuffer(index, false);
         return true;
      } catch (IllegalStateException var3) {
         Logging.e("MediaCodecVideoEncoder", "releaseOutputBuffer failed", var3);
         return false;
      }
   }

   static {
      qcomVp8HwProperties = new MediaCodecVideoEncoder.MediaCodecProperties("OMX.qcom.", 19, MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosVp8HwProperties = new MediaCodecVideoEncoder.MediaCodecProperties("OMX.Exynos.", 23, MediaCodecVideoEncoder.BitrateAdjustmentType.DYNAMIC_ADJUSTMENT);
      intelVp8HwProperties = new MediaCodecVideoEncoder.MediaCodecProperties("OMX.Intel.", 21, MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT);
      qcomVp9HwProperties = new MediaCodecVideoEncoder.MediaCodecProperties("OMX.qcom.", 23, MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosVp9HwProperties = new MediaCodecVideoEncoder.MediaCodecProperties("OMX.Exynos.", 23, MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT);
      vp9HwList = new MediaCodecVideoEncoder.MediaCodecProperties[]{qcomVp9HwProperties, exynosVp9HwProperties};
      qcomH264HwProperties = new MediaCodecVideoEncoder.MediaCodecProperties("OMX.qcom.", 19, MediaCodecVideoEncoder.BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosH264HwProperties = new MediaCodecVideoEncoder.MediaCodecProperties("OMX.Exynos.", 21, MediaCodecVideoEncoder.BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
      h264HwList = new MediaCodecVideoEncoder.MediaCodecProperties[]{qcomH264HwProperties, exynosH264HwProperties};
      H264_HW_EXCEPTION_MODELS = new String[]{"SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4"};
      supportedColorList = new int[]{19, 21, 2141391872, 2141391876};
      supportedSurfaceColorList = new int[]{2130708361};
   }

   static class OutputBufferInfo {
      public final int index;
      public final ByteBuffer buffer;
      public final boolean isKeyFrame;
      public final long presentationTimestampUs;

      public OutputBufferInfo(int index, ByteBuffer buffer, boolean isKeyFrame, long presentationTimestampUs) {
         this.index = index;
         this.buffer = buffer;
         this.isKeyFrame = isKeyFrame;
         this.presentationTimestampUs = presentationTimestampUs;
      }
   }

   public static class EncoderProperties {
      public final String codecName;
      public final int colorFormat;
      public final MediaCodecVideoEncoder.BitrateAdjustmentType bitrateAdjustmentType;

      public EncoderProperties(String codecName, int colorFormat, MediaCodecVideoEncoder.BitrateAdjustmentType bitrateAdjustmentType) {
         this.codecName = codecName;
         this.colorFormat = colorFormat;
         this.bitrateAdjustmentType = bitrateAdjustmentType;
      }
   }

   public interface MediaCodecVideoEncoderErrorCallback {
      void onMediaCodecVideoEncoderCriticalError(int var1);
   }

   private static class MediaCodecProperties {
      public final String codecPrefix;
      public final int minSdk;
      public final MediaCodecVideoEncoder.BitrateAdjustmentType bitrateAdjustmentType;

      MediaCodecProperties(String codecPrefix, int minSdk, MediaCodecVideoEncoder.BitrateAdjustmentType bitrateAdjustmentType) {
         this.codecPrefix = codecPrefix;
         this.minSdk = minSdk;
         this.bitrateAdjustmentType = bitrateAdjustmentType;
      }
   }

   public static enum BitrateAdjustmentType {
      NO_ADJUSTMENT,
      FRAMERATE_ADJUSTMENT,
      DYNAMIC_ADJUSTMENT;
   }

   public static enum VideoCodecType {
      VIDEO_CODEC_VP8,
      VIDEO_CODEC_VP9,
      VIDEO_CODEC_H264;
   }
}
