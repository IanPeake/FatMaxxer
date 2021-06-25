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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
//import android.util.Log;
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
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

// https://github.com/jjoe64/GraphView
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

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

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrData;

import static java.lang.Math.abs;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;

import static online.fatmaxxer.publicRelease1.MainActivity.FMMenuItem.*;

public class MainActivity extends AppCompatActivity {
    public static final boolean requestLegacyExternalStorage = true;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String API_LOGGER_TAG = "FatMaxxer";
    public static final String AUDIO_OUTPUT_ENABLED = "audioOutputEnabled";
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_TAG = "alpha1update";
    public static final String ALPHA_1_CALC_PERIOD_PREFERENCE_STRING = "alpha1CalcPeriod";
    public static final String LAMBDA_PREFERENCE_STRING = "lambdaPref";
    public static final String ARTIFACT_REJECTION_THRESHOLD_PREFERENCE_STRING = "artifactThreshold";
    public static final String NOTIFICATIONS_ENABLED_PREFERENCE_STRING = "notificationsEnabled";
    public static final String POLAR_DEVICE_ID_PREFERENCE_STRING = "polarDeviceID";
    public static final String KEEP_LOGS_PREFERENCE_STRING = "keepLogs";
    public static final String EXPERIMENTAL_PREFERENCE_STRING = "experimental";
    public static final String KEEP_SCREEN_ON_PREFERENCE_STRING = "keepScreenOn";
    public static final String NOTIFICATION_DETAIL_PREFERENCE_STRING = "notificationDetail";
    public static final String IMPORT_CSV_FAILED_DATA_IS_NULL = "Import CSV failed: data is null";
    public static final String IMPORT_CSV_FAILED_COULD_NOT_GET_URI_FROM_DATA = "Import CSV failed: could not get Uri from data";
    public static final String RR_LOGFILE_HEADER = "timestamp, rr, since_start ";

    final double alpha1HRVvt1 = 0.75;
    final double alpha1HRVvt2 = 0.5;
    private int a1v2cacheMisses = 0;
    private long lastScrollToEndElapsedSec = 0;

    // FIXME: Catch UncaughtException
    // https://stackoverflow.com/questions/19897628/need-to-handle-uncaught-exception-and-send-log-file

    public MainActivity() {
        //super(R.layout.activity_fragment_container);
        super(R.layout.activity_main);
        Log = new Log();
    }

    public void deleteFile(File f) {
        Log.d(TAG, "deleteFile " + f.getPath());
        File fdelete = f;
        if (fdelete.exists()) {
            Log.d(TAG, "file Deleted :" + fdelete.getPath());
            if (fdelete.delete()) {
                Log.d(TAG, "file Deleted :" + fdelete.getPath());
            } else {
                Log.d(TAG, "file not Deleted? Will try after exit :" + fdelete.getPath());
                fdelete.deleteOnExit();
            }
        } else {
            Log.d(TAG, "file does not exist??" + fdelete.getPath());
        }
    }

    public void startAnalysis() {
        searchForPolarDevices();
        // TODO: CHECK: is this safe or do we have to wait for some other setup tasks to finish...?
        tryPolarConnect();
    }

    @Override
    public void finish() {
        closeLogs();
        boolean keepLogs = sharedPreferences.getBoolean(KEEP_LOGS_PREFERENCE_STRING, false);
        if (!keepLogs) {
            Log.d(TAG,"finish: delete current log files");
            deleteCurrentLogFiles();
        } else {
            Toast.makeText(getBaseContext(), "Not deleting log files", Toast.LENGTH_LONG).show();
        }
        uiNotificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
        try {
            api.disconnectFromDevice(DEVICE_ID);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            Log.d(TAG, "Quit: disconnectFromDevice: polarInvalidArgument " +
                    polarInvalidArgument.getStackTrace());
        }
        super.finish();
    }

    private long pressedTime;

//    public void onBackPressed() {
//        if (pressedTime + 2000 > System.currentTimeMillis()) {
//            super.onBackPressed();
//            finish();
//        } else {
//            Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT).show();
//        }
//        pressedTime = System.currentTimeMillis();
//    }

