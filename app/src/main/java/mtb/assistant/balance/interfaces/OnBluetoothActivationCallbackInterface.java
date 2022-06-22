package mtb.assistant.balance.interfaces;

public interface OnBluetoothActivationCallbackInterface {
 /**
  * Called when Bluetooth is ready.
  */
 void onBluetoothActivated();

 /**
  * Called when one of the steps for activating Bluetooth has been rejected.
  */
 void onBluetoothActivationRejected();

 /**
  * Called when one of the steps for activating Bluetooth has failed.
  */
 void onBluetoothActivationFailed();
}
