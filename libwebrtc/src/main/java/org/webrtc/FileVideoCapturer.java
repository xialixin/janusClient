package org.webrtc;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class FileVideoCapturer implements VideoCapturer {
   private static final String TAG = "FileVideoCapturer";
   private final FileVideoCapturer.VideoReader videoReader;
   private VideoCapturer.CapturerObserver capturerObserver;
   private final Timer timer = new Timer();
   private final TimerTask tickTask = new TimerTask() {
      public void run() {
         FileVideoCapturer.this.tick();
      }
   };

   private int getFrameWidth() {
      return this.videoReader.getFrameWidth();
   }

   private int getFrameHeight() {
      return this.videoReader.getFrameHeight();
   }

   public FileVideoCapturer(String inputFile) throws IOException {
      try {
         this.videoReader = new FileVideoCapturer.VideoReaderY4M(inputFile);
      } catch (IOException var3) {
         Logging.d("FileVideoCapturer", "Could not open video file: " + inputFile);
         throw var3;
      }
   }

   private byte[] getNextFrame() {
      return this.videoReader.getNextFrame();
   }

   public void tick() {
      long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
      byte[] frameData = this.getNextFrame();
      this.capturerObserver.onByteBufferFrameCaptured(frameData, this.getFrameWidth(), this.getFrameHeight(), 0, captureTimeNs);
   }

   public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, VideoCapturer.CapturerObserver capturerObserver) {
      this.capturerObserver = capturerObserver;
   }

   public void startCapture(int width, int height, int framerate) {
      this.timer.schedule(this.tickTask, 0L, (long)(1000 / framerate));
   }

   public void stopCapture() throws InterruptedException {
      this.timer.cancel();
   }

   public void changeCaptureFormat(int width, int height, int framerate) {
   }

   public void dispose() {
      this.videoReader.close();
   }

   public boolean isScreencast() {
      return false;
   }

   public static native void nativeI420ToNV21(byte[] var0, int var1, int var2, byte[] var3);

   static {
      System.loadLibrary("jingle_peerconnection_so");
   }

   private static class VideoReaderY4M implements FileVideoCapturer.VideoReader {
      private static final String TAG = "VideoReaderY4M";
      private final int frameWidth;
      private final int frameHeight;
      private final int frameSize;
      private final long videoStart;
      private static final String Y4M_FRAME_DELIMETER = "FRAME";
      private final RandomAccessFile mediaFileStream;

      public int getFrameWidth() {
         return this.frameWidth;
      }

      public int getFrameHeight() {
         return this.frameHeight;
      }

      public VideoReaderY4M(String file) throws IOException {
         this.mediaFileStream = new RandomAccessFile(file, "r");
         StringBuilder builder = new StringBuilder();

         while(true) {
            int c = this.mediaFileStream.read();
            if (c == -1) {
               throw new RuntimeException("Found end of file before end of header for file: " + file);
            }

            if (c == 10) {
               this.videoStart = this.mediaFileStream.getFilePointer();
               String header = builder.toString();
               String[] headerTokens = header.split("[ ]");
               int w = 0;
               int h = 0;
               String colorSpace = "";
               String[] var8 = headerTokens;
               int var9 = headerTokens.length;

               for(int var10 = 0; var10 < var9; ++var10) {
                  String tok = var8[var10];
                  char cc = tok.charAt(0);
                  switch(cc) {
                  case 'C':
                     colorSpace = tok.substring(1);
                     break;
                  case 'H':
                     h = Integer.parseInt(tok.substring(1));
                     break;
                  case 'W':
                     w = Integer.parseInt(tok.substring(1));
                  }
               }

               Logging.d("VideoReaderY4M", "Color space: " + colorSpace);
               if (!colorSpace.equals("420") && !colorSpace.equals("420mpeg2")) {
                  throw new IllegalArgumentException("Does not support any other color space than I420 or I420mpeg2");
               }

               if (w % 2 != 1 && h % 2 != 1) {
                  this.frameWidth = w;
                  this.frameHeight = h;
                  this.frameSize = w * h * 3 / 2;
                  Logging.d("VideoReaderY4M", "frame dim: (" + w + ", " + h + ") frameSize: " + this.frameSize);
                  return;
               }

               throw new IllegalArgumentException("Does not support odd width or height");
            }

            builder.append((char)c);
         }
      }

      public byte[] getNextFrame() {
         byte[] frame = new byte[this.frameSize];

         try {
            byte[] frameDelim = new byte["FRAME".length() + 1];
            if (this.mediaFileStream.read(frameDelim) < frameDelim.length) {
               this.mediaFileStream.seek(this.videoStart);
               if (this.mediaFileStream.read(frameDelim) < frameDelim.length) {
                  throw new RuntimeException("Error looping video");
               }
            }

            String frameDelimStr = new String(frameDelim);
            if (!frameDelimStr.equals("FRAME\n")) {
               throw new RuntimeException("Frames should be delimited by FRAME plus newline, found delimter was: '" + frameDelimStr + "'");
            } else {
               this.mediaFileStream.readFully(frame);
               byte[] nv21Frame = new byte[this.frameSize];
               FileVideoCapturer.nativeI420ToNV21(frame, this.frameWidth, this.frameHeight, nv21Frame);
               return nv21Frame;
            }
         } catch (IOException var5) {
            throw new RuntimeException(var5);
         }
      }

      public void close() {
         try {
            this.mediaFileStream.close();
         } catch (IOException var2) {
            Logging.e("VideoReaderY4M", "Problem closing file", var2);
         }

      }
   }

   private interface VideoReader {
      int getFrameWidth();

      int getFrameHeight();

      byte[] getNextFrame();

      void close();
   }
}