    public void onBackPressed() {
        Toast.makeText(getBaseContext(), "Use Menu > Quit to exit", Toast.LENGTH_LONG).show();
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
    Disposable ecgDisposable;
    Disposable accDisposable;
    Disposable gyrDisposable;
    Disposable magDisposable;
    Disposable ppgDisposable;
    Disposable ppiDisposable;
    Disposable scanDisposable;
    Disposable autoConnectDisposable;
    // Serial number?? 90E2D72B
    String DEVICE_ID = "";
    SharedPreferences sharedPreferences;

    Notification initialNotification;

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
            Log.d(TAG, "LocalService: onDestroy");
            super.onDestroy();
            //mNM.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
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
        @Override
        public void onCreate() {
            Log.d(TAG, "FatMaxxer service onCreate");
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

            //notification.notify(NOTIFICATION_TAG, NOTIFICATION_ID, );
            startForeground(2, notification);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.d("FatMaxxerLocalService", "Received start id " + startId + ": " + intent);
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
        private String createNotificationChannel() {
            NotificationChannel chan = new NotificationChannel(SERVICE_CHANNEL_ID,
                    SERVICE_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
//            chan.lightColor = Color.BLUE;
//            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE;
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
//    public static class Binding extends Activity {

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
            Toast.makeText(MainActivity.this, "FatMaxxer bound to service",
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
                throwable.printStackTrace();
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
            ybox = smoothnDetrending(xbox);
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
        //double[] finalSegment = v_subtract(ybox, xfit, offset, scale);
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

    public double[] smoothnDetrending(double[] dRR) {
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
            x = smoothnDetrending(xUnsmoothed);
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

    public boolean experimental = false;
    // 120s ring buffer for dfa alpha1
    public final int featureWindowSizeSec = 120;
    // buffer to allow at least 45 beats forward/backward per Kubios
    public final int sampleBufferMarginSec = 45;
    //public final int rrWindowSizeSec = featureWindowSizeSec + sampleBufferMarginSec;
    public final int rrWindowSizeSec = featureWindowSizeSec;
    // time between alpha1 calculations
    public int alpha1EvalPeriod;
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
    public long firstSampleMS;
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
    final Set<String> emptyStringSet = new HashSet<String>();
    // elapsed time in terms of cumulative sum of all seen RRs (as for HRVLogger)
    long logRRelapsedMS = 0;
    // the last time (since epoch) a1 was evaluated
    public long prevA1Timestamp = 0;
    public double prevrr = 0;
    public boolean starting = false;
    public long prevSpokenUpdateMS = 0;
    public int totalRejected = 0;
    public boolean thisIsFirstSample = false;
    long currentTimeMS;
    long elapsedMS;
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

    private FileWriter debugLogStream;

    private class Log {
        public int d(String tag, String msg) {
            if (debugLogStream != null) writeLogFile(msg, debugLogStream, "debug");
            return android.util.Log.d(tag, msg);
        }

        public int w(String tag, String msg) {
            if (debugLogStream != null) writeLogFile(msg, debugLogStream, "debug");
            return android.util.Log.e(tag, msg);
        }

        public int e(String tag, String msg) {
            if (debugLogStream != null) writeLogFile(msg, debugLogStream, "debug");
            return android.util.Log.e(tag, msg);
        }

        public int i(String tag, String msg) {
            if (debugLogStream != null) writeLogFile(msg, debugLogStream, "debug");
            return android.util.Log.i(tag, msg);
        }
    }

    private static Log Log;

    private void closeLog(FileWriter fw) {
        try {
            fw.close();
        } catch (IOException e) {
            Log.d(TAG, "IOException closing " + fw.toString() + ": " + e.toString());
        }
    }

    private void closeLogs() {
        closeLog(rrLogStreamNew);
        //closeLog(rrLogStreamLegacy);
        closeLog(featureLogStreamNew);
        //closeLog(featureLogStreamLegacy);
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
        MENU_RENAME_LOGS,
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
        if (logOrReplayStarted()) startedStatus=" (before new connect/replay)";
        menu.add(0, FMMenuItem.MENU_QUIT.ordinal(), Menu.NONE, "Quit "+startedStatus);
        if (sharedPreferences.getBoolean(EXPERIMENTAL_PREFERENCE_STRING, false)) {
            menu.add(0, menuItem(MENU_IMPORT), Menu.NONE, "Import RR Log");
            menu.add(0, menuItem(MENU_REPLAY), Menu.NONE, "Replay RR Log");
            menu.add(0, menuItem(MENU_RENAME_LOGS), Menu.NONE, "Rename Current Logs");
        }
        menu.add(0, menuItem(MENU_EXPORT_SELECTED_LOG_FILES), Menu.NONE, "Export Selected Logs");
        menu.add(0, menuItem(MENU_DELETE_SELECTED_LOG_FILES), Menu.NONE, "Delete Selected Logs");
        menu.add(0, menuItem(MENU_OLD_LOG_FILES), Menu.NONE, "Delete All Old Logs");
        menu.add(0, menuItem(MENU_DELETE_DEBUG), Menu.NONE, "Delete All Debug Logs");
        menu.add(0, menuItem(MENU_DELETE_ALL), Menu.NONE, "Delete All Logs");
        String tmpDeviceId = sharedPreferences.getString(POLAR_DEVICE_ID_PREFERENCE_STRING, "");
        // Offer connect if not already connected/replaying
        if (tmpDeviceId.length() > 0 && !logOrReplayStarted()) {
            menu.add(0, menuItem(MENU_CONNECT_DEFAULT), Menu.NONE, "Connect preferred device " + tmpDeviceId);
        }
        int i = 0;
        // Offer connect for discovered devices if not already connected/replaying
        if (!logOrReplayStarted()) {
            for (String tmpDeviceID : discoveredDevices.keySet()) {
                menu.add(0, menuItem(MENU_CONNECT_DISCOVERED) + i, Menu.NONE, "Connect " + discoveredDevices.get(tmpDeviceID));
                discoveredDevicesMenu.put(menuItem(MENU_CONNECT_DISCOVERED) + i, tmpDeviceID);
                i++;
            }
        }
        menu.add(0, menuItem(MENU_SEARCH), Menu.NONE, "Search for Polar devices");
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean logOrReplayStarted() {
        return started; //testDataQueue!=null && !testDataQueue.isEmpty();
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

    public void importLogFile() {
        Intent receiveIntent = new Intent(Intent.ACTION_GET_CONTENT);
        receiveIntent.setType("text/*");
        receiveIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(receiveIntent, "Import CSV"), REQUEST_IMPORT_CSV); //REQUEST_IMPORT_CSV is just an int representing a request code for the activity result callback later
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
        startActivityForResult(Intent.createChooser(shareIntent, "Share log files to.."), exportLogFilesCode);
    }

    public List<File> rrLogFiles() {
        Log.d(TAG, "logFiles...");
        File privateRootDir = getFilesDir();
        privateRootDir.mkdir();
        File logsDir = new File(privateRootDir, "logs");
        logsDir.mkdir();
        File[] allFiles = logsDir.listFiles();
        List<File> rrLogFiles = new ArrayList<File>();
        for (File f : allFiles) {
            String name = f.getName();
            if (isRRfileName(name)) {
                Log.d(TAG, "Found RR log file: " + getUri(f));
                rrLogFiles.add(f);
            } else {
                Log.d(TAG, "Not RR log file: " + getUri(f));
            }
        }
        return rrLogFiles;
    }

    private boolean isRRfileName(String name) {
        return name.endsWith(".rr.csv") || name.endsWith("RRIntervals.csv");
    }

    public List<Uri> logFiles() {
        Log.d(TAG, "logFiles...");
        File logsDir = getLogsDir();
        File[] allFiles = logsDir.listFiles();
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        Arrays.sort(allFiles, new Comparator<File>() {
            public int compare(File f1, File f2) {
//                return f2.getName().compareTo(f1.getName());
                return -Long.compare(f1.lastModified(),f2.lastModified());
            }
        });
        for (File f : allFiles) {
            Log.d(TAG, "Found log file: " + getUri(f));
            allUris.add(getUri(f));
        }
        return allUris;
    }

    public void renameLogs() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Rename");
        alert.setMessage("Tag to include in current log names");
        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                String msg = "Rename to " + value;
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                Log.d(TAG, msg);
                File logsDir = getLogsDir();
                File newRR = new File(logsDir,makeLogfileName(value,"rr"));
                File newFeatures = new File(logsDir,makeLogfileName(value,"features"));
                File newDebug = new File(logsDir,makeLogfileName(value,"debug"));
                if (currentLogFiles.get("rr").renameTo(newRR))
                    currentLogFiles.put("rr",newRR);
                if (currentLogFiles.get("features").renameTo(newFeatures))
                    currentLogFiles.put("features",newFeatures);
                if (currentLogFiles.get("debug").renameTo(newDebug))
                    currentLogFiles.put("debug",newDebug);
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        alert.show();
    }


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

    public void exportFiles(ArrayList<Uri> allUris) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, allUris);
        shareIntent.setType("text/plain");
        Log.d(TAG,"Exporting log file/s via ShareSheet "+allUris.toString());
        startActivity(Intent.createChooser(shareIntent, "Share log files to.."));
    }

    public void deleteCurrentLogFiles() {
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        StringBuilder filenames = new StringBuilder();
        deleteFile(currentLogFiles.get("rr"));
        deleteFile(currentLogFiles.get("features"));
        deleteFile(currentLogFiles.get("debug"));
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
            Toast.makeText(getBaseContext(), "Deleted " + filenames.toString(), Toast.LENGTH_LONG).show();
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
        Toast.makeText(getBaseContext(), "Deleted " + filenames.toString(), Toast.LENGTH_LONG).show();
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
        startActivity(Intent.createChooser(shareIntent, "Share log files to.."));
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
        Toast.makeText(getBaseContext(), "Deleting (on exit) " + filenames.toString(), Toast.LENGTH_LONG).show();
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
        if (requestCode==REQUEST_IMPORT_CSV) {
            if (data == null) {
                Log.w(TAG, IMPORT_CSV_FAILED_DATA_IS_NULL);
                Toast.makeText(getBaseContext(), IMPORT_CSV_FAILED_DATA_IS_NULL, Toast.LENGTH_LONG);
            } else {
                Uri uri = data.getData();
                if (uri == null) {
                    Log.w(TAG, IMPORT_CSV_FAILED_COULD_NOT_GET_URI_FROM_DATA);
                    Toast.makeText(getBaseContext(), IMPORT_CSV_FAILED_COULD_NOT_GET_URI_FROM_DATA, Toast.LENGTH_LONG);
                } else {
                    importRRFile(uri, getLogsDir());
                }
            }
        }
    }

    // https://stackoverflow.com/questions/10854211/android-store-inputstream-in-file/39956218
    private void importRRFile(Uri uri, File dir) {
        String filename = getFileName(uri);
        if (!isRRfileName(filename)) {
            String msg = "Not RR log, not importing: "+filename;
            Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
            Log.w(TAG, msg);
            return;
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
                    Toast.makeText(getBaseContext(), "Imported RR file: " + filename, Toast.LENGTH_LONG).show();
                }
            } finally {
                    input.close();
            }
        } catch (Throwable e) {
            logException("Exception importing RR log",e);
        }
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
            uris[i] = uri;
            items[i] = uri.getLastPathSegment();
            i++;
        }
        adb.setMultiChoiceItems(items, checkItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i, boolean b) {
                checkItems[i] = b;
                //Toast.makeText(getBaseContext(), "Option " + i + " selected", Toast.LENGTH_SHORT).show();
            }
        });
        adb.setNegativeButton("Cancel", null);
        adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int iOption) {
                        //Toast.makeText(getBaseContext(), "Options: " + checkItems.toString(), Toast.LENGTH_SHORT).show();
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
        adb.setTitle("Select files for export");
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
        adb.setNegativeButton("Cancel", null);
        adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        int selectedPosition = ((AlertDialog) dialogInterface).getListView().getCheckedItemPosition();
                        File f = files[selectedPosition];
                        replayRRfile(f);
                    }
                }
        );
        adb.setTitle("Select RR file for test input");
        adb.show();
    }
    //                            logException("Exception opening RR log for replay", e);

    //runs without a timer by reposting handler at the end of the runnable
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
                Toast.makeText(getBaseContext(), "Finished replay", Toast.LENGTH_LONG).show();
                takeScreenshot();
            }
        }
    };

    static class TestDataPacket {
        PolarHrData polarData;
        long timestamp;
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
        // header
        try {
            String header = reader.readLine();
            String headerExpected = RR_LOGFILE_HEADER;
            if (!header.equals(headerExpected)) {
                String msg = f.getName() + ": warning, expected header " + headerExpected + " got " + header;
                Log.w(TAG, msg);
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
//                return;
            }
            // gap
            String gap = reader.readLine();
            if (!header.equals(headerExpected)) {
                String msg = f.getName() + ": warning, expected empty line, got " + gap;
                Log.w(TAG, msg);
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
                return;
            }
            // body
            String line = reader.readLine();
            double polarHR = 0;
            int lineCount = 0;
            while (line != null) {
                //Log.d(TAG, "RR file line: " + line);
                String[] fields = line.split(",");
                if (fields.length >= 3) {
                    //Log.d(TAG, "RR file line fields: " + fields[0] + " " + fields[1] + " " + fields[2]);
                    int rr = Integer.valueOf(fields[1]);
                    long localTimeStamp = Long.valueOf(fields[0]);
                    List<Integer> rrs = new ArrayList<Integer>();
                    rrs.add(rr);
                    PolarHrData data = new PolarHrData((60000 / rr), rrs, true, true, true);
                    TestDataPacket testData = new TestDataPacket();
                    testData.polarData = data;
                    testData.timestamp = localTimeStamp;
                    testDataQueue.add(testData);
                    //updateTrackedFeatures(data);
                    lineCount++;
                    if (lineCount == 1) {
                        Log.d(TAG, "Started replay timer");
                        timerHandler.postDelayed(timerRunnable, 1000);
                    }
                }
                line = reader.readLine();
            }
            Toast.makeText(getBaseContext(), "Started RR file replay: " + f.getName(), Toast.LENGTH_LONG).show();
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
                //Toast.makeText(getBaseContext(), "Option " + i + " selected", Toast.LENGTH_SHORT).show();
            }
        });
        adb.setNegativeButton("Cancel", null);
        adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
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
        adb.setTitle("Select files for deletion");
        adb.show();
    }


    public boolean onOptionsItemSelected(MenuItem item) {
        //    void tagSelectedLogsForExport() {
        //    public boolean onCreateOptionsMenu(Menu menu) {
        //        MenuInflater inflater = getMenuInflater();
        //        inflater.inflate(R.menu.options_menu, menu);
        //        return true;
        //    }
        //respond to menu item selection
        Log.d(TAG, "onOptionsItemSelected... " + item.getItemId());
        int itemID = item.getItemId();
        if (itemID == menuItem(MENU_QUIT)) finish();
        if (itemID == menuItem(MENU_RENAME_LOGS)) renameLogs();
        if (itemID == menuItem(MENU_REPLAY)) selectReplayRRfile();
        if (itemID == menuItem(MENU_EXPORT_SELECTED_LOG_FILES)) exportSelectedLogFiles();
        if (itemID == menuItem(MENU_DELETE_SELECTED_LOG_FILES)) deleteSelectedLogFiles();
        if (itemID == menuItem(MENU_EXPORT)) exportLogFiles();
        if (itemID == menuItem(MENU_IMPORT)) importLogFile();
        if (itemID == menuItem(MENU_DELETE_ALL)) deleteAllLogFiles();
        if (itemID == menuItem(MENU_DELETE_DEBUG)) deleteAllDebugFiles();
        if (itemID == menuItem(MENU_CONNECT_DEFAULT)) tryPolarConnect();
        if (itemID == menuItem(MENU_OLD_LOG_FILES)) deleteOldLogFiles();
        if (itemID == menuItem(MENU_SEARCH)) searchForPolarDevices();
        if (discoveredDevicesMenu.containsKey(item.getItemId())) {
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
        text_view.setText("Searching for Polar devices...");
        if (broadcastDisposable == null) {
            broadcastDisposable = api.startListenForPolarHrBroadcasts(null)
                    .subscribe(polarBroadcastData -> {
                                if (!discoveredDevices.containsKey(polarBroadcastData.polarDeviceInfo.deviceId)) {
                                    String desc = polarBroadcastData.polarDeviceInfo.name;
                                    String msg = "Discovered " + desc + " HR " + polarBroadcastData.hr;
                                    discoveredDevices.put(polarBroadcastData.polarDeviceInfo.deviceId, desc);
                                    text_view.setText(msg);
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
    private String createUINotificationChannel() {
        NotificationChannel chan = new NotificationChannel(UI_CHANNEL_ID,
                UI_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
//            chan.lightColor = Color.BLUE;
//            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE;
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
        Log.e(TAG, comment + " exception " + e.toString() + " " + getStackTraceString(e));
    }

    public void handleUncaughtException(Thread thread, Throwable e) {
        logException("uncaught ", e);
        exportDebug();
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set up handler for uncaught exceptions.
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                handleUncaughtException(thread, e);
            }
        });
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
        api = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_HR | PolarBleApi.FEATURE_BATTERY_INFO);
        api.setPolarFilter(false);

        text_time = this.findViewById(R.id.timeView);
        text_batt = this.findViewById(R.id.battView);
        text_mode = this.findViewById(R.id.modeView);
        text_hr = this.findViewById(R.id.hrTextView);
        //text_hr.setText("\u2764"+"300");
        text_secondary = this.findViewById(R.id.hrvTextView);
        text_secondary_label = this.findViewById(R.id.hrvLabel);
        text_a1 = this.findViewById(R.id.a1TextView);
        text_a1_label = this.findViewById(R.id.a1Label);
        text_artifacts = this.findViewById(R.id.artifactsView);
        text_view = this.findViewById(R.id.textView);

        //text.setTextSize(100);
        //text.setMovementMethod(new ScrollingMovementMethod());
        // text.setText(message);
        text_view.setText("Text output goes here...");

        scrollView = this.findViewById(R.id.application_container);
        // FIXME: Why does the scrollable not start with top visible?
        // scrollView.scrollTo(0,0);

        /////
        /////
        /////
        /////
        //testDFA_alpha1();
        //testRMSSD_1();
        /////
        /////
        /////
        /////

        api.setApiLogger(s -> Log.d(API_LOGGER_TAG, s));

        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo());

        ttobj = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttobj.setLanguage(Locale.UK);
                nonScreenUpdate("Voice output ready");
            }
        });

