package polar.com.alpha1;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarExerciseEntry;
import polar.com.sdk.api.model.PolarHrData;

import static java.lang.Math.abs;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;

public class MainActivity extends AppCompatActivity {
    public static final boolean requestLegacyExternalStorage = true;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String API_LOGGER_TAG = "FatMaxxer";
    public static final String AUDIO_OUTPUT_ENABLED = "audioOutputEnabled";
    private final int NOTIFICATION_ID = 0;
    private final String NOTIFICATION_TAG = "alpha1update";

    final double alpha1HRVvt1 = 0.75;
    final double alpha1HRVvt2 = 0.5;

    public MainActivity() {
        //super(R.layout.activity_fragment_container);
        super(R.layout.activity_main);
    }

    public void deleteFile(Uri uri) {
        File fdelete = new File(uri.getPath());
        if (fdelete.exists()) {
            if (fdelete.delete()) {
                System.out.println("file Deleted :" + uri.getPath());
            } else {
                System.out.println("file not Deleted :" + uri.getPath());
            }
        }
    }

    @Override
    public void finish() {
        closeLogs();
        notificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
        try {
            api.disconnectFromDevice(DEVICE_ID);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            Log.d(TAG, "Quit: disconnectFromDevice: polarInvalidArgument "+
                    polarInvalidArgument.getStackTrace());
        }
        //System.exit(0);
        super.finish();
    }

