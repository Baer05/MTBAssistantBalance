package mtb.assistant.balance.views;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.utils.MPPointF;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import mtb.assistant.balance.R;

public class PieChartActivity extends AppCompatActivity {

  private PieChart chart;
  protected Typeface tfRegular;
  protected Typeface tfLight;

  protected final String[] stats = new String[]{
      "Perfect", "Left", "Right", "Top", "Bottom",
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setContentView(R.layout.activity_pie_chart);
    tfRegular = Typeface.createFromAsset(getAssets(), "OpenSans-Regular.ttf");
    tfLight = Typeface.createFromAsset(getAssets(), "OpenSans-Light.ttf");

    chart = findViewById(R.id.chart1);
    chart.setUsePercentValues(true);
    chart.getDescription().setEnabled(false);
    chart.setExtraOffsets(5, 10, 5, 5);
    chart.setDragDecelerationFrictionCoef(0.95f);
    chart.setCenterTextTypeface(tfLight);
    chart.setCenterText(generateCenterSpannableText());
    chart.setDrawHoleEnabled(true);
    chart.setHoleColor(Color.WHITE);
    chart.setTransparentCircleColor(Color.WHITE);
    chart.setTransparentCircleAlpha(110);
    chart.setHoleRadius(58f);
    chart.setTransparentCircleRadius(61f);
    chart.setDrawCenterText(true);
    chart.setRotationAngle(0);
    // enable rotation of the chart by touch
    chart.setRotationEnabled(true);
    chart.setHighlightPerTapEnabled(true);
    chart.animateY(1400, Easing.EaseInOutQuad);
    Legend l = chart.getLegend();
    l.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
    l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
    l.setOrientation(Legend.LegendOrientation.VERTICAL);
    l.setDrawInside(false);
    l.setXEntrySpace(7f);
    l.setYEntrySpace(0f);
    l.setYOffset(0f);
    // entry label styling
    chart.setEntryLabelColor(Color.WHITE);
    chart.setEntryLabelTypeface(tfRegular);
    chart.setEntryLabelTextSize(12f);
    setData();
    Button onSave = findViewById(R.id.save_chart);
    onSave.setOnClickListener(v -> saveToGallery(chart, getIntent().getExtras().getString("fileName")));
    Button goBack = findViewById(R.id.go_back);
    goBack.setOnClickListener(v -> super.onBackPressed());
  }

