package hpsaturn.pollutionreporter;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;
import android.view.View;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.hpsaturn.tools.Logger;
import com.iamhabib.easy_preference.EasyPreference;
import com.yarolegovich.discretescrollview.DiscreteScrollView;
import com.yarolegovich.discretescrollview.transform.ScaleTransformer;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import hpsaturn.pollutionreporter.bleservice.ServiceBLE;
import hpsaturn.pollutionreporter.bleservice.ServiceInterface;
import hpsaturn.pollutionreporter.bleservice.ServiceManager;
import hpsaturn.pollutionreporter.bleservice.ServiceScheduler;
import hpsaturn.pollutionreporter.common.Keys;
import hpsaturn.pollutionreporter.models.SensorConfig;
import hpsaturn.pollutionreporter.models.SensorData;
import hpsaturn.pollutionreporter.models.SensorTrackInfo;
import hpsaturn.pollutionreporter.view.ChartFragment;
import hpsaturn.pollutionreporter.view.FragmentPickerAdapter;
import hpsaturn.pollutionreporter.view.FragmentPickerData;
import hpsaturn.pollutionreporter.view.FragmentPickerInfo;
import hpsaturn.pollutionreporter.view.MapFragment;
import hpsaturn.pollutionreporter.view.PostsFragment;
import hpsaturn.pollutionreporter.view.RecordsFragment;
import hpsaturn.pollutionreporter.view.ScanFragment;
import hpsaturn.pollutionreporter.view.SettingsFragment;

/**
 * Created by Antonio Vanegas @hpsaturn on 6/11/18.
 */