    private long pressedTime;
    public void onBackPressed() {
        if (pressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
            finish();
        } else {
            Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
        pressedTime = System.currentTimeMillis();
    }

    private final double[] samples1 = {667.0,674.0,688.0,704.0,690.0,688.0,671.0,652.0,644.0,636.0,631.0,639.0,637.0,634.0,642.0,642.0,
            653.0,718.0,765.0,758.0,729.0,713.0,691.0,677.0,694.0,695.0,692.0,684.0,685.0,677.0,667.0,657.0,648.0,632.0,
            652.0,641.0,644.0,665.0,711.0,753.0,772.0,804.0,844.0,842.0,833.0,818.0,793.0,781.0,799.0,822.0,820.0,835.0,
            799.0,793.0,745.0,764.0,754.0,764.0,768.0,764.0,770.0,766.0,765.0,777.0,767.0,756.0,724.0,747.0,812.0,893.0,
            905.0,924.0,945.0,946.0,897.0,857.0,822.0,571.0,947.0,770.0,794.0,840.0,805.0,1593.0,763.0,1498.0,735.0,
            745.0,742.0,737.0,748.0,756.0,756.0,762.0,783.0,814.0,826.0,838.0,865.0,877.0,859.0,858.0,855.0,861.0,870.0,
            902.0,902.0,879.0,847.0,835.0,847.0,884.0,940.0,971.0,936.0,896.0,873.0,879.0,888.0,896.0,904.0,902.0,901.0,899.0,
            893.0,914.0,997.0,966.0,902.0,899.0,909.0,933.0,954.0,947.0,892.0,830.0,825.0,813.0,790.0,759.0,744.0,739.0,
            724.0,699.0,1401.0,694.0,684.0,683.0,696.0,710.0,738.0};
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
    //String DEVICE_ID = "84B38E76BC"; //TODO replace with your device id
    //String DEVICE_ID = "90E2D72B"; //replace with your device id
    String DEVICE_ID = "deviceId not set";
    PolarExerciseEntry exerciseEntry;
    SharedPreferences sharedPreferences;

    Context thisContext = this;
    private int batteryLevel = 0;
    private String exerciseMode = "Light";
    private EditText input_field;
    private final String CHANNEL_ID = "FatMaxxerChannelID1";

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static class MySettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }
    }

    /*
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    */

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
    private boolean v_contains(double[] x, Function<Double,Boolean> t) {
        for (int i=0; i<x.length; i++) {
            try {
                if (t.apply(x[i])) return true;
            } catch (Throwable throwable) {
                text_view.setText("Exception "+throwable.toString());
                throwable.printStackTrace();
            }
        }
        return false;
    }
    public String v_toString(double[] x) {
        StringBuilder result = new StringBuilder();
        result.append("{");
        for (int i = 0; i < x.length; i++) {
            if (i!=0) {
                result.append(", ");
            }
            result.append(""+i+":"+x[i]);
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
    public double[] v_subtract(double[] x, double[] y, int x_offset, int length) {
        //Log.d(TAG, "v_subtract length  "+length);
        double result[] = new double[length];
        for (int i = 0; i < length; i++) {
            result[i] = x[x_offset + i] - y[i];
        }
        //Log.d(TAG,"v_subtract returning "+v_toString(result));
        return result;
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
        double result = ((double)v_sum(x)) / x.length;
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
    public double[] v_differential(double[] x) {
        double[] result = new double[x.length - 1];
        for (int i = 0; i < (x.length - 1); i++) {
            result[i] = x[i+1] - x[i];
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
    public double[] v_log2(double[] x) {
        double result[] = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = Math.log10(x[i]) / Math.log10(2);
        }
        return result;
    }
    // num: how many samples
    public double[] arange(int min, int max, int num) {
        double result[] = new double[num];
        double acc = min;
        double delta = ((double)(max * 1.0 - min * 1.0)) / num;
        for (int i = 0; i < num; i++) {
            result[i] = acc;
            acc += delta;
        }
        return result;
    }

    private double getDetrendedMean(double[] x, int scale, double[] scale_ax, int offset) {
        double[] ycut = v_slice(x, offset, scale);
        //Log.d(TAG,"rms detrended "+ scale +" cut@"+ offset +" ycut "+v_toString(ycut));
        //     coeff = np.polyfit(scale_ax, xcut, 1)
        double[] coeff = v_reverse(polyFit(scale_ax, ycut,1));
        //Log.d(TAG,"rmsd coeff "+v_toString(coeff));
        //     xfit = np.polyval(coeff, scale_ax)
        double[] xfit = polyVal(coeff, scale_ax);
        //Log.d(TAG,"rmsd xfit "+v_toString(xfit));
        //     # detrending and computing RMS of each window
        //     rms[e] = np.sqrt(np.mean((xcut-xfit)**2))
        double mean = v_mean(v_power_s2(v_subtract(x,xfit, offset, scale),2));
        return mean;
    }

    // RMS + detrending(!)
    // - divide x into x.length/scale non-overlapping boxes of size scale
    public double[] rms_detrended(double[] x, int scale) {
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
        double rms[] = v_zero(nrboxes*2);
        // for e, xcut in enumerate(X):
        int offset = 0;
        for (int i = 0; i < nrboxes; i++) {
            double mean = getDetrendedMean(x, scale, scale_ax, offset);
            rms[i] = sqrt(mean);
            //Log.d(TAG,"rmsd box "+i+" "+" "+rms[i]);
            offset += scale;
        }
        // boxes in reverse order starting with a "last" box aligned to the very end of the data
        offset = x.length - scale;
        for (int i = nrboxes; i < nrboxes*2; i++) {
            double mean = getDetrendedMean(x, scale, scale_ax, offset);
            rms[i] = sqrt(mean);
            //Log.d(TAG,"rmsd box "+i+" "+" "+rms[i]);
            offset -= scale;
        }
        //Log.d(TAG,"rms_detrended all @scale "+scale+"("+rms.length+")"+v_toString(rms));
        //     return rms
        return rms;
    }

    // x: samples; l_lim: lower limit; u_lim: upper limit
    public double dfa_alpha1(double x[], int l_lim, int u_lim, int nrscales) {
        // vector: cumulative sum, elmtwise-subtract, elmtwise-power, mean, interpolate, RMS, Zero, Interpolate
        // sqrt
        // polynomial fit
        // # Python from https://github.com/dokato/dfa/blob/master/dfa.py
        // y = np.cumsum(x - np.mean(x))
        double mean = v_mean(x);
        Log.d(TAG, "Polar alpha1 mean "+mean);
        double[] y = v_cumsum(v_subscalar(x, mean));
        Log.d(TAG, "Polar alpha1 y "+v_toString(y));
        // scales = (2**np.arange(scale_lim[0], scale_lim[1], scale_dens)).astype(np.int)
        double[] scales = v_power_s1(2, arange(l_lim,u_lim,nrscales));
        double[] exp_scales = { 3.,  4.,  4.,  4.,  4.,  5.,  5.,  5.,  5.,  6.,  6.,  6.,  7.,  7.,  7.,  8.,  8.,  9.,
                9.,  9., 10., 10., 11., 12., 12., 13., 13., 14., 15., 15.};
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
            int sc = (int)(scales[i]);
            //Log.d(TAG, "- scale "+i+" "+sc);
            double[] sc_rms = rms_detrended(y, sc);
            fluct[i] = sqrt(v_mean(v_power_s2(sc_rms,2)));
            //Log.d(TAG, "  - rms "+v_toString(sc_rms));
            //Log.d(TAG, "  - scale "+i+" "+sc+" fluct "+fluct[i]);
        }
        //Log.d(TAG, "Polar dfa_alpha1, x "+v_toString(x));
        Log.d(TAG, "Polar dfa_alpha1, scales "+v_toString(scales));
        Log.d(TAG, "fluct: "+v_toString(fluct));
        // # fitting a line to rms data
        double[] coeff = v_reverse(polyFit(v_log2(scales), v_log2(fluct), 1));
        Log.d(TAG, "dfa_alpha1 coefficients "+v_toString(coeff));
        double alpha = coeff[0];
        Log.d(TAG, "dfa_alpha1 = "+alpha);
        return alpha;
    }

    public void testDFA_alpha1() {
        text_view.setText("Self-test DFA alpha1");
        Log.d(TAG,"testDFA_alpha1");
        double[] values = {635.0, 628.0, 627.0, 625.0, 624.0, 627.0, 624.0, 623.0, 633.0, 636.0, 633.0, 628.0, 625.0, 628.0, 622.0, 621.0, 613.0, 608.0, 604.0, 612.0, 620.0, 616.0, 611.0, 616.0, 614.0, 622.0, 627.0, 625.0, 622.0, 617.0, 620.0, 622.0, 623.0, 615.0, 614.0, 627.0, 630.0, 632.0, 632.0, 632.0, 631.0, 627.0, 629.0, 634.0, 628.0, 625.0, 629.0, 633.0, 632.0, 628.0, 631.0, 631.0, 628.0, 623.0, 619.0, 618.0, 618.0, 628.0, 634.0, 631.0, 626.0, 633.0, 637.0, 636.0, 632.0, 634.0, 625.0, 614.0, 610.0, 607.0, 613.0, 616.0, 622.0, 625.0, 620.0, 633.0, 640.0, 639.0, 631.0, 626.0, 634.0, 628.0, 615.0, 610.0, 607.0, 611.0, 613.0, 614.0, 611.0, 608.0, 627.0, 625.0, 619.0, 618.0, 622.0, 625.0, 626.0, 625.0, 626.0, 624.0, 631.0, 631.0, 619.0, 611.0, 608.0, 607.0, 602.0, 586.0, 583.0, 576.0, 580.0, 571.0, 583.0, 591.0, 598.0, 607.0, 607.0, 621.0, 619.0, 622.0, 613.0, 604.0, 607.0, 603.0, 604.0, 598.0, 595.0, 592.0, 589.0, 594.0, 594.0, 602.0, 611.0, 614.0, 634.0, 635.0, 636.0, 628.0, 627.0, 628.0, 626.0, 619.0, 616.0, 616.0, 622.0, 615.0, 607.0, 611.0, 610.0, 619.0, 624.0, 625.0, 626.0, 633.0, 643.0, 647.0, 644.0, 644.0, 642.0, 645.0, 637.0, 628.0, 632.0, 633.0, 625.0, 626.0, 623.0, 620.0, 620.0, 610.0, 612.0, 612.0, 610.0, 614.0, 611.0, 609.0, 616.0, 624.0, 623.0, 618.0, 622.0, 623.0, 625.0, 629.0, 621.0, 622.0, 617.0, 619.0, 618.0, 610.0, 607.0, 606.0, 611.0};
        // Altini Python code
        // double exp_result = 1.5503173309573208;
        // FIXME: tiny discrepancy in double precision between Python and Java impl(?!)
        // This Java impl:
        double exp_result = 1.5503173309573228;
        double act_result = dfa_alpha1(values,2,4,30);
        if (Double.compare(exp_result,act_result)!=0) {
            String msg ="expected "+exp_result+" got "+act_result;
            text_view.setText("Self-test DFA alpha1 failed: "+msg);
            Log.d(TAG,"***** testDFA_alpha1 failed "+msg+" *****");
            throw new IllegalStateException("test failed, expected "+exp_result+" got "+act_result);
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
    TextView text_hrv;
    TextView text_a1;
    TextView text_artifacts;

    // 120s ring buffer
    public final int rrWindowSize = 120;
    // time between alpha1 calculations
    public int alpha1EvalPeriod;
    // max hr 300bpm(!?) * 120s window
    public final int maxrrs = 300 * rrWindowSize;
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
    // last known alpha1 (default resting nominally 1.0)
    double alpha1Windowed = 1.0;
    double rmssdWindowed = 0;
    // rounded alpha1
    double alpha1RoundedWindowed;
    int artifactsPercentWindowed;
    double hrWindowed = 0;
    // maximum tolerable variance of adjacent RR intervals
    double artifactCorrectionThreshold = 0.05;
    // elapsed time in terms of cumulative sum of all seen RRs (as for HRVLogger)
    long logRRelapsedMS = 0;
    // the last time (since epoch) a1 was evaluated
    public long prevA1Timestamp = 0;
    public double prevrr = 0;
    public boolean starting = false;
    public long prevSpokenUpdateMS = 0;
    public long prevSpokenArtifactsUpdateMS = 0;
    public int totalRejected = 0;
    public boolean thisIsFirstSample = false;

    NotificationManagerCompat notificationManager;
    NotificationCompat.Builder notificationBuilder;

    private TextToSpeech ttobj;
    MediaPlayer mp;

    private FileWriter rrLogStreamNew;
    private FileWriter featureLogStreamNew;

    private FileWriter rrLogStreamLegacy;
    private FileWriter featureLogStreamLegacy;

    private void closeLog(FileWriter fw) {
        try {
            fw.close();
        } catch (IOException e) {
            Log.d(TAG,"IOException closing "+fw.toString()+": "+e.toString());
        }
    }

    private void closeLogs() {
        closeLog(rrLogStreamNew);
        closeLog(rrLogStreamLegacy);
        closeLog(featureLogStreamNew);
        closeLog(featureLogStreamLegacy);
    }

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    //    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
//    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");

    ScrollView scrollView;

    GraphView graphView;
    LineGraphSeries<DataPoint> hrSeries = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1HRVvt1Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1HRVvt2Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a125Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1125Series = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> a1175Series = new LineGraphSeries<DataPoint>();
    //LineGraphSeries<DataPoint> hrvSeries = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> artifactSeries = new LineGraphSeries<DataPoint>();
    final int maxDataPoints = 65535;
    final double graphViewPortWidth = 2.0;
    final int graphMaxHR = 200;
    final int graphMaxErrorRatePercent = 10;

    /**
     * Return date in specified format.
     * @param milliSeconds Date in milliseconds
     * @param dateFormat Date format
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

    final int MENU_QUIT = 0;
    final int MENU_SEARCH = 1;
    final int MENU_CONNECT_DEFAULT = 2;
    final int MENU_EXPORT = 3;
    final int MENU_EXPORT_ALL = 4;
    final int MENU_DELETE_ALL = 5;
    final int MENU_CONNECT_DISCOVERED = 100;

    // collect devices by deviceId so we don't spam the menu
    Map<String,String> discoveredDevices = new HashMap<String,String>();
    Map<Integer,String> discoveredDevicesMenu = new HashMap<Integer,String>();

    /**
     * Gets called every time the user presses the menu button.
     * Use if your menu is dynamic.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, MENU_QUIT, Menu.NONE, "Quit");
        menu.add(0, MENU_EXPORT, Menu.NONE, "Export Current Logs");
        menu.add(0, MENU_EXPORT_ALL, Menu.NONE, "Export All Logs");
        menu.add(0, MENU_DELETE_ALL, Menu.NONE, "Delete All Logs");
        String tmpDeviceId = sharedPreferences.getString("polarDeviceID","");
        if (tmpDeviceId.length()>0) {
            menu.add(0, MENU_CONNECT_DEFAULT, Menu.NONE, "Connect preferred device "+tmpDeviceId);
        }
        int i = 0;
        for (String tmpDeviceID : discoveredDevices.keySet()) {
            menu.add(0, MENU_CONNECT_DISCOVERED + i, Menu.NONE, "Connect "+discoveredDevices.get(tmpDeviceID));
            discoveredDevicesMenu.put(MENU_CONNECT_DISCOVERED + i,tmpDeviceID);
            i++;
        }
        menu.add(0, MENU_SEARCH, Menu.NONE, "Search for Polar devices");
        return super.onPrepareOptionsMenu(menu);
    }

    public Uri getUri(File f) {
        try {
            return FileProvider.getUriForFile(
                    MainActivity.this,
                    "polar.com.alpha1.fileprovider",
                    f);
        } catch (IllegalArgumentException e) {
            Log.e("File Selector",
                    "The selected file can't be shared: " + f.toString());
        }
        return null;
    }

    public void exportLogFiles() {
        ArrayList<Uri> logUris = new ArrayList<Uri>();
        logUris.add(getUri(logFiles.get("rr"))); // Add your image URIs here
        logUris.add(getUri(logFiles.get("features"))); // Add your image URIs here

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, logUris);
        shareIntent.setType("text/plain");
        startActivity(Intent.createChooser(shareIntent, "Share log files to.."));
    }

    public void exportAllLogFiles() {
        Log.d(TAG,"exportAllLogFiles...");
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        File privateRootDir = getFilesDir();
        privateRootDir.mkdir();
        File logsDir = new File(privateRootDir, "logs");
        logsDir.mkdir();
        File[] allFiles = logsDir.listFiles();
        for (File f : allFiles) {
            Log.d(TAG,"Found log file: "+getUri(f));
            allUris.add(getUri(f));
        }
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, allUris);
        shareIntent.setType("text/plain");
        startActivity(Intent.createChooser(shareIntent, "Share log files to.."));
    }

    public void deleteAllLogFiles() {
        ArrayList<Uri> allUris = new ArrayList<Uri>();
        File privateRootDir = getFilesDir();
        privateRootDir.mkdir();
        File logsDir = new File(privateRootDir, "logs");
        logsDir.mkdir();
        File[] allFiles = logsDir.listFiles();
        for (File f : allFiles) {
            if (!logFiles.containsValue(f)) {
                Log.d(TAG,"deleting log file "+f);
                f.delete();
            }
        }
    }


    //    public boolean onCreateOptionsMenu(Menu menu) {
    //        MenuInflater inflater = getMenuInflater();
    //        inflater.inflate(R.menu.options_menu, menu);
    //        return true;
    //    }
    public boolean onOptionsItemSelected(MenuItem item) {
        //respond to menu item selection
        Log.d(TAG, "onOptionsItemSelected... "+item.getItemId());
        int itemID = item.getItemId();
        if (itemID == MENU_QUIT) finish();
        if (itemID == MENU_EXPORT) exportLogFiles();
        if (itemID == MENU_EXPORT_ALL) exportAllLogFiles();
        if (itemID == MENU_DELETE_ALL) deleteAllLogFiles();
        if (itemID == MENU_CONNECT_DEFAULT) tryPolarConnect();
        if (itemID == MENU_SEARCH) searchForPolarDevices();
        if (discoveredDevicesMenu.containsKey(item.getItemId())) {
            tryPolarConnect(discoveredDevicesMenu.get(item.getItemId()));
        }
        return super.onOptionsItemSelected(item);
    }

    public void quitSearchForPolarDevices() {
        broadcastDisposable.dispose();
        broadcastDisposable = null;
    }

    public void searchForPolarDevices() {
        text_view.setText("Searching for Polar devices...");
        if (broadcastDisposable == null) {
            broadcastDisposable = api.startListenForPolarHrBroadcasts(null)
                    .subscribe(polarBroadcastData -> {
                                    if (!discoveredDevices.containsKey(polarBroadcastData.polarDeviceInfo.deviceId)) {
                                        String desc = polarBroadcastData.polarDeviceInfo.name;
                                        String msg = "Discovered " + desc + " HR " + polarBroadcastData.hr;
                                        discoveredDevices.put(polarBroadcastData.polarDeviceInfo.deviceId,desc);
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

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_fragment_container);
        setContentView(R.layout.activity_main);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        createNotificationChannel();

        /*
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.graph_container, GraphFragment.class, null)
                .commit();
        getSupportFragmentManager().executePendingTransactions();
         */


        // Notice PolarBleApi.ALL_FEATURES are enabled
        api = PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.ALL_FEATURES);
        api.setPolarFilter(false);

        final Button connect = this.findViewById(R.id.connect_button);
        connect.setVisibility(View.GONE);
        final Button speech_on = this.findViewById(R.id.speech_on_button);
        speech_on.setVisibility(View.GONE);
        final Button speech_off = this.findViewById(R.id.speech_off_button);
        speech_off.setVisibility(View.GONE);
        final Button test_feature = this.findViewById(R.id.testFeature_button);
        test_feature.setVisibility(View.GONE);
        final Button setDeviceIDButton = this.findViewById(R.id.setDeviceIdButton);
        setDeviceIDButton.setVisibility(View.GONE);
        final Button broadcast = this.findViewById(R.id.broadcast_button);
        broadcast.setVisibility(View.GONE);
        final Button disconnect = this.findViewById(R.id.disconnect_button);
        disconnect.setVisibility(View.GONE);
        final Button autoConnect = this.findViewById(R.id.auto_connect_button);
        autoConnect.setVisibility(View.GONE);
        final Button ecg = this.findViewById(R.id.ecg_button);
        ecg.setVisibility(View.GONE);
        final Button acc = this.findViewById(R.id.acc_button);
        acc.setVisibility(View.GONE);
        final Button gyr = this.findViewById(R.id.gyr_button);
        gyr.setVisibility(View.GONE);
        final Button mag = this.findViewById(R.id.mag_button);
        mag.setVisibility(View.GONE);
        final Button ppg = this.findViewById(R.id.ohr_ppg_button);
        ppg.setVisibility(View.GONE);
        final Button ppi = this.findViewById(R.id.ohr_ppi_button);
        ppi.setVisibility(View.GONE);
        final Button scan = this.findViewById(R.id.scan_button);
        scan.setVisibility(View.GONE);
        final Button list = this.findViewById(R.id.list_exercises);
        list.setVisibility(View.GONE);
        final Button read = this.findViewById(R.id.read_exercise);
        read.setVisibility(View.GONE);
        final Button remove = this.findViewById(R.id.remove_exercise);
        remove.setVisibility(View.GONE);
        final Button startH10Recording = this.findViewById(R.id.start_h10_recording);
        startH10Recording.setVisibility(View.GONE);
        final Button stopH10Recording = this.findViewById(R.id.stop_h10_recording);
        stopH10Recording.setVisibility(View.GONE);
        final Button readH10RecordingStatus = this.findViewById(R.id.h10_recording_status);
        readH10RecordingStatus.setVisibility(View.GONE);

        text_time = this.findViewById(R.id.timeView);
        text_batt = this.findViewById(R.id.battView);
        text_mode = this.findViewById(R.id.modeView);
        text_hr = this.findViewById(R.id.hrTextView);
        //text_hr.setText("\u2764"+"300");
        text_hrv = this.findViewById(R.id.hrvTextView);
        text_a1 = this.findViewById(R.id.a1TextView);
        text_artifacts = this.findViewById(R.id.artifactsView);
        text_view = this.findViewById(R.id.textView);

        //text.setTextSize(100);
        //text.setMovementMethod(new ScrollingMovementMethod());
        // text.setText(message);
        text_view.setText("Text output goes here...");

        scrollView = this.findViewById(R.id.application_container);
        // FIXME: Why does the scrollable not start with top visible?
        // scrollView.scrollTo(0,0);

        testDFA_alpha1();
        testRMSSD_1();

        api.setApiLogger(s -> Log.d(API_LOGGER_TAG, s));

        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo());

        ttobj = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttobj.setLanguage(Locale.UK);
                nonScreenUpdate("Voice output ready");
            }
        });

        rrLogStreamLegacy = createLogFile("rr");
        featureLogStreamLegacy = createLogFile("features");

        rrLogStreamNew = createLogFileNew("rr");
        writeLogFile("timestamp,rr,since_start", rrLogStreamNew, rrLogStreamLegacy, "rr");
        featureLogStreamNew = createLogFileNew("features");
        writeLogFile("timestamp,heartrate,rmssd,sdnn,alpha1,filtered,samples,droppedPercent,artifactThreshold", featureLogStreamNew, featureLogStreamLegacy, "features");

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
        graphView.addSeries(a1Series);
        graphView.addSeries(a125Series);
        graphView.addSeries(a1125Series);
        graphView.addSeries(a1175Series);
        graphView.addSeries(a1HRVvt1Series);
        graphView.addSeries(a1HRVvt2Series);
        graphView.addSeries(hrSeries);
        // REQUIRED
        graphView.getSecondScale().addSeries(artifactSeries);
        graphView.getSecondScale().setMaxY(10);
        graphView.getSecondScale().setMinY(0);
        a1Series.setColor(Color.GREEN);
        a1Series.setThickness(5);
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
        // yellow is a lot less visible that red
        a1HRVvt1Series.setThickness(6);
        // red is a lot more visible than yellow
        a1HRVvt2Series.setThickness(2);
        a1HRVvt1Series.appendData(new DataPoint(0, alpha1HRVvt1 * 100), false, maxDataPoints);
        a1HRVvt2Series.appendData(new DataPoint(0,alpha1HRVvt2 * 100), false, maxDataPoints);
        a125Series.appendData(new DataPoint(0,25), false, maxDataPoints);
        a1125Series.appendData(new DataPoint(0,125), false, maxDataPoints);
        a1175Series.appendData(new DataPoint(0,175), false, maxDataPoints);
        a1HRVvt1Series.appendData(new DataPoint(graphViewPortWidth,alpha1HRVvt1 * 100), false, maxDataPoints);
        a1HRVvt2Series.appendData(new DataPoint(graphViewPortWidth,alpha1HRVvt2 * 100), false, maxDataPoints);
        a125Series.appendData(new DataPoint(graphViewPortWidth,25), false, maxDataPoints);
        a1125Series.appendData(new DataPoint(graphViewPortWidth,125), false, maxDataPoints);
        a1175Series.appendData(new DataPoint(graphViewPortWidth,175), false, maxDataPoints);

        //hrvSeries.setColor(Color.BLUE);

        //setContentView(R.layout.activity_settings);
        Log.d(TAG, "Settings...");
        text_view.setText("Settings");
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, MySettingsFragment.class, null)
                .commit();

        notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("FatMaxxer")
                .setContentText("FatMaxxer started")
                .setSmallIcon(R.mipmap.fatmaxxer_small_icon)
                .setOngoing(true)
