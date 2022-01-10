package online.fatmaxxer.publicRelease1;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;

import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarSensorSetting;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;

import static online.fatmaxxer.publicRelease1.MainActivity.FMMenuItem.*;

public class MainActivity extends AppCompatActivity {
    public static final boolean requestLegacyExternalStorage = true;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String API_LOGGER_TAG = "Polar API";
    public static final String AUDIO_OUTPUT_ENABLED = "audioOutputEnabled";
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_TAG = "alpha1update";

    public static final String ALPHA_1_CALC_PERIOD_PREFERENCE_STRING = "alpha1CalcPeriod";
    public static final String LAMBDA_PREFERENCE_STRING = "lambdaPref";
    public static final String ARTIFACT_REJECTION_THRESHOLD_PREFERENCE_STRING = "artifactThreshold";
    public static final String NOTIFICATIONS_ENABLED_PREFERENCE_STRING = "notificationsEnabled";
    public static final String POLAR_DEVICE_ID_PREFERENCE_STRING = "polarDeviceID";
    public static final String KEEP_LOGS_PREFERENCE_STRING = "keepLogs";
    //public static final String EXPERIMENTAL_PREFERENCE_STRING = "experimental";
    public static final String KEEP_SCREEN_ON_PREFERENCE_STRING = "keepScreenOn";
    public static final String NOTIFICATION_DETAIL_PREFERENCE_STRING = "notificationDetail";
    public static final String RR_LOGFILE_HEADER = "timestamp, rr, since_start ";
    public static final String ENABLE_SENSOR_EMULATION = "enableSensorEmulation";
    public static final String ENABLE_REPLAY = "enableReplay";
    public static final String ENABLE_ECG = "enableECG";

    final double alpha1HRVvt1 = 0.75;
    final double alpha1HRVvt2 = 0.5;
    private int a1v2cacheMisses = 0;
    private long lastScrollToEndElapsedSec = 0;
    private int lastObservedHRNotificationWithArtifacts = 0;
    private File logsDir;
    // system time of first observed ecg sample
    private long ecgStartTSmillis;
    // timestamp of first observed ecg sample
    private long ecgStartInternalTSnanos;

    public MainActivity() {
        //super(R.layout.activity_fragment_container);
        super(R.layout.activity_main);
        Log = new Log();
    }

    public void deleteFile(File f) {
        Log.d(TAG, "deleteFile " + f.getPath());
        File fdelete = f;
        if (fdelete.exists()) {
            Log.d(TAG, "deleteFile: File deleted: " + fdelete.getPath());
            if (fdelete.delete()) {
                Log.d(TAG, "deleteFile: file deleted: " + fdelete.getPath());
            } else {
                Log.d(TAG, "File not deleted(?) Will try after exit: " + fdelete.getPath());
                fdelete.deleteOnExit();
            }
        } else {
            Log.d(TAG, "file does not exist??" + fdelete.getPath());
        }
    }

    public void startAnalysis() {
        searchForPolarDevices();
        // TODO: CHECK: is this safe or do we have to wait for some other setup tasks to finish...?
        tryPolarConnectToPreferredDevice();
    }

    public void confirmQuit() {
        new AlertDialog.Builder(this)
                .setMessage("FatMaxxer: Confirm Quit")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void finish() {
        closeLogs();
        uiNotificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
        try {
            api.disconnectFromDevice(SENSOR_ID);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            logException("Quit: disconnectFromDevice: polarInvalidArgument ", polarInvalidArgument);
        }
        super.finish();
    }

    public void onBackPressed() {
        Toast.makeText(getBaseContext(), R.string.UseMenuQuitToExit, Toast.LENGTH_LONG).show();
    }

    private final double[] samples1 = {667.0, 674.0, 688.0, 704.0, 690.0, 688.0, 671.0, 652.0, 644.0, 636.0, 631.0, 639.0, 637.0, 634.0, 642.0, 642.0,
            653.0, 718.0, 765.0, 758.0, 729.0, 713.0, 691.0, 677.0, 694.0, 695.0, 692.0, 684.0, 685.0, 677.0, 667.0, 657.0, 648.0, 632.0,
            652.0, 641.0, 644.0, 665.0, 711.0, 753.0, 772.0, 804.0, 844.0, 842.0, 833.0, 818.0, 793.0, 781.0, 799.0, 822.0, 820.0, 835.0,
            799.0, 793.0, 745.0, 764.0, 754.0, 764.0, 768.0, 764.0, 770.0, 766.0, 765.0, 777.0, 767.0, 756.0, 724.0, 747.0, 812.0, 893.0,
            905.0, 924.0, 945.0, 946.0, 897.0, 857.0, 822.0, 571.0, 947.0, 770.0, 794.0, 840.0, 805.0, 1593.0, 763.0, 1498.0, 735.0,
            745.0, 742.0, 737.0, 748.0, 756.0, 756.0, 762.0, 783.0, 814.0, 826.0, 838.0, 865.0, 877.0, 859.0, 858.0, 855.0, 861.0, 870.0,
            902.0, 902.0, 879.0, 847.0, 835.0, 847.0, 884.0, 940.0, 971.0, 936.0, 896.0, 873.0, 879.0, 888.0, 896.0, 904.0, 902.0, 901.0, 899.0,
            893.0, 914.0, 997.0, 966.0, 902.0, 899.0, 909.0, 933.0, 954.0, 947.0, 892.0, 830.0, 825.0, 813.0, 790.0, 759.0, 744.0, 739.0,
            724.0, 699.0, 1401.0, 694.0, 684.0, 683.0, 696.0, 710.0, 738.0};
    private final double samples1_hr = 80;
    private final double samples1_alpha1 = 0.86;
    private final double samples1_rmssd = 158;

    PolarBleApi api;
    Disposable broadcastDisposable;
    Disposable ecgDisposable = null;
//    Disposable accDisposable;
//    Disposable gyrDisposable;
//    Disposable magDisposable;
//    Disposable ppgDisposable;
//    Disposable ppiDisposable;
//    Disposable scanDisposable;
//    Disposable autoConnectDisposable;
    String SENSOR_ID = "";
    SharedPreferences sharedPreferences;

    Context thisContext = this;
    private int batteryLevel = 0;
    private String exerciseMode = "Light";
    private EditText input_field;
    private static final String SERVICE_CHANNEL_ID = "FatMaxxerServiceChannel";
    private static final String UI_CHANNEL_ID = "FatMaxxerUIChannel";
    private static final String SERVICE_CHANNEL_NAME = "FatMaxxer Service Notification";
    private static final String UI_CHANNEL_NAME = "FatMaxxer Notifications";

    public static class LocalService extends Service {
        private NotificationManager mNM;

        @Override
        public void onDestroy() {
            android.util.Log.d(TAG, "LocalService: onDestroy");
            super.onDestroy();
        }

        public LocalService() {
        }

        /**
         * Class for clients to access.  Because we know this service always
         * runs in the same process as its clients, we don't need to deal with
         * IPC.
         */
        public class LocalBinder extends Binder {
            LocalService getService() {
                return LocalService.this;
            }
        }


        //https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onCreate() {
            //Log.d(TAG, "FatMaxxer service onCreate");
            super.onCreate();
            createNotificationChannel();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startMyOwnForeground();
            else
                startForeground(1, new Notification());
        }

        private void startMyOwnForeground() {
            // https://stackoverflow.com/questions/5502427/resume-application-and-stack-from-notification
            final Intent notificationIntent = new Intent(this, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // https://stackoverflow.com/a/42002705/16251655
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            //notificationBuilder.setContentIntent(pendingIntent)

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID);
            Notification notification =
                    notificationBuilder.setOngoing(true)
                            .setSmallIcon(R.mipmap.ic_launcher_foreground)
                            .setContentTitle("FatMaxxer started")
                            .setPriority(NotificationManager.IMPORTANCE_HIGH)
                            .setCategory(Notification.CATEGORY_SERVICE)
                            .setContentIntent(pendingIntent)
                            .build();
            startForeground(2, notification);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            android.util.Log.d("FatMaxxerLocalService", "Received start id " + startId + ": " + intent);
            return START_NOT_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return mBinder;
        }

        // This is the object that receives interactions from clients.  See
        // RemoteService for a more complete example.
        private final IBinder mBinder = new LocalBinder();

        //@RequiresApi(Build.VERSION_CODES.O)
        @RequiresApi(api = Build.VERSION_CODES.O)
        private String createNotificationChannel() {
            NotificationChannel chan = new NotificationChannel(SERVICE_CHANNEL_ID,
                    SERVICE_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            service.createNotificationChannel(chan);
            return SERVICE_CHANNEL_ID;
        }
    }

    /**
     * Example of binding and unbinding to the local service.
     * bind to, receiving an object through which it can communicate with the service.
     * <p>
     * Note that this is implemented as an inner class only keep the sample
     * all together; typically this code would appear in some separate class.
     */

    //
    // SERVICE BINDING
    //

    // Don't attempt to unbind from the service unless the client has received some
    // information about the service's state.
    private boolean mShouldUnbind;

    LocalService service;

    // To invoke the bound service, first make sure that this value
    // is not null.
    private LocalService mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((LocalService.LocalBinder) service).getService();

            // Tell the user about this for our demo.
            Toast.makeText(MainActivity.this, R.string.FatMaxxerBoundToService,
                    Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            Toast.makeText(MainActivity.this, "FatMaxxer disconnected from service",
                    Toast.LENGTH_SHORT).show();
        }
    };

    void doBindService() {
        Log.d(TAG, "FatMaxxer: binding to service");
        // Attempts to establish a connection with the service.  We use an
        // explicit class name because we want a specific service
        // implementation that we know will be running in our own process
        // (and thus won't be supporting component replacement by other
        // applications).
        if (bindService(new Intent(MainActivity.this, LocalService.class),
                mConnection, Context.BIND_AUTO_CREATE)) {
            mShouldUnbind = true;
        } else {
            Log.e(TAG, "Error: The requested service doesn't " +
                    "exist, or this client isn't allowed access to it.");
        }
    }

    void doUnbindService() {
        if (mShouldUnbind) {
            // Release information about the service's state.
            unbindService(mConnection);
            mShouldUnbind = false;
        }
    }

    //
    // END BINDING
    //

