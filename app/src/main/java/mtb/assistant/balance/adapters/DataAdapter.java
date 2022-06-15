package mtb.assistant.balance.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xsens.dot.android.sdk.events.XsensDotData;

import java.util.ArrayList;
import java.util.HashMap;

import mtb.assistant.balance.R;

/**
 * A view adapter for item view to present data.
 */
public class DataAdapter extends RecyclerView.Adapter<DataAdapter.DataViewHolder> {

    private static final String TAG = DataAdapter.class.getSimpleName();
    // The keys of HashMap
    public static final String KEY_ADDRESS = "address", KEY_TAG = "tag", KEY_DATA = "data";
    // The application context
    private Context mContext;
    // Put all data from sensors into one list
    private ArrayList<HashMap<String, Object>> mDataList;

    /**
     * Default constructor.
     *
     * @param context  The application context
     * @param dataList A list contains tag and data
     */
    public DataAdapter(Context context, ArrayList<HashMap<String, Object>> dataList) {
        mContext = context;
        mDataList = dataList;
    }

    @NonNull
    @Override
    public DataViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_data, parent, false);
        return new DataViewHolder(itemView);
    }

    @Override
    @SuppressLint("DefaultLocale")
    public void onBindViewHolder(@NonNull DataViewHolder holder, int position) {
        String tag = (String) mDataList.get(position).get(KEY_TAG);
        XsensDotData xsData = (XsensDotData) mDataList.get(position).get(KEY_DATA);
        holder.sensorName.setText(tag);
        double[] eulerAngles = xsData.getEuler();
        String eulerAnglesStr =
                String.format("%.6f", eulerAngles[0]) + ", " +
                String.format("%.6f", eulerAngles[1]) + ", " +
                String.format("%.6f", eulerAngles[2]);
        holder.orientationData.setText(eulerAnglesStr);
        float[] freeAcc = xsData.getFreeAcc();
        String freeAccStr =
                String.format("%.6f", freeAcc[0]) + ", " +
                String.format("%.6f", freeAcc[1]) + ", " +
                String.format("%.6f", freeAcc[2]);
        holder.freeAccData.setText(freeAccStr);
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 : mDataList.size();
    }

    /**
     * A Customized class for ViewHolder of RecyclerView.
     */
    static class DataViewHolder extends RecyclerView.ViewHolder {
        View rootView;
        TextView sensorName;
        TextView orientationData;
        TextView freeAccData;

        DataViewHolder(View v) {
            super(v);
            rootView = v;
            sensorName = v.findViewById(R.id.sensor_name);
            orientationData = v.findViewById(R.id.orientation_data);
            freeAccData = v.findViewById(R.id.free_acc_data);
        }
    }
}
