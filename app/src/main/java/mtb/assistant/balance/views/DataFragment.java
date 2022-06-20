package mtb.assistant.balance.views;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.opencsv.CSVWriter;
import com.xsens.dot.android.sdk.BuildConfig;
import com.xsens.dot.android.sdk.events.XsensDotData;
import com.xsens.dot.android.sdk.interfaces.XsensDotSyncCallback;
import com.xsens.dot.android.sdk.models.FilterProfileInfo;
import com.xsens.dot.android.sdk.models.XsensDotDevice;
import com.xsens.dot.android.sdk.models.XsensDotSyncManager;
import com.xsens.dot.android.sdk.utils.XsensDotLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mtb.assistant.balance.R;
import mtb.assistant.balance.adapters.DataAdapter;
import mtb.assistant.balance.databinding.FragmentDataBinding;
import mtb.assistant.balance.interfaces.DataChangeInterface;
import mtb.assistant.balance.interfaces.StreamingClickInterface;
import mtb.assistant.balance.services.UdpClientThread;
import mtb.assistant.balance.viewmodels.SensorViewModel;

import static com.xsens.dot.android.sdk.models.XsensDotPayload.PAYLOAD_TYPE_COMPLETE_QUATERNION;
import static mtb.assistant.balance.adapters.DataAdapter.KEY_ADDRESS;
import static mtb.assistant.balance.adapters.DataAdapter.KEY_DATA;
import static mtb.assistant.balance.adapters.DataAdapter.KEY_TAG;
import static mtb.assistant.balance.views.HomeActivity.FRAGMENT_TAG_DATA;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.LOG_STATE_ON;
import static com.xsens.dot.android.sdk.models.XsensDotDevice.PLOT_STATE_ON;
import static com.xsens.dot.android.sdk.models.XsensDotPayload.PAYLOAD_TYPE_COMPLETE_EULER;

/**
 * A fragment for presenting the data and storing to file.
 */
public class DataFragment extends Fragment implements StreamingClickInterface, DataChangeInterface, XsensDotSyncCallback {

    private static final String TAG = DataFragment.class.getSimpleName();
    // The code of request
    private static final int SYNCING_REQUEST_CODE = 1001;
    // The keys of HashMap
    public static final String KEY_LOGGER = "logger";
    // The view binder of DataFragment
    private FragmentDataBinding mBinding;
    // The devices view model instance
    private SensorViewModel mSensorViewModel;
    // The adapter for data item
    private DataAdapter mDataAdapter;
    // A list contains tag and data from each sensor
    private final ArrayList<HashMap<String, Object>> mDataList = new ArrayList<>();
    // A list contains mac address and XsensDotLogger object.
    private final List<HashMap<String, Object>> mLoggerList = new ArrayList<>();
    // A variable for data logging flag
    private boolean mIsLogging = false;
    // A dialog during the synchronization
    private AlertDialog mSyncingDialog;
    UdpClientHandler udpClientHandler;
    private UdpClientThread udpSocket;
    // create a List which contains String array
    List<String[]> data_one = new ArrayList<>();
    List<String[]> data_two = new ArrayList<>();
    boolean isFirstDataArray = true;
    private boolean isThreadRunning = false;
    private Thread writeThread;
    private String fileName = "";

    /**
     * Get the instance of DataFragment
     *
     * @return The instance of DataFragment
     */
    public static DataFragment newInstance() {
        return new DataFragment();
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
        mBinding = FragmentDataBinding.inflate(LayoutInflater.from(getContext()));
        mBinding.toolbar.setTitle(getString(R.string.menu_start_streaming));
        ((AppCompatActivity) requireActivity()).setSupportActionBar(mBinding.toolbar);
        mBinding.editCsvName.getText();
        udpClientHandler = new UdpClientHandler(this);
        udpSocket = new UdpClientThread(udpClientHandler);
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mSensorViewModel.setStates(PLOT_STATE_ON, LOG_STATE_ON);
        mDataAdapter = new DataAdapter(mDataList);
        mBinding.dataRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.dataRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mBinding.dataRecyclerView.setAdapter(mDataAdapter);
        AlertDialog.Builder syncingDialogBuilder = new AlertDialog.Builder(getActivity());
        syncingDialogBuilder.setView(R.layout.dialog_syncing);
        syncingDialogBuilder.setCancelable(false);
        mSyncingDialog = syncingDialogBuilder.create();
        mSyncingDialog.setOnDismissListener(dialog -> {
            ProgressBar bar = mSyncingDialog.findViewById(R.id.syncing_progress);
            // Reset progress to 0 for next time to use.
            if (bar != null) bar.setProgress(0);
        });
        // Set the StreamingClickInterface instance to main activity.
        if (getActivity() != null) ((HomeActivity) getActivity()).setStreamingTriggerListener(this);
        Log.d(TAG, String.valueOf(mBinding.editCsvName.getText()));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Notify main activity to refresh menu.
        HomeActivity.sCurrentFragment = FRAGMENT_TAG_DATA;
        if (getActivity() != null) getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Stop measurement for each sensor when exiting this page.
        mSensorViewModel.setMeasurement(false);
        // It's necessary to update this status, because user may enter this page again.
        mSensorViewModel.updateStreamingStatus(false);
        closeFiles();
    }