public class MainActivity extends BaseActivity implements
        DiscreteScrollView.ScrollStateChangeListener<FragmentPickerAdapter.ViewHolder>,
        DiscreteScrollView.OnItemChangedListener<FragmentPickerAdapter.ViewHolder> {

    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.fab)
    FloatingActionButton fab;

    @BindView(R.id.coordinatorLayout)
    CoordinatorLayout coordinatorLayout;

    @BindView(R.id.fragment_picker)
    DiscreteScrollView fragmentPicker;

    private ScanFragment scanFragment;
    private EasyPreference.Builder prefBuilder;
    private ChartFragment chartFragment;
    private ServiceManager serviceManager;
    private MapFragment mapFragment;
    private RecordsFragment recordsFragment;
    private PostsFragment postsFragment;
    private SettingsFragment settingsFragment;
    private DatabaseReference mDatabase;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBleService();

        ButterKnife.bind(this);
        prefBuilder = AppData.getPrefBuilder(this);

        setSupportActionBar(toolbar);
        checkBluetoohtBle();
        setupUI();
        startDataBase();
        serviceManager = new ServiceManager(this, serviceListener);
    }

    private void startDataBase(){
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mDatabase.keepSynced(true);
    }

    private ServiceInterface serviceListener = new ServiceInterface() {

        @Override
        public void onServiceStatus(String status) {
            if (status.equals(ServiceManager.STATUS_BLE_START)) {
                showFragment(chartFragment);
            } else if (status.equals(ServiceManager.STATUS_SERVICE_OK)){
            } else if (status.equals(ServiceManager.STATUS_BLE_FAILURE)) {
                showSnackMessage(R.string.msg_device_reconnecting);
            }
        }

        @Override
        public void onServiceStart() {
        }

        @Override
        public void onServiceStop() {

        }

        @Override
        public void onSensorNotificationData(SensorData data) {
//            if (recordsFragment!=null && !recordsFragment.isShowingData()) fabUpdateLayout();
            if (chartFragment != null) chartFragment.addData(data.P25);
        }

        @Override
        public void onServiceRecord() {

        }

        @Override
        public void onServiceRecordStop() {

        }

        @Override
        public void onTracksUpdated() {
            if(recordsFragment!=null)recordsFragment.loadData();
        }

        @Override
        public void requestSensorConfigRead() {

        }

        @Override
        public void requestSensorDataRead() {

        }

        @Override
        public void onSensorConfigRead(SensorConfig config) {
            settingsFragment.configCallBack(config);
        }

        @Override
        public void onSensorDataRead(SensorData data) {

        }

        @Override
        public void onSensorConfigWrite(SensorConfig config) {

        }
    };

    private boolean isRecording(){
        return prefBuilder.getBoolean(Keys.SENSOR_RECORD, false);
    }

    private View.OnClickListener onFabClickListener = view -> {
        if (isRecording()) {
            stopRecord();
        } else {
            startRecord();
        }
    };

    private View.OnClickListener onFabShareClickListener = view -> {
        if(recordsFragment!=null)recordsFragment.shareAction();
    };

    private void stopRecord() {
        showSnackMessageSlow(R.string.msg_record_stop);
        prefBuilder.addBoolean(Keys.SENSOR_RECORD, false).save();
        serviceManager.serviceRecordStop();
        fabUpdateLayout();
    }

    private void startRecord() {
        showSnackMessageSlow(R.string.msg_record);
        prefBuilder.addBoolean(Keys.SENSOR_RECORD, true).save();
        serviceManager.serviceRecord();
        fabUpdateLayout();
    }


    private void setupUI() {
        fab.setOnClickListener(onFabClickListener);
        checkForPermissions();
        setupAppFragments();
        if(isPaired())fab.show();
    }

    public void setupAppFragments(){
        Logger.i(TAG, "setupAppFragments");
        addPostsFragment();
        addRecordsFragment();
        addMapFragment();
        addSettingsFragment();
        if(isPaired())addChartFragment();
        else addScanFragment();
        //showFragment(chartFragment);
        setupFragmentPicker();
    }

    private void setupFragmentPicker() {
        List<FragmentPickerInfo> fragmentPickerInfos = FragmentPickerData.get().getFragmentsInfo();
        fragmentPicker.setVisibility(View.VISIBLE);
        fragmentPicker.setSlideOnFling(true);
        fragmentPicker.setAdapter(new FragmentPickerAdapter(fragmentPickerInfos));
        fragmentPicker.addOnItemChangedListener(this);
        fragmentPicker.addScrollStateChangeListener(this);
        if(isPaired())fragmentPicker.scrollToPosition(2);
        else fragmentPicker.scrollToPosition(1);
        fragmentPicker.setItemTransitionTimeMillis(100);
        fragmentPicker.setItemTransformer(new ScaleTransformer.Builder().setMinScale(0.8f).build());
    }

    private void fragmentPickerHide(){
        fragmentPicker.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onCurrentItemChanged(@Nullable FragmentPickerAdapter.ViewHolder viewHolder, int position) {
        // TODO: Refactor to dinamic plus fragment to scroll view
        Logger.d(TAG, "onCurrentItemChanged: " + position);
        switch (position) {
            case 0:
                fab.hide();
                hideFragment(postsFragment);
                hideFragment(recordsFragment);
                hideFragment(settingsFragment);
                if(isPaired())hideFragment(chartFragment);
                else hideFragment(scanFragment);
                showFragment(mapFragment);
                break;
            case 1:
                fab.hide();
                hideFragment(recordsFragment);
                hideFragment(mapFragment);
                hideFragment(settingsFragment);
                if(isPaired())hideFragment(chartFragment);
                else hideFragment(scanFragment);
                showFragment(postsFragment);
                postsFragment.refresh();
                break;
            case 2:
                fabUpdateLayout();
                hideFragment(postsFragment);
                hideFragment(mapFragment);
                hideFragment(recordsFragment);
                hideFragment(settingsFragment);
                if(isPaired()) showFragment(chartFragment);
                else showFragment(scanFragment);
                break;
            case 3:
                scrollToRecordsFragment();
                break;

            case 4:
                fab.hide();
                hideFragment(postsFragment);
                hideFragment(mapFragment);
                hideFragment(recordsFragment);
                if(isPaired())hideFragment(chartFragment);
                else hideFragment(scanFragment);
                showFragment(settingsFragment);
                fragmentPickerHide();
                serviceManager.readSensorConfig();
                break;

        }
        viewHolder.showText();
    }

    private void scrollToRecordsFragment() {
        fab.hide();
        hideFragment(postsFragment);
        hideFragment(mapFragment);
        hideFragment(settingsFragment);
        if(isPaired())hideFragment(chartFragment);
        else hideFragment(scanFragment);
        showFragment(recordsFragment);
    }

    private void fabUpdateLayout() {
        fab.hide();
        if (prefBuilder.getBoolean(Keys.SENSOR_RECORD, false)) {
            fab.setBackgroundTintList(ContextCompat.getColorStateList(this,R.color.color_state_record_stop));
            fab.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.ic_stop_white_24dp));
        } else {
            fab.setBackgroundTintList(ContextCompat.getColorStateList(this,R.color.color_state_record));
            fab.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.ic_record_white_24dp));
        }
        if(isPaired())fab.show();
    }

    public void enableShareButton (){
        fab.show();
        fab.setOnClickListener(onFabShareClickListener);
        fab.setBackgroundTintList(ContextCompat.getColorStateList(this,R.color.color_state_record));
        fab.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.ic_share));
    }

    public void disableShareButton (){
        if(recordsFragment!=null)recordsFragment.setIsShowingData(false);
        fab.setOnClickListener(onFabClickListener);
        fab.hide();
    }

    private void addChartFragment() {
        if (chartFragment == null) chartFragment = ChartFragment.newInstance();
        if (chartFragment.isAdded()) return;
        addFragment(chartFragment, ChartFragment.TAG, false);
    }

    private void addScanFragment() {
        if (scanFragment == null) scanFragment = ScanFragment.newInstance();
        if (scanFragment.isAdded()) return;
        addFragment(scanFragment, ScanFragment.TAG, false);
    }

    private void addMapFragment() {
        if (mapFragment == null) mapFragment = MapFragment.newInstance();
        if (mapFragment.isAdded()) return;
        addFragment(mapFragment, MapFragment.TAG, false);
    }

    private void addRecordsFragment() {
        if (recordsFragment == null) recordsFragment = RecordsFragment.newInstance();
        if (recordsFragment.isAdded()) return;
        addFragment(recordsFragment, RecordsFragment.TAG, false);
    }

    private void addSettingsFragment() {
        if (settingsFragment == null) settingsFragment = new SettingsFragment();
        if (settingsFragment.isAdded()) return;
        addFragment(settingsFragment, SettingsFragment.TAG, false);
    }

    private void addPostsFragment() {
        if (postsFragment == null) postsFragment = PostsFragment.newInstance();
        if (postsFragment.isAdded()) return;
        addFragment(postsFragment, PostsFragment.TAG, false);
    }

    public void removeScanFragment() {
        if (scanFragment != null) removeFragment(scanFragment);
        addChartFragment();
        fabUpdateLayout();
    }

    public void showSnackMessage(int id) {
        Snackbar.make(coordinatorLayout, getString(id), Snackbar.LENGTH_LONG).setAction("Action", null).show();
    }

    public void showSnackMessageSlow(int id) {
        Snackbar.make(coordinatorLayout, getString(id), Snackbar.LENGTH_SHORT).setAction("Action", null).show();
    }

    private void startBleService() {
        Intent newIntent = new Intent(this, ServiceBLE.class);
        startService(newIntent);
        ServiceScheduler.startScheduleService(this, Config.DEFAULT_INTERVAL);
    }

    public void deviceConnect() {
        if (prefBuilder.getBoolean(Keys.DEVICE_PAIR, false)) {
            startBleService();
            showSnackMessage(R.string.msg_device_connecting);
        }
    }

    public DatabaseReference getDatabase() {
        return mDatabase;
    }

    @Override
    void onPermissionGranted() {
       if(mapFragment!=null)mapFragment.enableMyLocation();
    }

    @Override
    void actionUnPair() {
        if(isRecording()){
            showSnackMessageSlow(R.string.msg_record_stop_alert);
        } else {
            Logger.i(TAG, "[BLE] unpaired..");
            stopRecord();
            serviceManager.stop();
            prefBuilder.clearAll().save();
            fab.hide();
            if (chartFragment != null) chartFragment.clearData();
            removeFragment(chartFragment);
            addScanFragment();
            fragmentPicker.scrollToPosition(2);
            showFragment(scanFragment);
        }
    }

    @Override
    protected void onDestroy() {
        serviceManager.stop();
        serviceManager.unregister();
        super.onDestroy();
    }

    private boolean isPaired(){
        return prefBuilder.getBoolean(Keys.DEVICE_PAIR, false);
    }

    @Override
    public void onBackPressed() {
        if(settingsFragment.isVisible()){
            fragmentPicker.setVisibility(View.VISIBLE);
            fragmentPicker.scrollToPosition(3);
            scrollToRecordsFragment();
        }else{
            super.onBackPressed();
        }
    }

    @Override
    public void onScrollStart(@NonNull FragmentPickerAdapter.ViewHolder holder, int adapterPosition) {
        Logger.d(TAG, "onScrollStart");
        holder.hideText();
    }

    @Override
    public void onScrollEnd(@NonNull FragmentPickerAdapter.ViewHolder currentItemHolder, int adapterPosition) {
        Logger.d(TAG, "onScrollEnd");
    }

    @Override
    public void onScroll(float pos, int index, int newIndex, @Nullable FragmentPickerAdapter.ViewHolder holder, @Nullable FragmentPickerAdapter.ViewHolder newHolder) {

    }

    public void addTrackToMap(SensorTrackInfo trackInfo) {
        if(mapFragment!=null)mapFragment.addMarker(trackInfo);
    }

    public ServiceManager getServiceManager() {
        return serviceManager;
    }
}
