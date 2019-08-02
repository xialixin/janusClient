package org.webrtc;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class RTCStats {
   private final long timestampUs;
   private final String type;
   private final String id;
   private final Map<String, Object> members;

   public RTCStats(long timestampUs, String type, String id, Map<String, Object> members) {
      this.timestampUs = timestampUs;
      this.type = type;
      this.id = id;
      this.members = members;
   }

   public double getTimestampUs() {
      return (double)this.timestampUs;
   }

   public String getType() {
      return this.type;
   }

   public String getId() {
      return this.id;
   }

   public Map<String, Object> getMembers() {
      return this.members;
   }

   public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("{ timestampUs: ").append(this.timestampUs).append(", type: ").append(this.type).append(", id: ").append(this.id);
      boolean first = true;
      Iterator var3 = this.members.entrySet().iterator();

      while(var3.hasNext()) {
         Entry<String, Object> entry = (Entry)var3.next();
         builder.append(", ").append((String)entry.getKey()).append(": ");
         appendValue(builder, entry.getValue());
      }

      builder.append(" }");
      return builder.toString();
   }

   private static void appendValue(StringBuilder builder, Object value) {
      if (value instanceof Object[]) {
         Object[] arrayValue = (Object[])((Object[])value);
         builder.append('[');

         for(int i = 0; i < arrayValue.length; ++i) {
            if (i != 0) {
               builder.append(", ");
            }

            appendValue(builder, arrayValue[i]);
         }

         builder.append(']');
      } else if (value instanceof String) {
         builder.append('"').append(value).append('"');
      } else {
         builder.append(value);
      }

   }
}