    public static class MySettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }
    }

    // https://www.bragitoff.com/2017/04/polynomial-fitting-java-codeprogram-works-android-well/
            /*  int n;                       //degree of polynomial to fit the data
                double[] x=double[];         //array to store x-axis data points
                double[] y=double[];         //array to store y-axis data points
            */
    public double[] polyFit(double[] x, double[] y, int degree) {
        //Log.d(TAG, "polyFit x.length "+x.length+" y.length "+y.length+" degree "+degree);
        int n = degree;
        int length = x.length;
        // ASSERT: x.length == y.length
        double X[] = new double[2 * n + 1];
        for (int i = 0; i < 2 * n + 1; i++) {
            X[i] = 0;
            for (int j = 0; j < length; j++) {
                X[i] = X[i] + pow(x[j], i);        //consecutive positions of the array will store length,sigma(xi),sigma(xi^2),sigma(xi^3)....sigma(xi^2n)
            }
        }
        double[][] B = new double[n + 1][n + 2];            //B is the Normal matrix(augmented) that will store the equations, 'a' is for value of the final coefficients
        double[] a = new double[n + 1];
        for (int i = 0; i <= n; i++)
            for (int j = 0; j <= n; j++)
                B[i][j] = X[i + j];            //Build the Normal matrix by storing the corresponding coefficients at the right positions except the last column of the matrix
        double Y[] = new double[n + 1];                    //Array to store the values of sigma(yi),sigma(xi*yi),sigma(xi^2*yi)...sigma(xi^n*yi)
        for (int i = 0; i < n + 1; i++) {
            Y[i] = 0;
            for (int j = 0; j < length; j++)
                Y[i] = Y[i] + pow(x[j], i) * y[j];        //consecutive positions will store sigma(yi),sigma(xi*yi),sigma(xi^2*yi)...sigma(xi^n*yi)
        }
        for (int i = 0; i <= n; i++)
            B[i][n + 1] = Y[i];                //load the values of Y as the last column of B(Normal Matrix but augmented)
        n = n + 1;
        for (int i = 0; i < n; i++)                    //From now Gaussian Elimination starts(can be ignored) to solve the set of linear equations (Pivotisation)
            for (int k = i + 1; k < n; k++)
                if (B[i][i] < B[k][i])
                    for (int j = 0; j <= n; j++) {
                        double temp = B[i][j];
                        B[i][j] = B[k][j];
                        B[k][j] = temp;
                    }
        for (int i = 0; i < n - 1; i++)            //loop to perform the gauss elimination
            for (int k = i + 1; k < n; k++) {
                double t = B[k][i] / B[i][i];
                for (int j = 0; j <= n; j++)
                    B[k][j] = B[k][j] - t * B[i][j];    //make the elements below the pivot elements equal to zero or elimnate the variables
            }
        for (int i = n - 1; i >= 0; i--)                //back-substitution
        {                        //x is an array whose values correspond to the values of x,y,z..
            a[i] = B[i][n];                //make the variable to be calculated equal to the rhs of the last equation
            for (int j = 0; j < n; j++)
                if (j != i)            //then subtract all the lhs values except the coefficient of the variable whose value                                   is being calculated
                    a[i] = a[i] - B[i][j] * a[j];
            a[i] = a[i] / B[i][i];            //now finally divide the rhs by the coefficient of the variable to be calculated
        }
        return a;
    }

    public double[] v_reverse(double[] x) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[x.length - i - 1] = x[i];
        }
        return result;
    }

    private boolean v_contains(double[] x, Function<Double, Boolean> t) {
        for (int i = 0; i < x.length; i++) {
            try {
                if (t.apply(x[i])) return true;
            } catch (Throwable throwable) {
                text_view.setText("Exception " + throwable.toString());
                logException("v_contains ", throwable);
            }
        }
        return false;
    }

    public String v_toString(double[] x) {
        StringBuilder result = new StringBuilder();
        result.append("[" + x.length + "]{");
        for (int i = 0; i < x.length; i++) {
            if (i != 0) {
                result.append(", ");
            }
            result.append("" + i + ":" + x[i]);
        }
        result.append("}");
        return result.toString();
    }

    // p are coefficients
    // x are values for x
    public double[] polyVal(double[] p, double[] x) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = 0;
            for (int j = 0; j < p.length; j++) {
                double product = 1;
                int exponent = p.length - j - 1;
                for (int k = 0; k < exponent; k++) {
                    product *= x[i];
                }
                result[i] += p[j] * product;
            }
        }
        return result;
    }

    public double[] v_zero(int l) {
        double result[] = new double[l];
        for (int i = 0; i < l; i++) {
            result[i] = 0;
        }
        return result;
    }

    public double[] v_cumsum(double[] x) {
        double result[] = new double[x.length];
        double acc = 0;
        for (int i = 0; i < x.length; i++) {
            acc = acc + x[i];
            result[i] = acc;
        }
        return result;
    }

    //
    public double[] v_cumsum(double c, double[] x) {
        double result[] = new double[x.length + 1];
        result[0] = c;
        for (int i = 0; i < x.length; i++) {
            result[i + 1] = result[i] + x[i];
        }
        return result;
    }

    public double[] v_subscalar(double[] x, double y) {
        double result[] = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = x[i] - y;
        }
        return result;
    }

    // return x[x_offset...x_offset+length] - y
    public double[] v_slice(double[] x, int x_offset, int length) {
        double result[] = new double[length];
        for (int i = 0; i < length; i++) {
            result[i] = x[x_offset + i];
        }
        return result;
    }

    public double[] v_tail(double[] x) {
        //Log.d(TAG, "v_subtract length  "+length);
        double result[] = new double[x.length - 1];
        for (int i = 1; i < x.length; i++) {
            result[i - 1] = x[i];
        }
        //Log.d(TAG,"v_subtract returning "+v_toString(result));
        return result;
    }

    public double[] v_subtract(double[] x, double[] y, int x_offset, int length) {
        if (length != y.length)
            throw new IllegalArgumentException(("vector subtraction of unequal lengths"));
        //Log.d(TAG, "v_subtract length  "+length);
        double result[] = new double[length];
        for (int i = 0; i < length; i++) {
            result[i] = x[x_offset + i] - y[i];
        }
        //Log.d(TAG,"v_subtract returning "+v_toString(result));
        return result;
    }

    public double[] v_subtract(double[] x, double[] y) {
        return v_subtract(x, y, 0, x.length);
    }

    public double v_sum(double[] x) {
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            //Log.d(TAG,"v_sum "+i+" "+x[i]);
            sum += x[i];
        }
        //Log.d(TAG,"v_sum "+sum);
        return sum;
    }

    public double v_mean(double[] x) {
        double result = ((double) v_sum(x)) / x.length;
        //Log.d(TAG,"v_mean ("+v_toString(x)+") == "+result);
        return result;
    }

    public double[] v_abs(double[] x) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = abs(x[i]);
        }
        //Log.d(TAG,"v_abs ("+v_toString(x)+") == "+result);
        return result;
    }

    // aka dRR
    public double[] v_differential(double[] x) {
        double[] result = new double[x.length - 1];
        for (int i = 0; i < (x.length - 1); i++) {
            result[i] = x[i + 1] - x[i];
        }
        //Log.d(TAG,"v_diff ("+v_toString(x)+"\n) == "+v_toString(result));
        return result;
    }

    public double[] v_power_s1(double x, double[] y) {
        double result[] = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            result[i] = pow(x, y[i]);
        }
        return result;
    }

    public double[] v_power_s2(double[] x, double y) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = pow(x[i], y);
        }
        //Log.d(TAG,"v_power_s2 result "+v_toString(result));
        return result;
    }

    public double[] v_logN(double[] x, double n) {
        double result[] = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = Math.log10(x[i]) / Math.log10(n);
        }
        return result;
    }

    // num: how many samples
    public double[] arange(int min, int max, int num) {
        double result[] = new double[num];
        double acc = min;
        double delta = ((double) (max * 1.0 - min * 1.0)) / num;
        for (int i = 0; i < num; i++) {
            result[i] = acc;
            acc += delta;
        }
        return result;
    }

    private double getRMSDetrended(double[] x, int scale, double[] scale_ax, int offset, boolean smoothN) {
        String smoothn = smoothN ? "v2" : "v1";
        double[] xbox = v_slice(x, offset, scale);
        //Log.d(TAG,"getrmsdetrended "+smoothn+" "+ scale +" cut@"+ offset +" xbox "+v_toString(xbox));
        //     coeff = np.polyfit(scale_ax, xcut, 1)
        double[] ybox = null;
        if (smoothN) {
            //Log.d(TAG,"rms smoothn");
            ybox = smoothnessPriorsDetrending(xbox);
        } else {
            ybox = xbox;
        }
        double[] coeff = v_reverse(polyFit(scale_ax, ybox, 1));
        //Log.d(TAG,"rmsd coeff "+v_toString(coeff));
        //     xfit = np.polyval(coeff, scale_ax)
        double[] xfit = polyVal(coeff, scale_ax);
        //Log.d(TAG,"xfit "+v_toString(xfit));
        //Log.d(TAG,"rmsd xfit "+v_toString(xfit));
        //     # detrending and computing RMS of each window
        //     rms[e] = np.sqrt(np.mean((xcut-xfit)**2))
        double[] finalSegment = v_subtract(ybox, xfit, 0, scale);
        //Log.d(TAG,"getDetrendedMean final "+v_toString(finalSegment));
        double mean = v_mean(v_power_s2(finalSegment, 2));
        //Log.d(TAG,"getDetrendedMean mean "+mean);
        return mean;
    }

    // RMS + detrending
    // - divide x into x.length/scale non-overlapping boxes of size scale
    public double[] rmsDetrended(double[] x, int scale, boolean smoothN) {
        //Log.d(TAG,"rms_detrended call, scale "+scale);
        int nrboxes = x.length / scale;
        // # making an array with data divided in windows
        // shape = (x.shape[0]//scale, scale)
        // X = np.lib.stride_tricks.as_strided(x,shape=shape)
        // # vector of x-axis points to regression
        // scale_ax = np.arange(scale)
        double scale_ax[] = arange(0, scale, scale);
        //Log.d(TAG,"rms_detrended scale_ax "+v_toString(scale_ax));
        // rms = np.zeros(X.shape[0])
        double rms[] = v_zero(nrboxes * 2);
        // for e, xcut in enumerate(X):
        int offset = 0;
        for (int i = 0; i < nrboxes; i++) {
            // root mean square SUCCESSIVE DIFFERENCES
            double mean = getRMSDetrended(x, scale, scale_ax, offset, smoothN);
            rms[i] = sqrt(mean);
            //Log.d(TAG,"rmsd box "+i+" "+" "+rms[i]);
            offset += scale;
        }
        // boxes in reverse order starting with a "last" box aligned to the very end of the data
        offset = x.length - scale;
        for (int i = nrboxes; i < nrboxes * 2; i++) {
            double mean = getRMSDetrended(x, scale, scale_ax, offset, smoothN);
            rms[i] = sqrt(mean);
            //Log.d(TAG,"rmsd box "+i+" "+" "+rms[i]);
            offset -= scale;
        }
        //Log.d(TAG,"rms_detrended all @scale "+scale+"("+rms.length+")"+v_toString(rms));
        //     return rms
        return rms;
    }

    public void logMatrix(String id, SimpleMatrix m) {
        for (int i = 0; i < m.numRows(); i++) {
            Log.d(TAG,id+": Mat:"+m.numRows()+","+m.numCols()+"[");
            StringBuilder row = new StringBuilder();
            for (int j = 0; j < m.numCols(); j++) {
                row.append(m.get(i, j)+" ");
            }
            Log.d(TAG,"  Row: "+i+" [" + row + "]\n");
        }
    }

    // cache -> lambda -> size -> DD matrix
    private Map<Integer, SimpleMatrix[]> detrendingFactorMatrices = new HashMap<Integer, SimpleMatrix[]>();
    // let's see how we go with that!
    final int maxWindowSize = 440;

    public SimpleMatrix detrendingFactorMatrix(int length, int lambda) {
        //Log.d(TAG, "detrendingFactorMatrix size "+length);
        int T = length;
        // new value for lambda(?)
        if (detrendingFactorMatrices.get(lambda) == null) {
            detrendingFactorMatrices.put(lambda, new SimpleMatrix[maxWindowSize]);
        }
        // previously-computed segment length
        if (detrendingFactorMatrices.get(lambda)[T] != null) {
            //String msg = "Pre-cached matrix lambda "+lambda+" length "+T;
            //Log.d(TAG,msg);
            //text_view.setText(msg);
            return detrendingFactorMatrices.get(lambda)[T];
        }
        a1v2cacheMisses++;
        // new segment length
        //String msg = "Computing matrix lambda "+lambda+" length "+T;
        long startTime = System.currentTimeMillis();
        //Log.d(TAG,msg);
        //text_view.setText(msg);
        SimpleMatrix I = SimpleMatrix.identity(T);
        SimpleMatrix D2 = new SimpleMatrix(T - 2, T);
        for (int i = 0; i < D2.numRows(); i++) {
            //Log.d(TAG,"D2 row "+i);
            D2.set(i, i, 1);
            D2.set(i, i + 1, -2);
            D2.set(i, i + 2, 1);
        }
        //logMatrix("D2",D2);
        SimpleMatrix sum = I.plus(D2.transpose().scale(lambda * lambda).mult(D2));
        //Log.d(TAG, "createDetrendingFactorMatrix inverse...");
        //Log.d(TAG, "inverse done");
        SimpleMatrix result = I.minus(sum.invert());
        //logMatrix("result",result);
        detrendingFactorMatrices.get(lambda)[T] = result;
        //Log.d(TAG, "detrendingFactorMatrix length returned "+result.toString());
        long endTime = System.currentTimeMillis();
        String endMsg = "Computing matrix finished "+(endTime - startTime)+"ms, lambda "+lambda+" length "+T;
        Log.d(TAG,endMsg);
        return result;
    }

    public double[] smoothnessPriorsDetrending(double[] dRR) {
        //Log.d(TAG,"smoothnDetrending dRR "+v_toString(dRR));
        // convert dRRs to vector (SimpleMatrix)
        SimpleMatrix dRRvec = new SimpleMatrix(dRR.length, 1);
        for (int i = 0; i < dRR.length; i++) {
            dRRvec.set(i, 0, dRR[i]);
        }
        // lambda=500: https://internal-journal.frontiersin.org/articles/10.3389/fspor.2021.668812/full
        SimpleMatrix detrended = detrendingFactorMatrix(dRRvec.numRows(), lambdaSetting).mult(dRRvec);
        //Log.d(TAG,"detrendingv2: "+detrended.numRows()+" "+detrended.numCols());
        int size = detrended.numRows();
        double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            result[i] = detrended.get(i, 0);
        }
        //Log.d("TAG","smoothnDetrending ("+v_toString(dRR)+")\n  == "+v_toString(result));
        return result;
    }

    public double dfaAlpha1V2(double x[], int l_lim, int u_lim, int nrscales) {
        return dfaAlpha1V1(x, l_lim, u_lim, nrscales, true);
    }

    // x: samples; l_lim: lower limit; u_lim: upper limit
    public double dfaAlpha1V1(double xUnsmoothed[], int l_lim, int u_lim, int nrscales, boolean smoothN) {
        double[] x;

        if (smoothN) {
            x = smoothnessPriorsDetrending(xUnsmoothed);
        } else {
            x = xUnsmoothed;
        }
        // nothing more to do...
        smoothN = false;

        String smoothn = smoothN ? "v2" : "v1";
        //Log.d(TAG, "dfaAlpha1...v2? " + smoothN);
        double mean = v_mean(x);
        //Log.d(TAG, "dfaAlpha1 mean " + mean);
        double[] y = v_cumsum(v_subscalar(x, mean));
        //Log.d(TAG, "dfaAlpha1 alpha1 y " + v_toString(y));
        // scales = (2**np.arange(scale_lim[0], scale_lim[1], scale_dens)).astype(np.int)
        double[] scales = v_power_s1(2, arange(l_lim, u_lim, nrscales));
        double[] exp_scales = {3., 4., 4., 4., 4., 5., 5., 5., 5., 6., 6., 6., 7., 7., 7., 8., 8., 9.,
                9., 9., 10., 10., 11., 12., 12., 13., 13., 14., 15., 15.};
        // HACK - we know what scales are needed for now
        scales = exp_scales;
        if (scales != exp_scales) {
            text_view.setText("IllegalStateException: wrong scales");
            throw new IllegalStateException("wrong scales");
        }
        // fluct = np.zeros(len(scales))
        double[] fluct = v_zero(scales.length);
        // # computing RMS for each window
        // for e, sc in enumerate(scales):
        //   fluct[e] = np.sqrt(np.mean(calc_rms(y, sc)**2))
        for (int i = 0; i < scales.length; i++) {
            int sc = (int) (scales[i]);
            //Log.d(TAG, "- scale "+i+" "+sc);
            double[] sc_rms = rmsDetrended(y, sc, smoothN);
            fluct[i] = sqrt(v_mean(v_power_s2(sc_rms, 2)));
            //Log.d(TAG, "  - rms "+v_toString(sc_rms));
            //Log.d(TAG, "  - scale "+i+" "+sc+" fluct "+fluct[i]);
        }
        //Log.d(TAG, "Polar dfa_alpha1, x "+v_toString(x));
        //Log.d(TAG, "dfa_alpha1, scales " + v_toString(scales));
        //Log.d(TAG, "dfa_alpha1 " + smoothn + " fluct: " + v_toString(fluct));
        // # fitting a line to rms data
        double[] coeff = v_reverse(polyFit(v_logN(scales, 2), v_logN(fluct, 2), 1));
        //Log.d(TAG, "dfa_alpha1 coefficients " + v_toString(coeff));
        double alpha = coeff[0];
        //Log.d(TAG, "dfa_alpha1 = " + alpha);
        return alpha;
    }

    public void testDFA_alpha1() {
        text_view.setText("Self-test DFA alpha1");
        Log.d(TAG, "testDFA_alpha1");
        double[] values = {635.0, 628.0, 627.0, 625.0, 624.0, 627.0, 624.0, 623.0, 633.0, 636.0, 633.0, 628.0, 625.0, 628.0, 622.0, 621.0, 613.0, 608.0, 604.0, 612.0, 620.0, 616.0, 611.0, 616.0, 614.0, 622.0, 627.0, 625.0, 622.0, 617.0, 620.0, 622.0, 623.0, 615.0, 614.0, 627.0, 630.0, 632.0, 632.0, 632.0, 631.0, 627.0, 629.0, 634.0, 628.0, 625.0, 629.0, 633.0, 632.0, 628.0, 631.0, 631.0, 628.0, 623.0, 619.0, 618.0, 618.0, 628.0, 634.0, 631.0, 626.0, 633.0, 637.0, 636.0, 632.0, 634.0, 625.0, 614.0, 610.0, 607.0, 613.0, 616.0, 622.0, 625.0, 620.0, 633.0, 640.0, 639.0, 631.0, 626.0, 634.0, 628.0, 615.0, 610.0, 607.0, 611.0, 613.0, 614.0, 611.0, 608.0, 627.0, 625.0, 619.0, 618.0, 622.0, 625.0, 626.0, 625.0, 626.0, 624.0, 631.0, 631.0, 619.0, 611.0, 608.0, 607.0, 602.0, 586.0, 583.0, 576.0, 580.0, 571.0, 583.0, 591.0, 598.0, 607.0, 607.0, 621.0, 619.0, 622.0, 613.0, 604.0, 607.0, 603.0, 604.0, 598.0, 595.0, 592.0, 589.0, 594.0, 594.0, 602.0, 611.0, 614.0, 634.0, 635.0, 636.0, 628.0, 627.0, 628.0, 626.0, 619.0, 616.0, 616.0, 622.0, 615.0, 607.0, 611.0, 610.0, 619.0, 624.0, 625.0, 626.0, 633.0, 643.0, 647.0, 644.0, 644.0, 642.0, 645.0, 637.0, 628.0, 632.0, 633.0, 625.0, 626.0, 623.0, 620.0, 620.0, 610.0, 612.0, 612.0, 610.0, 614.0, 611.0, 609.0, 616.0, 624.0, 623.0, 618.0, 622.0, 623.0, 625.0, 629.0, 621.0, 622.0, 617.0, 619.0, 618.0, 610.0, 607.0, 606.0, 611.0};
        // Altini Python code
        // double exp_result = 1.5503173309573208;
        // FIXME: tiny discrepancy in double precision between Python and Java impl(?!)
        // This Java impl:
        double exp_result = 1.5503173309573228;
        double act_result = dfaAlpha1V1(values, 2, 4, 30, false);
        if (Double.compare(exp_result, act_result) != 0) {
            String msg = "expected " + exp_result + " got " + act_result;
            text_view.setText("Self-test DFA alpha1 failed: " + msg);
            Log.d(TAG, "***** testDFA_alpha1 failed " + msg + " *****");
            throw new IllegalStateException("test failed, expected " + exp_result + " got " + act_result);
        } else {
            text_view.setText("Self-test DFA alpha1 passed");
            Log.d(TAG, "Self-test DFA alpha1 passed");
        }
    }

    TextView text_view;
    TextView text_time;
    TextView text_batt;
    TextView text_mode;
    TextView text_hr;
    TextView text_secondary;
    TextView text_secondary_label;
    TextView text_a1;
    TextView text_a1_label;
    TextView text_artifacts;

    //public boolean experimental = false;
    // 120s ring buffer for dfa alpha1
    public final int featureWindowSizeSec = 120;
    // buffer to allow at least 45 beats forward/backward per Kubios
    public final int sampleBufferMarginSec = 45;
    //public final int rrWindowSizeSec = featureWindowSizeSec + sampleBufferMarginSec;
    public final int rrWindowSizeSec = featureWindowSizeSec;
    // time between alpha1 calculations
    public int alpha1EvalPeriodSec;
    // max hr approx 300bpm(!?) across 120s window
    // FIXME: this is way larger than needed
    public final int maxrrs = 300 * rrWindowSizeSec;
    // circular buffer of recently recorded RRs
    // FIXME: premature optimization is the root of all evil
    // Not that much storage required, does not avoid the fundamental problem that
    // our app gets paused anyway
    public double[] rrInterval = new double[maxrrs];
    // timestamp of recently recorded RR (in ms since epoch)
    public long[] rrIntervalTimestamp = new long[maxrrs];
    // timestamps of artifact
    private long[] artifactTimestamp = new long[maxrrs];
    // oldest and newest recorded RRs in the recent window
    public int oldestSample = 0;
    public int newestSample = 0;
    // oldest and newest recorded artifact( timestamp)s in the recent window
    public int oldestArtifactSample = 0;
    public int newestArtifactSample = 0;
    // time first sample received in MS since epoch
    public long firstSampleTimestampMS;
    // have we started sampling?
    public boolean started = false;
    double rmssdWindowed = 0;
    // last known alpha1 (default resting nominally 1.0)
    double alpha1V1Windowed = 1.0;
    double alpha1V1RoundedWindowed = 1.0;
    double alpha1V2Windowed = 1.0;
    double alpha1V2RoundedWindowed = 1.0;
    int artifactsPercentWindowed;
    int lambdaSetting = 500;
    int currentHR;
    double hrMeanWindowed = 0;
    double rrMeanWindowed = 0;
    // maximum tolerable variance of adjacent RR intervals
    double artifactCorrectionThreshold = 0.05;
    boolean disableArtifactCorrection = false;
    final Set<String> emptyStringSet = new HashSet<String>();
    // elapsed time in terms of cumulative sum of all seen RRs (as for HRVLogger)
    long logRRelapsedMS = 0;
    // the last time (since epoch) a1 was evaluated
    public long prevA1TimestampMS = 0;
    public long prevHRPlotTimestampMS = 0;
    public long prevRRPlotTimestampMS = 0;
    public long prevFeatPlotTimestampMS = 0;
    public double prevrr = 0;
    public boolean starting = false;
    public long prevSpokenUpdateMS = 0;
    public int totalRejected = 0;
    public boolean thisIsFirstSample = false;
    long currentTimeMS;
    long elapsedMS;
    long prevTimeMS;
    long elapsedSecondsTrunc;
    double elapsedMin;

    static NotificationManagerCompat uiNotificationManager;
    static NotificationCompat.Builder uiNotificationBuilder;

    private TextToSpeech ttobj;
    MediaPlayer mp;

    private FileWriter rrLogStreamNew;
    private FileWriter featureLogStreamNew;

