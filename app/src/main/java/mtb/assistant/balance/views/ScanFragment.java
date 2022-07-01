package mtb.assistant.balance.views;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.xsens.dot.android.sdk.interfaces.XsensDotScannerCallback;
import com.xsens.dot.android.sdk.utils.XsensDotScanner;

import java.util.ArrayList;
import java.util.HashMap;

import mtb.assistant.balance.R;
import mtb.assistant.balance.adapters.ScanAdapter;
import mtb.assistant.balance.databinding.FragmentScanBinding;
import mtb.assistant.balance.interfaces.BatteryChangedInterface;
import mtb.assistant.balance.interfaces.ScanClickInterface;
import mtb.assistant.balance.interfaces.SensorClickInterface;
import mtb.assistant.balance.viewmodels.BluetoothViewModel;
import mtb.assistant.balance.viewmodels.SensorViewModel;

import static com.xsens.dot.android.sdk.models.XsensDotDevice.CONN_STATE_CONNECTED;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.CONN_STATE_CONNECTING;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.CONN_STATE_DISCONNECTED;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.CONN_STATE_RECONNECTING;
import static mtb.assistant.balance.adapters.ScanAdapter.KEY_BATTERY_PERCENTAGE;
import static mtb.assistant.balance.adapters.ScanAdapter.KEY_BATTERY_STATE;
import static mtb.assistant.balance.adapters.ScanAdapter.KEY_CONNECTION_STATE;
import static mtb.assistant.balance.adapters.ScanAdapter.KEY_DEVICE;
import static mtb.assistant.balance.adapters.ScanAdapter.KEY_TAG;
import static mtb.assistant.balance.views.HomeActivity.FRAGMENT_TAG_SCAN;

/**
 * A fragment for scanned item.
 */
