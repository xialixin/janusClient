package org.webrtc;

public class DtmfSender {
   final long nativeDtmfSender;

   public DtmfSender(long nativeDtmfSender) {
      this.nativeDtmfSender = nativeDtmfSender;
   }

   public boolean canInsertDtmf() {
      return nativeCanInsertDtmf(this.nativeDtmfSender);
   }

   public boolean insertDtmf(String tones, int duration, int interToneGap) {
      return nativeInsertDtmf(this.nativeDtmfSender, tones, duration, interToneGap);
   }

   public String tones() {
      return nativeTones(this.nativeDtmfSender);
   }

   public int duration() {
      return nativeDuration(this.nativeDtmfSender);
   }

   public int interToneGap() {
      return nativeInterToneGap(this.nativeDtmfSender);
   }

   public void dispose() {
      free(this.nativeDtmfSender);
   }

   private static native boolean nativeCanInsertDtmf(long var0);

   private static native boolean nativeInsertDtmf(long var0, String var2, int var3, int var4);

   private static native String nativeTones(long var0);

   private static native int nativeDuration(long var0);

   private static native int nativeInterToneGap(long var0);

   private static native void free(long var0);
}
