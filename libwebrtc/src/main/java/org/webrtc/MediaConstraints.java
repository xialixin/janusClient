package org.webrtc;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class MediaConstraints {
   public final List<MediaConstraints.KeyValuePair> mandatory = new LinkedList();
   public final List<MediaConstraints.KeyValuePair> optional = new LinkedList();

   private static String stringifyKeyValuePairList(List<MediaConstraints.KeyValuePair> list) {
      StringBuilder builder = new StringBuilder("[");

      MediaConstraints.KeyValuePair pair;
      for(Iterator var2 = list.iterator(); var2.hasNext(); builder.append(pair.toString())) {
         pair = (MediaConstraints.KeyValuePair)var2.next();
         if (builder.length() > 1) {
            builder.append(", ");
         }
      }

      return builder.append("]").toString();
   }

   public String toString() {
      return "mandatory: " + stringifyKeyValuePairList(this.mandatory) + ", optional: " + stringifyKeyValuePairList(this.optional);
   }

   public static class KeyValuePair {
      private final String key;
      private final String value;

      public KeyValuePair(String key, String value) {
         this.key = key;
         this.value = value;
      }

      public String getKey() {
         return this.key;
      }

      public String getValue() {
         return this.value;
      }

      public String toString() {
         return this.key + ": " + this.value;
      }

      public boolean equals(Object other) {
         if (this == other) {
            return true;
         } else if (other != null && this.getClass() == other.getClass()) {
            MediaConstraints.KeyValuePair that = (MediaConstraints.KeyValuePair)other;
            return this.key.equals(that.key) && this.value.equals(that.value);
         } else {
            return false;
         }
      }

      public int hashCode() {
         return this.key.hashCode() + this.value.hashCode();
      }
   }
}
