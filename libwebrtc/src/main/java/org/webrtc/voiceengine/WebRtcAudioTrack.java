package org.webrtc.voiceengine;

import android.annotation.TargetApi;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioAttributes.Builder;
import android.os.Process;
import java.nio.ByteBuffer;
import org.webrtc.ContextUtils;
import org.webrtc.Logging;

public class WebRtcAudioTrack {
   private static final boolean DEBUG = false;
   private static final String TAG = "WebRtcAudioTrack";
   private static final int BITS_PER_SAMPLE = 16;
   private static final int CALLBACK_BUFFER_SIZE_MS = 10;
   private static final int BUFFERS_PER_SECOND = 100;
   private final long nativeAudioTrack;
   private final AudioManager audioManager;
   private ByteBuffer byteBuffer;
   private AudioTrack audioTrack = null;
   private WebRtcAudioTrack.AudioTrackThread audioThread = null;
   private static volatile boolean speakerMute = false;
   private byte[] emptyBytes;
   private static WebRtcAudioTrack.WebRtcAudioTrackErrorCallback errorCallback = null;

   public static void setErrorCallback(WebRtcAudioTrack.WebRtcAudioTrackErrorCallback errorCallback) {
      Logging.d("WebRtcAudioTrack", "Set error callback");
      WebRtcAudioTrack.errorCallback = errorCallback;
   }

   WebRtcAudioTrack(long nativeAudioTrack) {
      Logging.d("WebRtcAudioTrack", "ctor" + WebRtcAudioUtils.getThreadInfo());
      this.nativeAudioTrack = nativeAudioTrack;
      this.audioManager = (AudioManager)ContextUtils.getApplicationContext().getSystemService("audio");
   }

