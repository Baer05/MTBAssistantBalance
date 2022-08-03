package mtb.assistant.balance.viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

/**
 * A view model class for notifying data to views.
 */
public class BluetoothViewModel extends ViewModel {

  // A variable to notify the Bluetooth status
  private MutableLiveData<Boolean> mIsBluetoothEnabled = new MutableLiveData<>();
  // A variable to notify the scanning status
  private MutableLiveData<Boolean> mIsScanning = new MutableLiveData<>();

  /**
   * Get the instance of BluetoothViewModel
   *
   * @param owner The life cycle owner from activity/fragment
   * @return The BluetoothViewModel
   */
  public static BluetoothViewModel getInstance(@NonNull ViewModelStoreOwner owner) {
    return new ViewModelProvider(owner, new ViewModelProvider.NewInstanceFactory()).get(BluetoothViewModel.class);
  }

  /**
   * Observe this function to listen the status of Bluetooth adapter.
   *
   * @return The latest status
   */
  public MutableLiveData<Boolean> isBluetoothEnabled() {
    return mIsBluetoothEnabled;
  }

  /**
   * Notify the Bluetooth adapter status to activity/fragment
   *
   * @param enabled he status of Bluetooth
   */
  public void updateBluetoothEnableState(boolean enabled) {
    mIsBluetoothEnabled.postValue(enabled);
  }

  /**
   * Observe this function to listen the scanning status.
   *
   * @return The latest scan status
   */
  public MutableLiveData<Boolean> isScanning() {
    return mIsScanning;
  }

  /**
   * Notify the scan status to activity/fragment
   *
   * @param scanning The status of scanning
   */
  public void updateScanState(boolean scanning) {
    mIsScanning.postValue(scanning);
  }
}