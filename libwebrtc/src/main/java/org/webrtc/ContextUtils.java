package org.webrtc;

import android.content.Context;

public class ContextUtils {
   private static final String TAG = "ContextUtils";
   private static Context applicationContext;

   public static void initialize(Context applicationContext) {
      if (ContextUtils.applicationContext != null) {
         Logging.e("ContextUtils", "Calling ContextUtils.initialize multiple times, this will crash in the future!");
      }

      if (applicationContext == null) {
         throw new RuntimeException("Application context cannot be null for ContextUtils.initialize.");
      } else {
         ContextUtils.applicationContext = applicationContext;
      }
   }

   public static Context getApplicationContext() {
      return applicationContext;
   }
}
