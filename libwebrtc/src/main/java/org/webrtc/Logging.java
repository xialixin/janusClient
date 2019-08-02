package org.webrtc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Logging {
   private static final Logger fallbackLogger = createFallbackLogger();
   private static volatile boolean tracingEnabled;
   private static volatile boolean loggingEnabled;
   private static volatile Logging.NativeLibStatus nativeLibStatus;

   private static Logger createFallbackLogger() {
      Logger fallbackLogger = Logger.getLogger("org.webrtc.Logging");
      fallbackLogger.setLevel(Level.ALL);
      return fallbackLogger;
   }

   private static boolean loadNativeLibrary() {
      if (nativeLibStatus == Logging.NativeLibStatus.UNINITIALIZED) {
         try {
            System.loadLibrary("jingle_peerconnection_so");
            nativeLibStatus = Logging.NativeLibStatus.LOADED;
         } catch (UnsatisfiedLinkError var1) {
            nativeLibStatus = Logging.NativeLibStatus.FAILED;
            fallbackLogger.log(Level.WARNING, "Failed to load jingle_peerconnection_so: ", var1);
         }
      }

      return nativeLibStatus == Logging.NativeLibStatus.LOADED;
   }

   public static void enableLogThreads() {
      if (!loadNativeLibrary()) {
         fallbackLogger.log(Level.WARNING, "Cannot enable log thread because native lib not loaded.");
      } else {
         nativeEnableLogThreads();
      }
   }

   public static void enableLogTimeStamps() {
      if (!loadNativeLibrary()) {
         fallbackLogger.log(Level.WARNING, "Cannot enable log timestamps because native lib not loaded.");
      } else {
         nativeEnableLogTimeStamps();
      }
   }

   public static synchronized void enableTracing(String path, EnumSet<Logging.TraceLevel> levels) {
      if (!loadNativeLibrary()) {
         fallbackLogger.log(Level.WARNING, "Cannot enable tracing because native lib not loaded.");
      } else if (!tracingEnabled) {
         int nativeLevel = 0;

         Logging.TraceLevel level;
         for(Iterator var3 = levels.iterator(); var3.hasNext(); nativeLevel |= level.level) {
            level = (Logging.TraceLevel)var3.next();
         }

         nativeEnableTracing(path, nativeLevel);
         tracingEnabled = true;
      }
   }

   public static synchronized void enableLogToDebugOutput(Logging.Severity severity) {
      if (!loadNativeLibrary()) {
         fallbackLogger.log(Level.WARNING, "Cannot enable logging because native lib not loaded.");
      } else {
         nativeEnableLogToDebugOutput(severity.ordinal());
         loggingEnabled = true;
      }
   }

   public static void log(Logging.Severity severity, String tag, String message) {
      if (loggingEnabled) {
         nativeLog(severity.ordinal(), tag, message);
      } else {
         Level level;
         switch(severity) {
         case LS_ERROR:
            level = Level.SEVERE;
            break;
         case LS_WARNING:
            level = Level.WARNING;
            break;
         case LS_INFO:
            level = Level.INFO;
            break;
         default:
            level = Level.FINE;
         }

         fallbackLogger.log(level, tag + ": " + message);
      }
   }

   public static void d(String tag, String message) {
      log(Logging.Severity.LS_INFO, tag, message);
   }

   public static void e(String tag, String message) {
      log(Logging.Severity.LS_ERROR, tag, message);
   }

   public static void w(String tag, String message) {
      log(Logging.Severity.LS_WARNING, tag, message);
   }

   public static void e(String tag, String message, Throwable e) {
      log(Logging.Severity.LS_ERROR, tag, message);
      log(Logging.Severity.LS_ERROR, tag, e.toString());
      log(Logging.Severity.LS_ERROR, tag, getStackTraceString(e));
   }

   public static void w(String tag, String message, Throwable e) {
      log(Logging.Severity.LS_WARNING, tag, message);
      log(Logging.Severity.LS_WARNING, tag, e.toString());
      log(Logging.Severity.LS_WARNING, tag, getStackTraceString(e));
   }

   public static void v(String tag, String message) {
      log(Logging.Severity.LS_VERBOSE, tag, message);
   }

   private static String getStackTraceString(Throwable e) {
      if (e == null) {
         return "";
      } else {
         StringWriter sw = new StringWriter();
         PrintWriter pw = new PrintWriter(sw);
         e.printStackTrace(pw);
         return sw.toString();
      }
   }

   private static native void nativeEnableTracing(String var0, int var1);

   private static native void nativeEnableLogToDebugOutput(int var0);

   private static native void nativeEnableLogThreads();

   private static native void nativeEnableLogTimeStamps();

   private static native void nativeLog(int var0, String var1, String var2);

   static {
      nativeLibStatus = Logging.NativeLibStatus.UNINITIALIZED;
   }

   public static enum Severity {
      LS_SENSITIVE,
      LS_VERBOSE,
      LS_INFO,
      LS_WARNING,
      LS_ERROR,
      LS_NONE;
   }

   public static enum TraceLevel {
      TRACE_NONE(0),
      TRACE_STATEINFO(1),
      TRACE_WARNING(2),
      TRACE_ERROR(4),
      TRACE_CRITICAL(8),
      TRACE_APICALL(16),
      TRACE_DEFAULT(255),
      TRACE_MODULECALL(32),
      TRACE_MEMORY(256),
      TRACE_TIMER(512),
      TRACE_STREAM(1024),
      TRACE_DEBUG(2048),
      TRACE_INFO(4096),
      TRACE_TERSEINFO(8192),
      TRACE_ALL(65535);

      public final int level;

      private TraceLevel(int level) {
         this.level = level;
      }
   }

   private static enum NativeLibStatus {
      UNINITIALIZED,
      LOADED,
      FAILED;
   }
}