   private boolean initPlayout(int sampleRate, int channels) {
      Logging.d("WebRtcAudioTrack", "initPlayout(sampleRate=" + sampleRate + ", channels=" + channels + ")");
      int bytesPerFrame = channels * 2;
      ByteBuffer var10001 = this.byteBuffer;
      this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (sampleRate / 100));
      Logging.d("WebRtcAudioTrack", "byteBuffer.capacity: " + this.byteBuffer.capacity());
      this.emptyBytes = new byte[this.byteBuffer.capacity()];
      this.nativeCacheDirectBufferAddress(this.byteBuffer, this.nativeAudioTrack);
      int channelConfig = this.channelCountToConfiguration(channels);
      int minBufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRate, channelConfig, 2);
      Logging.d("WebRtcAudioTrack", "AudioTrack.getMinBufferSize: " + minBufferSizeInBytes);
      if (minBufferSizeInBytes < this.byteBuffer.capacity()) {
         this.reportWebRtcAudioTrackInitError("AudioTrack.getMinBufferSize returns an invalid value.");
         return false;
      } else if (this.audioTrack != null) {
         this.reportWebRtcAudioTrackInitError("Conflict with existing AudioTrack.");
         return false;
      } else {
         try {
            if (WebRtcAudioUtils.runningOnLollipopOrHigher()) {
               this.audioTrack = this.createAudioTrackOnLollipopOrHigher(sampleRate, channelConfig, minBufferSizeInBytes);
            } else {
               this.audioTrack = new AudioTrack(0, sampleRate, channelConfig, 2, minBufferSizeInBytes, 1);
            }
         } catch (IllegalArgumentException var7) {
            this.reportWebRtcAudioTrackInitError(var7.getMessage());
            this.releaseAudioResources();
            return false;
         }

         if (this.audioTrack != null && this.audioTrack.getState() == 1) {
            this.logMainParameters();
            this.logMainParametersExtended();
            return true;
         } else {
            this.reportWebRtcAudioTrackInitError("Initialization of audio track failed.");
            this.releaseAudioResources();
            return false;
         }
      }
   }

   private boolean startPlayout() {
      Logging.d("WebRtcAudioTrack", "startPlayout");
      assertTrue(this.audioTrack != null);
      assertTrue(this.audioThread == null);
      if (this.audioTrack.getState() != 1) {
         this.reportWebRtcAudioTrackStartError("AudioTrack instance is not successfully initialized.");
         return false;
      } else {
         this.audioThread = new WebRtcAudioTrack.AudioTrackThread("AudioTrackJavaThread");
         this.audioThread.start();
         return true;
      }
   }

   private boolean stopPlayout() {
      Logging.d("WebRtcAudioTrack", "stopPlayout");
      assertTrue(this.audioThread != null);
      this.logUnderrunCount();
      this.audioThread.joinThread();
      this.audioThread = null;
      this.releaseAudioResources();
      return true;
   }

   private int getStreamMaxVolume() {
      Logging.d("WebRtcAudioTrack", "getStreamMaxVolume");
      assertTrue(this.audioManager != null);
      return this.audioManager.getStreamMaxVolume(0);
   }

   private boolean setStreamVolume(int volume) {
      Logging.d("WebRtcAudioTrack", "setStreamVolume(" + volume + ")");
      assertTrue(this.audioManager != null);
      if (this.isVolumeFixed()) {
         Logging.e("WebRtcAudioTrack", "The device implements a fixed volume policy.");
         return false;
      } else {
         this.audioManager.setStreamVolume(0, volume, 0);
         return true;
      }
   }

   private boolean isVolumeFixed() {
      return !WebRtcAudioUtils.runningOnLollipopOrHigher() ? false : this.audioManager.isVolumeFixed();
   }

   private int getStreamVolume() {
      Logging.d("WebRtcAudioTrack", "getStreamVolume");
      assertTrue(this.audioManager != null);
      return this.audioManager.getStreamVolume(0);
   }

   private void logMainParameters() {
      StringBuilder var10001 = (new StringBuilder()).append("AudioTrack: session ID: ").append(this.audioTrack.getAudioSessionId()).append(", channels: ").append(this.audioTrack.getChannelCount()).append(", sample rate: ").append(this.audioTrack.getSampleRate()).append(", max gain: ");
      AudioTrack var10002 = this.audioTrack;
      Logging.d("WebRtcAudioTrack", var10001.append(AudioTrack.getMaxVolume()).toString());
   }

   @TargetApi(21)
   private AudioTrack createAudioTrackOnLollipopOrHigher(int sampleRateInHz, int channelConfig, int bufferSizeInBytes) {
      Logging.d("WebRtcAudioTrack", "createAudioTrackOnLollipopOrHigher");
      int nativeOutputSampleRate = AudioTrack.getNativeOutputSampleRate(0);
      Logging.d("WebRtcAudioTrack", "nativeOutputSampleRate: " + nativeOutputSampleRate);
      if (sampleRateInHz != nativeOutputSampleRate) {
         Logging.w("WebRtcAudioTrack", "Unable to use fast mode since requested sample rate is not native");
      }

      return new AudioTrack((new Builder()).setUsage(2).setContentType(1).build(), (new android.media.AudioFormat.Builder()).setEncoding(2).setSampleRate(sampleRateInHz).setChannelMask(channelConfig).build(), bufferSizeInBytes, 1, 0);
   }

   @TargetApi(24)
   private void logMainParametersExtended() {
      if (WebRtcAudioUtils.runningOnMarshmallowOrHigher()) {
         Logging.d("WebRtcAudioTrack", "AudioTrack: buffer size in frames: " + this.audioTrack.getBufferSizeInFrames());
      }

      if (WebRtcAudioUtils.runningOnNougatOrHigher()) {
         Logging.d("WebRtcAudioTrack", "AudioTrack: buffer capacity in frames: " + this.audioTrack.getBufferCapacityInFrames());
      }

   }

   @TargetApi(24)
   private void logUnderrunCount() {
      if (WebRtcAudioUtils.runningOnNougatOrHigher()) {
         Logging.d("WebRtcAudioTrack", "underrun count: " + this.audioTrack.getUnderrunCount());
      }

   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   private int channelCountToConfiguration(int channels) {
      return channels == 1 ? 4 : 12;
   }

   private native void nativeCacheDirectBufferAddress(ByteBuffer var1, long var2);

   private native void nativeGetPlayoutData(int var1, long var2);

   public static void setSpeakerMute(boolean mute) {
      Logging.w("WebRtcAudioTrack", "setSpeakerMute(" + mute + ")");
      speakerMute = mute;
   }

   private void releaseAudioResources() {
      if (this.audioTrack != null) {
         this.audioTrack.release();
         this.audioTrack = null;
      }

   }

   private void reportWebRtcAudioTrackInitError(String errorMessage) {
      Logging.e("WebRtcAudioTrack", "Init error: " + errorMessage);
      if (errorCallback != null) {
         errorCallback.onWebRtcAudioTrackInitError(errorMessage);
      }

   }

   private void reportWebRtcAudioTrackStartError(String errorMessage) {
      Logging.e("WebRtcAudioTrack", "Start error: " + errorMessage);
      if (errorCallback != null) {
         errorCallback.onWebRtcAudioTrackStartError(errorMessage);
      }

   }

   private void reportWebRtcAudioTrackError(String errorMessage) {
      Logging.e("WebRtcAudioTrack", "Run-time playback error: " + errorMessage);
      if (errorCallback != null) {
         errorCallback.onWebRtcAudioTrackError(errorMessage);
      }

   }

   private class AudioTrackThread extends Thread {
      private volatile boolean keepAlive = true;

      public AudioTrackThread(String name) {
         super(name);
      }

      public void run() {
         Process.setThreadPriority(-19);
         Logging.d("WebRtcAudioTrack", "AudioTrackThread" + WebRtcAudioUtils.getThreadInfo());

         try {
            WebRtcAudioTrack.this.audioTrack.play();
            WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 3);
         } catch (IllegalStateException var4) {
            WebRtcAudioTrack.this.reportWebRtcAudioTrackStartError("AudioTrack.play failed: " + var4.getMessage());
            WebRtcAudioTrack.this.releaseAudioResources();
            return;
         }

         for(int sizeInBytes = WebRtcAudioTrack.this.byteBuffer.capacity(); this.keepAlive; WebRtcAudioTrack.this.byteBuffer.rewind()) {
            WebRtcAudioTrack.this.nativeGetPlayoutData(sizeInBytes, WebRtcAudioTrack.this.nativeAudioTrack);
            WebRtcAudioTrack.assertTrue(sizeInBytes <= WebRtcAudioTrack.this.byteBuffer.remaining());
            if (WebRtcAudioTrack.speakerMute) {
               WebRtcAudioTrack.this.byteBuffer.clear();
               WebRtcAudioTrack.this.byteBuffer.put(WebRtcAudioTrack.this.emptyBytes);
               WebRtcAudioTrack.this.byteBuffer.position(0);
            }

            //int bytesWritten = false;
            int bytesWrittenx;
            if (WebRtcAudioUtils.runningOnLollipopOrHigher()) {
               bytesWrittenx = this.writeOnLollipop(WebRtcAudioTrack.this.audioTrack, WebRtcAudioTrack.this.byteBuffer, sizeInBytes);
            } else {
               bytesWrittenx = this.writePreLollipop(WebRtcAudioTrack.this.audioTrack, WebRtcAudioTrack.this.byteBuffer, sizeInBytes);
            }

            if (bytesWrittenx != sizeInBytes) {
               Logging.e("WebRtcAudioTrack", "AudioTrack.write failed: " + bytesWrittenx);
               if (bytesWrittenx == -3) {
                  this.keepAlive = false;
                  WebRtcAudioTrack.this.reportWebRtcAudioTrackError("AudioTrack.write failed: " + bytesWrittenx);
               }
            }
         }

         try {
            WebRtcAudioTrack.this.audioTrack.stop();
         } catch (IllegalStateException var3) {
            Logging.e("WebRtcAudioTrack", "AudioTrack.stop failed: " + var3.getMessage());
         }

         WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 1);
         WebRtcAudioTrack.this.audioTrack.flush();
      }

      @TargetApi(21)
      private int writeOnLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
         return audioTrack.write(byteBuffer, sizeInBytes, 0);
      }

      private int writePreLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
         return audioTrack.write(byteBuffer.array(), byteBuffer.arrayOffset(), sizeInBytes);
      }

      public void joinThread() {
         this.keepAlive = false;

         while(this.isAlive()) {
            try {
               this.join();
            } catch (InterruptedException var2) {
            }
         }

      }
   }

   public interface WebRtcAudioTrackErrorCallback {
      void onWebRtcAudioTrackInitError(String var1);

      void onWebRtcAudioTrackStartError(String var1);

      void onWebRtcAudioTrackError(String var1);
   }
}