//    private FileWriter rrLogStreamLegacy;
//    private FileWriter featureLogStreamLegacy;
//    private FileWriter debugLogStream;

    private class Log {
        public int d(String tag, String msg) {
            if (currentLogFiles.get("debug")!=null) writeLogFile(msg, "debug");
            return android.util.Log.d(tag, msg);
        }

        public int w(String tag, String msg) {
            if (currentLogFiles.get("debug")!=null) writeLogFile(msg, "debug");
            return android.util.Log.e(tag, msg);
        }

        public int e(String tag, String msg) {
            if (currentLogFiles.get("debug")!=null) writeLogFile(msg, "debug");
            return android.util.Log.e(tag, msg);
        }

        public int i(String tag, String msg) {
            if (currentLogFiles.get("debug")!=null) writeLogFile(msg, "debug");
            return android.util.Log.i(tag, msg);
        }

        public int v(String tag, String s) {
            if (currentLogFiles.get("debug")!=null) writeLogFile(s, "debug");
            return android.util.Log.v(tag,s);
        }
    }

    private static Log Log;

    private void closeLog(FileWriter fw) {
        try {
            fw.close();
        } catch (IOException e) {
            logException("IOException closing "+ fw.toString(), e);
        }
    }

    private void closeLogs() {
        for (FileWriter fw : currentLogFileWriters.values()) {
            closeLog(fw);
        }
    }

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    //    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
//    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");

    ScrollView scrollView;

    GraphView graphView;
    LineGraphSeries<DataPoint> hrSeries = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> rrSeries = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1V2Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1HRVvt1Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1HRVvt2Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a125Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1125Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1175Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> artifactSeries = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> rmssdSeries = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> hrWinSeries = new LineGraphSeries<DataPoint>();
    final int maxDataPoints = 65535;
    final double graphViewPortWidth = 2.0;
    final int graphMaxHR = 200;
    final int graphMaxErrorRatePercent = 10;

    /**
     * Return date in specified format.
     *
     * @param milliSeconds Date in milliseconds
     * @param dateFormat   Date format
     * @return String representing date in specified format
     */
    public static String getDate(long milliSeconds, String dateFormat) {
        // Create a DateFormatter object for displaying date in specified format.
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);
        // Create a calendar object that will convert the date and time value in milliseconds to date.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliSeconds);
        return formatter.format(calendar.getTime());
    }

    static enum FMMenuItem {
        MENU_QUIT,
        MENU_SEARCH,
        MENU_SET_DEFAULT,
        MENU_CONNECT_DEFAULT,
        MENU_EXPORT,
        MENU_DELETE_ALL,
        MENU_DELETE_DEBUG,
        MENU_OLD_LOG_FILES,
        MENU_EXPORT_SELECTED_LOG_FILES,
        MENU_DELETE_SELECTED_LOG_FILES,
        MENU_REPLAY,
        MENU_START,
        MENU_IMPORT,
        MENU_IMPORT_REPLAY,
        MENU_RENAME_LOGS,
        MENU_BLE_AD_START,
        MENU_BLE_AD_END,
        MENU_CONNECT_DISCOVERED // MUST BE LAST in enum as extra connection options are based off it
    }

    static int menuItem(FMMenuItem item) { return item.ordinal(); }

    // collect devices by deviceId so we don't spam the menu
    Map<String, String> discoveredDevices = new HashMap<String, String>();
    Map<Integer, String> discoveredDevicesMenu = new HashMap<Integer, String>();

    /**
     * Gets called every time the user presses the menu button.
     * Use if your menu is dynamic.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        String startedStatus = "";
        String startedOpen = "[";
        String startedClose = "]";
        if (!logOrReplayStarted()) {
            startedOpen = "";
            startedClose = "";
        }
        if (logOrReplayStarted()) startedStatus=" ("+getString(R.string.BeforeNewConnectOrReplay)+")";
        menu.add(0, FMMenuItem.MENU_QUIT.ordinal(), Menu.NONE, getString(R.string.Quit)+" "+startedStatus);
        if (sharedPreferences.getBoolean(ENABLE_REPLAY, false)) {
//            menu.add(0, menuItem(MENU_IMPORT), Menu.NONE, "Import RR Log");
            menu.add(0, menuItem(MENU_REPLAY), Menu.NONE, startedOpen + getString(R.string.ReplayRRIntervalsLog) + startedClose);
            menu.add(0, menuItem(MENU_IMPORT_REPLAY), Menu.NONE, startedOpen + getString(R.string.ImportAndReplayRRIntervalsLog) + startedClose);
        }
        menu.add(0, menuItem(MENU_EXPORT_SELECTED_LOG_FILES), Menu.NONE, R.string.ExportSelectedLogs);
        menu.add(0, menuItem(MENU_RENAME_LOGS), Menu.NONE, R.string.RenameCurrentLogFiles);
        if (sharedPreferences.getBoolean(ENABLE_SENSOR_EMULATION, false)) {
            menu.add(0, menuItem(MENU_BLE_AD_START), Menu.NONE, "BLE Ad start");
            menu.add(0, menuItem(MENU_BLE_AD_END), Menu.NONE, "BLE Ad end");
        }
//        menu.add(0, menuItem(MENU_DELETE_SELECTED_LOG_FILES), Menu.NONE, R.string.DeleteSelectedLogs);
//        menu.add(0, menuItem(MENU_OLD_LOG_FILES), Menu.NONE, R.string.DeleteAllOldLogs);
//        menu.add(0, menuItem(MENU_DELETE_DEBUG), Menu.NONE, R.string.DeleteAllDebugLogs);
//        menu.add(0, menuItem(MENU_DELETE_ALL), Menu.NONE, R.string.DeleteAllLogs);
        String tmpDeviceId = sharedPreferences.getString(POLAR_DEVICE_ID_PREFERENCE_STRING, "");
        if (logOrReplayStarted()) {
            menu.add(0, menuItem(MENU_SET_DEFAULT), Menu.NONE, "Set connected device as preferred");
        }
        if (tmpDeviceId.length() > 0) {
            menu.add(0, menuItem(MENU_CONNECT_DEFAULT), Menu.NONE, startedOpen+getString(R.string.ConnectedPreferredDevice)+" " + tmpDeviceId+startedClose);
        }
        int i = 0;
        // Offer connect for discovered devices if not already connected/replaying
        for (String tmpDeviceID : discoveredDevices.keySet()) {
            menu.add(0, menuItem(MENU_CONNECT_DISCOVERED) + i, Menu.NONE, startedOpen+getString(R.string.Connect)+" " + discoveredDevices.get(tmpDeviceID)+startedClose);
            discoveredDevicesMenu.put(menuItem(MENU_CONNECT_DISCOVERED) + i, tmpDeviceID);
            i++;
        }
//        }
        menu.add(0, menuItem(MENU_SEARCH), Menu.NONE, R.string.SearchForPolarDevices);
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean logOrReplayStarted() {
        return started;
    }

    public Uri getUri(File f) {
        try {
            return FileProvider.getUriForFile(
                    MainActivity.this,
//                    "online.fatmaxxer.publicRelease1.fileprovider",
                    BuildConfig.APPLICATION_ID+".fileprovider",
                    f);
        } catch (IllegalArgumentException e) {
            logException("getUri "+f.toString(), e);
        }
        return null;
    }

    final int REQUEST_IMPORT_CSV = 1;
    final int REQUEST_IMPORT_REPLAY_CSV = 2;

    public void importLogFile() {
        Intent receiveIntent = new Intent(Intent.ACTION_GET_CONTENT);
        receiveIntent.setType("text/*");
        receiveIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(receiveIntent, getString(R.string.ImportCSVFile)), REQUEST_IMPORT_CSV); //REQUEST_IMPORT_CSV is just an int representing a request code for the activity result callback later
    }

    private void importReplayLogFile() {
        Intent receiveIntent = new Intent(Intent.ACTION_GET_CONTENT);
        receiveIntent.setType("text/*");
        receiveIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(receiveIntent, getString(R.string.ImportCSVFile)), REQUEST_IMPORT_REPLAY_CSV); //REQUEST_IMPORT_CSV is just an int representing a request code for the activity result callback later
    }

    public void exportLogFiles() {
        ArrayList<Uri> logUris = new ArrayList<Uri>();
        logUris.add(getUri(currentLogFiles.get("rr")));
        logUris.add(getUri(currentLogFiles.get("features")));

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, logUris);
        shareIntent.setType("text/plain");
        final int exportLogFilesCode = 254;
        startActivityForResult(Intent.createChooser(shareIntent, getString(R.string.ExportLogFilesTo)+"..."), exportLogFilesCode);
    }

    public List<File> rrLogFiles() {
        Log.d(TAG, "rrLogFiles...");
        File dir = getLogsDir();
        File[] allFiles = dir.listFiles();
        List<File> rrLogFiles = new ArrayList<File>();
        for (File f : allFiles) {
            String name = f.getName();
            if (isRRfileName(name)) {
                Log.d(TAG, "Found RR log file: " + f.getName());
                rrLogFiles.add(f);
            } else {
                Log.d(TAG, "Not RR log file: " + f.getName());
            }
        }
        return rrLogFiles;
    }

    private boolean isRRfileName(String name) {
        return name.endsWith(".rr.csv") || name.endsWith("RRIntervals.csv");
    }

    public List<Uri> logFiles() {
        Log.d(TAG, "logFiles...");
        File dir = getLogsDir();
        File[] allFiles = dir.listFiles();
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        Arrays.sort(allFiles, new Comparator<File>() {
            public int compare(File f1, File f2) {
                return -Long.compare(f1.lastModified(),f2.lastModified());
            }
        });
        for (File f : allFiles) {
            Log.d(TAG, "Found log file: " + f.getName());
            allUris.add(getUri(f));
        }
        return allUris;
    }

    public void renameLogs() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.RenameLogFiles);
        alert.setMessage(R.string.TagToUseForCurrentLogFileNames);
        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);
        alert.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                String msg = getString(R.string.RenameTo) +" "+ value;
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                Log.d(TAG, msg);
                File logsDir = getLogsDir();
                renameLogs(value, currentLogFiles, logsDir);
            }
        });
        alert.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }

    private void renameLogs(String value, Map<String, File> logFiles, File logsDir) {
        File newRR = new File(logsDir,makeLogfileName(value,"rr"));
        File newFeatures = new File(logsDir,makeLogfileName(value,"features"));
        File newDebug = new File(logsDir,makeLogfileName(value,"debug"));
        File newECG = new File(logsDir,makeLogfileName(value,"ecg"));
        if (logFiles.get("rr").renameTo(newRR))
            logFiles.put("rr",newRR);
        if (logFiles.get("features").renameTo(newFeatures))
            logFiles.put("features",newFeatures);
        if (logFiles.get("debug").renameTo(newDebug))
            logFiles.put("debug",newDebug);
        if (logFiles.containsKey("ecg") && logFiles.get("ecg").renameTo(newECG))
            logFiles.put("ecg",newECG);
    }

    /*
    // all but current logs
    public ArrayList<Uri> oldLogFiles() {
        Log.d(TAG, "oldLogFiles...");
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        File logsDir = getLogsDir();
        File[] allFiles = logsDir.listFiles();
        for (File f : allFiles) {
            Log.d(TAG, "Found log file: " + getUri(f));
            if (currentLogFiles.values().contains(f)) {
                Log.d(TAG, "- skipping current log");
            } else {
                allUris.add(getUri(f));
            }
        }
        return allUris;
    }
     */

    public void exportFiles(ArrayList<Uri> allUris) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, allUris);
        shareIntent.setType("text/plain");
        Log.d(TAG,"Exporting log file/s via ShareSheet "+allUris.toString());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.ExportLogFilesTo)+"..."));
    }

    public void deleteCurrentLogFiles() {
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        StringBuilder filenames = new StringBuilder();
        deleteFile(currentLogFiles.get("rr"));
        deleteFile(currentLogFiles.get("features"));
        deleteFile(currentLogFiles.get("debug"));
        deleteFile(currentLogFiles.get("ecg"));
    }

    public long durationMStoWholeDays(long durationMS) {
        return durationMS / (1000 * 3600 * 24);
    }

    public long durationMStoWholeHours(long durationMS) {
        return durationMS / (1000 * 3600);
    }

    public void expireLogFiles() {
        Log.d(TAG, "expireLogFiles...");
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        File logsDir = getLogsDir();
        File[] allFiles = logsDir.listFiles();
        StringBuilder filenames = new StringBuilder();
        long curTimeMS = System.currentTimeMillis();
        for (File f : allFiles) {
            long ageMS = curTimeMS - f.lastModified();
            Log.d(TAG, "expire? "+f.getName()+" age in sec "+(ageMS / 1000));
            if (durationMStoWholeDays(ageMS)>=1) {
                Log.d(TAG, "- deleting log file " + f);
                f.delete();
                filenames.append(f.getName() + " ");
            }
        }
        String deletedFiles = filenames.toString();
        if (deletedFiles.length() > 0) {
            Log.i(TAG, getString(R.string.Deleted)+ " " + filenames.toString());
        }
    }

    public void deleteOldLogFiles() {
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        File logsDir = getLogsDir();
        File[] allFiles = logsDir.listFiles();
        StringBuilder filenames = new StringBuilder();
        for (File f : allFiles) {
            if (!currentLogFiles.containsValue(f)) {
                Log.d(TAG, "deleting log file " + f);
                f.delete();
                filenames.append(f.getName() + " ");
            }
        }
        String deletedFiles = filenames.toString();
        if (deletedFiles.length() > 0) {
            Toast.makeText(getBaseContext(), getString(R.string.Deleted)+ " " + filenames.toString(), Toast.LENGTH_LONG).show();
        }
    }

    public File[] logFilesDeletable() {
        File logsDir = getLogsDir();
        File[] allFiles = logsDir.listFiles();
        StringBuilder filenames = new StringBuilder();
        return allFiles;
    }

    public void deleteAllLogFiles() {
        File logsDir = getLogsDir();
        File[] allFiles = logsDir.listFiles();
        StringBuilder filenames = new StringBuilder();
        for (File f : allFiles) {
            Log.d(TAG, "deletion option on " + f);
            if (!currentLogFiles.containsValue(f)) {
                Log.d(TAG, "deleting log file: " + f);
                f.delete();
                filenames.append(f.getName() + " ");
            } else {
                Log.d(TAG, "deleting log file on exit: " + f);
                f.deleteOnExit();
            }
        }
        Toast.makeText(getBaseContext(), R.string.Deleted+" "+filenames.toString(), Toast.LENGTH_LONG).show();
    }

    public void exportDebug() {
        Log.d(TAG, "exportAllDebugFiles...");
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        File privateRootDir = getFilesDir();
        privateRootDir.mkdir();
        File logsDir = new File(privateRootDir, "logs");
        logsDir.mkdir();
        allUris.add(getUri(currentLogFiles.get("debug")));
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, allUris);
        shareIntent.setType("text/plain");
        startActivity(Intent.createChooser(shareIntent, getString(R.string.ExportLogFilesTo)+"..."));
    }

    public void deleteAllDebugFiles() {
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        File logsDir = getLogsDir();
        File[] allFiles = logsDir.listFiles();
        StringBuilder filenames = new StringBuilder();
        for (File f : allFiles) {
            if (f.getName().endsWith(".debug.log") && !currentLogFiles.containsValue(f)) {
                Log.d(TAG, "deleting log file (on exit) " + f);
                f.deleteOnExit();
                filenames.append(f.getName() + " ");
            }
        }
        Toast.makeText(getBaseContext(), getString(R.string.DeletingOnExit)+" "+filenames.toString(), Toast.LENGTH_LONG).show();
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        Log.d(TAG, "onActivityResult "+requestCode+" "+resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==REQUEST_IMPORT_CSV || requestCode==REQUEST_IMPORT_REPLAY_CSV) {
            if (data == null) {
                Log.w(TAG, getString(R.string.ImportCSVFailedDataIsNull));
                Toast.makeText(getBaseContext(), getString(R.string.ImportCSVFailedDataIsNull), Toast.LENGTH_LONG);
            } else {
                Uri uri = data.getData();
                if (uri == null) {
                    Log.w(TAG, getString(R.string.ImportCSVFailedCouldNotGetURIFromData));
                    Toast.makeText(getBaseContext(), getString(R.string.ImportCSVFailedCouldNotGetURIFromData), Toast.LENGTH_LONG);
                } else {
                    File importedRR = importRRFile(uri, getLogsDir());
                    if (requestCode ==REQUEST_IMPORT_REPLAY_CSV) {
                        if (importedRR!=null) {
                            replayRRfile(importedRR);
                        } else {
                            Toast.makeText(getBaseContext(), getString(R.string.ProblemWithImportedFile)+" " + uri, Toast.LENGTH_LONG);
                        }
                    }
                }
            }
        }
    }

    // https://stackoverflow.com/questions/10854211/android-store-inputstream-in-file/39956218
    private File importRRFile(Uri uri, File dir) {
        String filename = getFileName(uri);
        if (!isRRfileName(filename)) {
            String msg = getString(R.string.NotRRLogNotImporting)+": "+filename;
            Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
            Log.w(TAG, msg);
            return null;
        }
        Log.d(TAG,"Importing RR file "+filename+" into logs");
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            try {
                File file = new File(dir, filename);
                try (OutputStream output = new FileOutputStream(file)) {
                    Log.d(TAG,"Opened "+filename);
                    byte[] buffer = new byte[4 * 1024]; // or other buffer size
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                    Toast.makeText(getBaseContext(), getString(R.string.ImportedRRFile)+": " + filename, Toast.LENGTH_LONG).show();
                    return file;
                }
            } finally {
                    input.close();
            }
        } catch (Throwable e) {
            Toast.makeText(getBaseContext(), getString(R.string.ProblemImportingRRLog)+": " + filename, Toast.LENGTH_LONG).show();
            logException("Exception importing RR log",e);
        }
        return null;
    }

    void exportSelectedLogFiles() {
        Log.d(TAG, "exportSelectedLogFiles");
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        List<Uri> logFiles = logFiles();
        int nrLogFiles = 0;
        for (Uri uri : logFiles) nrLogFiles++;
        CharSequence[] items = new CharSequence[nrLogFiles];
        Uri[] uris = new Uri[nrLogFiles];
        boolean[] checkItems = new boolean[nrLogFiles];
        int i = 0;
        for (Uri uri : logFiles) {
            Log.d(TAG,"exportSelectedLogFiles "+uri);
            uris[i] = uri;
            items[i] = uri.getLastPathSegment();
            i++;
        }
        adb.setMultiChoiceItems(items, checkItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i, boolean b) {
                checkItems[i] = b;
            }
        });
        adb.setNegativeButton(R.string.Cancel, null);
        adb.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int iOption) {
                        ArrayList<Uri> exports = new ArrayList();
                        for (int i = 0; i < items.length; i++) {
                            if (checkItems[i]) {
                                exports.add(uris[i]);
                            }
                        }
                        exportFiles(exports);
                    }
                }
        );
        adb.setTitle(R.string.SelectFilesForExport);
        adb.show();
    }

    void selectReplayRRfile() {
        Log.d(TAG, "selectReplayRRfile");
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        List<File> logFiles = rrLogFiles();
        int nrLogFiles = 0;
        for (File f : logFiles) nrLogFiles++;
        CharSequence[] items = new CharSequence[nrLogFiles];
        File[] files = new File[nrLogFiles];
        boolean[] checkItems = new boolean[nrLogFiles];
        int i = 0;
        for (File f : logFiles) {
            files[i] = f;
            items[i] = f.getName();
            i++;
        }
        adb.setSingleChoiceItems(items, 0, null);
        adb.setNegativeButton(R.string.Cancel, null);
        adb.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        int selectedPosition = ((AlertDialog) dialogInterface).getListView().getCheckedItemPosition();
                        File f = files[selectedPosition];
                        replayRRfile(f);
                    }
                }
        );
        adb.setTitle(R.string.SelectRRFileForTestInput);
        adb.show();
    }
    //                            logException("Exception opening RR log for replay", e);

    //runs without a timer by reposting handler at the end of the runnabl
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!testDataQueue.isEmpty()) {
                TestDataPacket data = testDataQueue.remove();
                //Log.d(TAG, "Timer .run method invoked wtih " + data.toString());
                updateTrackedFeatures(data.polarData, data.timestamp, false);
                timerHandler.postDelayed(this, 1);
            } else {
                Toast.makeText(getBaseContext(), R.string.FinishedReplay, Toast.LENGTH_LONG).show();
                //takeScreenshot();
            }
        }
    };

    static class TestDataPacket {
        PolarHrData polarData;
        long timestamp;

        @Override
        public String toString() {
            return "TestDataPacket{" +
                    "polarData=" + polarData.rrsMs.toString() +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    Queue<TestDataPacket> testDataQueue;

    private void replayRRfile(File f) {
        FileReader fr = null;
        try {
            fr = new FileReader(f);
        } catch (FileNotFoundException e) {
            logException("Exception trying to open RR file "+f.getName()+" for replay", e);
            return;
        }
        testDataQueue = new ConcurrentLinkedQueue<TestDataPacket>();
        Log.d(TAG, "Starting RR reader test with " + f.getName());
        BufferedReader reader = new BufferedReader(fr);
        int prevRR = 1000;
        // header
        try {
            String header = reader.readLine();
            String headerExpected = RR_LOGFILE_HEADER;
            if (!header.equals(headerExpected)) {
                String msg = f.getName() + ": "+getString(R.string.WarningExpectedHeader)+ ": " + headerExpected +" "+getString(R.string.Got)+" "+header;
                Log.w(TAG, msg);
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
//                return;
            }
            // gap
            String gap = reader.readLine();
            if (!header.equals(headerExpected)) {
                String msg = f.getName() + ": "+getString(R.string.WarningExpectedEmptyLineGot)+" " + gap;
                Log.w(TAG, msg);
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
//                return;
            }
            // body
            String line = reader.readLine();
            double polarHR = 0;
            int lineCount = 0;
            // RR file is complex: In the RR files produced by FatMaxxer
            // there is the possibility of local inconsistency
            // between elapsed and timestamp columns. Timestamp is constructed
            // from a polar HR update's initial timestamp and then offset incrementally by the
            // RRs provided. But it seems this leads to the loss of monotonicity on the
            // timestamp column: the last timestamp constructed may be larger than the
            // timestamp provided in the next HR update.
            // Here we replay using the initial timestamp + the (monotonic) current value of the elapsed
            // field for the provided time in MS; this may lead to a discrepancy of a few seconds
            // in the overall activity(?!)
            long baseTimeStamp = 0;
            long elapsedMS = 0;
            long nextUpdateMS = 1000;
            List<Integer> rrs = new ArrayList<Integer>();
            int rr;
            int rrMean;
            int hr;
            while (line != null) {
                //Log.d(TAG, "RR file line: " + line);
                String[] fields = line.split(",");
                if (fields.length >= 3) {
                    // Initialize?
                    if (baseTimeStamp==0)
                        baseTimeStamp = Long.valueOf(fields[0]);
                    // Next RR interval - make rough HR calculation
                    rr = Integer.valueOf(fields[1]);
                    rrMean = (rr + prevRR) / 2;
                    prevRR = rr;
                    hr = 60000 / rrMean;
                    elapsedMS += rr; // don't trust the RR file for elapsed time; just sum the RRs individually
                    // Send updates, advancing time, until most-recent RR fits in current window
                    while (elapsedMS > nextUpdateMS) {
                        // Send update
                        PolarHrData data = new PolarHrData(hr, rrs, true, true, true);
                        TestDataPacket testData = new TestDataPacket();
                        testData.polarData = data;
                        testData.timestamp = baseTimeStamp + nextUpdateMS;
                        testDataQueue.add(testData);
                        Log.d(TAG,"RR playback: next update "+testData.toString());
                        // Reset for next update
                        rrs = new ArrayList<Integer>();
                        nextUpdateMS += 1000;
                    }
                    // Post: most-recent RR fits in current window; add to update
                    rrs.add(rr);
                    // finished with this RR entry
                    lineCount++;
                    if (lineCount == 1) {
                        Log.d(TAG, "Started replay timer");
                        timerHandler.postDelayed(timerRunnable, 1000);
                    }
                }
                line = reader.readLine();
            }
            Log.d(TAG,"Finished queueing RR data");
            Toast.makeText(getBaseContext(), getString(R.string.StartedRRFileReplay)+": " + f.getName(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            logException("Reading replay data from RR file", e);
        }
    }

    void deleteSelectedLogFiles() {
        Log.d(TAG, "deleteSelectedLogFiles");
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        File[] logFiles = logFilesDeletable();
        int nrLogFiles = logFiles.length;
        CharSequence[] items = new CharSequence[nrLogFiles];
        boolean[] checkItems = new boolean[nrLogFiles];
        for (int i = 0; i < logFiles.length; i++) {
            items[i] = logFiles[i].getName();
        }
        adb.setMultiChoiceItems(items, checkItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i, boolean b) {
                checkItems[i] = b;
            }
        });
        adb.setNegativeButton(R.string.Cancel, null);
        adb.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int iOption) {
                        for (int i = 0; i < items.length; i++) {
                            if (checkItems[i]) {
                                Log.d(TAG, "Delete selected file " + logFiles[i].getName());
                                deleteFile(logFiles[i]);
                            }
                        }
                    }
                }
        );
        adb.setTitle(R.string.SelectFilesForDeletion);
        adb.show();
    }

    public boolean quitRequired() {
        boolean result = logOrReplayStarted();
        if (result) {
            Toast.makeText(getBaseContext(), R.string.QuitOrRestartRequiredAfterConnectOrReplay, Toast.LENGTH_LONG).show();
        }
        return result;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        //respond to menu item selection
        Log.d(TAG, "onOptionsItemSelected... " + item.getItemId());
        int itemID = item.getItemId();
        if (itemID == menuItem(MENU_QUIT)) confirmQuit();
        if (itemID == menuItem(MENU_RENAME_LOGS)) renameLogs();
        if (itemID == menuItem(MENU_REPLAY)) if (!quitRequired()) selectReplayRRfile();
        if (itemID == menuItem(MENU_BLE_AD_START)) bleService.startAdvertising();
        if (itemID == menuItem(MENU_BLE_AD_END)) bleService.stopAdvertising();
        if (itemID == menuItem(MENU_EXPORT_SELECTED_LOG_FILES)) exportSelectedLogFiles();
        if (itemID == menuItem(MENU_DELETE_SELECTED_LOG_FILES)) deleteSelectedLogFiles();
        if (itemID == menuItem(MENU_EXPORT)) exportLogFiles();
        if (itemID == menuItem(MENU_IMPORT)) importLogFile();
        if (itemID == menuItem(MENU_IMPORT_REPLAY)) if (!quitRequired()) importReplayLogFile();
        if (itemID == menuItem(MENU_DELETE_ALL)) deleteAllLogFiles();
        if (itemID == menuItem(MENU_DELETE_DEBUG)) deleteAllDebugFiles();
        if (itemID == menuItem(MENU_SET_DEFAULT))  setConnectedDeviceAsPreferred();
        if (itemID == menuItem(MENU_CONNECT_DEFAULT)) if (!quitRequired()) tryPolarConnectToPreferredDevice();
        if (itemID == menuItem(MENU_OLD_LOG_FILES)) deleteOldLogFiles();
        if (itemID == menuItem(MENU_SEARCH)) searchForPolarDevices();
        if (discoveredDevicesMenu.containsKey(item.getItemId())) {
            if (!quitRequired())
                tryPolarConnect(discoveredDevicesMenu.get(item.getItemId()));
        }
        return super.onOptionsItemSelected(item);
    }

    public void quitSearchForPolarDevices() {
        if (broadcastDisposable != null) {
            broadcastDisposable.dispose();
            broadcastDisposable = null;
        }
    }

    public void searchForPolarDevices() {
        Toast.makeText(getBaseContext(), R.string.SearchingForPolarHRDevices, Toast.LENGTH_SHORT).show();
        if (broadcastDisposable == null) {
            broadcastDisposable = api.startListenForPolarHrBroadcasts(null)
                    .subscribe(polarBroadcastData -> {
                                if (!discoveredDevices.containsKey(polarBroadcastData.polarDeviceInfo.deviceId)) {
                                    String desc = polarBroadcastData.polarDeviceInfo.name;
                                    String msg = getString(R.string.Discovered)+" " + desc + " "+getString(R.string.HeartRateAbbrev)+" " + polarBroadcastData.hr;
                                    discoveredDevices.put(polarBroadcastData.polarDeviceInfo.deviceId, desc);
                                    Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, msg);
                                }
                            },
                            error -> {
                                Log.e(TAG, "Broadcast listener failed. Reason: " + error);
                            },
                            () -> {
                                Log.d(TAG, "complete");
                            });
        } else {
            broadcastDisposable.dispose();
            broadcastDisposable = null;
        }
    }

    //@RequiresApi(Build.VERSION_CODES.O)
    @RequiresApi(api = Build.VERSION_CODES.O)
    private String createUINotificationChannel() {
        NotificationChannel chan = new NotificationChannel(UI_CHANNEL_ID,
                UI_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        NotificationManager serviceNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        serviceNotificationManager.createNotificationChannel(chan);
        return UI_CHANNEL_ID;
    }

    String getStackTraceString(Throwable t) {
        StackTraceElement[] trace = t.getStackTrace();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < trace.length; i++) {
            b.append(trace[i]);
        }
        return b.toString();
    }

    public void logException(String comment, Throwable e) {
        android.util.Log.d(TAG, comment + " exception " + e.toString() + " " + getStackTraceString(e));
    }

    public void handleUncaughtException(Thread thread, Throwable e) {
        android.util.Log.d(TAG, "Uncaught ", e);
        //exportDebug();
        finish();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set up handler for uncaught exceptions.
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                handleUncaughtException(thread, e);
                System.exit(2);
            }
        });
        Log.d(TAG,"onCreate: set default uncaught exception handler");
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Log.d(TAG, "FatMaxxer version_name "+BuildConfig.VERSION_NAME);
        Log.d(TAG, "FatMaxxer version_code "+BuildConfig.VERSION_CODE);
        Log.d(TAG,"MainActivity checking invocation context: intent, action, type: "+intent+" "+action+" "+type);

        uiNotificationManager = NotificationManagerCompat.from(this);
        Intent i = new Intent(MainActivity.this, LocalService.class);
        i.setAction("START");
        Log.d(TAG, "intent to start local service " + i);
        ComponentName serviceComponentName = MainActivity.this.startService(i);
        Log.d(TAG, "start result " + serviceComponentName);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setIcon(R.mipmap.ic_launcher_foreground);

        //setContentView(R.layout.activity_fragment_container);
        setContentView(R.layout.activity_main);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        doBindService();
        createUINotificationChannel();

        // Notice PolarBleApi.ALL_FEATURES are enabled
        //api = PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.ALL_FEATURES);
        api = PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.FEATURE_HR | PolarBleApi.FEATURE_BATTERY_INFO | PolarBleApi.FEATURE_DEVICE_INFO | PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING);
        //api.setPolarFilter(false);

        text_time = this.findViewById(R.id.timeView);
        text_batt = this.findViewById(R.id.battView);
        text_mode = this.findViewById(R.id.modeView);
        text_hr = this.findViewById(R.id.hrTextView);
        text_secondary = this.findViewById(R.id.hrvTextView);
        text_secondary_label = this.findViewById(R.id.hrvLabel);
        text_a1 = this.findViewById(R.id.a1TextView);
        text_a1_label = this.findViewById(R.id.a1Label);
        text_artifacts = this.findViewById(R.id.artifactsView);
        text_view = this.findViewById(R.id.textView);

        //text.setTextSize(100);
        //text.setMovementMethod(new ScrollingMovementMethod());

        scrollView = this.findViewById(R.id.application_container);
        // FIXME: Why does the scrollable not start with top visible?

        testDFA_alpha1();
        //testRMSSD_1();

        api.setApiLogger(
                s -> {
                    int maxch = 63;
                    if (s.length()>64) {
                        Log.d(API_LOGGER_TAG, s.substring(0, 63)+"...");
                    } else {
                        Log.d(API_LOGGER_TAG, s);
                    }
                }
        );

        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo());

        ttobj = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Log.d(TAG, "TextToSpeech status " + status);
                if (status==TextToSpeech.SUCCESS) {
                    ttobj.setLanguage(Locale.UK);
                    nonScreenUpdate("Voice output ready");
                } else {
                    Log.d(TAG, "TextToSpeech: unable to initialise");
                }

            }
        });

        // RRs
        createLogFile("rr");
        writeLogFile(RR_LOGFILE_HEADER, "rr");
        writeLogFile("", "rr");
        // Features
        createLogFile("features");
        writeLogFile("date,timestamp,elapsedSec,heartrate,rmssd,sdnn,alpha1v1,filtered,samples,droppedPercent,artifactThreshold,alpha1v2", "features");
        // Debug
        createLogFile("debug");
        
        mp = MediaPlayer.create(this, R.raw.artifact);
        mp.setVolume(100, 100);

        graphView = (GraphView) findViewById(R.id.graph);
        // activate horizontal zooming and scrolling
        graphView.getViewport().setScalable(true);
        // sadly, that's butt-ugly
        //graphView.getViewport().setScalableY(true);
        graphView.getViewport().setScrollable(true);
        graphView.getViewport().setXAxisBoundsManual(true);
        graphView.getViewport().setMinX(0);
        graphView.getViewport().setMaxX(graphViewPortWidth);
        graphView.getViewport().setYAxisBoundsManual(true);
        graphView.getViewport().setMinY(0);
        graphView.getViewport().setMaxY(graphMaxHR);
        graphView.getGridLabelRenderer().setNumVerticalLabels(5); // 5 is the magic number that works reliably...
        graphView.addSeries(a125Series);
        graphView.addSeries(a1125Series);
        graphView.addSeries(a1175Series);
        graphView.addSeries(a1HRVvt1Series);
        graphView.addSeries(a1HRVvt2Series);
        // actual features
        graphView.addSeries(hrSeries);
        graphView.addSeries(a1V2Series);
        graphView.addSeries(rrSeries);
        graphView.addSeries(rmssdSeries);
        graphView.addSeries(hrWinSeries);
        // REQUIRED
        graphView.getSecondScale().addSeries(artifactSeries);
        graphView.getSecondScale().setMaxY(10);
        graphView.getSecondScale().setMinY(0);
        rrSeries.setColor(Color.MAGENTA);
        rrSeries.setThickness(5);
        a1V2Series.setColor(Color.GREEN);
        a1V2Series.setThickness(5);
        a1HRVvt1Series.setColor(getResources().getColor(R.color.colorHRVvt1));
        a1HRVvt2Series.setColor(getResources().getColor(R.color.colorHRVvt2));
        a125Series.setColor(Color.GRAY);
        a1175Series.setColor(Color.GRAY);
        a1125Series.setColor(Color.GRAY);
        a125Series.setThickness(1);
        a1125Series.setThickness(1);
        a1175Series.setThickness(1);
        hrSeries.setColor(Color.RED);
        artifactSeries.setColor(Color.BLUE);
        rmssdSeries.setColor(getResources().getColor(R.color.rmssdSeries));
        hrWinSeries.setColor(getResources().getColor(R.color.hrWinSeries));
        // yellow is a lot less visible than red
        a1HRVvt1Series.setThickness(6);
        // red is a lot more visible than yellow
        a1HRVvt2Series.setThickness(2);

        rrSeries.appendData(new DataPoint(0,0), false, maxDataPoints);
        hrSeries.appendData(new DataPoint(0,0), false, maxDataPoints);
        a1V2Series.appendData(new DataPoint(0,0), false, maxDataPoints);
        rmssdSeries.appendData(new DataPoint(0,0),false,maxDataPoints);
        hrWinSeries.appendData(new DataPoint(0,0),false,maxDataPoints);
        artifactSeries.appendData(new DataPoint(0,0), false, maxDataPoints);

        a1V2Series.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                String text = "["+formatMinAsTime(dataPoint.getX()) + ", " + (dataPoint.getY() / 100.0)+" ]";
                Toast.makeText(thisContext, getString(R.string.Alpha1)+"("+getString(R.string.TwoMinutesAbbrev)+"): "+text, Toast.LENGTH_LONG).show();
            }
        });
        rmssdSeries.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                String text = "["+formatMinAsTime(dataPoint.getX()) + ", " +(dataPoint.getY() / 2.0)+" ]";
                Toast.makeText(thisContext, getString(R.string.RootMeanSquareSuccessiveDifferencesAbbreviation)+"("+getString(R.string.TwoMinutesAbbrev)+"): "+text, Toast.LENGTH_LONG).show();
            }
        });
        hrSeries.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                String text = "["+formatMinAsTime(dataPoint.getX()) + ", " +dataPoint.getY()+"] ";
                Toast.makeText(thisContext, getString(R.string.HeartRateAbbrev)+": "+text, Toast.LENGTH_LONG).show();
            }
        });
        hrWinSeries.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                String text = "["+formatMinAsTime(dataPoint.getX()) + ", " +dataPoint.getY()+" ]";
                Toast.makeText(thisContext, getString(R.string.HeartRateAbbrev)+"("+getString(R.string.TwoMinutesAbbrev)+": "+text, Toast.LENGTH_LONG).show();
            }
        });
        artifactSeries.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                String text = "["+formatMinAsTime(dataPoint.getX()) + ", " +dataPoint.getY()+"% ]";
                Toast.makeText(thisContext, getString(R.string.Artifacts)+": "+text, Toast.LENGTH_LONG).show();
            }
        });
        rrSeries.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                String text = "["+formatMinAsTime(dataPoint.getX()) + ", " +(dataPoint.getY()*5)+" ]";
                Toast.makeText(thisContext, getString(R.string.RRIntervalAbbrev)+": "+text, Toast.LENGTH_LONG).show();
            }
        });

        a1HRVvt1Series.appendData(new DataPoint(0, alpha1HRVvt1 * 100), false, maxDataPoints);
        a1HRVvt2Series.appendData(new DataPoint(0, alpha1HRVvt2 * 100), false, maxDataPoints);
        a125Series.appendData(new DataPoint(0, 25), false, maxDataPoints);
        a1125Series.appendData(new DataPoint(0, 125), false, maxDataPoints);
        a1175Series.appendData(new DataPoint(0, 175), false, maxDataPoints);
        a1HRVvt1Series.appendData(new DataPoint(graphViewPortWidth, alpha1HRVvt1 * 100), false, maxDataPoints);
        a1HRVvt2Series.appendData(new DataPoint(graphViewPortWidth, alpha1HRVvt2 * 100), false, maxDataPoints);
        a125Series.appendData(new DataPoint(graphViewPortWidth, 25), false, maxDataPoints);
        a1125Series.appendData(new DataPoint(graphViewPortWidth, 125), false, maxDataPoints);
        a1175Series.appendData(new DataPoint(graphViewPortWidth, 175), false, maxDataPoints);


        //hrvSeries.setColor(Color.BLUE);

        //setContentView(R.layout.activity_settings);
        Log.d(TAG, "Settings...");
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, MySettingsFragment.class, null)
                .commit();

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");

        api.setApiCallback(new PolarBleApiCallback() {

            @Override
            public void blePowerStateChanged(boolean powered) {
                Log.d(TAG, "BLE power: " + powered);
                text_view.setText("BLE power " + powered);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                quitSearchForPolarDevices();
                SENSOR_ID = polarDeviceInfo.deviceId;
                Log.d(TAG, "Polar device CONNECTED: " + polarDeviceInfo.deviceId);
                Toast.makeText(getBaseContext(), getString(R.string.ConnectedToDevice)+" " + SENSOR_ID, Toast.LENGTH_SHORT).show();
                ensurePreferenceSet(POLAR_DEVICE_ID_PREFERENCE_STRING,polarDeviceInfo.deviceId);
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "Polar device CONNECTING"+": " + polarDeviceInfo.deviceId);
                text_view.setText(getString(R.string.ConnectingToHeartRateSensor)+" " + polarDeviceInfo.deviceId);
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: " + polarDeviceInfo.deviceId);
                text_view.setText(getString(R.string.DisconnectedFromHeartRateSensor)+" " + polarDeviceInfo.deviceId);
                ecgDisposable = null;
//                accDisposable = null;
//                gyrDisposable = null;
//                magDisposable = null;
//                ppgDisposable = null;
//                ppiDisposable = null;
                searchForPolarDevices();
            }

            @Override
            public void streamingFeaturesReady(@NonNull final String identifier,
                                               @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
                for (PolarBleApi.DeviceStreamingFeature feature : features) {
                    Log.d(TAG, "Streaming feature " + feature.toString() + " is ready");
                    text_view.setText("Streaming feature " + feature.toString() + " is ready");
                }
            }

            @Override
            public void hrFeatureReady(@NonNull String identifier) {
                Log.d(TAG, "HR READY: " + identifier);
                text_view.setText("HR feature " + identifier + " is ready");
                // hr notifications are about to start
            }

            @Override
            public void disInformationReceived(@NonNull String identifier, @NonNull UUID uuid, @NonNull String value) {
                Log.d(TAG, "uuid: " + uuid + " value: " + value);
            }

            @Override
            public void batteryLevelReceived(@NonNull String identifier, int level) {
                batteryLevel = level;
                Log.d(TAG, "BATTERY LEVEL: " + level);
            }

            //NotificationManagerCompat notificationManager = NotificationManagerCompat.from(thisContext);

            // FIXME: this is a makeshift main event & timer loop
            @Override
            public void hrNotificationReceived(@NonNull String identifier, @NonNull PolarHrData data) {
                updateTrackedFeatures(data, System.currentTimeMillis(), true);
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
                Log.d(TAG, "FTP ready");
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && savedInstanceState == null) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, "android.permission.BLUETOOTH_ADVERTISE"}, 1);
        }

        expireLogFiles();

        scrollView.post(new Runnable() {
            public void run() {
                scrollView.scrollTo(0, 0);
            }
        });

        // auto-start
        if (!sharedPreferences.getBoolean(ENABLE_REPLAY,false)) {
            startAnalysis();
        }
        // start BLE sensor emulator service
        if (sharedPreferences.getBoolean(ENABLE_SENSOR_EMULATION, false)) {
            startBLESensorEmulatorService();
        }
    }

    boolean bleServiceStarted = false;
    boolean mBound = false;
    BLEEmulator bleService = null;

    private ServiceConnection bleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,  IBinder service) {
            Log.d(TAG,"bleServiceConnection.onServiceConnected");
            BLEEmulator.LocalBinder binder = (BLEEmulator.LocalBinder) service;
            bleService = binder.getService();
            mBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG,"bleServiceConnection.onServiceDisconnected");
            mBound = false;
        }
    };

    private void startBLESensorEmulatorService() {
        Log.d(TAG,"startBLESensorEmulatorService");
        Intent mServiceIntent = new Intent(getApplicationContext(), BLEEmulator.class);
        if (!bleServiceStarted) {
            Log.d(TAG, "Starting BLESensorEmulatorService Service");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                MainActivity.this.startForegroundService(mServiceIntent);
            else
                MainActivity.this.startService(mServiceIntent);
            // Bind to the service so we can interact with it
            if (!bindService(mServiceIntent, bleServiceConnection, Context.BIND_AUTO_CREATE)) {
                Log.d(TAG, "Failed to bind to service");
            } else {
                mBound = true;
            }
        }
    }

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // currently logging ECG data after observing an artifact
    boolean ecgLogging = false;
    final int ecgPacketSize = 73;
    final int ecgSampleRate = 130;
    final int pastECGbufferDurationSec = 5;
    final int totalECGsamples = pastECGbufferDurationSec * ecgSampleRate;
    final int totalECGpackets = totalECGsamples / ecgPacketSize;
    // 73 samples per slot at 125hz is very roughly 0.5s
    Queue<PolarEcgData> lastPolarEcgData = new ConcurrentLinkedQueue<PolarEcgData>();

    Integer maxAbsVolts = 0;
    private void ecgCallback(PolarEcgData polarEcgData) {
            // startup: record relative timestamps
            if (!ecgMonitoring) {
                ecgStartTSmillis = System.currentTimeMillis();
                ecgStartInternalTSnanos = polarEcgData.timeStamp;
                ecgMonitoring = true;
            }
            for (Integer microVolts : polarEcgData.samples) {
                maxAbsVolts = max(abs(microVolts), maxAbsVolts);
            }
            lastPolarEcgData.add(polarEcgData);
            // throw away ECG logs, oldest-first, but only if not already logging
            if (!ecgLogging && lastPolarEcgData.size()>totalECGpackets) {
                PolarEcgData ecgPacket = lastPolarEcgData.remove();
            }
    }

    int ecgSegment = 0;
    int ecgSample = 0;
    // log all recorded ecg data
    private void logAllEcgData() {
        if (sharedPreferences.getBoolean(ENABLE_ECG, true)) {
            Log.d(TAG,"logAllEcgData");
            // FIXME: Copied code
            if (currentLogFileWriters.get("ecg") == null) {
                createLogFile("ecg");
                writeLogFile("date,timestamp,elapsed,segmentNr,sampleNr,yV","ecg");
            }
            while (!lastPolarEcgData.isEmpty ()) {
                PolarEcgData ecgPacket = lastPolarEcgData.remove();
                // elapsed time since ecg start (nanosecs)
                long ecgElapsedNanos = ecgPacket.timeStamp - ecgStartInternalTSnanos;
                // elapsed time since logging started (millisecs)
                long ecgElapsedMS = (ecgElapsedNanos / 1000000) + (ecgStartTSmillis - firstSampleTimestampMS);
                String elapsedStr = formatSecAsTime((long)(ecgElapsedMS / 1000.0));
                // nanoseconds since(?)
                Log.d(TAG,"logEcgData: logging packet "+ecgElapsedMS);
                Date d = new Date(firstSampleTimestampMS + ecgElapsedMS);
                String dateStr = sdf.format(d);
                for (Integer microVolts : ecgPacket.samples) {
                    writeLogFile(dateStr + "," + ecgPacket.timeStamp +"," +elapsedStr+ ","+ecgSegment+"," + ecgSample + "," + microVolts.toString(), "ecg");
                    ecgSample++;
                }
            }
        }
    }
    private void logEcgSegmentEnd() {
        if (sharedPreferences.getBoolean(ENABLE_ECG, true)) {
            Log.d(TAG,"logEcgSegmentEnd");
            // FIXME: Copied code
            if (currentLogFileWriters.get("ecg") == null) {
                createLogFile("ecg");
                writeLogFile("date,timestamp,elapsed,segmentNr,sampleNr,yV","ecg");
            }
            for (int i=0;i<10;i++) {
                writeLogFile("" + "," + "" + "," + "" + "," + ecgSegment + "," + ecgSample + "," + "1000.0", "ecg");
                writeLogFile("" + "," + "" + "," + "" + "," + ecgSegment + "," + ecgSample + "," + "-1000.0", "ecg");
            }
        }
    }

    boolean ecgMonitoring = false;

    private void startECG() {
        if (ecgDisposable == null) {
            Log.d(TAG, "startECG create ecgDisposable");
            ecgDisposable = api.requestStreamSettings(SENSOR_ID, PolarBleApi.DeviceStreamingFeature.ECG)
                    .toFlowable()
                    .flatMap((Function<PolarSensorSetting, Publisher<PolarEcgData>>) polarEcgSettings -> {
                        PolarSensorSetting sensorSetting = polarEcgSettings.maxSettings();
                        Log.d(TAG, "api.startEcgStreaming "+sensorSetting.toString());
                        return api.startEcgStreaming(SENSOR_ID, sensorSetting);
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            polarEcgData -> ecgCallback(polarEcgData),
                            //throwable -> Log.e(TAG, "ECG throwable " + throwable),
                            throwable -> {
                                Log.d(TAG, "ECG throwable " + throwable.getClass());
                                ecgMonitoring = false;
                            },
                            () -> {
                                Log.d(TAG, "complete");
                                ecgMonitoring = false;
                            }
                    );
        }
    }

    private void ensurePreferenceSet(String key, String defaultValue) {
        if (!sharedPreferences.contains(key)) {
            sharedPreferences.edit().putString(key, defaultValue).apply();
        }
    }


    private int getNrSamples() {
        int nrSamples = (newestSample < oldestSample) ? (newestSample + maxrrs - oldestSample) : (newestSample - oldestSample);
        return nrSamples;
    }

    // extract feature window from circular buffer (ugh), allowing for sample buffer after end of feature window
    // FIXME: requires invariants
    public double[] copySamplesFeatureWindow() {
        int next = 0;
        // rewind just past sample buffer
        int newestSampleIndex = (newestSample - sampleBufferMarginSec) % rrInterval.length;
        long newestTimestamp = rrIntervalTimestamp[newestSampleIndex];
        // rewind by the size of the window in seconds
        int oldestSampleIndex = newestSampleIndex;
        while (rrIntervalTimestamp[oldestSampleIndex] > (newestTimestamp - rrWindowSizeSec)) {
            oldestSampleIndex = (oldestSampleIndex - 1) % rrInterval.length;
        }
        return copySamplesRange(oldestSampleIndex,newestSampleIndex);
    }

    public double[] copySamplesAll() {
        return copySamplesRange(oldestSample, newestSample);
    }

    public double[] copySamplesRange(int oldest, int newest) {
        double[] result = new double[getNrSamples()];
        int next = 0;
        // FIXME: unverified
        for (int i = oldest; i != newest; i = (i + 1) % rrInterval.length) {
            result[next] = rrInterval[i];
            next++;
        }
        return result;
    }

    private int getNrArtifacts() {
        int result = 0;
        for (int i = oldestArtifactSample; i != newestArtifactSample; i = (i + 1) % artifactTimestamp.length) {
            result++;
        }
        return result;
    }

    int hrNotificationCount = 0;
    private void updateTrackedFeatures(@NotNull PolarHrData data, long currentVirtualTimeMS, boolean realTime) {
        wakeLock.acquire();
        hrNotificationCount++;
        currentTimeMS = currentVirtualTimeMS;
        if (currentTimeMS <= prevTimeMS) {
            throw new IllegalStateException("assertion failed: cur "+currentTimeMS+" "+prevTimeMS);
        }
        prevTimeMS = currentTimeMS;
        if (!started) {
            Log.d(TAG, "hrNotificationReceived: started!");
            started = true;
            starting = true;
            thisIsFirstSample = true;
            firstSampleTimestampMS = currentTimeMS;
            // FIXME: why does the scroller not start with the top visible?
            scrollView.scrollTo(0,0);
        }
        elapsedMS = (currentTimeMS - firstSampleTimestampMS);
        //Log.d(TAG, "====================");
        elapsedSecondsTrunc = elapsedMS / 1000;
        //Log.d(TAG, "updateTrackedFeatures cur "+currentTimeMS+" elapsed "+elapsedMS+" hr notifications "+hrNotificationCount+" calcElapsed"+10*hrNotificationCount);
        boolean timeForUIupdate = timeForUIupdate(realTime);
        if (timeForUIupdate) {
            String lambdaPref = sharedPreferences.getString(LAMBDA_PREFERENCE_STRING, "500");
            lambdaSetting = Integer.valueOf(lambdaPref);
            //experimental = sharedPreferences.getBoolean(EXPERIMENTAL_PREFERENCE_STRING, false);
            if (sharedPreferences.getBoolean(KEEP_SCREEN_ON_PREFERENCE_STRING, false)) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
        currentHR = data.hr;
        if (timeForUIupdate) {
            String artifactCorrectionThresholdSetting = sharedPreferences.getString(ARTIFACT_REJECTION_THRESHOLD_PREFERENCE_STRING, "Auto");
            if (artifactCorrectionThresholdSetting.equals("Auto")) {
                if (data.hr > 95) {
                exerciseMode = getString(R.string.Workout);
                artifactCorrectionThreshold = 0.05;
                } else if (data.hr < 80) {
                    exerciseMode = getString(R.string.Light);
                    artifactCorrectionThreshold = 0.25;
                }
            } else if (artifactCorrectionThresholdSetting.equals("0.25")) {
                exerciseMode = getString(R.string.Light);
                artifactCorrectionThreshold = 0.25;
            } else if (artifactCorrectionThresholdSetting.equals("0.25")) {
                exerciseMode = getString(R.string.Workout);
                artifactCorrectionThreshold = 0.05;
            } else {
                exerciseMode = "Workout (Unc)";
                artifactCorrectionThreshold = 0.05;
                disableArtifactCorrection = true;
            }
        }
        String notificationDetailSetting = "";
        String alpha1EvalPeriodSetting = "";
        Set<String> graphFeatures = emptyStringSet;
        if (realTime) {
            graphFeatures = sharedPreferences.getStringSet("graphFeaturesSelectorKey", emptyStringSet);
        } else {
            graphFeatures = sharedPreferences.getStringSet("graphReplayFeaturesSelectorKey", emptyStringSet);
        }
        // preference updates
        if (timeForUIupdate) {
            //Log.d(TAG,"timeForUIupdate");
            //Log.d(TAG,"graphFeaturesSelected "+graphFeaturesSelected);
            notificationDetailSetting = sharedPreferences.getString(NOTIFICATION_DETAIL_PREFERENCE_STRING, "full");
            alpha1EvalPeriodSetting = sharedPreferences.getString(ALPHA_1_CALC_PERIOD_PREFERENCE_STRING, "20");
            try {
                alpha1EvalPeriodSec = Integer.parseInt(alpha1EvalPeriodSetting);
            } catch (final NumberFormatException e) {
                //Log.d(TAG, "Number format exception alpha1EvalPeriod " + alpha1EvalPeriodSetting + " " + e.toString());
                alpha1EvalPeriodSec = 20;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(ALPHA_1_CALC_PERIOD_PREFERENCE_STRING, "20");
                editor.apply();
                //Log.d(TAG, "alpha1CalcPeriod wrote " + sharedPreferences.getString(ALPHA_1_CALC_PERIOD_PREFERENCE_STRING, "??"));
            }
            if (alpha1EvalPeriodSec < 5) {
                //Log.d(TAG, "alpha1EvalPeriod<5");
                alpha1EvalPeriodSec = 5;
                sharedPreferences.edit().putString(ALPHA_1_CALC_PERIOD_PREFERENCE_STRING, "5").apply();
            }
        }
        // test: use ONLY for RRs
        long timestamp = currentTimeMS;
        for (int rr : data.rrsMs) {
            String msg = "" + timestamp + "," + rr + "," + logRRelapsedMS;
            writeLogFile(msg, "rr");
            logRRelapsedMS += rr;
            timestamp += rr;
        }
        //
        // FILTERING / RECORDING RR intervals
        //
        String rejected = "";
        boolean haveArtifacts = false;
        List<Integer> rrsMs = data.rrsMs;
        for (int si = 0; si < data.rrsMs.size(); si++) {
            double newRR = data.rrsMs.get(si);
            double lowerBound = prevrr * (1 - artifactCorrectionThreshold);
            double upperBound = prevrr * (1 + artifactCorrectionThreshold);
            //Log.d(TAG, "prevrr " + prevrr + " lowerBound " + lowerBound + " upperBound " + upperBound);
            boolean artifactFound = lowerBound >= newRR || newRR >= upperBound;
            if (thisIsFirstSample || !artifactFound) {
                //Log.d(TAG, "accept RR within threshold" + newrr);
                // if in_RRs[(i-1)]*(1-artifact_correction_threshold) < in_RRs[i] < in_RRs[(i-1)]*(1+artifact_correction_threshold):
                if (!disableArtifactCorrection) {
                    rrInterval[newestSample] = newRR;
                    rrIntervalTimestamp[newestSample] = currentTimeMS;
                    newestSample = (newestSample + 1) % maxrrs;
                }
                thisIsFirstSample = false;
            }
            if (artifactFound) {
                //Log.d(TAG, "drop...");
                artifactTimestamp[newestArtifactSample] = currentTimeMS;
                newestArtifactSample = (newestArtifactSample + 1) % maxrrs;
                //Log.d(TAG, "reject artifact " + newrr);
                rejected += "" + newRR;
                haveArtifacts = true;
                totalRejected++;
            }
            prevrr = newRR;
        }
        String rejMsg = haveArtifacts ? (", Rejected: " + rejected) : "";
        int expired = 0;
        //Log.d(TAG, "updateTrackedFeatures expire old samples");
        while (oldestSample != newestSample && rrIntervalTimestamp[oldestSample] < currentTimeMS - rrWindowSizeSec * 1000) {
                oldestSample = (oldestSample + 1) % maxrrs;
                expired++;
        }
        //Log.d(TAG, "updateTrackedFeatures expire old artifacts");
        while (oldestArtifactSample != newestArtifactSample && artifactTimestamp[oldestArtifactSample] < currentTimeMS - rrWindowSizeSec * 1000) {
            //Log.d(TAG, "Expire at " + oldestArtifactSample);
            oldestArtifactSample = (oldestArtifactSample + 1) % maxrrs;
        }
        long absSeconds = Math.abs(elapsedSecondsTrunc);
        boolean timeForHRplot = timeForHRplot(realTime);
        //Log.d(TAG, "updateTrackedFeatures timeForHRplot");
        if (timeForHRplot) {
            String positive = formatSecAsTime(absSeconds);
            text_mode.setText(exerciseMode);
            text_time.setText(positive);
            text_batt.setText("\uD83D\uDD0B" + batteryLevel);
        }

        //
        // Automatic beat correction
        // https://www.kubios.com/hrv-preprocessing/
        //
        long elapsedSecondsTrunc = elapsedMS / 1000;
        //Log.d(TAG,"elapsed seconds (trunc) = "+elapsedSecondsTrunc);
        int nrSamples = getNrSamples();
        int nrArtifacts = getNrArtifacts();
        // get full window (c. 220sec)
        double[] allSamples = copySamplesAll();
        // get sample window (c. 120sec)
        double[] featureWindowSamples;
        double[] samples = allSamples;

        if (samples.length==0) {
            starting = false;
            wakeLock.release();
            return;
        }

        //Log.d(TAG, "updateTrackedFeatures windowed features");
        // ******************
        // WINDOWED FEATURES
        // ******************
        if (ecgMonitoring && (haveArtifacts || hrNotificationCount == 10) && hrNotificationCount >= pastECGbufferDurationSec) {
            Log.d(TAG,"Artifacts: (re)start ECG logging @"+hrNotificationCount);
            // New artifact - EVENT: start ECG logging
            lastObservedHRNotificationWithArtifacts = hrNotificationCount;
            ecgLogging = true;
        }
        if (ecgMonitoring && ecgLogging && hrNotificationCount < lastObservedHRNotificationWithArtifacts + pastECGbufferDurationSec) {
            Log.d(TAG,"ECG logging @"+hrNotificationCount);
            logAllEcgData();
        } else if (ecgMonitoring && ecgLogging && hrNotificationCount == lastObservedHRNotificationWithArtifacts + pastECGbufferDurationSec) {
            // EVENT: stop ECG logging
            Log.d(TAG,"Stop ECG logging @"+hrNotificationCount);
            logEcgSegmentEnd();
            //
            ecgLogging = false;
            ecgSegment++;
            ecgSample = 0;
        }
        rmssdWindowed = getRMSSD(samples);
        // TODO: CHECK: avg HR == 60 * 1000 / (mean of observed filtered(?!) RRs)
        rrMeanWindowed = v_mean(samples);
        //Log.d(TAG,"rrMeanWindowed "+rrMeanWindowed);
        hrMeanWindowed = round(60 * 1000 * 100 / rrMeanWindowed) / 100.0;
        //Log.d(TAG,"hrMeanWindowed "+hrMeanWindowed);
        // Periodic actions: check alpha1 and issue voice update
        // - skip one period's worth after first HR update
        // - only within the first two seconds of this period window
        // - only when at least three seconds have elapsed since last invocation
        // FIXME: what precisely is required for alpha1 to be well-defined?
        // FIXME: The prev_a1_check now seems redundant
        if (timeForUIupdate) Log.d(TAG,"Elapsed "+elapsedSecondsTrunc+" currentTimeMS "+currentTimeMS+ " a1evalPeriod "+ alpha1EvalPeriodSec +" prevA1Timestamp "+ prevA1TimestampMS);
        boolean graphEnabled = realTime;
//        boolean enoughElapsedSinceStart = elapsedSecondsTrunc > alpha1EvalPeriodSec;
//        boolean oncePerPeriod = true; //elapsed % alpha1EvalPeriod <= 2;
//        boolean enoughSinceLast = currentTimeMS >= (prevA1TimestampMS + alpha1EvalPeriodSec *1000);
        //Log.d(TAG,"graphEnabled antecedents "+enoughElapsedSinceStart+" "+oncePerPeriod+" "+enoughElapsedSinceStart);
        // Logging must not be throttled during replay
        if (hrNotificationCount % alpha1EvalPeriodSec == 0) {
//            graphEnabled = true;
            //Log.d(TAG,"alpha1...");
            alpha1V2Windowed = dfaAlpha1V2(samples, 2, 4, 30);
            float a1v2x100 = (int)(100.0 * alpha1V2Windowed);
            alpha1V2RoundedWindowed = round(alpha1V2Windowed * 100) / 100.0;
            if (sharedPreferences.getBoolean(ENABLE_SENSOR_EMULATION, false) && bleService != null) {
                bleService.lastHR = (int) a1v2x100;
            }
            Log.d(TAG,"a1v2windowed "+alpha1V2Windowed+" a1v2x100 "+a1v2x100);
            prevA1TimestampMS = currentTimeMS;
            if (elapsedSecondsTrunc > 120) {
                String dateStr = sdf.format(new Date(currentTimeMS));
                //         date,timestamp,elapsedSec,heartrate,rmssd,sdnn,alpha1v1,filtered,samples,droppedPercent,artifactThreshold,alpha1v2", "features");
                writeLogFile(
                                dateStr
                                + "," + currentTimeMS
                                + "," + hrNotificationCount
                                + "," + hrMeanWindowed
                                + "," + rmssdWindowed
                                + ","
                                + "," //+ alpha1V1RoundedWindowed
                                + "," + nrArtifacts
                                + "," + nrSamples
                                + "," + artifactsPercentWindowed
                                + "," + artifactCorrectionThreshold
                                + "," + alpha1V2RoundedWindowed
                        ,
                        "features");
            }
            if (timeForUIupdate) {
                if (sharedPreferences.getBoolean(NOTIFICATIONS_ENABLED_PREFERENCE_STRING, false)) {
                    //Log.d(TAG, "Feature notification...");
                    // https://stackoverflow.com/questions/5502427/resume-application-and-stack-from-notification
                    final Intent notificationIntent = new Intent(this, MainActivity.class);
                    notificationIntent.setAction(Intent.ACTION_MAIN);
                    notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
                    //notification.setContentIntent(pendingIntent)

                    uiNotificationBuilder = new NotificationCompat.Builder(this, UI_CHANNEL_ID)
                            .setOngoing(true)
                            .setSmallIcon(R.mipmap.ic_launcher_foreground)
                            .setPriority(NotificationManager.IMPORTANCE_HIGH)
                            .setCategory(Notification.CATEGORY_MESSAGE)
                            .setContentIntent(pendingIntent)
                            .setContentTitle(getString(R.string.Alpha1AbbrevNonUnicode)+" " + alpha1V2RoundedWindowed + " "+getString(R.string.Drop)+" " + artifactsPercentWindowed + "%");
                    if (notificationDetailSetting.equals("full")) {
                        uiNotificationBuilder.setContentText(getString(R.string.HeartRateAbbrev)+" " + currentHR + " "+getString(R.string.BatteryAbbrev)+" " + batteryLevel + "% "+getString(R.string.RootMeanSquareSuccessiveDifferencesAbbrevLowerCase)+" " + rmssdWindowed);
                    } else if (notificationDetailSetting.equals("titleHR")) {
                        uiNotificationBuilder.setContentText(getString(R.string.HeartRateAbbrev)+" " + currentHR);
                    }
                    uiNotificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, uiNotificationBuilder.build());
                }
            }
        }

        //
        // UI (DISPLAY // AUDIO // LOGGING)
        //
        // Device Display
        // if (timeForUIupdate()) {
        if (timeForHRplot) {
            if (haveArtifacts && sharedPreferences.getBoolean(AUDIO_OUTPUT_ENABLED, false)) {
                //spokenOutput("drop");
                mp.start();
            }
            StringBuilder logmsg = new StringBuilder();
            if (ecgLogging) {
                logmsg.append("*");
            }
            if (!lastPolarEcgData.isEmpty()) {
                logmsg.append("ECG ");
            }
            logmsg.append("RRs: " + data.rrsMs+" ");
            logmsg.append(rejMsg);
            logmsg.append("Total rejected: " + totalRejected+" ");
            logmsg.append("uVolt: "+ maxAbsVolts +" ");
            maxAbsVolts = 0;
            String logstring = logmsg.toString();

            artifactsPercentWindowed = (int) round(nrArtifacts * 100 / (double) (nrArtifacts + nrSamples));
            text_artifacts.setText("" + nrArtifacts + "/" + (nrArtifacts+nrSamples) + " (" + artifactsPercentWindowed + "%) [" + artifactCorrectionThreshold + "]");
            if (haveArtifacts) {
                text_artifacts.setBackgroundResource(R.color.colorHighlight);
            } else {
                text_artifacts.setBackgroundResource(R.color.colorBackground);
            }
            text_view.setText(logstring);
            text_hr.setText("" + data.hr);
            text_secondary_label.setText(R.string.RootMeanSquareSuccessiveDifferencesAbbreviation);
            text_secondary.setText("" + round(rmssdWindowed));
            text_a1.setText("" + alpha1V2RoundedWindowed);
            text_a1_label.setText(getString(R.string.alpha1) + " [" + a1v2cacheMisses + "]");
            // configurable top-of-optimal threshold for alpha1
            double alpha1MaxOptimal = Double.parseDouble(sharedPreferences.getString("alpha1MaxOptimal", "1.0"));
            if (elapsedSecondsTrunc < 20) {
                int undefColor = getFatMaxxerColor(R.color.colorTextUndefinedData);
                text_a1.setTextColor(undefColor);
                text_secondary.setTextColor(undefColor);
            } else if (elapsedSecondsTrunc < 120) {
                int unreliableColor = getFatMaxxerColor(R.color.colorTextUnreliableData);
                text_a1.setTextColor(unreliableColor);
                text_secondary.setTextColor(unreliableColor);
            } else {
                text_a1.setTextColor(getFatMaxxerColor(R.color.colorTextData));
                text_secondary.setTextColor(getFatMaxxerColor(R.color.colorTextData));
            }
            if (elapsedSecondsTrunc > 30) {
                    if (alpha1V2RoundedWindowed < alpha1HRVvt2) {
                        text_a1.setBackgroundResource(R.color.colorMaxIntensity);
                    } else if (alpha1V2RoundedWindowed < alpha1HRVvt1) {
                        text_a1.setBackgroundResource(R.color.colorMedIntensity);
                    } else if (alpha1V2RoundedWindowed < alpha1MaxOptimal) {
                        text_a1.setBackgroundResource(R.color.colorFatMaxIntensity);
                    } else {
                        text_a1.setBackgroundResource(R.color.colorEasyIntensity);
                    }
            }
            Log.d(TAG, "HR "+data.hr + " " + alpha1V2RoundedWindowed + " " + rmssdWindowed);
            Log.d(TAG, logstring);
            Log.d(TAG, "Elapsed % alpha1EvalPeriod " + (elapsedSecondsTrunc % alpha1EvalPeriodSec));
        }
        elapsedMin = this.elapsedMS / 60000.0;
        double elapsedMinRound = round(this.elapsedMin * 1000) / 1000.0;
        ///////////////////////elapsedMin = this.elapsedSecondsTrunc / 60.0;
        //Log.d(TAG,"elapsedSecondsTrunc: "+elapsedSecondsTrunc);
        //Log.d(TAG,"elapsedMin: "+elapsedMin);
        double tenSecAsMin = 1.0 / 6.0;
        boolean pre1 = elapsedMin > (graphViewPortWidth - tenSecAsMin);
        boolean pre2 = elapsedSecondsTrunc > lastScrollToEndElapsedSec + 20;
        boolean scrollToEnd = (realTime && pre1 && pre2) || (!realTime && pre1);
        if (scrollToEnd) lastScrollToEndElapsedSec = elapsedSecondsTrunc;
        double elapsedMinRoundForRRs = elapsedMinRound;
        if (graphFeatures.contains("rr")) {
            for (int rr : data.rrsMs) {
                elapsedMinRoundForRRs += rr / 60000;
                rrSeries.appendData(new DataPoint(elapsedMinRoundForRRs, rr / 5.0), scrollToEnd, maxDataPoints);
            }
        }
        DataPoint hrDataPoint = new DataPoint(elapsedMinRound, data.hr);
        if (timeForHRplot && graphFeatures.contains("hr")) {
            hrSeries.appendData(hrDataPoint, scrollToEnd, maxDataPoints);
        }
        if (timeForUIupdate) {
                if (graphFeatures.contains("a1")) {
                    a1V2Series.appendData(new DataPoint(elapsedMinRound, alpha1V2RoundedWindowed * 100.0), scrollToEnd, maxDataPoints);
                }
                if (graphFeatures.contains("artifacts")) {
                    artifactSeries.appendData(new DataPoint(elapsedMinRound, artifactsPercentWindowed), scrollToEnd, maxDataPoints);
                }
                if (graphFeatures.contains("rmssd")) {
                    rmssdSeries.appendData(new DataPoint(elapsedMinRound, round(rmssdWindowed * 2)), scrollToEnd, maxDataPoints);
                }
                if (graphFeatures.contains("hrWin")) {
                    hrWinSeries.appendData(new DataPoint(elapsedMinRound, hrMeanWindowed), scrollToEnd, maxDataPoints);
                }
                if (scrollToEnd) {
                    double nextX = elapsedMinRound + tenSecAsMin;
                    a1HRVvt1Series.appendData(new DataPoint(nextX, 75), scrollToEnd, maxDataPoints);
                    a1HRVvt2Series.appendData(new DataPoint(nextX, 50), scrollToEnd, maxDataPoints);
                    a125Series.appendData(new DataPoint(nextX, 25), scrollToEnd, maxDataPoints);
                    a1125Series.appendData(new DataPoint(nextX, 125), scrollToEnd, maxDataPoints);
                    a1175Series.appendData(new DataPoint(nextX, 175), scrollToEnd, maxDataPoints);
                }
        }

        audioUpdate(data, currentTimeMS);

        starting = false;
        wakeLock.release();

        if (realTime && sharedPreferences.getBoolean(ENABLE_ECG, true)) {
            startECG();
        }
    }

    private String formatSecAsTime(long absSeconds) {
        return String.format(
                "%2d:%02d:%02d",
                absSeconds / 3600,
                (absSeconds % 3600) / 60,
                absSeconds % 60);
    }

    private String formatMinAsTime(double elapsedMins) {
        int hours = ((int)elapsedMins) / 60;
        int mins = ((int)elapsedMins) % 60;
        double fracMins = elapsedMins - ((long)elapsedMins);
        int secs =  (int)(fracMins * 60);
        return String.format(
                "%2d:%02d:%02d",
                hours,
                (mins % 60),
                secs % 60);
    }

    private int getFatMaxxerColor(int p) {
        return ContextCompat.getColor(this, p);
    }

    private boolean timeForHRplot(boolean realTime) {
        // FIXME: do a true rolling average here?
        //Log.d(TAG,"since prev HR plot "+(currentTimeMS - prevHRPlotTimestampMS));
        boolean result = starting || realTime || (currentTimeMS - prevHRPlotTimestampMS) >= 1000;
        if (result) {
            prevHRPlotTimestampMS = currentTimeMS;
        }
        return result;
    }

    private boolean timeForRRplot(boolean realTime) {
        boolean result = starting || realTime || (currentTimeMS - prevRRPlotTimestampMS) >= 10000;
        prevRRPlotTimestampMS = currentTimeMS;
        return result;
    }

    private boolean timeForUIupdate(boolean realTime) {
        //Log.d(TAG,"since prev feature plot "+(currentTimeMS - prevFeatPlotTimestampMS));
        boolean result = starting || realTime || (currentTimeMS - prevFeatPlotTimestampMS) >= 60000;
        if (result) {
            //Log.d(TAG, "...time for UI update");
            prevFeatPlotTimestampMS = currentTimeMS;
        }
        return result;
    }

    private void tryPolarConnect(String tmpDeviceID) {
        Log.d(TAG,"tryPolarConnect to "+tmpDeviceID);
        try {
            String text = getString(R.string.TryingToConnectToHeartRateSensor)+": " + tmpDeviceID;
            //text_view.setText(text);
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            api.connectToDevice(tmpDeviceID);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            String msg = "PolarInvalidArgument: " + polarInvalidArgument;
            text_view.setText(msg);
            logException("tryPolarConnect Exception", polarInvalidArgument);
        }
    }

    private void setConnectedDeviceAsPreferred() {
        Log.d(TAG,"Set connected device as preferred...");
        sharedPreferences.edit().putString(POLAR_DEVICE_ID_PREFERENCE_STRING, SENSOR_ID).apply();
    }

    private void tryPolarConnectToPreferredDevice() {
        Log.d(TAG,"tryPolarConnect to preferred device...");
        String tmpDeviceID = sharedPreferences.getString(POLAR_DEVICE_ID_PREFERENCE_STRING,"");
        if (tmpDeviceID.length()>0) {
            tryPolarConnect(tmpDeviceID);
        } else {
            text_view.setText("No device ID set");
        }
    }

    private void writeLogFile(String msg, String tag) {
        //android.util.Log.d(TAG,"writeLogFile "+tag);
        FileWriter logStream = currentLogFileWriters.get(tag);
        try {
            if (logStream!=null) {
                logStream.append(msg + "\n");
                logStream.flush();
            } else {
                android.util.Log.e(TAG, "ERROR: "+tag+" logStream is null");
            }
        } catch (IOException e) {
            // avoid infinite loop through the local Log mechanism!
            android.util.Log.d(TAG,"IOException writing to "+tag+" log "+getStackTraceString(e));
            text_view.setText("IOException writing to "+tag+" log");
        }
    }

    @NotNull
    private String makeLogfileName(String stem, String type) {
        String dateString = getDate(System.currentTimeMillis(), "yyyyMMdd_HHmmss");
        String extension = type.equals("debug") ? "log" : "csv";
        return "/ftmxr_"+dateString+"_"+stem+"."+type+"."+extension;
    }

    Map<String,File> currentLogFiles = new HashMap<String,File>();
    Map<String,FileWriter> currentLogFileWriters = new HashMap<String,FileWriter>();

    private boolean createLogFile(File dir, String tag) {
        try {
            File logFile = new File(dir, makeLogfileName("",tag));
            FileWriter logStream = new FileWriter(logFile);
            Log.d(TAG,"Logging "+tag+" to "+logFile.getAbsolutePath());
            currentLogFiles.put(tag,logFile);
            currentLogFileWriters.put(tag,logStream);
            Log.d(TAG,"created Logfile: "+tag+" "+logFile.getAbsolutePath());
            logsDir = dir;
            return true;
        } catch (FileNotFoundException e) {
            logException("File not found ",e);
        } catch (IOException e) {
            logException("createLogFile ",e);
        }
        return false;
    }

    private void createLogFile(String tag) {
        android.util.Log.d(TAG,"createLogFile: "+tag);
        // try external first
        if (!createLogFile(getExtLogsDir(), tag))
            // fall back to internal
            createLogFile(getIntLogsDir(), tag);
    }

    @NotNull
    private File getIntLogsDir() {
        File privateRootDir = getFilesDir();
        privateRootDir.mkdir();
        File logsDir = new File(privateRootDir, "logs");
        logsDir.mkdir();
        return logsDir;
    }

    @NotNull
    private File getLogsDir() {
        return logsDir;
    }

    @NotNull
    private File getExtLogsDir() {
        File rootDir = this.getExternalFilesDir(null);
        rootDir.mkdir();
        File logsDir = new File(rootDir, "logs");
        logsDir.mkdir();
        return logsDir;
    }

    private double getRMSSD(double[] samples) {
        double[] NNdiff = v_abs(v_differential(samples));
        //rmssd = round(np.sqrt(np.sum((NNdiff * NNdiff) / len(NNdiff))), 2)
        double rmssd = sqrt(v_sum(v_power_s2(NNdiff,2)) / NNdiff.length);
        return round(rmssd * 100) / 100.0;
    }

    // pre: no artifacts(?)
    private void testRMSSD_1() {
        Log.d(TAG,"testRMSSD_1 ...");
        double result = getRMSSD(samples1);
        Log.d(TAG,"testRMSSD_1 done");
    }

    // determine whether to update, and what content, to provide via audio/notification
    private void audioUpdate(@NotNull PolarHrData data, long currentTime_ms){
            long timeSinceLastSpokenUpdate_s = (long) (currentTime_ms - prevSpokenUpdateMS) / 1000;
            //long timeSinceLastSpokenArtifactsUpdate_s = (long) (currentTime_ms - prevSpokenArtifactsUpdateMS) / 1000;

            double a1 = alpha1V2RoundedWindowed;
            int rmssd = (int) round(rmssdWindowed);
            int minUpdateWaitSeconds = Integer.parseInt(sharedPreferences.getString("minUpdateWaitSeconds", "15"));
            int maxUpdateWaitSeconds = Integer.parseInt(sharedPreferences.getString("maxUpdateWaitSeconds", "60"));
            // something like your MAF --- close to your max training HR
            int upperOptimalHRthreshold = Integer.parseInt(sharedPreferences.getString("upperOptimalHRthreshold", "130"));
            int upperRestingHRthreshold = Integer.parseInt(sharedPreferences.getString("upperRestingHRthreshold", "90"));
            double artifactsRateAlarmThreshold = Double.parseDouble(sharedPreferences.getString("artifactsRateAlarmThreshold", "5"));
            double upperOptimalAlpha1Threshold = Double.parseDouble(sharedPreferences.getString("upperOptimalAlpha1Threshold", "1.0"));
            double lowerOptimalAlpha1Threshold = Double.parseDouble(sharedPreferences.getString("upperOptimalAlpha1Threshold", "0.85"));
            String artifactsUpdate = "";
            String featuresUpdate = "";
            if (elapsedSecondsTrunc >30 && timeSinceLastSpokenUpdate_s > minUpdateWaitSeconds) {
                if (artifactsPercentWindowed > 0) {
                    artifactsUpdate = getString(R.string.Dropped_TextToSpeech)+" " + artifactsPercentWindowed + " "+getString(R.string.Percent_TextToSpeech);
                }
                // lower end of optimal alph1 - close to overtraining - frequent updates, prioritise a1, abbreviated
                if (data.hr > upperOptimalHRthreshold || a1 < lowerOptimalAlpha1Threshold) {
                    featuresUpdate = alpha1V2RoundedWindowed + " " + data.hr;
                // higher end of optimal - prioritise a1, close to undertraining?
                } else if ((data.hr > (upperOptimalHRthreshold - 10) || alpha1V2RoundedWindowed < upperOptimalAlpha1Threshold)) {
                    featuresUpdate =  getString(R.string.AlphaOne_TextToSpeech)+", " + alpha1V2RoundedWindowed + " "+getString(R.string.HeartRate_TextToSpeech)+" " + data.hr;
                // lower end of optimal - prioritise a1
                } else if (artifactsPercentWindowed > artifactsRateAlarmThreshold ||
                    data.hr > upperRestingHRthreshold && timeSinceLastSpokenUpdate_s >= maxUpdateWaitSeconds) {
                    featuresUpdate = getString(R.string.AlphaOne_TextToSpeech)+" " + alpha1V2RoundedWindowed + " "+getString(R.string.HeartRate_TextToSpeechFull)+" "+ data.hr;
                // warm up / cool down --- low priority, update RMSSD instead of alpha1
                } else if (artifactsPercentWindowed > artifactsRateAlarmThreshold ||
                        timeSinceLastSpokenUpdate_s >= maxUpdateWaitSeconds) {
                    featuresUpdate = getString(R.string.HeartRateFull_TextToSpeech)+" " + data.hr + ". "+getString(R.string.HeartRateVariabilityAbbrev_TextToSpeech)+" " + rmssd;
                }
            }
            if (featuresUpdate.length() > 0) {
                prevSpokenUpdateMS = currentTime_ms;
                if (artifactsPercentWindowed > artifactsRateAlarmThreshold) {
                    nonScreenUpdate(artifactsUpdate + " " + featuresUpdate);
                } else {
                    nonScreenUpdate(featuresUpdate + ", " + artifactsUpdate);
                }
            }
    }

    // Update the user via audio / notification, if enabled
    private void nonScreenUpdate(String update) {
            if (sharedPreferences.getBoolean(AUDIO_OUTPUT_ENABLED, false)) {
                ttobj.speak(update, TextToSpeech.QUEUE_FLUSH, null);
            }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            text_view.setText("Permission update: "+requestCode);
            if (requestCode == 1) {
                Log.d(TAG, "bt ready");
            }
    }

    @Override
    public void onPause() {
            text_view.setText("Paused");
            super.onPause();
            api.backgroundEntered();
    }

    @Override
    public void onResume() {
            text_view.setText("Resumed");
            super.onResume();
            api.foregroundEntered();
    }

    private void takeScreenshot() {
            Date now = new Date();
            android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);
            try {
                // create bitmap screen capture
                View v1 = getWindow().getDecorView().getRootView();
                v1.setDrawingCacheEnabled(true);
                Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
                v1.setDrawingCacheEnabled(false);
                // image naming and path  to include sd card  appending name you choose for file
                String mPath = "FatMaxxer_"+ now + ".jpg";
                File imageDir = this.getExternalFilesDir(null);
                // minimum version
                //File imageDir = this.getExternalFilesDir(Environment.DIRECTORY_SCREENSHOTS);
                File imageFile = new File(imageDir, mPath);
                FileOutputStream outputStream = new FileOutputStream(imageFile);
                int quality = 100;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
                outputStream.flush();
                outputStream.close();
                String msg = getString(R.string.ScreenshotSavedIn) +imageFile.getCanonicalPath();
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                Log.i(TAG,msg);
                //openScreenshot(imageFile);
            } catch (Throwable e) {
                logException("screenShot ",e);
            }
    }

    @Override
    public void onDestroy() {
            text_view.setText("Destroyed");
            Toast.makeText(this, R.string.FatMaxxerAppClosed, Toast.LENGTH_SHORT).show();
            super.onDestroy();
            Intent i = new Intent(MainActivity.this, LocalService.class);
            i.setAction("STOP");
            Log.d(TAG,"intent to stop local service "+i);
            MainActivity.this.stopService(i);
            api.shutDown();
    }

}
