package mtb.assistant.balance.interfaces;

/**
 * This class is to react battery changes event between fragment and view model.
 */
public interface BatteryChangedInterface {

    /**
     * This function will be triggered when the battery information of sensor is changed.
     *
     * @param address    The mac address of device
     * @param status     This state can be one of BATT_STATE_NOT_CHARGING or BATT_STATE_CHARGING
     * @param percentage The range of battery level is 0 to 100
     */
    void onBatteryChanged(String address, int status, int percentage);
}
