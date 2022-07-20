package mtb.assistant.balance.views;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.gson.Gson;
import com.opencsv.CSVWriter;
import com.xsens.dot.android.sdk.events.XsensDotData;
import com.xsens.dot.android.sdk.interfaces.XsensDotSyncCallback;
import com.xsens.dot.android.sdk.models.XsensDotDevice;
import com.xsens.dot.android.sdk.models.XsensDotSyncManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.feelspace.fslib.BeltConnectionState;
import de.feelspace.fslib.NavigationController;
import de.feelspace.fslib.NavigationEventListener;
import de.feelspace.fslib.NavigationState;
import de.feelspace.fslib.PowerStatus;
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

/**
 * A fragment for presenting the data and storing to file.
 */
public class DataFragment extends Fragment implements StreamingClickInterface, DataChangeInterface, XsensDotSyncCallback, NavigationEventListener {

  private static final String TAG = DataFragment.class.getSimpleName();
  private static final int SYNCING_REQUEST_CODE = 1001;   // The code of request
  private FragmentDataBinding mBinding;    // The view binder of DataFragment
  private SensorViewModel mSensorViewModel;     // The devices view model instance
  private DataAdapter mDataAdapter;   // The adapter for data item
  // A list contains tag and data from each sensor
  private final ArrayList<HashMap<String, Object>> mDataList = new ArrayList<>();
  private AlertDialog mSyncingDialog; // A dialog during the synchronization
  UdpClientHandler udpClientHandler;
  private UdpClientThread udpSocket;
  // create a List which contains String array
  List<String[]> data_one = new ArrayList<>();
  List<String[]> data_two = new ArrayList<>();
  boolean isFirstDataArray = true;    // boolean decides which ArrayList will be used
  private boolean isWriteThreadRunning = false;
  private Thread writeThread;     // Thread for write to csv
  private Thread beltThread;      // Thread search for belt
  private String fileName = "";
  private NavigationController navigationController;   // Belt navigation controller
  private boolean firstToHighValue = false;
  private long firstToHighTimestamp = 0;
  List<int[]> collected_data = new ArrayList<int[]>();

  // Formats
  private static final DecimalFormat integerPercentFormat = new DecimalFormat("#0 '%'");


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
    mBinding.editCsvTitle.getText();
    udpClientHandler = new UdpClientHandler(this);
    udpSocket = new UdpClientThread(udpClientHandler);
    navigationController = new NavigationController(requireContext());
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

