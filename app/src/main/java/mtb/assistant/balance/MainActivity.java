package mtb.assistant.balance;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.xsens.dot.android.sdk.XsensDotSdk;

import mtb.assistant.balance.views.HomeActivity;

/**
 * A customized application class for basic initialization.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button startButton = findViewById(R.id.main_start_btn);
        startButton.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, HomeActivity.class)));

        initXsensDotSdk();
    }

    /**
     * Setup for Xsens DOT SDK.
     */
    private void initXsensDotSdk() {
        // Get the version name of SDK.
        String version = XsensDotSdk.getSdkVersion();
        Log.i(TAG, "initXsensDotSdk() - version: " + version);
        // Enable this feature to monitor logs from SDK.
        XsensDotSdk.setDebugEnabled(true);
        // Enable this feature then SDK will start reconnection when the connection is lost.
        XsensDotSdk.setReconnectEnabled(true);
    }
}