    @Override
    public void onStreamingTriggered() {
        fileName = mBinding.editCsvName.getText().toString();
        if(TextUtils.isEmpty(fileName)) {
            doToast(getString(R.string.empty_file_name));
        }else {
            if (Boolean.TRUE.equals(mSensorViewModel.isStreaming().getValue())) {
                // To stop.
                mSensorViewModel.setMeasurement(false);
                mSensorViewModel.updateStreamingStatus(false);
                XsensDotSyncManager.getInstance(this).stopSyncing();
                udpSocket.stopUDPSocket();
                stopWriteDataThread();
                closeFiles();
            } else {
                // To start.
                resetPage();
                if (!mSensorViewModel.checkConnection()) {
                    doToast(getString(R.string.hint_check_connection));
                    return;
                }
                // Set first device to root.
                mSensorViewModel.setRootDevice(true);
                final ArrayList<XsensDotDevice> devices = mSensorViewModel.getAllSensors();
                // Devices will disconnect during the syncing, and do reconnection automatically.
                XsensDotSyncManager.getInstance(this).startSyncing(devices, SYNCING_REQUEST_CODE);
                if (!mSyncingDialog.isShowing()) mSyncingDialog.show();
            }
        }
    }

    /**
     * Initialize and observe view models.
     */
    private void bindViewModel() {
        if (getActivity() != null) {
            mSensorViewModel = SensorViewModel.getInstance(getActivity());
            // Implement DataChangeInterface and override onDataChanged() function to receive data.
            mSensorViewModel.setDataChangeCallback(this);
        }
    }

    /**
     * Reset page UI to default.
     */
    @SuppressLint("NotifyDataSetChanged")
    private void resetPage() {
        mBinding.syncResult.setText("-");
        mDataList.clear();
        mDataAdapter.notifyDataSetChanged();
    }

    /**
     * Get the filter profile name.
     *
     * @param device The XsensDotDevice object
     * @return The filter profile name, "General" by default
     */
    private String getFilterProfileName(XsensDotDevice device) {
        int index = device.getCurrentFilterProfileIndex();
        ArrayList<FilterProfileInfo> list = device.getFilterProfileInfoList();
        for (FilterProfileInfo info : list) {
            if (info.getIndex() == index) return info.getName();
        }
        return "General";
    }