  private void setData() {
    String collectedDataString = getIntent().getStringExtra("collectedData");
    String timestampArrayString = getIntent().getStringExtra("timestampArray");
    Gson gson = new Gson();
    Type type = new TypeToken<List<float[]>>() {
    }.getType();
    Type timestampType = new TypeToken<List<Long>>() {
    }.getType();
    List<float[]> collectedData = gson.fromJson(collectedDataString, type);
    List<Long> timestampArray = gson.fromJson(timestampArrayString, timestampType);
    double threshold = getIntent().getExtras().getDouble("threshold");
    long timeThreshold = getIntent().getExtras().getLong("timeThreshold");
    int perfect = 0;
    int left = 0;
    int right = 0;
    int top = 0;
    int bottom = 0;
    boolean toHighValueFound = false;
    long firstHighTimestamp = 0;
    for (int i = 0; i < collectedData.size(); i++) {
      boolean isTop = false;
      boolean isBottom = false;
      boolean isLeft = false;
      boolean isRight = false;
      boolean noToHighValueFound = true;
      List<Float> values = new ArrayList<>();
      for (int x = 0; x < collectedData.get(i).length; x++) {
        float value = collectedData.get(i)[x];
        values.add(value);
        if (value > threshold) {
          if (!toHighValueFound) {
            toHighValueFound = true;
            noToHighValueFound = false;
            firstHighTimestamp = timestampArray.get(i);
          }
          switch (x) {
            case 0:
            case 1:
            case 3:
            case 4:
              isTop = true;
              break;
            case 2:
            case 5:
              isBottom = true;
              break;
          }
        } else if (noToHighValueFound) {
          toHighValueFound = false;
          firstHighTimestamp = 0;
        }
      }

      if (firstHighTimestamp != 0 && (firstHighTimestamp - timestampArray.get(i) >= timeThreshold)) {
        if (values.get(0) > values.get(3) || values.get(0) > values.get(4) || values.get(0) > values.get(5) ||
            values.get(1) > values.get(3) || values.get(1) > values.get(4) || values.get(1) > values.get(5) ||
            values.get(2) > values.get(3) || values.get(2) > values.get(4) || values.get(2) > values.get(5)) {
          if (values.get(3) < threshold && values.get(4) < threshold && values.get(5) < threshold &&
              (values.get(0) > threshold || values.get(1) > threshold || values.get(2) > threshold)) {
            isLeft = true;
          }
        }
        if (values.get(3) > values.get(0) || values.get(3) > values.get(1) || values.get(3) > values.get(2) ||
            values.get(4) > values.get(0) || values.get(4) > values.get(1) || values.get(4) > values.get(2) ||
            values.get(5) > values.get(0) || values.get(5) > values.get(1) || values.get(5) > values.get(2)) {
          if (values.get(0) < threshold && values.get(1) < threshold && values.get(2) < threshold &&
              (values.get(3) > threshold || values.get(4) > threshold || values.get(5) > threshold)) {
            isRight = true;
          }
        }
        if (isLeft) left += 1;
        if (isRight) right += 1;
        if (isTop) top += 1;
        if (isBottom) bottom += 1;
      } else {
        perfect += 1;
      }
      /*if (!isTop && !isBottom && !isLeft && !isRight) {
        perfect += 1;
      }*/
    }
    ArrayList<PieEntry> entries = new ArrayList<>();
    // NOTE: The order of the entries when being added to the entries array determines their position around the center of
    // the chart.
    for (int i = 0; i < 5; i++) {
      entries.add(new PieEntry((float) (i == 0 ? perfect : i == 1 ? left : i == 2 ? right : i == 3 ?
          top : bottom), stats[i % stats.length]));
    }
    PieDataSet dataSet = new PieDataSet(entries, getString(R.string.stats));
    dataSet.setDrawIcons(false);
    dataSet.setSliceSpace(3f);
    dataSet.setIconsOffset(new MPPointF(0, 40));
    dataSet.setSelectionShift(5f);
    // add a lot of colors
    ArrayList<Integer> colors = new ArrayList<>();
    for (int c : ColorTemplate.VORDIPLOM_COLORS)
      colors.add(c);
    for (int c : ColorTemplate.JOYFUL_COLORS)
      colors.add(c);
    for (int c : ColorTemplate.COLORFUL_COLORS)
      colors.add(c);
    for (int c : ColorTemplate.LIBERTY_COLORS)
      colors.add(c);
    for (int c : ColorTemplate.PASTEL_COLORS)
      colors.add(c);
    colors.add(ColorTemplate.getHoloBlue());
    dataSet.setColors(colors);
    PieData data = new PieData(dataSet);
    data.setValueFormatter(new PercentFormatter());
    data.setValueTextSize(11f);
    data.setValueTextColor(Color.DKGRAY);
    data.setValueTypeface(tfLight);
    chart.setData(data);
    // undo all highlights
    chart.highlightValues(null);
    chart.invalidate();
  }

  private SpannableString generateCenterSpannableText() {
    SpannableString s = new SpannableString(getString(R.string.stats));
    s.setSpan(new RelativeSizeSpan(1.7f), 0, s.length(), 0);
    s.setSpan(new StyleSpan(Typeface.NORMAL), 0, s.length(), 0);
    s.setSpan(new ForegroundColorSpan(Color.GRAY), 0, s.length(), 0);
    return s;
  }

  private void saveToGallery(Chart chart, String name) {
    if (chart.saveToGallery(name + "_" + System.currentTimeMillis(), 70)) {
      Toast.makeText(getApplicationContext(), "Saving SUCCESSFUL!",
          Toast.LENGTH_SHORT).show();
      super.onBackPressed();
    } else {
      Toast.makeText(getApplicationContext(), "Saving FAILED!", Toast.LENGTH_SHORT)
          .show();
    }
  }
}
