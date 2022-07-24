package mtb.assistant.balance.utils;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

/**
 * This class is for some additional feature, such as: check Bluetooth adapter, check location premission...etc.
 */
public class Utils {
  private static final String[] BLE_PERMISSIONS = new String[]{
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.ACCESS_FINE_LOCATION,
  };

  private static final String[] ANDROID_12_BLE_PERMISSIONS = new String[]{
      Manifest.permission.BLUETOOTH_SCAN,
      Manifest.permission.BLUETOOTH_CONNECT,
      Manifest.permission.ACCESS_FINE_LOCATION,
  };

  /**
   * Check the Bluetooth adapter is enabled or not.
   *
   * @param context The application context
   * @return True - if the Bluetooth adapter is on
   */
  public static boolean isBluetoothAdapterEnabled(Context context) {
    BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    if (bluetoothManager != null) {
      BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
      if (bluetoothAdapter != null) return bluetoothAdapter.isEnabled();
    }
    return false;
  }

  /**
   * If the Bluetooth adapter is disabled, popup a system dialog for user to enable it.
   *
   * @param activity    The main activity
   * @param requestCode The request code for this intent
   */
  public static void requestEnableBluetooth(Activity activity, int requestCode) {
    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    activity.startActivityForResult(intent, requestCode);
  }


  public static boolean isBlePermissionGranted(Activity activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      return activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
          PackageManager.PERMISSION_GRANTED && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
          == PackageManager.PERMISSION_GRANTED;
    } else {
      return activity.checkSelfPermission(Manifest.permission.BLUETOOTH) ==
          PackageManager.PERMISSION_GRANTED && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN)
          == PackageManager.PERMISSION_GRANTED;
    }
  }

  public static void requestBlePermission(Activity activity, int requestCode) {
    Log.d("TEST", "ich bin hier1");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Log.d("TEST", "ich bin hier2");
      ActivityCompat.requestPermissions(activity, ANDROID_12_BLE_PERMISSIONS, requestCode);
    } else {
      Log.d("TEST", "ich bin hier3");
      ActivityCompat.requestPermissions(activity, BLE_PERMISSIONS, requestCode);
    }
  }

  /**
   * Above Android 6.0+, user have to  allow app to access location information then scan BLE device.
   *
   * @param activity The activity class
   * @return True - if the permission is granted
   */
  public static boolean isLocationPermissionGranted(Activity activity) {
    return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  /**
   * If the location permission isn't granted, popup a system dialog for user to enable it.
   *
   * @param activity    The main activity
   * @param requestCode The request code for this action
   */
  public static void requestLocationPermission(Activity activity, int requestCode) {
    activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode);
  }

  /**
   * Allow app to write to external storage.
   *
   * @param activity The activity class
   * @return True - if the permission is granted
   */
  public static boolean isWriteStoragePermissionGranted(Activity activity) {
    return activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED;
  }

  /**
   * If the write to external storage permission isn't granted, popup a system dialog for user to enable it.
   *
   * @param activity    The main activity
   * @param requestCode The request code for this action
   */
  public static void requestWriteExternalStoragePermission(Activity activity, int requestCode) {
    activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
  }
}
