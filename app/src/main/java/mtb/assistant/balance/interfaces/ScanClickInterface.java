package mtb.assistant.balance.interfaces;

/**
 * This class is to react click event between fragment and activity.
 */
public interface ScanClickInterface {
  /**
   * This function will be triggered when the start/stop scanning button is clicked.
   *
   * @param started The status of scanning
   */
  void onScanTriggered(boolean started);
}