public class ScanFragment extends Fragment implements XsensDotScannerCallback, SensorClickInterface,
        ScanClickInterface, BatteryChangedInterface {

    private static final String TAG = ScanFragment.class.getSimpleName();

    private FragmentScanBinding mBinding;    // The view binder of ScanFragment
    private BluetoothViewModel mBluetoothViewModel; // The Bluetooth view model instance
    private SensorViewModel mSensorViewModel;    // The devices view model instance
    private ScanAdapter mScanAdapter;    // The adapter for scanned device item
    // A list contains scanned Bluetooth device
    private final ArrayList<HashMap<String, Object>> mScannedSensorList = new ArrayList<>();
    private XsensDotScanner mXsDotScanner;   // The XsensDotScanner object
    private boolean mIsScanning = false;     // A variable for scanning flag
    private AlertDialog mConnectionDialog;   // A dialog during the connection

    /**
     * Get the instance of ScanFragment
     *
     * @return The instance of ScanFragment
     */
    public static ScanFragment newInstance() {
        return new ScanFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        bindViewModel();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mBinding = FragmentScanBinding.inflate(LayoutInflater.from(getContext()));
        mBinding.toolbar.setTitle(getString(R.string.title_scan));
        ((AppCompatActivity) getActivity()).setSupportActionBar(mBinding.toolbar);
        // Retrieve the navigation controller
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mScanAdapter = new ScanAdapter(getContext(), mScannedSensorList);
        mScanAdapter.setSensorClickListener(this);
        mBinding.sensorRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.sensorRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mBinding.sensorRecyclerView.setAdapter(mScanAdapter);
        AlertDialog.Builder connectionDialogBuilder = new AlertDialog.Builder(getActivity());
        connectionDialogBuilder.setTitle(getString(R.string.connecting));
        connectionDialogBuilder.setMessage(getString(R.string.hint_connecting));
        mConnectionDialog = connectionDialogBuilder.create();
        // Set the SensorClickInterface instance to main activity.
        if (getActivity() != null) ((HomeActivity) getActivity()).setScanTriggerListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Notify main activity to refresh menu.
        HomeActivity.sCurrentFragment = FRAGMENT_TAG_SCAN;
        if (getActivity() != null) getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop scanning to let other apps to use scan function.
        if (mXsDotScanner != null) mXsDotScanner.stopScan();
        mBluetoothViewModel.updateScanState(false);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Release all connections when app is destroyed.
        mSensorViewModel.disconnectAllSensors();
    }

    @Override
    public void onScanTriggered(boolean triggered) {
        if (triggered) {
            // Disconnect to all sensors to make sure the connection has been released.
            mSensorViewModel.disconnectAllSensors();
            // This line is for connecting and reconnecting device.
            // Because they don't triggered onXsensDotConnectionChanged() function to remove sensor from list.
            mSensorViewModel.removeAllDevice();
            mScannedSensorList.clear();
            mScanAdapter.notifyDataSetChanged();
            mIsScanning = mXsDotScanner.startScan();
        } else {
            // If success for stopping, it will return True from SDK. So use !(not) here.
            mIsScanning = !mXsDotScanner.stopScan();
        }
        mBluetoothViewModel.updateScanState(mIsScanning);
    }

    @Override
    public void onXsensDotScanned(BluetoothDevice device, int rssi) {
        if (isAdded()) {
            // Use the mac address as UID to filter the same scan result.
            boolean isExist = false;
            for (HashMap<String, Object> map : mScannedSensorList) {
                if (((BluetoothDevice) map.get(KEY_DEVICE)).getAddress().equals(device.getAddress()))
                    isExist = true;
            }
            if (!isExist) {
                // The original connection state is Disconnected.
                // Also set tag, battery state, battery percentage to default value.
                HashMap<String, Object> map = new HashMap<>();
                map.put(KEY_DEVICE, device);
                map.put(KEY_CONNECTION_STATE, CONN_STATE_DISCONNECTED);
                map.put(KEY_TAG, "");
                map.put(KEY_BATTERY_STATE, -1);
                map.put(KEY_BATTERY_PERCENTAGE, -1);
                mScannedSensorList.add(map);
                mScanAdapter.notifyItemInserted(mScannedSensorList.size() - 1);
            }
        }
    }

    @Override
    public void onSensorClick(View v, int position) {
        // If success for stopping, it will return True from SDK. So use !(not) here.
        mIsScanning = !mXsDotScanner.stopScan();
        // Notify main activity to update the scan button.
        mBluetoothViewModel.updateScanState(false);
        int state = mScanAdapter.getConnectionState(position);
        BluetoothDevice device = mScanAdapter.getDevice(position);
        /*
         * state = 0 : Disconnected
         * state = 1 : Connecting
         * state = 2 : Connected
         * state = 4 : Reconnecting
         */
        switch (state) {
            case CONN_STATE_DISCONNECTED:
                mConnectionDialog.show();
                // The sensor isn't exist in the mSensorList(SensorViewModel), try to connect and add it.
                mSensorViewModel.connectSensor(getContext(), device);
                break;
            case CONN_STATE_CONNECTING:
                mScanAdapter.updateConnectionState(position, CONN_STATE_DISCONNECTED);
                mScanAdapter.notifyItemChanged(position);
                // This line is necessary to close Bluetooth gatt.
                mSensorViewModel.disconnectSensor(device.getAddress());
                // Remove this sensor from device list.
                mSensorViewModel.removeDevice(device.getAddress());
                break;
            case CONN_STATE_CONNECTED:
                mSensorViewModel.disconnectSensor(device.getAddress());
                // No need to call removeDevice() function, just wait for onXsensDotConnectionChanged() callback function.
                break;
            case CONN_STATE_RECONNECTING:
                mScanAdapter.updateConnectionState(position, CONN_STATE_DISCONNECTED);
                mScanAdapter.notifyItemChanged(position);
                // This line is necessary to close Bluetooth gatt.
                mSensorViewModel.cancelReconnection(device.getAddress());
                // Remove this sensor from device list.
                mSensorViewModel.removeDevice(device.getAddress());
                break;
        }
    }

    /**
     * Initialize and observe view models.
     */
    private void bindViewModel() {
        if (getActivity() != null) {
            mBluetoothViewModel = BluetoothViewModel.getInstance(getActivity());
            mSensorViewModel = SensorViewModel.getInstance(getActivity());
            mBluetoothViewModel.isBluetoothEnabled().observe(this, enabled -> {
                if (enabled) {
                    initXsDotScanner();
                } else {
                    mIsScanning = false;
                    mBluetoothViewModel.updateScanState(false);
                }
            });
            mSensorViewModel.getConnectionChangedDevice().observe(this, device -> {
                String address = device.getAddress();
                int state = device.getConnectionState();
                Log.d(TAG, "getConnectionChangedDevice() - address = " + address + ", state = " + state);
                for (HashMap<String, Object> map : mScannedSensorList) {
                    BluetoothDevice _device = (BluetoothDevice) map.get(KEY_DEVICE);
                    if (_device != null) {
                        String _address = _device.getAddress();
                        // Update connection state by the same mac address.
                        if (_address.equals(address)) {
                            map.put(KEY_CONNECTION_STATE, state);
                            mScanAdapter.notifyDataSetChanged();
                        }
                    }
                }
                if (state == CONN_STATE_CONNECTED) {
                    if (mConnectionDialog.isShowing()) mConnectionDialog.dismiss();
                }
            });
            mSensorViewModel.getTagChangedDevice().observe(this, device -> {
                String address = device.getAddress();
                String tag = device.getTag();
                mScanAdapter.updateTag(address, tag);
                mScanAdapter.notifyDataSetChanged();
                Log.d(TAG, "getTagChangedDevice() - address = " + address + ", tag = " + tag);
            });
            mSensorViewModel.setBatteryChangedCallback(this);
        }
    }

    /**
     * Setup for Xsens DOT scanner.
     */
    private void initXsDotScanner() {
        if (mXsDotScanner == null) {
            mXsDotScanner = new XsensDotScanner(getContext(), this);
            mXsDotScanner.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        }
    }

    @Override
    public void onBatteryChanged(String address, int state, int percentage) {
        Log.d(TAG, "onBatteryChanged() - address = " + address + ", state = " + state + ", percentage = " + percentage);
        mScanAdapter.updateBattery(address, state, percentage);
        if (getActivity() != null) {
            // This event is coming from background thread, use UI thread to update item.
            getActivity().runOnUiThread(() -> mScanAdapter.notifyDataSetChanged());
        }
    }
}
