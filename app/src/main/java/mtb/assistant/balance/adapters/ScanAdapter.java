package mtb.assistant.balance.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;

import mtb.assistant.balance.R;
import mtb.assistant.balance.interfaces.SensorClickInterface;

import static com.xsens.dot.android.sdk.models.XsensDotDevice.BATT_STATE_CHARGING;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.CONN_STATE_CONNECTED;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.CONN_STATE_CONNECTING;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.CONN_STATE_DISCONNECTED;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.CONN_STATE_RECONNECTING;

/**
 * A view adapter for item view of scanned BLE device.
 */
public class ScanAdapter extends RecyclerView.Adapter<ScanAdapter.ScanViewHolder> {

    private static final String TAG = ScanAdapter.class.getSimpleName();
    // The keys of HashMap
    public static final String
            KEY_DEVICE = "device",
            KEY_CONNECTION_STATE = "state",
            KEY_TAG = "tag",
            KEY_BATTERY_STATE = "battery_state",
            KEY_BATTERY_PERCENTAGE = "battery_percentage";

    // The application context
    private Context mContext;
    // Send the click event to fragment
    private SensorClickInterface mListener;
    // Put all scanned devices into one list
    private ArrayList<HashMap<String, Object>> mSensorList;

    /**
     * Default constructor.
     *
     * @param context           The application context
     * @param scannedSensorList The scanned devices list
     */
    public ScanAdapter(Context context, ArrayList<HashMap<String, Object>> scannedSensorList) {
        mContext = context;
        mSensorList = scannedSensorList;
    }

    @NonNull
    @Override
    public ScanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sensor, parent, false);
        return new ScanViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanViewHolder holder, final int position) {
        BluetoothDevice device = (BluetoothDevice) mSensorList.get(position).get(KEY_DEVICE);
        if (device != null) {
            String tag = (String) mSensorList.get(position).get(KEY_TAG);
            if (tag != null) holder.sensorName.setText(tag.isEmpty() ? device.getName() : tag);
            else holder.sensorName.setText(device.getName());
            int batteryPercentage = (int) mSensorList.get(position).get(KEY_BATTERY_PERCENTAGE);
            int batteryState = (int) mSensorList.get(position).get(KEY_BATTERY_STATE);
            String batteryStr = "";
            if (batteryPercentage != -1)
                batteryStr = batteryPercentage + "% ";
            if (batteryState == BATT_STATE_CHARGING)
                batteryStr = batteryStr + mContext.getString(R.string.batt_state_charging);
            holder.sensorBattery.setText(batteryStr);
            holder.sensorMacAddress.setText(device.getAddress());
        }
        int state = (int) mSensorList.get(position).get(KEY_CONNECTION_STATE);
        // Update connection result on the screen.
        switch (state) {
            case CONN_STATE_DISCONNECTED:
                holder.sensorState.setVisibility(View.GONE);
                holder.sensorState.setText(mContext.getResources().getString(R.string.disconnected));
                break;
            case CONN_STATE_CONNECTING:
                holder.sensorState.setVisibility(View.VISIBLE);
                holder.sensorState.setText(mContext.getResources().getString(R.string.connecting));
                break;
            case CONN_STATE_CONNECTED:
                holder.sensorState.setVisibility(View.VISIBLE);
                holder.sensorState.setText(mContext.getResources().getString(R.string.connected));
                break;
            case CONN_STATE_RECONNECTING:
                holder.sensorState.setVisibility(View.VISIBLE);
                holder.sensorState.setText(mContext.getResources().getString(R.string.reconnecting));
                break;
        }
        holder.rootView.setOnClickListener(v -> {
            // Notify the position of click event to fragment.
            if (mListener != null) mListener.onSensorClick(v, position);
        });
    }

    @Override
    public int getItemCount() {
        return mSensorList == null ? 0 : mSensorList.size();
    }

    /**
     * Get the Bluetooth device.
     *
     * @param position The position of item view
     * @return The scanned Bluetooth device
     */
    public BluetoothDevice getDevice(int position) {
        if (mSensorList != null) {
            return (BluetoothDevice) mSensorList.get(position).get(KEY_DEVICE);
        }
        return null;
    }

    /**
     * Get the connection state of device.
     *
     * @param position The position of item view
     * @return The connection state
     */
    public int getConnectionState(int position) {
        if (mSensorList != null) {
            return (int) mSensorList.get(position).get(KEY_CONNECTION_STATE);
        }
        return CONN_STATE_DISCONNECTED;
    }

    /**
     * Update the connection state to list.
     *
     * @param position The position of item view
     * @param state    The connection state
     */
    public void updateConnectionState(int position, int state) {
        if (mSensorList != null) {
            mSensorList.get(position).put(KEY_CONNECTION_STATE, state);
        }
    }

    /**
     * Update tag name to the list.
     *
     * @param address The mac address of device
     * @param tag     The device tag
     */
    public void updateTag(String address, String tag) {
        if (mSensorList != null) {
            for (HashMap<String, Object> map : mSensorList) {
                BluetoothDevice device = (BluetoothDevice) map.get(KEY_DEVICE);
                if (device != null) {
                    String _address = device.getAddress();
                    if (_address.equals(address)) {
                        map.put(KEY_TAG, tag);
                    }
                }
            }
        }
    }

    /**
     * Update battery information to the list.
     *
     * @param address    The mac address of device
     * @param state      This state can be one of BATT_STATE_NOT_CHARGING or BATT_STATE_CHARGING
     * @param percentage The range of battery level is 0 to 100
     */
    public void updateBattery(String address, int state, int percentage) {
        if (mSensorList != null) {
            for (HashMap<String, Object> map : mSensorList) {
                BluetoothDevice device = (BluetoothDevice) map.get(KEY_DEVICE);
                if (device != null) {
                    String _address = device.getAddress();
                    if (_address.equals(address)) {
                        map.put(KEY_BATTERY_STATE, state);
                        map.put(KEY_BATTERY_PERCENTAGE, percentage);
                    }
                }
            }
        }
    }

    /**
     * Initialize click listener of item view.
     *
     * @param listener The fragment which implemented SensorClickInterface
     */
    public void setSensorClickListener(SensorClickInterface listener) {
        mListener = listener;
    }

    /**
     * A Customized class for ViewHolder of RecyclerView.
     */
    static class ScanViewHolder extends RecyclerView.ViewHolder {
        View rootView;
        TextView sensorName;
        TextView sensorMacAddress;
        TextView sensorBattery;
        TextView sensorState;

        ScanViewHolder(View v) {
            super(v);
            rootView = v;
            sensorName = v.findViewById(R.id.sensor_name);
            sensorMacAddress = v.findViewById(R.id.sensor_mac_address);
            sensorBattery = v.findViewById(R.id.sensor_battery);
            sensorState = v.findViewById(R.id.sensor_state);
        }
    }
}
