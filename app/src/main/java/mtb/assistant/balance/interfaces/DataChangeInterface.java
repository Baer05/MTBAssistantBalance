package mtb.assistant.balance.interfaces;

import com.xsens.dot.android.sdk.events.XsensDotData;

/**
 * This class is to react data changes event between fragment and view model.
 */
public interface DataChangeInterface {

    /**
     * This function will be triggered when data is changed.
     *
     * @param address The mac address of device
     * @param data    The XsensDotData packet
     */
    void onDataChanged(String address, XsensDotData data);
}