//        rrLogStreamLegacy = createLogFile("rr");
//        featureLogStreamLegacy = createLogFile("features");

        rrLogStreamNew = createLogFileNew("rr", "csv");
//        writeLogFiles("timestamp, rr, since_start ", rrLogStreamNew, rrLogStreamLegacy, "rr");
//        writeLogFiles("", rrLogStreamNew, rrLogStreamLegacy, "rr");
        writeLogFile(RR_LOGFILE_HEADER, rrLogStreamNew, "rr");
        writeLogFile("", rrLogStreamNew, "rr");
        featureLogStreamNew = createLogFileNew("features", "csv");
        writeLogFile("timestamp,heartrate,rmssd,sdnn,alpha1v1,filtered,samples,droppedPercent,artifactThreshold,alpha1v2", featureLogStreamNew, "features");
        debugLogStream = createLogFileNew("debug", "log");
        
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

        rrSeries.appendData(new DataPoint(0,0), false, maxDataPoints);
        hrSeries.appendData(new DataPoint(0,0), false, maxDataPoints);
        a1V2Series.appendData(new DataPoint(0,0), false, maxDataPoints);
        rmssdSeries.appendData(new DataPoint(0,0),false,maxDataPoints);
        hrWinSeries.appendData(new DataPoint(0,0),false,maxDataPoints);

        //hrvSeries.setColor(Color.BLUE);

        //setContentView(R.layout.activity_settings);
        Log.d(TAG, "Settings...");
        text_view.setText("Settings");
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
                Log.d(TAG, "Polar device CONNECTED: " + polarDeviceInfo.deviceId);
                text_view.setText("Connected to " + polarDeviceInfo.deviceId);
                Toast.makeText(getBaseContext(), "Connected to " + polarDeviceInfo.deviceId, Toast.LENGTH_SHORT).show();
                ensurePreferenceSet(POLAR_DEVICE_ID_PREFERENCE_STRING,polarDeviceInfo.deviceId);
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "Polar device CONNECTING: " + polarDeviceInfo.deviceId);
                text_view.setText("Connecting to " + polarDeviceInfo.deviceId);
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: " + polarDeviceInfo.deviceId);
                text_view.setText("Disconnected from " + polarDeviceInfo.deviceId);
                ecgDisposable = null;
                accDisposable = null;
                gyrDisposable = null;
                magDisposable = null;
                ppgDisposable = null;
                ppiDisposable = null;
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
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        // auto-start
        if(!sharedPreferences.getBoolean(EXPERIMENTAL_PREFERENCE_STRING,false)) {
            startAnalysis();
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

    private void updateTrackedFeatures(@NotNull PolarHrData data, long currentVirtualTimeMS, boolean realTime) {
////////////////        currentTimeMS = System.currentTimeMillis();
        currentTimeMS = currentVirtualTimeMS;
        if (!started) {
            Log.d(TAG, "hrNotificationReceived: started!");
            started = true;
            starting = true;
            thisIsFirstSample = true;
            firstSampleMS = currentTimeMS;
            // FIXME: why does the scroller not start with the top visible?
            scrollView.scrollTo(0,0);
        }
        elapsedMS = (currentTimeMS - firstSampleMS);
        elapsedSecondsTrunc = elapsedMS / 1000;
        //Log.d(TAG, "hrNotificationReceived cur "+currentTimeMS+" elapsed "+elapsedMS);
        wakeLock.acquire();
        //Log.d(TAG, "updateTrackedFeatures");
        if (timeForUIupdate(realTime)) {
            String lambdaPref = sharedPreferences.getString(LAMBDA_PREFERENCE_STRING, "500");
            lambdaSetting = Integer.valueOf(lambdaPref);
            experimental = sharedPreferences.getBoolean(EXPERIMENTAL_PREFERENCE_STRING, false);
            if (sharedPreferences.getBoolean(KEEP_SCREEN_ON_PREFERENCE_STRING, false)) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
        currentHR = data.hr;
        if (timeForUIupdate(realTime)) {
            String artifactCorrectionThresholdSetting = sharedPreferences.getString(ARTIFACT_REJECTION_THRESHOLD_PREFERENCE_STRING, "Auto");
            if (artifactCorrectionThresholdSetting.equals("Auto")) {
                if (data.hr > 95) {
                    exerciseMode = "Workout";
                    artifactCorrectionThreshold = 0.05;
                } else if (data.hr < 80) {
                    exerciseMode = "Light";
                    artifactCorrectionThreshold = 0.25;
                }
            } else if (artifactCorrectionThresholdSetting.equals("0.25")) {
                exerciseMode = "Light";
                artifactCorrectionThreshold = 0.25;
            } else {
                exerciseMode = "Workout";
                artifactCorrectionThreshold = 0.05;
            }
        }
        String notificationDetailSetting = "";
        String alpha1EvalPeriodSetting = "";
        Set<String> graphFeaturesSelected = emptyStringSet;
        graphFeaturesSelected = sharedPreferences.getStringSet("graphFeaturesSelectorKey",emptyStringSet);
        // preference updates
        if (timeForUIupdate(realTime)) {
            Log.d(TAG,"timeForUIupdate");
            Log.d(TAG,"graphFeaturesSelected "+graphFeaturesSelected);
            notificationDetailSetting = sharedPreferences.getString(NOTIFICATION_DETAIL_PREFERENCE_STRING, "full");
            alpha1EvalPeriodSetting = sharedPreferences.getString(ALPHA_1_CALC_PERIOD_PREFERENCE_STRING, "20");
            try {
                alpha1EvalPeriod = Integer.parseInt(alpha1EvalPeriodSetting);
            } catch (final NumberFormatException e) {
                //Log.d(TAG, "Number format exception alpha1EvalPeriod " + alpha1EvalPeriodSetting + " " + e.toString());
                alpha1EvalPeriod = 20;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(ALPHA_1_CALC_PERIOD_PREFERENCE_STRING, "20");
                editor.apply();
                //Log.d(TAG, "alpha1CalcPeriod wrote " + sharedPreferences.getString(ALPHA_1_CALC_PERIOD_PREFERENCE_STRING, "??"));
            }
            if (alpha1EvalPeriod < 5) {
                //Log.d(TAG, "alpha1EvalPeriod<5");
                alpha1EvalPeriod = 5;
                sharedPreferences.edit().putString(ALPHA_1_CALC_PERIOD_PREFERENCE_STRING, "5").apply();
            }
        }
        long timestamp = currentTimeMS;
        long logRRelapsedMS_snapshot = logRRelapsedMS;
        for (int rr : data.rrsMs) {
            String msg = "" + timestamp + "," + rr + "," + logRRelapsedMS;
            writeLogFile(msg, rrLogStreamNew, "rr");
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
            double newrr = data.rrsMs.get(si);
            double lowerBound = prevrr * (1 - artifactCorrectionThreshold);
            double upperBound = prevrr * (1 + artifactCorrectionThreshold);
            //Log.d(TAG, "prevrr " + prevrr + " lowerBound " + lowerBound + " upperBound " + upperBound);
            if (thisIsFirstSample || lowerBound < newrr && newrr < upperBound) {
                //Log.d(TAG, "accept RR within threshold" + newrr);
                // if in_RRs[(i-1)]*(1-artifact_correction_threshold) < in_RRs[i] < in_RRs[(i-1)]*(1+artifact_correction_threshold):
                rrInterval[newestSample] = newrr;
                rrIntervalTimestamp[newestSample] = currentTimeMS;
                newestSample = (newestSample + 1) % maxrrs;
                thisIsFirstSample = false;
            } else {
                //Log.d(TAG, "drop...");
                artifactTimestamp[newestArtifactSample] = currentTimeMS;
                newestArtifactSample = (newestArtifactSample + 1) % maxrrs;
                //Log.d(TAG, "reject artifact " + newrr);
                rejected += "" + newrr;
                haveArtifacts = true;
                totalRejected++;
            }
            prevrr = newrr;
        }
        String rejMsg = haveArtifacts ? (", Rejected: " + rejected) : "";
        int expired = 0;
        // expire old samples
        while (rrIntervalTimestamp[oldestSample] < currentTimeMS - rrWindowSizeSec * 1000) {
            oldestSample = (oldestSample + 1) % maxrrs;
            expired++;
        }
        //Log.d(TAG, "Expire old artifacts");
        while (oldestArtifactSample != newestArtifactSample && artifactTimestamp[oldestArtifactSample] < currentTimeMS - rrWindowSizeSec * 1000) {
            //Log.d(TAG, "Expire at " + oldestArtifactSample);
            oldestArtifactSample = (oldestArtifactSample + 1) % maxrrs;
        }
        //Log.d(TAG, "elapsedMS " + elapsedMS);
        //
        long absSeconds = Math.abs(elapsedSecondsTrunc);
        if (timeForUIupdate(realTime)) {
            String positive = String.format(
                    "%2d:%02d:%02d",
                    absSeconds / 3600,
                    (absSeconds % 3600) / 60,
                    absSeconds % 60);
            //text_time.setText(mode + "    " +positive + "    \uD83D\uDD0B"+batteryLevel);
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
//        if (elapsedSeconds > rrWindowSizeSeconds) {
//            featureWindowSamples = copySamplesFeatureWindow();
//            samples = featureWindowSamples;
//        }
        //Log.d(TAG, "Samples: " + v_toString(samples));
//        double[] dRR = v_differential(samples);
        //Log.d(TAG, "dRR: " + v_toString(dRR));

        //
        // FEATURES
        //
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
        if (timeForUIupdate(realTime)) Log.d(TAG,"Elapsed "+elapsedSecondsTrunc+" currentTimeMS "+currentTimeMS+ " a1evalPeriod "+alpha1EvalPeriod+" prevA1Timestamp "+prevA1Timestamp);
        boolean graphEnabled = realTime;
        boolean enoughElapsedSinceStart = elapsedSecondsTrunc > alpha1EvalPeriod;
        boolean oncePerPeriod = true; //elapsed % alpha1EvalPeriod <= 2;
        boolean enoughSinceLast = currentTimeMS >= (prevA1Timestamp + alpha1EvalPeriod*1000);
        //Log.d(TAG,"graphEnabled antecedents "+enoughElapsedSinceStart+" "+oncePerPeriod+" "+enoughElapsedSinceStart);
        // Logging must not be throttled during replay
        if (enoughElapsedSinceStart && oncePerPeriod && enoughSinceLast) {
//            graphEnabled = true;
            //Log.d(TAG,"alpha1...");
            alpha1V2Windowed = dfaAlpha1V2(samples, 2, 4, 30);
            alpha1V2RoundedWindowed = round(alpha1V2Windowed * 100) / 100.0;
            prevA1Timestamp = currentTimeMS;
            if (elapsedSecondsTrunc > 120) {
                writeLogFile("" + timestamp
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
                        featureLogStreamNew,
//                    featureLogStreamLegacy,
                        "features");
            }
            if (timeForUIupdate(realTime)) {
                if (sharedPreferences.getBoolean(NOTIFICATIONS_ENABLED_PREFERENCE_STRING, false)) {
                    Log.d(TAG, "Feature notification...");
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
                            .setContentTitle("a1 " + alpha1V2RoundedWindowed + " drop " + artifactsPercentWindowed + "%");
                    if (notificationDetailSetting.equals("full")) {
                        uiNotificationBuilder.setContentText("HR " + currentHR + " batt " + batteryLevel + "% rmssd " + rmssdWindowed);
                    } else if (notificationDetailSetting.equals("titleHR")) {
                        uiNotificationBuilder.setContentText("HR " + currentHR);
                    }
                    uiNotificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, uiNotificationBuilder.build());
                }
            }
        }

        //
        // UI (DISPLAY // AUDIO // LOGGING)
        //
        // Device Display
        if (timeForUIupdate(realTime)) {
            if (haveArtifacts && sharedPreferences.getBoolean(AUDIO_OUTPUT_ENABLED, false)) {
                //spokenOutput("drop");
                mp.start();
            }
            StringBuilder logmsg = new StringBuilder();
            logmsg.append(elapsedSecondsTrunc + "s");
            logmsg.append(", rrsMs: " + data.rrsMs);
            logmsg.append(rejMsg);
            logmsg.append(", a1V2 " + alpha1V2RoundedWindowed);
            //logmsg.append(", a1V1 " + alpha1V1RoundedWindowed);
            logmsg.append(", total rejected: " + totalRejected);
            String logstring = logmsg.toString();

            artifactsPercentWindowed = (int) round(nrArtifacts * 100 / (double) nrSamples);
            text_artifacts.setText("" + nrArtifacts + "/" + nrSamples + " (" + artifactsPercentWindowed + "%) [" + artifactCorrectionThreshold + "]");
            if (haveArtifacts) {
                text_artifacts.setBackgroundResource(R.color.colorHighlight);
            } else {
                text_artifacts.setBackgroundResource(R.color.colorBackground);
            }
            text_view.setText(logstring);
            text_hr.setText("" + data.hr);
            text_secondary_label.setText("RMSSD");
            text_secondary.setText("" + round(rmssdWindowed));
            text_a1.setText("" + alpha1V2RoundedWindowed);
            text_a1_label.setText("⍺1v2 ["+a1v2cacheMisses+"]");
            // configurable top-of-optimal threshold for alpha1
            double alpha1MaxOptimal = Double.parseDouble(sharedPreferences.getString("alpha1MaxOptimal", "1.0"));
            // wait for run-in period
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
            Log.d(TAG, data.hr + " " + alpha1V2RoundedWindowed + " " + rmssdWindowed);
            Log.d(TAG, logstring);
            Log.d(TAG, "Elapsed % alpha1EvalPeriod " + (elapsedSecondsTrunc % alpha1EvalPeriod));
        }
        elapsedMin = this.elapsedSecondsTrunc / 60.0;
        double tenSecAsMin = 1.0 / 6.0;
        boolean pre1 = elapsedMin > (graphViewPortWidth - tenSecAsMin);
        // 20 sec delay before next scroll
        //Log.d(TAG,"lastScrollToEndElapsedSed "+lastScrollToEndElapsedSec);
        boolean pre2 = elapsedSecondsTrunc > lastScrollToEndElapsedSec + 20;
        boolean scrollToEnd = (realTime && pre1 && pre2) || (!realTime && pre1);
        if (scrollToEnd) lastScrollToEndElapsedSec = elapsedSecondsTrunc;
        if (graphFeaturesSelected.contains("rr")) {
            for (int rr : data.rrsMs) {
                logRRelapsedMS_snapshot += rr;
                double tmpRRMins = logRRelapsedMS_snapshot / 60000.0;
                rrSeries.appendData(new DataPoint(tmpRRMins, rr / 5.0), scrollToEnd, maxDataPoints);
            }
        }
//        Log.d(TAG, "realTime "+realTime);
//        Log.d(TAG, "scrollToEnd antecedents "+pre1+" "+pre2);
//        Log.d(TAG, "tenSecAsMin "+tenSecAsMin);
//        Log.d(TAG, "elapsedMin "+elapsedMin);
//        Log.d(TAG, "scrollToEnd "+scrollToEnd);
//        Log.d(TAG, "graphViewPortWidth "+graphViewPortWidth);
//        Log.d(TAG, "graphEnabled "+graphEnabled);
        if (timeForUIupdate(realTime)) {
                //Log.d(TAG,"plot...");
                if (graphFeaturesSelected.contains("hr")) {
                    //Log.d(TAG,"plot hr");
                    hrSeries.appendData(new DataPoint(elapsedMin, data.hr), scrollToEnd, maxDataPoints);
                }
                if (graphFeaturesSelected.contains("a1")) {
                    //Log.d(TAG,"plot a1");
                    a1V2Series.appendData(new DataPoint(elapsedMin, alpha1V2Windowed * 100.0), scrollToEnd, maxDataPoints);
                }
                if (graphFeaturesSelected.contains("artifacts")) {
                    //Log.d(TAG,"plot artifacts");
                    artifactSeries.appendData(new DataPoint(elapsedMin, artifactsPercentWindowed), scrollToEnd, maxDataPoints);
                }
                if (graphFeaturesSelected.contains("rmssd")) {
                    //Log.d(TAG,"plot rmssd");
                    rmssdSeries.appendData(new DataPoint(elapsedMin, round(rmssdWindowed * 2)), scrollToEnd, maxDataPoints);
                }
                if (graphFeaturesSelected.contains("hrWin")) {
                    //Log.d(TAG,"plot hrWin");
                    hrWinSeries.appendData(new DataPoint(elapsedMin, hrMeanWindowed), scrollToEnd, maxDataPoints);
                }
                if (scrollToEnd) {
                    double nextX = elapsedMin + tenSecAsMin;
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
    }

    private boolean timeForUIupdate(boolean realTime) {
        return starting || realTime || elapsedSecondsTrunc % 60 == 0;
    }

    private void tryPolarConnect(String tmpDeviceID) {
        Log.d(TAG,"tryPolarConnect to "+tmpDeviceID);
        try {
            text_view.setText("Trying to connect to: " + tmpDeviceID);
            api.connectToDevice(tmpDeviceID);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            String msg = "PolarInvalidArgument: " + polarInvalidArgument;
            text_view.setText(msg);
            logException("tryPolarConnect Exception", polarInvalidArgument);
        }
    }

    private void tryPolarConnect() {
        Log.d(TAG,"tryPolarConnect to preferred device...");
        DEVICE_ID = sharedPreferences.getString(POLAR_DEVICE_ID_PREFERENCE_STRING,"");
        if (DEVICE_ID.length()>0) {
            tryPolarConnect(DEVICE_ID);
        } else {
            text_view.setText("No device ID set");
        }
    }


    /*
    private void tryPolarConnect() {
        Log.d(TAG,"tryPolarConnect");
        DEVICE_ID = sharedPreferences.getString(POLAR_DEVICE_ID_PREFERENCE_STRING,"");
        if (DEVICE_ID.length()>0) {
            try {
                text_view.setText("Trying to connect to: " + DEVICE_ID);
                api.connectToDevice(DEVICE_ID);
            } catch (PolarInvalidArgument polarInvalidArgument) {
                text_view.setText("PolarInvalidArgument: " + polarInvalidArgument);
                polarInvalidArgument.printStackTrace();
            }
        } else {
            text_view.setText("No device ID set");
        }
    }
    */

    private void writeLogFile(String msg, FileWriter logStream, String tag) {
        try {
            logStream.append(msg+"\n");
            logStream.flush();
            // avoid feedback loop through the local Log mechanism
            //android.util.Log.d(TAG,"Wrote to "+tag+" log: "+msg);
        } catch (IOException e) {
            android.util.Log.d(TAG,"IOException writing to "+tag+" log");
            text_view.setText("IOException writing to "+tag+" log");
            e.printStackTrace();
        }
    }

    private FileWriter createLogFile(String tag) {
        FileWriter logStream = null;
        try {
            String dateString = getDate(System.currentTimeMillis(), "yyyyMMdd_HHmmss");
            File file = new File(getApplicationContext().getExternalFilesDir(null), "/ftmxr."+dateString+"."+tag+".csv");
            logStream = new FileWriter(file);
            Log.d(TAG,"Logging RRs to "+file.getAbsolutePath());
        } catch (FileNotFoundException e) {
            text_view.setText("FileNotFoundException");
            Log.d(TAG,"FileNotFoundException");
            e.printStackTrace();
        } catch (IOException e) {
            text_view.setText("IOException creating log file");
            Log.d(TAG,"IOException creating log file");
            e.printStackTrace();
        }
        return logStream;
    }

    @NotNull
    private String makeLogfileName(String stem, String type) {
        String dateString = getDate(System.currentTimeMillis(), "yyyyMMdd_HHmmss");
        String extension = type.equals("debug") ? "log" : "csv";
        return "/ftmxr_"+dateString+"_"+stem+"."+type+"."+extension;
    }

    Map<String,File> currentLogFiles = new HashMap<String,File>();

    private FileWriter createLogFileNew(String tag, String extension) {
        FileWriter logStream = null;
        try {
            File logsDir = getLogsDir();
            //File file = new File(getApplicationContext().getExternalFilesDir(null), "/FatMaxOptimiser."+dateString+"."+tag+".csv");
            File file = new File(logsDir, makeLogfileName("", tag));
            // Get the files/images subdirectory;
            logStream = new FileWriter(file);
            Log.d(TAG,"Logging "+tag+" to "+file.getAbsolutePath());
            currentLogFiles.put(tag,file);
        } catch (FileNotFoundException e) {
                text_view.setText("FileNotFoundException");
                Log.d(TAG,"FileNotFoundException");
                e.printStackTrace();
        } catch (IOException e) {
            text_view.setText("IOException creating log file");
            Log.d(TAG,"IOException creating log file");
            e.printStackTrace();
        }
        return logStream;
    }

    @NotNull
    private File getLogsDir() {
        File privateRootDir = getFilesDir();
        privateRootDir.mkdir();
        File logsDir = new File(privateRootDir, "logs");
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
                    artifactsUpdate = "dropped " + artifactsPercentWindowed + " percent";
                }
                // lower end of optimal alph1 - close to overtraining - frequent updates, prioritise a1, abbreviated
                if (data.hr > upperOptimalHRthreshold || a1 < lowerOptimalAlpha1Threshold) {
                    featuresUpdate = alpha1V2RoundedWindowed + " " + data.hr;
                // higher end of optimal - prioritise a1, close to undertraining?
                } else if ((data.hr > (upperOptimalHRthreshold - 10) || alpha1V2RoundedWindowed < upperOptimalAlpha1Threshold)) {
                    featuresUpdate =  "Alpha one, " + alpha1V2RoundedWindowed + " heart rate " + data.hr;
                // lower end of optimal - prioritise a1
                } else if (artifactsPercentWindowed > artifactsRateAlarmThreshold ||
                    data.hr > upperRestingHRthreshold && timeSinceLastSpokenUpdate_s >= maxUpdateWaitSeconds) {
                    featuresUpdate = "Alpha one " + alpha1V2RoundedWindowed + " heart rate "+ data.hr;
                // warm up / cool down --- low priority, update RMSSD instead of alpha1
                } else if (artifactsPercentWindowed > artifactsRateAlarmThreshold ||
                        timeSinceLastSpokenUpdate_s >= maxUpdateWaitSeconds) {
                    featuresUpdate = "Heart rate " + data.hr + ". HRV " + rmssd;
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
                String msg = "Screenshot saved in "+imageFile.getCanonicalPath();
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                Log.i(TAG,msg);
                //openScreenshot(imageFile);
            } catch (Throwable e) {
                // Several error may come out with file handling or DOM
                e.printStackTrace();
            }
        }

        @Override
        public void onDestroy() {
            text_view.setText("Destroyed");
            Toast.makeText(this, "FatMaxxer stopped", Toast.LENGTH_SHORT).show();
            super.onDestroy();
            try {
                rrLogStreamNew.close();
            } catch (IOException e) {
                text_view.setText("IOException "+e.toString());
                e.printStackTrace();
            }
            doUnbindService();

            Intent i = new Intent(MainActivity.this, LocalService.class);
            i.setAction("STOP");
            Log.d(TAG,"intent to stop local service "+i);
            MainActivity.this.stopService(i);

            api.shutDown();
        }
}