    /**
     * Create data logger for each sensor.
     */
    private void createFiles() {
        // Remove XsensDotLogger objects from list before start data logging.
        mLoggerList.clear();
        ArrayList<XsensDotDevice> devices = mSensorViewModel.getAllSensors();
        for (XsensDotDevice device : devices) {
            String appVersion = BuildConfig.VERSION_NAME;
            String fwVersion = device.getFirmwareVersion();
            String address = device.getAddress();
            String tag = device.getTag().isEmpty() ? device.getName() : device.getTag();
            String filename = "";
            if (getContext() != null) {
                // Store log file in app internal folder.
                // Don't need user to granted the storage permission.
                File dir = getContext().getExternalFilesDir(null);
                if (dir != null) {
                    // This filename contains full file path.
                    filename = dir.getAbsolutePath() +
                            File.separator +
                            tag + "_" +
                            new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date()) +
                            ".csv";
                }
            }
            Log.d(TAG, "createFiles() - " + filename);
            XsensDotLogger logger = new XsensDotLogger(
                    getContext(),
                    XsensDotLogger.TYPE_CSV,
                    PAYLOAD_TYPE_COMPLETE_EULER,
                    filename,
                    tag,
                    fwVersion,
                    device.isSynced(),
                    device.getCurrentOutputRate(),
                    getFilterProfileName(device),
                    appVersion);
            // Use mac address as a key to find logger object.
            HashMap<String, Object> map = new HashMap<>();
            map.put(KEY_ADDRESS, address);
            map.put(KEY_LOGGER, logger);
            mLoggerList.add(map);
        }
        mIsLogging = true;
    }

    /**
     * Update data to specific file.
     *
     * @param address The mac address of device
     * @param data    The XsensDotData packet
     */
    private void updateFiles(String address, XsensDotData data) {
        for (HashMap<String, Object> map : mLoggerList) {
            String _address = (String) map.get(KEY_ADDRESS);
            if (_address != null) {
                if (_address.equals(address)) {
                    XsensDotLogger logger = (XsensDotLogger) map.get(KEY_LOGGER);
                    if (logger != null && mIsLogging) logger.update(data);
                }
            }
        }
    }

    /**
     * Close the data output stream.
     */
    private void closeFiles() {
        mIsLogging = false;
        for (HashMap<String, Object> map : mLoggerList) {
            // Call stop() function to flush and close the output stream.
            // Data is kept in the stream buffer and write to file when the buffer is full.
            // Call this function to write data to file whether the buffer is full or not.
            XsensDotLogger logger = (XsensDotLogger) map.get(KEY_LOGGER);
            if (logger != null) logger.stop();
        }
    }

    @Override
    public void onSyncingStarted(String address, boolean isSuccess, int requestCode) {
        Log.i(TAG, "onSyncingStarted() - address = " + address + ", isSuccess = " + isSuccess + ", requestCode = " + requestCode);
    }

    @Override
    public void onSyncingProgress(final int progress, final int requestCode) {
        Log.i(TAG, "onSyncingProgress() - progress = " + progress + ", requestCode = " + requestCode);
        if (requestCode == SYNCING_REQUEST_CODE) {
            if (mSyncingDialog.isShowing()) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Find the view of progress bar in dialog layout and update.
                        ProgressBar bar = mSyncingDialog.findViewById(R.id.syncing_progress);
                        if (bar != null) bar.setProgress(progress);
                    });
                }
            }
        }
    }

    @Override
    public void onSyncingResult(String address, boolean isSuccess, int requestCode) {
        Log.i(TAG, "onSyncingResult() - address = " + address + ", isSuccess = " + isSuccess + ", requestCode = " + requestCode);
    }

    @Override
    public void onSyncingDone(final HashMap<String, Boolean> syncingResultMap, final boolean isSuccess, final int requestCode) {
        Log.i(TAG, "onSyncingDone() - isSuccess = " + isSuccess + ", requestCode = " + requestCode);
        if (requestCode == SYNCING_REQUEST_CODE) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (mSyncingDialog.isShowing()) mSyncingDialog.dismiss();
                    mSensorViewModel.setRootDevice(false);
                    if (isSuccess) {
                        mBinding.syncResult.setText(R.string.sync_result_success);
                        // Syncing precess is success, choose one measurement mode to start measuring.
                        mSensorViewModel.setMeasurementMode(PAYLOAD_TYPE_COMPLETE_QUATERNION);
                        createFiles();
                        mSensorViewModel.setMeasurement(true);
                        // Notify the current streaming status to MainActivity to refresh the menu.
                        mSensorViewModel.updateStreamingStatus(true);
                        udpSocket.startUDPSocket();
                        data_one.add(new String[] {"Timestamp",  "S1", "S2", "q0", "q1", "q2", "q3", "ACCx", "ACCy", "ACCz"});
                        mBinding.editCsvName.setFocusable(false);
                        mBinding.editCsvName.setShowSoftInputOnFocus(false);
                        startWriteDataThread();
                    } else {
                        mBinding.syncResult.setText(R.string.sync_result_fail);
                        // If the syncing result is fail, show a message to user
                        doToast(getString(R.string.hint_syncing_failed));
                        for (Map.Entry<String, Boolean> result : syncingResultMap.entrySet()) {
                            if (!result.getValue()) {
                                // It's preferred to stop measurement of all sensors.
                                mSensorViewModel.setMeasurement(false);
                                // Notify the current streaming status to MainActivity to refresh the menu.
                                mSensorViewModel.updateStreamingStatus(false);
                            }
                        }
                    }
                });
            }
        }
    }

    public void onSyncingStopped(String address, boolean isSuccess, int requestCode) {
        Log.i(TAG, "onSyncingStopped() - address = " + address + ", isSuccess = " + isSuccess + ", requestCode = " + requestCode);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onDataChanged(String address, XsensDotData data) {
        boolean isExist = false;
        for (HashMap<String, Object> map : mDataList) {
            String _address = (String) map.get(KEY_ADDRESS);
            assert _address != null;
            if (_address.equals(address)) {
                // If the data is exist, try to update it.
                map.put(KEY_DATA, data);
                isExist = true;
                break;
            }
        }
        if (!isExist) {
            // It's the first data of this sensor, create a new set and add it.
            HashMap<String, Object> map = new HashMap<>();
            map.put(KEY_ADDRESS, address);
            map.put(KEY_TAG, mSensorViewModel.getTag(address));
            map.put(KEY_DATA, data);
            mDataList.add(map);
        }
        updateFiles(address, data);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                // The data is coming from background thread, change to UI thread for updating.
                mDataAdapter.notifyDataSetChanged();
            });
        }
    }

    public final void doToast(String msg) {
        Toast.makeText(this.getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private boolean checkExternalMedia(){
        boolean mExternalStorageWriteable;
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // Can read and write the media
           mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // Can only read the media
            mExternalStorageWriteable = false;
        } else {
            // Can't read or write
            mExternalStorageWriteable = false;
        }
        return mExternalStorageWriteable;
    }


    public void writeDataAtOne() {
        try {
            if (checkExternalMedia() && !TextUtils.isEmpty(fileName)) {
                // first create file object for file placed at location
                File root = android.os.Environment.getExternalStorageDirectory();
                File dir = new File (root.getAbsolutePath() + "/MTBAssistantRecording");
                if(!dir.exists()) {
                    boolean wasSuccessful = dir.mkdirs();
                }

                String csvName = fileName + ".csv";
                File file = new File(dir, csvName);
                // create FileWriter object with file as parameter
                FileWriter outputFile = new FileWriter(file, true);
                // create CSVWriter object fileWriter object as parameter
                CSVWriter writer = new CSVWriter(outputFile);
                if(isFirstDataArray) {
                    isFirstDataArray = false;
                    writer.writeAll(data_one);
                    data_one.clear();
                } else {
                    isFirstDataArray = true;
                    writer.writeAll(data_two);
                    data_two.clear();
                }
                // closing writer connection
                writer.close();
            }
        } catch(IOException e){
            e.printStackTrace();
        }
    }

    private void startWriteDataThread() {
       isThreadRunning = true;
       writeThread = new Thread(() -> {
          while (isThreadRunning) {
              try {
                  Thread.sleep(10000);
                  writeDataAtOne();
              } catch (InterruptedException e) {
                  e.printStackTrace();
              }
          }
       });
       writeThread.start();
    }

    private void stopWriteDataThread() {
        isThreadRunning = false;
        data_one.clear();
        data_two.clear();
        writeThread.interrupt();
        mBinding.editCsvName.setFocusableInTouchMode(true);
        mBinding.editCsvName.getText().clear();
        fileName = "";
    }



    public static class UdpClientHandler extends Handler {
        private final DataFragment parent;

        public UdpClientHandler(DataFragment parent) {
            super();
            this.parent = parent;
        }
        @Override
        public void handleMessage(Message msg) {
            parent.doToast(msg.obj.toString());
            String[] values = msg.obj.toString().split(",");
            int[] intArray = new int[values.length];
            for(int i = 0; i < values.length; i++) {
                intArray[i] = Integer.parseInt(values[i]);
            }
            if(parent.mDataList.size() > 0) {
                XsensDotData xsData = (XsensDotData) parent.mDataList.get(parent.mDataList.size() - 1).get(KEY_DATA);
                assert xsData != null;
                float[] quaternions = xsData.getQuat();
                float[] freeAcc = xsData.getFreeAcc();
                if (intArray.length == 2 && quaternions.length == 4 && freeAcc.length == 3) {
                    String [] output = new String[] {DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").
                            format(LocalDateTime.now()), String.valueOf(intArray[0]), String.
                            valueOf(intArray[1]), String.valueOf(quaternions[0]), String.
                            valueOf(quaternions[1]), String.valueOf(quaternions[2]), String.
                            valueOf(quaternions[3]), String.valueOf(freeAcc[0]), String.
                            valueOf(freeAcc[1]), String.valueOf(freeAcc[2])};
                    if(parent.isFirstDataArray) {
                        parent.data_one.add(output);
                    } else {
                        parent.data_two.add(output);
                    }
                }
            }
        }
    }
}
