package mtb.assistant.balance.interfaces;

import android.view.View;

/**
 * This class is to react click event between fragment and adapter.
 */
public interface SensorClickInterface {
  /**
   * This function will be triggered when the item view is clicked.
   *
   * @param v        The item view
   * @param position The position of item view
   */
  void onSensorClick(View v, int position);
}
