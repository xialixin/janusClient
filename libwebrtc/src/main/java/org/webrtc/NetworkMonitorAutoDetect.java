package org.webrtc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkRequest.Builder;
import android.net.wifi.WifiInfo;
import android.os.Build.VERSION;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NetworkMonitorAutoDetect extends BroadcastReceiver {
   static final long INVALID_NET_ID = -1L;
   private static final String TAG = "NetworkMonitorAutoDetect";
   private final NetworkMonitorAutoDetect.Observer observer;
   private final IntentFilter intentFilter;
   private final Context context;
   private final NetworkCallback mobileNetworkCallback;
   private final NetworkCallback allNetworkCallback;
   private NetworkMonitorAutoDetect.ConnectivityManagerDelegate connectivityManagerDelegate;
   private NetworkMonitorAutoDetect.WifiManagerDelegate wifiManagerDelegate;
   private boolean isRegistered;
   private NetworkMonitorAutoDetect.ConnectionType connectionType;
   private String wifiSSID;

   @SuppressLint({"NewApi"})
   public NetworkMonitorAutoDetect(NetworkMonitorAutoDetect.Observer observer, Context context) {
      this.observer = observer;
      this.context = context;
      this.connectivityManagerDelegate = new NetworkMonitorAutoDetect.ConnectivityManagerDelegate(context);
      this.wifiManagerDelegate = new NetworkMonitorAutoDetect.WifiManagerDelegate(context);
      NetworkMonitorAutoDetect.NetworkState networkState = this.connectivityManagerDelegate.getNetworkState();
      this.connectionType = getConnectionType(networkState);
      this.wifiSSID = this.getWifiSSID(networkState);
      this.intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
      this.registerReceiver();
      if (this.connectivityManagerDelegate.supportNetworkCallback()) {
         NetworkCallback tempNetworkCallback = new NetworkCallback();

         try {
            this.connectivityManagerDelegate.requestMobileNetwork(tempNetworkCallback);
         } catch (SecurityException var6) {
            Logging.w("NetworkMonitorAutoDetect", "Unable to obtain permission to request a cellular network.");
            tempNetworkCallback = null;
         }

         this.mobileNetworkCallback = tempNetworkCallback;
         this.allNetworkCallback = new NetworkMonitorAutoDetect.SimpleNetworkCallback();
         this.connectivityManagerDelegate.registerNetworkCallback(this.allNetworkCallback);
      } else {
         this.mobileNetworkCallback = null;
         this.allNetworkCallback = null;
      }

   }

   public boolean supportNetworkCallback() {
      return this.connectivityManagerDelegate.supportNetworkCallback();
   }

   void setConnectivityManagerDelegateForTests(NetworkMonitorAutoDetect.ConnectivityManagerDelegate delegate) {
      this.connectivityManagerDelegate = delegate;
   }

   void setWifiManagerDelegateForTests(NetworkMonitorAutoDetect.WifiManagerDelegate delegate) {
      this.wifiManagerDelegate = delegate;
   }

   boolean isReceiverRegisteredForTesting() {
      return this.isRegistered;
   }

   List<NetworkMonitorAutoDetect.NetworkInformation> getActiveNetworkList() {
      return this.connectivityManagerDelegate.getActiveNetworkList();
   }

   public void destroy() {
      if (this.allNetworkCallback != null) {
         this.connectivityManagerDelegate.releaseCallback(this.allNetworkCallback);
      }

      if (this.mobileNetworkCallback != null) {
         this.connectivityManagerDelegate.releaseCallback(this.mobileNetworkCallback);
      }

      this.unregisterReceiver();
   }

   private void registerReceiver() {
      if (!this.isRegistered) {
         this.isRegistered = true;
         this.context.registerReceiver(this, this.intentFilter);
      }
   }

   private void unregisterReceiver() {
      if (this.isRegistered) {
         this.isRegistered = false;
         this.context.unregisterReceiver(this);
      }
   }

   public NetworkMonitorAutoDetect.NetworkState getCurrentNetworkState() {
      return this.connectivityManagerDelegate.getNetworkState();
   }

   public long getDefaultNetId() {
      return this.connectivityManagerDelegate.getDefaultNetId();
   }

   public static NetworkMonitorAutoDetect.ConnectionType getConnectionType(NetworkMonitorAutoDetect.NetworkState networkState) {
      if (!networkState.isConnected()) {
         return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_NONE;
      } else {
         switch(networkState.getNetworkType()) {
         case 0:
            switch(networkState.getNetworkSubType()) {
            case 1:
            case 2:
            case 4:
            case 7:
            case 11:
               return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_2G;
            case 3:
            case 5:
            case 6:
            case 8:
            case 9:
            case 10:
            case 12:
            case 14:
            case 15:
               return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_3G;
            case 13:
               return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_4G;
            default:
               return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_UNKNOWN_CELLULAR;
            }
         case 1:
            return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_WIFI;
         case 2:
         case 3:
         case 4:
         case 5:
         case 8:
         default:
            return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_UNKNOWN;
         case 6:
            return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_4G;
         case 7:
            return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_BLUETOOTH;
         case 9:
            return NetworkMonitorAutoDetect.ConnectionType.CONNECTION_ETHERNET;
         }
      }
   }

   private String getWifiSSID(NetworkMonitorAutoDetect.NetworkState networkState) {
      return getConnectionType(networkState) != NetworkMonitorAutoDetect.ConnectionType.CONNECTION_WIFI ? "" : this.wifiManagerDelegate.getWifiSSID();
   }

   public void onReceive(Context context, Intent intent) {
      NetworkMonitorAutoDetect.NetworkState networkState = this.getCurrentNetworkState();
      if ("android.net.conn.CONNECTIVITY_CHANGE".equals(intent.getAction())) {
         this.connectionTypeChanged(networkState);
      }

   }

   private void connectionTypeChanged(NetworkMonitorAutoDetect.NetworkState networkState) {
      NetworkMonitorAutoDetect.ConnectionType newConnectionType = getConnectionType(networkState);
      String newWifiSSID = this.getWifiSSID(networkState);
      if (newConnectionType != this.connectionType || !newWifiSSID.equals(this.wifiSSID)) {
         this.connectionType = newConnectionType;
         this.wifiSSID = newWifiSSID;
         Logging.d("NetworkMonitorAutoDetect", "Network connectivity changed, type is: " + this.connectionType);
         this.observer.onConnectionTypeChanged(newConnectionType);
      }
   }

   @SuppressLint({"NewApi"})
   private static long networkToNetId(Network network) {
      return VERSION.SDK_INT >= 23 ? network.getNetworkHandle() : (long)Integer.parseInt(network.toString());
   }

   public interface Observer {
      void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType var1);

      void onNetworkConnect(NetworkMonitorAutoDetect.NetworkInformation var1);

      void onNetworkDisconnect(long var1);
   }

   static class WifiManagerDelegate {
      private final Context context;

      WifiManagerDelegate(Context context) {
         this.context = context;
      }

      WifiManagerDelegate() {
         this.context = null;
      }

      String getWifiSSID() {
         Intent intent = this.context.registerReceiver((BroadcastReceiver)null, new IntentFilter("android.net.wifi.STATE_CHANGE"));
         if (intent != null) {
            WifiInfo wifiInfo = (WifiInfo)intent.getParcelableExtra("wifiInfo");
            if (wifiInfo != null) {
               String ssid = wifiInfo.getSSID();
               if (ssid != null) {
                  return ssid;
               }
            }
         }

         return "";
      }
   }

   static class ConnectivityManagerDelegate {
      private final ConnectivityManager connectivityManager;

      ConnectivityManagerDelegate(Context context) {
         this.connectivityManager = (ConnectivityManager)context.getSystemService("connectivity");
      }

      ConnectivityManagerDelegate() {
         this.connectivityManager = null;
      }

      NetworkMonitorAutoDetect.NetworkState getNetworkState() {
         return this.connectivityManager == null ? new NetworkMonitorAutoDetect.NetworkState(false, -1, -1) : this.getNetworkState(this.connectivityManager.getActiveNetworkInfo());
      }

      @SuppressLint({"NewApi"})
      NetworkMonitorAutoDetect.NetworkState getNetworkState(Network network) {
         return this.connectivityManager == null ? new NetworkMonitorAutoDetect.NetworkState(false, -1, -1) : this.getNetworkState(this.connectivityManager.getNetworkInfo(network));
      }

      NetworkMonitorAutoDetect.NetworkState getNetworkState(NetworkInfo networkInfo) {
         return networkInfo != null && networkInfo.isConnected() ? new NetworkMonitorAutoDetect.NetworkState(true, networkInfo.getType(), networkInfo.getSubtype()) : new NetworkMonitorAutoDetect.NetworkState(false, -1, -1);
      }

      @SuppressLint({"NewApi"})
      Network[] getAllNetworks() {
         return this.connectivityManager == null ? new Network[0] : this.connectivityManager.getAllNetworks();
      }

      List<NetworkMonitorAutoDetect.NetworkInformation> getActiveNetworkList() {
         if (!this.supportNetworkCallback()) {
            return null;
         } else {
            ArrayList<NetworkMonitorAutoDetect.NetworkInformation> netInfoList = new ArrayList();
            Network[] var2 = this.getAllNetworks();
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
               Network network = var2[var4];
               NetworkMonitorAutoDetect.NetworkInformation info = this.networkToInfo(network);
               if (info != null) {
                  netInfoList.add(info);
               }
            }

            return netInfoList;
         }
      }

      @SuppressLint({"NewApi"})
      long getDefaultNetId() {
         if (!this.supportNetworkCallback()) {
            return -1L;
         } else {
            NetworkInfo defaultNetworkInfo = this.connectivityManager.getActiveNetworkInfo();
            if (defaultNetworkInfo == null) {
               return -1L;
            } else {
               Network[] networks = this.getAllNetworks();
               long defaultNetId = -1L;
               Network[] var5 = networks;
               int var6 = networks.length;

               for(int var7 = 0; var7 < var6; ++var7) {
                  Network network = var5[var7];
                  if (this.hasInternetCapability(network)) {
                     NetworkInfo networkInfo = this.connectivityManager.getNetworkInfo(network);
                     if (networkInfo != null && networkInfo.getType() == defaultNetworkInfo.getType()) {
                        if (defaultNetId != -1L) {
                           throw new RuntimeException("Multiple connected networks of same type are not supported.");
                        }

                        defaultNetId = NetworkMonitorAutoDetect.networkToNetId(network);
                     }
                  }
               }

               return defaultNetId;
            }
         }
      }

      @SuppressLint({"NewApi"})
      private NetworkMonitorAutoDetect.NetworkInformation networkToInfo(Network network) {
         LinkProperties linkProperties = this.connectivityManager.getLinkProperties(network);
         if (linkProperties == null) {
            Logging.w("NetworkMonitorAutoDetect", "Detected unknown network: " + network.toString());
            return null;
         } else if (linkProperties.getInterfaceName() == null) {
            Logging.w("NetworkMonitorAutoDetect", "Null interface name for network " + network.toString());
            return null;
         } else {
            NetworkMonitorAutoDetect.NetworkState networkState = this.getNetworkState(network);
            if (networkState.connected && networkState.getNetworkType() == 17) {
               networkState = this.getNetworkState();
            }

            NetworkMonitorAutoDetect.ConnectionType connectionType = NetworkMonitorAutoDetect.getConnectionType(networkState);
            if (connectionType == NetworkMonitorAutoDetect.ConnectionType.CONNECTION_NONE) {
               Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " is disconnected");
               return null;
            } else {
               if (connectionType == NetworkMonitorAutoDetect.ConnectionType.CONNECTION_UNKNOWN || connectionType == NetworkMonitorAutoDetect.ConnectionType.CONNECTION_UNKNOWN_CELLULAR) {
                  Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " connection type is " + connectionType + " because it has type " + networkState.getNetworkType() + " and subtype " + networkState.getNetworkSubType());
               }

               NetworkMonitorAutoDetect.NetworkInformation networkInformation = new NetworkMonitorAutoDetect.NetworkInformation(linkProperties.getInterfaceName(), connectionType, NetworkMonitorAutoDetect.networkToNetId(network), this.getIPAddresses(linkProperties));
               return networkInformation;
            }
         }
      }

      @SuppressLint({"NewApi"})
      boolean hasInternetCapability(Network network) {
         if (this.connectivityManager == null) {
            return false;
         } else {
            NetworkCapabilities capabilities = this.connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(12);
         }
      }

      @SuppressLint({"NewApi"})
      public void registerNetworkCallback(NetworkCallback networkCallback) {
         this.connectivityManager.registerNetworkCallback((new Builder()).addCapability(12).build(), networkCallback);
      }

      @SuppressLint({"NewApi"})
      public void requestMobileNetwork(NetworkCallback networkCallback) {
         Builder builder = new Builder();
         builder.addCapability(12).addTransportType(0);
         this.connectivityManager.requestNetwork(builder.build(), networkCallback);
      }

      @SuppressLint({"NewApi"})
      NetworkMonitorAutoDetect.IPAddress[] getIPAddresses(LinkProperties linkProperties) {
         NetworkMonitorAutoDetect.IPAddress[] ipAddresses = new NetworkMonitorAutoDetect.IPAddress[linkProperties.getLinkAddresses().size()];
         int i = 0;

         for(Iterator var4 = linkProperties.getLinkAddresses().iterator(); var4.hasNext(); ++i) {
            LinkAddress linkAddress = (LinkAddress)var4.next();
            ipAddresses[i] = new NetworkMonitorAutoDetect.IPAddress(linkAddress.getAddress().getAddress());
         }

         return ipAddresses;
      }

      @SuppressLint({"NewApi"})
      public void releaseCallback(NetworkCallback networkCallback) {
         if (this.supportNetworkCallback()) {
            Logging.d("NetworkMonitorAutoDetect", "Unregister network callback");
            this.connectivityManager.unregisterNetworkCallback(networkCallback);
         }

      }

      public boolean supportNetworkCallback() {
         return VERSION.SDK_INT >= 21 && this.connectivityManager != null;
      }
   }

   @SuppressLint({"NewApi"})
   private class SimpleNetworkCallback extends NetworkCallback {
      private SimpleNetworkCallback() {
      }

      public void onAvailable(Network network) {
         Logging.d("NetworkMonitorAutoDetect", "Network becomes available: " + network.toString());
         this.onNetworkChanged(network);
      }

      public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
         Logging.d("NetworkMonitorAutoDetect", "capabilities changed: " + networkCapabilities.toString());
         this.onNetworkChanged(network);
      }

      public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
         Logging.d("NetworkMonitorAutoDetect", "link properties changed: " + linkProperties.toString());
         this.onNetworkChanged(network);
      }

      public void onLosing(Network network, int maxMsToLive) {
         Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " is about to lose in " + maxMsToLive + "ms");
      }

      public void onLost(Network network) {
         Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " is disconnected");
         NetworkMonitorAutoDetect.this.observer.onNetworkDisconnect(NetworkMonitorAutoDetect.networkToNetId(network));
      }

      private void onNetworkChanged(Network network) {
         NetworkMonitorAutoDetect.NetworkInformation networkInformation = NetworkMonitorAutoDetect.this.connectivityManagerDelegate.networkToInfo(network);
         if (networkInformation != null) {
            NetworkMonitorAutoDetect.this.observer.onNetworkConnect(networkInformation);
         }

      }

      // $FF: synthetic method
      SimpleNetworkCallback(Object x1) {
         this();
      }
   }

   static class NetworkState {
      private final boolean connected;
      private final int type;
      private final int subtype;

      public NetworkState(boolean connected, int type, int subtype) {
         this.connected = connected;
         this.type = type;
         this.subtype = subtype;
      }

      public boolean isConnected() {
         return this.connected;
      }

      public int getNetworkType() {
         return this.type;
      }

      public int getNetworkSubType() {
         return this.subtype;
      }
   }

   public static class NetworkInformation {
      public final String name;
      public final NetworkMonitorAutoDetect.ConnectionType type;
      public final long handle;
      public final NetworkMonitorAutoDetect.IPAddress[] ipAddresses;

      public NetworkInformation(String name, NetworkMonitorAutoDetect.ConnectionType type, long handle, NetworkMonitorAutoDetect.IPAddress[] addresses) {
         this.name = name;
         this.type = type;
         this.handle = handle;
         this.ipAddresses = addresses;
      }
   }

   public static class IPAddress {
      public final byte[] address;

      public IPAddress(byte[] address) {
         this.address = address;
      }
   }

   public static enum ConnectionType {
      CONNECTION_UNKNOWN,
      CONNECTION_ETHERNET,
      CONNECTION_WIFI,
      CONNECTION_4G,
      CONNECTION_3G,
      CONNECTION_2G,
      CONNECTION_UNKNOWN_CELLULAR,
      CONNECTION_BLUETOOTH,
      CONNECTION_NONE;
   }
}
