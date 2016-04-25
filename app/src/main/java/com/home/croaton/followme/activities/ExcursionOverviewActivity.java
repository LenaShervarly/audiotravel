package com.home.croaton.followme.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.daimajia.slider.library.Animations.DescriptionAnimation;
import com.daimajia.slider.library.SliderLayout;
import com.daimajia.slider.library.SliderTypes.BaseSliderView;
import com.daimajia.slider.library.SliderTypes.DefaultSliderView;
import com.home.croaton.followme.R;
import com.home.croaton.followme.domain.Excursion;
import com.home.croaton.followme.domain.ExcursionBrief;
import com.home.croaton.followme.download.IExcursionDownloader;
import com.home.croaton.followme.download.S3ExcursionDownloader;
import com.home.croaton.followme.instrumentation.ConnectionHelper;
import com.home.croaton.followme.maps.MapHelper;

import org.osmdroid.bonuspack.cachemanager.CacheManager;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.views.MapView;

import java.util.ArrayList;

public class ExcursionOverviewActivity extends AppCompatActivity {
    private String DOWNLOADED_MAP_KEY_PREFIX = "com.home.croaton.followme_map_";

    private ExcursionBrief currentExcursion;
    private String currentLanguage;
    private Button openButton;
    private Button loadButton;
    private ProgressDialog progressDialog;
    private SliderLayout slider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_excursion_overview);

        Intent intent = getIntent();
        currentExcursion = intent.getParcelableExtra(IntentNames.SELECTED_EXCURSION_BRIEF);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        currentLanguage = sharedPref.getString(getString(R.string.settings_language_preference), "ru");

        initUI();
    }

    private void initUI() {
        initSlider();

        openButton = (Button) findViewById(R.id.open_button);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ExcursionOverviewActivity.this, MapsActivity.class);
                intent.putExtra(IntentNames.SELECTED_EXCURSION_NAME, currentExcursion.getKey());
                startActivity(intent);
            }
        });

        loadButton = (Button) findViewById(R.id.load_button);
        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ExcursionOverviewActivity.this);
                boolean mapDownloaded = settings.getBoolean(DOWNLOADED_MAP_KEY_PREFIX + currentExcursion.getKey(), false);
                if (!mapDownloaded)
                {
                     if (ConnectionHelper.hasInternetConnection(ExcursionOverviewActivity.this)) {
                         MapView mapView = new MapView(ExcursionOverviewActivity.this);
                         MapHelper.chooseBeautifulMapProvider(ExcursionOverviewActivity.this, mapView);
                         mapView.setMinZoomLevel(1);
                         mapView.setMaxZoomLevel(18);
                         CacheManager cacheManager = new CacheManager(mapView);

                         cacheManager.downloadAreaAsync(ExcursionOverviewActivity.this,
                                 new BoundingBoxE6(currentExcursion.getArea().get(0).getLatitude(),
                                         currentExcursion.getArea().get(1).getLongitude(),
                                         currentExcursion.getArea().get(1).getLatitude(),
                                         currentExcursion.getArea().get(0).getLongitude()), 5, 18);
                         settings.edit().putBoolean(DOWNLOADED_MAP_KEY_PREFIX + currentExcursion.getKey(), true);
                     }
                }

                DownloadExcursionTask downloadTask = new DownloadExcursionTask(ExcursionOverviewActivity.this);
                downloadTask.execute();
            }
        });

        progressDialog = new ProgressDialog(ExcursionOverviewActivity.this);
        progressDialog.setMessage("A message");
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(true);

        TextView aboutExcursion = (TextView)findViewById(R.id.excursion_overview);
        aboutExcursion.setText(currentExcursion.getContentByLanguage(currentLanguage).getOverview());

        TextView excursionDuration = (TextView)findViewById(R.id.excursion_duration);
        excursionDuration.setText(Double.toString(currentExcursion.getDuration()) + getString(R.string.hours));

        TextView excursionLength = (TextView)findViewById(R.id.excursion_length);
        excursionLength.setText(Double.toString(currentExcursion.getLength()) + getString(R.string.kilometers));

        TextView excursionCost = (TextView)findViewById(R.id.excursion_cost);
        excursionCost.setText(Double.toString(currentExcursion.getCost()) + getString(R.string.euro));

        setTitle(currentExcursion.getContentByLanguage(currentLanguage).getName());
    }

    private void initSlider() {
        slider = (SliderLayout)findViewById(R.id.slider);
        ArrayList<Integer> imageIds = new ArrayList<>();
        imageIds.add(R.drawable.gamlastan);
        imageIds.add(R.drawable.gamlastan1);
        imageIds.add(R.drawable.gamlastan2);

        for(int imageId : imageIds){
            DefaultSliderView textSliderView = new DefaultSliderView(this);

            textSliderView
                    .image(imageId)
                    .setScaleType(BaseSliderView.ScaleType.Fit);

            slider.addSlider(textSliderView);
        }
        slider.setLayoutMode(ViewGroup.LAYOUT_MODE_CLIP_BOUNDS);
        slider.setPresetTransformer(SliderLayout.Transformer.RotateDown);
        slider.setPresetIndicator(SliderLayout.PresetIndicators.Center_Bottom);
        slider.setCustomAnimation(new DescriptionAnimation());
        slider.setDuration(4000);
    }

    private class DownloadExcursionTask extends AsyncTask<Void, Integer, Excursion> {
        private final Context context;

        public DownloadExcursionTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected Excursion doInBackground(Void... inputs) {
            IExcursionDownloader downloader = new S3ExcursionDownloader(context);
            return downloader.downloadExcursion(currentExcursion, currentLanguage);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(100);
            progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Excursion result) {
            progressDialog.dismiss();
        }
    }
}