//                .setLargeIcon(aBitmap)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notificationBuilder.build());

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");

        api.setApiCallback(new PolarBleApiCallback() {

                @Override
                public void blePowerStateChanged(boolean powered) {
                    Log.d(TAG, "BLE power: " + powered);
                    text_view.setText("BLE power "+ powered);
                }

                @Override
                public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                    quitSearchForPolarDevices();
                    Log.d(TAG, "Polar device CONNECTED: " + polarDeviceInfo.deviceId);
                    text_view.setText("Connected to "+polarDeviceInfo.deviceId);
                    // if no default device, store this one
                    DEVICE_ID = sharedPreferences.getString("polarDeviceID","");
                    if (DEVICE_ID.length()==0) {
                        Log.d(TAG,"Setting default device "+polarDeviceInfo.deviceId);
                        text_view.setText("Setting default device "+ polarDeviceInfo.deviceId);
                        sharedPreferences.edit().putString("polarDeviceID", polarDeviceInfo.deviceId).commit();
                    }
                }

                @Override
                public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                    Log.d(TAG, "Polar device CONNECTING: " + polarDeviceInfo.deviceId);
                    text_view.setText("Connecting to "+polarDeviceInfo.deviceId);
                }

                @Override
                public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                    Log.d(TAG, "DISCONNECTED: " + polarDeviceInfo.deviceId);
                    text_view.setText("Disconnected from "+polarDeviceInfo.deviceId);
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
                    updateTrackedFeatures(data);
                }

                @Override
                    public void polarFtpFeatureReady(@NonNull String s) {
                        Log.d(TAG, "FTP ready");
                    }
                });

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && savedInstanceState == null) {
                    this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                }

                searchForPolarDevices();
                // TODO: CHECK: is this safe or do we have to wait for some other setup tasks to finish...?
                tryPolarConnect();
    }

    private int getNrSamples() {
        int nrSamples = (newestSample < oldestSample) ? (newestSample + maxrrs - oldestSample) : (newestSample - oldestSample);
        return nrSamples;
    }

    // extract current history from circular buffer (ugh)
    // FIXME: requires invariants
    public double[] copySamples() {
        double[] result = new double[getNrSamples()];
        int next = 0;
        // FIXME: unverified
        for (int i = oldestSample; i != newestSample; i = (i + 1) % rrInterval.length) {
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

    private void updateTrackedFeatures(@NotNull PolarHrData data) {
        wakeLock.acquire();
        Log.d(TAG, "hrNotificationReceived");

        if (sharedPreferences.getBoolean("keepScreenOn", false)) {
            text_view.setText("Keep screen on");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            text_view.setText("Don't keep screen on");
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        String artifactCorrectionThresholdSetting = sharedPreferences.getString("artifactThreshold", "Auto");
        if (artifactCorrectionThresholdSetting.equals("Auto")) {
            if (data.hr>95) {
                exerciseMode = "Workout";
                artifactCorrectionThreshold = 0.05;
            } else if (data.hr<80) {
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

        String alpha1EvalPeriodSetting =  sharedPreferences.getString("alpha1CalcPeriod", "20");
        try {
            alpha1EvalPeriod = Integer.parseInt(alpha1EvalPeriodSetting);
        } catch (final NumberFormatException e) {
            Log.d(TAG,"Number format exception alpha1EvalPeriod "+alpha1EvalPeriodSetting+" "+e.toString());
            alpha1EvalPeriod = 20;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("alpha1CalcPeriod", "20");
            editor.apply();
            Log.d(TAG, "alpha1CalcPeriod wrote "+sharedPreferences.getString("alpha1CalcPeriod", "??"));
        }
        if (alpha1EvalPeriod<5) {
            Log.d(TAG,"alpha1EvalPeriod<5");
            alpha1EvalPeriod = 5;
            sharedPreferences.edit().putString("alpha1CalcPeriod", "5").commit();
        }

        long currentTimeMS = System.currentTimeMillis();
        long timestamp = currentTimeMS;
        for (int rr : data.rrsMs) {
            String msg = "" + timestamp + "," + rr + "," + logRRelapsedMS;
            writeLogFile(msg, rrLogStreamNew, rrLogStreamLegacy, "rr");
            logRRelapsedMS += rr;
            timestamp += rr;
        }
        if (!started) {
            Log.d(TAG, "hrNotificationReceived: started!");
            started = true;
            starting = true;
            thisIsFirstSample = true;
            firstSampleMS = currentTimeMS;
            // FIXME: why does the scroller not start with the top visible?
            scrollView.scrollTo(0,0);
        }
        //
        // FILTERING / RECORDING RR intervals
        //
        String rejected = "";
        boolean haveArtifacts = false;
        List<Integer> rrsMs = data.rrsMs;
        for (int si = 0; si < data.rrsMs.size(); si++) {
            double newrr = data.rrsMs.get(si);
            double lowbound = prevrr * (1 - artifactCorrectionThreshold);
            double upbound = prevrr * (1 + artifactCorrectionThreshold);
            Log.d(TAG, "prevrr " + prevrr + " lowbound " + lowbound + " upbound " + upbound);
            if (thisIsFirstSample || lowbound < newrr && newrr < upbound) {
                Log.d(TAG, "accept " + newrr);
                // if in_RRs[(i-1)]*(1-artifact_correction_threshold) < in_RRs[i] < in_RRs[(i-1)]*(1+artifact_correction_threshold):
                rrInterval[newestSample] = newrr;
                rrIntervalTimestamp[newestSample] = currentTimeMS;
                newestSample = (newestSample + 1) % maxrrs;
                thisIsFirstSample = false;
            } else {
                Log.d(TAG, "drop...");
                artifactTimestamp[newestArtifactSample] = currentTimeMS;
                newestArtifactSample = (newestArtifactSample + 1) % maxrrs;
                Log.d(TAG, "reject artifact " + newrr);
                rejected += "" + newrr;
                haveArtifacts = true;
                totalRejected++;
            }
            prevrr = newrr;
        }
        String rejMsg = haveArtifacts ? (", Rejected: " + rejected) : "";
        Log.d(TAG, "Polar device HR value: " + data.hr + " rrsMs: " + data.rrsMs + " rr: " + data.rrs + " contact: " + data.contactStatus + "," + data.contactStatusSupported + " " + rejMsg);
        int expired = 0;
        // expire old samples
        Log.d(TAG, "Expire old RRs");
        while (rrIntervalTimestamp[oldestSample] < currentTimeMS - rrWindowSize * 1000) {
            oldestSample = (oldestSample + 1) % maxrrs;
            expired++;
        }
        Log.d(TAG, "Expire old artifacts");
        while (oldestArtifactSample != newestArtifactSample && artifactTimestamp[oldestArtifactSample] < currentTimeMS - rrWindowSize * 1000) {
            Log.d(TAG, "Expire at " + oldestArtifactSample);
            oldestArtifactSample = (oldestArtifactSample + 1) % maxrrs;
        }
        long elapsedMS = (currentTimeMS - firstSampleMS);
        Log.d(TAG, "elapsedMS " + elapsedMS);
        //
        long elapsedSeconds = elapsedMS / 1000;
        long absSeconds = Math.abs(elapsedSeconds);
        String positive = String.format(
                "%02d:%02d:%02d",
                absSeconds / 3600,
                (absSeconds % 3600) / 60,
                absSeconds % 60);
        //text_time.setText(mode + "    " +positive + "    \uD83D\uDD0B"+batteryLevel);
        text_mode.setText(exerciseMode);
        text_time.setText(positive);
        text_batt.setText("\uD83D\uDD0B"+batteryLevel);

        //
        // FEATURES
        //
        long elapsed = elapsedMS / 1000;
        int nrSamples = getNrSamples();
        int nrArtifacts = getNrArtifacts();
        double[] samples = copySamples();
        Log.d(TAG, "Samples: " + v_toString(samples));
                /*
                int firstArtifactIndex = v_containsArtifacts(samples);
                if (firstArtifactIndex >= 0) {
                    String msg = "Contains artifact at "+firstArtifactIndex+": "+v_toString(samples);
                    text.setText("IllegalStateException: "+msg);
                    throw new IllegalStateException(msg);
                }
                */
        rmssdWindowed = getRMSSD(samples);
        // TODO: CHECK: avg HR == 60 * 1000 / (mean of observed filtered(?!) RRs)
        hrWindowed = round(60 * 1000 * 100 / v_mean(samples)) / 100.0;
        // Periodic actions: check alpha1 and issue voice update
        // - skip one period's worth after first HR update
        // - only within the first two seconds of this period window
        // - only when at least three seconds have elapsed since last invocation
        // FIXME: what precisely is required for alpha1 to be well-defined?
        // FIXME: The prev_a1_check now seems redundant
        if ((elapsed > alpha1EvalPeriod) && (elapsed % alpha1EvalPeriod <= 2) && (currentTimeMS > prevA1Timestamp + 3000)) {
            alpha1Windowed = dfa_alpha1(samples, 2, 4, 30);
            prevA1Timestamp = currentTimeMS;
            //writeLogFile("timestamp,heartrate,rmssd,sdnn,alpha1,filtered,samples,droppedPercent,,artifactThreshold",featureLogStream,"features");
            writeLogFile("" + timestamp
                    + "," + hrWindowed
                    + "," + rmssdWindowed
                    + ","
                    + "," + alpha1RoundedWindowed
                    + "," + nrArtifacts
                    + "," + nrSamples
                    + "," + artifactsPercentWindowed
                    + "," + artifactCorrectionThreshold
                    ,
                    featureLogStreamNew,
                    featureLogStreamLegacy,
                    "features");
            alpha1RoundedWindowed = round(alpha1Windowed * 100) / 100.0;
            if (sharedPreferences.getBoolean("notificationsEnabled", true)) {
                notificationBuilder.setContentTitle("a1 " + alpha1RoundedWindowed +" drop "+artifactsPercentWindowed+"%");
                notificationBuilder.setContentText("a1 " + alpha1RoundedWindowed +" drop "+artifactsPercentWindowed+"% batt "+batteryLevel+"% rmssd "+rmssdWindowed);
                notificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notificationBuilder.build());
            }
        }

        //
        // DISPLAY // AUDIO // LOGGING
        //
        if (haveArtifacts && sharedPreferences.getBoolean(AUDIO_OUTPUT_ENABLED, false)) {
            //spokenOutput("drop");
            mp.start();
        }
        StringBuilder logmsg = new StringBuilder();
        logmsg.append(elapsed + "s");
        logmsg.append(", rrsMs: " + data.rrsMs);
        logmsg.append(rejMsg);
        logmsg.append(", total rejected: " + totalRejected);
        logmsg.append(" battery " + batteryLevel);
        String logstring = logmsg.toString();

        artifactsPercentWindowed = (int) round(nrArtifacts * 100 / (double) nrSamples);
        text_artifacts.setText("" + nrArtifacts + "/" + nrSamples + " (" + artifactsPercentWindowed + "%) ["+artifactCorrectionThreshold+"]");
        if (haveArtifacts) {
            text_artifacts.setBackgroundResource(R.color.colorHighlight);
        } else {
            text_artifacts.setBackgroundResource(R.color.colorBackground);
        }
        text_view.setText(logstring);
        text_hr.setText("" + data.hr);
        text_hrv.setText("" + round(rmssdWindowed));
        text_a1.setText("" + alpha1RoundedWindowed);
        // configurable top-of-optimal threshold for alpha1
        double alpha1MaxOptimal = Double.parseDouble(sharedPreferences.getString("alpha1MaxOptimal", "1.0"));
        // wait for run-in period
        if (elapsed > 30) {
            if (alpha1RoundedWindowed < alpha1HRVvt2) {
                text_a1.setBackgroundResource(R.color.colorMaxIntensity);
            } else if (alpha1RoundedWindowed < alpha1HRVvt1) {
                text_a1.setBackgroundResource(R.color.colorMedIntensity);
            } else if (alpha1RoundedWindowed < alpha1MaxOptimal) {
                text_a1.setBackgroundResource(R.color.colorFatMaxIntensity);
            } else {
                text_a1.setBackgroundResource(R.color.colorEasyIntensity);
            }
        }
        double elapsedMin = elapsed / 60.0;
        double tenSecAsMin = 1.0 / 6.0;
        boolean scrollToEnd = (elapsedMin > (graphViewPortWidth - tenSecAsMin)) && (elapsed % 10 == 0);
        hrSeries.appendData(new DataPoint(elapsedMin, data.hr), scrollToEnd, maxDataPoints);
        a1Series.appendData(new DataPoint(elapsedMin, alpha1Windowed * 100.0), scrollToEnd, maxDataPoints);
        if (scrollToEnd) {
            double nextX = elapsedMin + tenSecAsMin;
            a1HRVvt1Series.appendData(new DataPoint(nextX, 75), scrollToEnd, maxDataPoints);
            a1HRVvt2Series.appendData(new DataPoint(nextX, 50), scrollToEnd, maxDataPoints);
            a125Series.appendData(new DataPoint(nextX, 25), scrollToEnd, maxDataPoints);
            a1125Series.appendData(new DataPoint(nextX, 125), scrollToEnd, maxDataPoints);
            a1175Series.appendData(new DataPoint(nextX, 175), scrollToEnd, maxDataPoints);
        }
        artifactSeries.appendData(new DataPoint(elapsedMin, artifactsPercentWindowed), scrollToEnd, maxDataPoints);

        Log.d(TAG, data.hr + " " + alpha1RoundedWindowed + " " + rmssdWindowed);
        Log.d(TAG, logstring);
        Log.d(TAG, "" + (elapsed % alpha1EvalPeriod));
        audioUpdate(data, currentTimeMS);
        starting = false;
        wakeLock.release();
    }

    private void tryPolarConnect(String tmpDeviceID) {
//        quitSearchForPolarDevices();
        Log.d(TAG,"tryPolarConnect to "+tmpDeviceID);
        try {
            text_view.setText("Trying to connect to: " + tmpDeviceID);
            api.connectToDevice(tmpDeviceID);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            text_view.setText("PolarInvalidArgument: " + polarInvalidArgument);
            polarInvalidArgument.printStackTrace();
        }
    }

    private void tryPolarConnect() {
        //quitSearchForPolarDevices();
        Log.d(TAG,"tryPolarConnect");
        DEVICE_ID = sharedPreferences.getString("polarDeviceID","");
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

    private void writeLogFile(String msg, FileWriter logStream, FileWriter logStreamLegacy, String tag) {
        writeLogFileBasic(msg,logStream,tag);
        writeLogFileBasic(msg,logStreamLegacy,tag);
    }

    private void writeLogFileBasic(String msg, FileWriter logStream, String tag) {
        try {
            logStream.append(msg+"\n");
            logStream.flush();
            Log.d(TAG,"Wrote to "+tag+" log: "+msg);
        } catch (IOException e) {
            Log.d(TAG,"IOException writing to "+tag+" log");
            text_view.setText("IOException writing to "+tag+" log");
            e.printStackTrace();
        }
    }

    private FileWriter createLogFile(String tag) {
        FileWriter logStream = null;
        try {
//            File dir = new File(getApplicationContext().getFilesDir(), "FatMaxOptimizer");
//            File dir = new File(getApplicationContext().getExternalFilesDir(null), "FatMaxOptimizer");
            String dateString = getDate(System.currentTimeMillis(), "yyyyMMdd_HHmmss");
            File file = new File(getApplicationContext().getExternalFilesDir(null), "/FatMaxOptimiser."+dateString+"."+tag+".csv");
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

    Map<String,File> logFiles = new HashMap<String,File>();

    private FileWriter createLogFileNew(String tag) {
        FileWriter logStream = null;
        try {
//            File dir = new File(getApplicationContext().getFilesDir(), "FatMaxOptimizer");
//            File dir = new File(getApplicationContext().getExternalFilesDir(null), "FatMaxOptimizer");
            String dateString = getDate(System.currentTimeMillis(), "yyyyMMdd_HHmmss");
            File privateRootDir = getFilesDir();
            privateRootDir.mkdir();
            File logsDir = new File(privateRootDir, "logs");
            logsDir.mkdir();
            //File file = new File(getApplicationContext().getExternalFilesDir(null), "/FatMaxOptimiser."+dateString+"."+tag+".csv");
            File file = new File(logsDir, "/FatMaxOptimiser."+dateString+"."+tag+".csv");
            // Get the files/images subdirectory;
            logStream = new FileWriter(file);
            Log.d(TAG,"Logging "+tag+" to "+file.getAbsolutePath());
            logFiles.put(tag,file);
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

    // pre: samples.length > 1
    private int v_containsArtifacts(double[] samples) {
        for (int i = 0; i<samples.length - 1; i++) {
            if (i>=1) {
                double prev = samples[i - 1];
                double next = samples[i];
                if (next <= prev * (1 - artifactCorrectionThreshold)) {
                    Log.d(TAG,"Artifact at "+(i)+": ("+prev+"), "+next);
                    return i;
                }
                if (next >= prev * (1 + artifactCorrectionThreshold)) {
                    Log.d(TAG,"Artifact at "+(i)+": ("+prev+"), "+next);
                    return i;
                }
            }
        }
        return -1;
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
            long timeSinceLastSpokenArtifactsUpdate_s = (long) (currentTime_ms - prevSpokenArtifactsUpdateMS) / 1000;

            double a1 = alpha1RoundedWindowed;
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
            if (timeSinceLastSpokenArtifactsUpdate_s > minUpdateWaitSeconds) {
                if (artifactsPercentWindowed > artifactsRateAlarmThreshold && data.hr > (upperOptimalHRthreshold - 10)
                        || timeSinceLastSpokenArtifactsUpdate_s >= maxUpdateWaitSeconds
                ) {
                    artifactsUpdate = "dropped " + artifactsPercentWindowed + " percent";
                }
            }
            if (timeSinceLastSpokenUpdate_s > minUpdateWaitSeconds) {
                // lower end of optimal alph1 - close to overtraining - frequent updates, prioritise a1, abbreviated
                if (data.hr > upperOptimalHRthreshold || a1 < lowerOptimalAlpha1Threshold) {
                    artifactsUpdate = "dropped " + artifactsPercentWindowed + " percent";
                    featuresUpdate = alpha1RoundedWindowed + " " + data.hr;
                // higher end of optimal - prioritise a1, close to undertraining?
                } else if ((data.hr > (upperOptimalHRthreshold - 10) || alpha1RoundedWindowed < upperOptimalAlpha1Threshold)) {
                    featuresUpdate =  "Alpha one, " + alpha1RoundedWindowed + ". Heart rate " + data.hr;
                // lower end of optimal - prioritise a1
                } else if (data.hr > upperRestingHRthreshold && timeSinceLastSpokenUpdate_s >= maxUpdateWaitSeconds) {
                    featuresUpdate = "Heart rate " + data.hr + ". Alpha one, " + alpha1RoundedWindowed;
                // warm up / cool down --- low priority, update RMSSD instead of alpha1
                } else if (timeSinceLastSpokenUpdate_s >= maxUpdateWaitSeconds) {
                    featuresUpdate = "Heart rate " + data.hr + ". HRV " + rmssd;
                }
            }
            if (featuresUpdate.length() > 0 || artifactsUpdate.length() > 0) {
                if (artifactsPercentWindowed > artifactsRateAlarmThreshold) {
                    nonScreenUpdate(artifactsUpdate + " " + featuresUpdate);
                } else {
                    nonScreenUpdate(featuresUpdate + ", " + artifactsUpdate);
                }
                if (featuresUpdate.length() > 0) prevSpokenUpdateMS = currentTime_ms;
                if (artifactsUpdate.length() > 0) prevSpokenArtifactsUpdateMS = currentTime_ms;
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

        @Override
        public void onDestroy() {
            text_view.setText("Destroyed");
            super.onDestroy();
            try {
                rrLogStreamNew.close();
            } catch (IOException e) {
                text_view.setText("IOException "+e.toString());
                e.printStackTrace();
            }
            api.shutDown();
        }
}