    searchForBelt();
    mBinding.feelSpaceSetIntensitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        mBinding.feelSpaceSetIntensityButton.setText(getString(
            R.string.feel_space_set_intensity_formatted_button_text,
            mBinding.feelSpaceSetIntensitySlider.getProgress() + 5));
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
    mBinding.feelSpaceSetIntensityButton.setOnClickListener(v -> {
      if (navigationController.getConnectionState() == BeltConnectionState.STATE_CONNECTED) {
        int intensity = mBinding.feelSpaceSetIntensitySlider.getProgress() + 5;
        navigationController.changeDefaultVibrationIntensity(intensity, true);
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    // Notify main activity to refresh menu.
    HomeActivity.sCurrentFragment = FRAGMENT_TAG_DATA;
    if (getActivity() != null) getActivity().invalidateOptionsMenu();
    navigationController.addNavigationEventListener(this);
    updateUI();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    navigationController.removeNavigationEventListener(this);
  }

  @Override
  public void onDetach() {
    super.onDetach();
    // Stop measurement for each sensor when exiting this page.
    mSensorViewModel.setMeasurement(false);
    // It's necessary to update this status, because user may enter this page again.
    mSensorViewModel.updateStreamingStatus(false);
  }

  @Override
  public void onStreamingTriggered() {
    fileName = Objects.requireNonNull(mBinding.editCsvTitle.getText()).toString();
    if (TextUtils.isEmpty(fileName)) {
      doToast(getString(R.string.empty_file_name));
    } else {
      if (Boolean.TRUE.equals(mSensorViewModel.isStreaming().getValue())) {
        // To stop.
        mSensorViewModel.setMeasurement(false);
        mSensorViewModel.updateStreamingStatus(false);
        XsensDotSyncManager.getInstance(this).stopSyncing();
        udpSocket.stopUDPSocket();
        stopWriteDataThread();
        Intent intent = new Intent(getActivity(), PieChartActivity.class);
        intent.putExtra("collectedData", new Gson().toJson(collected_data));
        intent.putExtra("threshold", 300);
        intent.putExtra("fileName", fileName);
        startActivity(intent);
      } else if (checkIfFileExist()) {
        doToast(getString(R.string.file_already_exists));
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
            mSensorViewModel.setMeasurement(true);
            // Notify the current streaming status to MainActivity to refresh the menu.
            mSensorViewModel.updateStreamingStatus(true);
            udpSocket.startUDPSocket();
            data_one.add(new String[]{"Timestamp", "S1", "S2", "S3", "S4", "S5", "S6",
                "q0", "q1", "q2", "q3", "ACCx", "ACCy", "ACCz"});
            mBinding.editCsvTitle.setFocusable(false);
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
    if (getActivity() != null) {
      getActivity().runOnUiThread(() -> {
        // The data is coming from background thread, change to UI thread for updating.
        mDataAdapter.notifyDataSetChanged();
      });
    }
  }

  private void searchForBelt() {
    beltThread = new Thread(() -> {
      if (navigationController.getConnectionState() == BeltConnectionState.STATE_DISCONNECTED) {
        try {
          navigationController.searchAndConnectBelt();
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } else {
        beltThread.interrupt();
      }
    });
    beltThread.start();
  }

  public final void doToast(String msg) {
    Toast.makeText(this.getContext(), msg, Toast.LENGTH_SHORT).show();
  }

  private boolean checkExternalMedia() {
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

  private boolean checkIfFileExist() {
    String csvName = fileName + ".csv";
    Path path = Paths.get(android.os.Environment.getExternalStorageDirectory().
        getAbsolutePath() + "/MTBAssistantRecording/" + csvName);
    return Files.exists(path);
  }

  public void writeDataAtOne() {
    try {
      if (checkExternalMedia() && !TextUtils.isEmpty(fileName)) {
        // first create file object for file placed at location
        File root = android.os.Environment.getExternalStorageDirectory();
        File dir = new File(root.getAbsolutePath() + "/MTBAssistantRecording");
        if (!dir.exists()) {
          boolean wasSuccessful = dir.mkdirs();
        }
        String csvName = fileName + ".csv";
        File file = new File(dir, csvName);
        // create FileWriter object with file as parameter
        FileWriter outputFile = new FileWriter(file, true);
        // create CSVWriter object fileWriter object as parameter
        CSVWriter writer = new CSVWriter(outputFile);
        if (isFirstDataArray) {
          isFirstDataArray = false;
          writer.writeAll(data_one, true);
          data_one.clear();
        } else {
          isFirstDataArray = true;
          writer.writeAll(data_two, true);
          data_two.clear();
        }
        // closing writer connection
        writer.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void startWriteDataThread() {
    isWriteThreadRunning = true;
    writeThread = new Thread(() -> {
      while (isWriteThreadRunning) {
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
    isWriteThreadRunning = false;
    data_one.clear();
    data_two.clear();
    writeThread.interrupt();
    mBinding.editCsvTitle.setFocusableInTouchMode(true);
    Objects.requireNonNull(mBinding.editCsvTitle.getText()).clear();
    fileName = "";
  }

  /**
   * Updates UI components according to the belt state and data.
   */
  private void updateUI() {
    updateConnectionStateUI();
    updateBatteryUI();
    updateIntensityUI();
  }

  /**
   * Updates connection state label.
   */
  private void updateConnectionStateUI() {
    requireActivity().runOnUiThread(() -> {
      // Connection state label
      mBinding.feelSpaceConnectionStatusLabel.setText(
          navigationController.getConnectionState().toString());
    });
  }

  /**
   * Updates battery labels.
   */
  private void updateBatteryUI() {
    requireActivity().runOnUiThread(() -> {
      PowerStatus powerStatus = navigationController.getBeltPowerStatus();
      if (powerStatus == null) {
        mBinding.feelSpacePowerStatusLabel.setText("-");
      } else {
        mBinding.feelSpacePowerStatusLabel.setText(powerStatus.toString());
      }
      Integer batteryLevel = navigationController.getBeltBatteryLevel();
      if (batteryLevel == null) {
        mBinding.feelSpaceBatteryLevelLabel.setText("-");
      } else {
        mBinding.feelSpaceBatteryLevelLabel.setText(integerPercentFormat.format(batteryLevel));
      }
    });
  }

  /**
   * Updates belt intensity label.
   */
  private void updateIntensityUI() {
    requireActivity().runOnUiThread(() -> {
      // Default intensity label
      Integer intensity = navigationController.getDefaultVibrationIntensity();
      if (intensity == null) {
        mBinding.feelSpaceDefaultIntensityLabel.setText("-");
      } else {
        mBinding.feelSpaceDefaultIntensityLabel.setText(integerPercentFormat.format(intensity));
      }
    });
  }

  @Override
  public void onNavigationStateChanged(NavigationState state) {
  }

  @Override
  public void onBeltHomeButtonPressed(boolean navigating) {
  }

  @Override
  public void onBeltDefaultVibrationIntensityChanged(int intensity) {
    updateIntensityUI();
  }

  @Override
  public void onBeltOrientationUpdated(int beltHeading, boolean accurate) {
  }

  @Override
  public void onBeltBatteryLevelUpdated(int batteryLevel, PowerStatus status) {
    updateBatteryUI();
  }

  @Override
  public void onCompassAccuracySignalStateUpdated(boolean enabled) {
  }

  @Override
  public void onBeltConnectionStateChanged(BeltConnectionState state) {
    updateUI();
  }

  @Override
  public void onBeltConnectionLost() {
    doToast(getString(R.string.toast_connection_lost));
  }

  @Override
  public void onBeltConnectionFailed() {
    doToast(getString(R.string.toast_connection_failed));
  }

  @Override
  public void onNoBeltFound() {
    doToast(getString(R.string.toast_no_belt_found));
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
      for (int i = 0; i < values.length; i++) {
        intArray[i] = Integer.parseInt(values[i]);
      }
      parent.collected_data.add(intArray);
      if (parent.mDataList.size() > 0) {
        XsensDotData xsData = (XsensDotData) parent.mDataList.get(parent.mDataList.size() - 1).get(KEY_DATA);
        assert xsData != null;
        float[] quaternions = xsData.getQuat();
        float[] freeAcc = xsData.getFreeAcc();
        if (intArray.length == 6 && quaternions.length == 4 && freeAcc.length == 3) {
          String[] output = new String[]{DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").
              format(LocalDateTime.now()), String.valueOf(intArray[0]), String.
              valueOf(intArray[1]), String.valueOf(intArray[2]),
              String.valueOf(intArray[3]), String.valueOf(intArray[4]),
              String.valueOf(intArray[5]), String.valueOf(quaternions[0]),
              String.valueOf(quaternions[1]), String.valueOf(quaternions[2]),
              String.valueOf(quaternions[3]), String.valueOf(freeAcc[0]),
              String.valueOf(freeAcc[1]), String.valueOf(freeAcc[2])};
          if (parent.isFirstDataArray) {
            parent.data_one.add(output);
          } else {
            parent.data_two.add(output);
          }
          long currentTimestamp = 0;
          for (int i = 0; i < values.length; i++) {
            intArray[i] = Integer.parseInt(values[i]);
            if (intArray[i] > 3500) {
              parent.firstToHighTimestamp = new Date().getTime();
              currentTimestamp = new Date().getTime();
              break;
            }
          }
          if (currentTimestamp - parent.firstToHighTimestamp >= 2) {
            //parent.navigationController.notifyWarning(true);
            Log.d(TAG, "value over two seconds to high");
          } else {
            parent.firstToHighTimestamp = 0;
          }

        }
      }
    }
  }
}
