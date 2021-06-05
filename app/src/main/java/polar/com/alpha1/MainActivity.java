package polar.com.alpha1;

import android.Manifest;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

//import com.jjoe64.graphview.GraphView;
//import com.jjoe64.graphview.series.DataPoint;
//import com.jjoe64.graphview.series.LineGraphSeries;

import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarAccelerometerData;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarExerciseEntry;
import polar.com.sdk.api.model.PolarGyroData;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarMagnetometerData;
import polar.com.sdk.api.model.PolarOhrData;
import polar.com.sdk.api.model.PolarOhrPPIData;
import polar.com.sdk.api.model.PolarSensorSetting;

import static java.lang.Math.abs;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;
import static polar.com.sdk.api.model.PolarOhrData.OHR_DATA_TYPE.PPG3_AMBIENT1;

public class MainActivity extends AppCompatActivity {
    public static final boolean requestLegacyExternalStorage = true;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String API_LOGGER_TAG = "API LOGGER";

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
    String DEVICE_ID = "90E2D72B"; //TODO replace with your device id
    PolarExerciseEntry exerciseEntry;

    Context thisContext = this;
    private int batteryLevel = 100;

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
    TextView text_hr;
    TextView text_hrv;
    TextView text_a1;
    TextView text_artifacts;

    // 120s ring buffer
    public final int rrWindowSize = 120;
    // time between alpha1 calculations
    public final int alpha1EvalPeriod = 20;
    // max hr 300bpm(!?) * 120s window
    public final int maxrrs = 300 * rrWindowSize;
    // circular buffer of recently recorded RRs
    public double[] rr = new double[maxrrs];
    // timestamp of recently recorded RR (in ms since epoch)
    public long[] rr_timestamp = new long[maxrrs];
    // timestamps of artifact
    private long[] dropped_ts = new long[maxrrs];
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
    double alpha1 = 1.0;
    // rounded alpha1
    double a1_r;
    int artifactsPercent;
    // maximum tolerable variance of adjacent RR intervals
    final double artifactCorrectionThreshold = 0.05;
    // elapsed time in terms of cumulative sum of all seen RRs (redundant?)
    long logRRelapsedMS = 0;
    private TextToSpeech ttobj;
    private boolean soundEnabled = false;
    // the last time (since epoch) a1 was evaluated
    private long prev_a1_check = 0;
    private FileWriter rrLogStream;
    private FileWriter featureLogStream;
    private double prevrr = 0;
    private boolean starting = false;
    private long prevSpokenUpdate_ms = 0;
    private long prevSpokenArtifactsUpdate_ms = 0;
    private int totalRejected = 0;
    private boolean firstSample = false;

    //GraphView graph;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Notice PolarBleApi.ALL_FEATURES are enabled
        api = PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.ALL_FEATURES);
        api.setPolarFilter(false);

        final Button connect = this.findViewById(R.id.connect_button);
        final Button speech_on = this.findViewById(R.id.speech_on_button);
        final Button speech_off = this.findViewById(R.id.speech_off_button);
        final Button test_feature = this.findViewById(R.id.testFeature_button);
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
        final Button setTime = this.findViewById(R.id.set_time);
        setTime.setVisibility(View.GONE);

        text_time = this.findViewById(R.id.timeView);
        text_hr = this.findViewById(R.id.hrTextView);
        text_hrv = this.findViewById(R.id.hrvTextView);
        text_a1 = this.findViewById(R.id.a1TextView);
        text_artifacts = this.findViewById(R.id.artifactsView);
        text_view = this.findViewById(R.id.textView);

        //text.setTextSize(100);
        //text.setMovementMethod(new ScrollingMovementMethod());
        // text.setText(message);
        text_view.setText("Text output goes here...");
        //setContentView(text);

        testDFA_alpha1();
        testRMSSD_1();

        api.setApiLogger(s -> Log.d(API_LOGGER_TAG, s));

        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo());

        ttobj = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttobj.setLanguage(Locale.UK);
                spokenOutput("Voice output ready");
            }
        });

        rrLogStream = createLogFile("rr");
        writeLogFile("timestamp,rr",rrLogStream,"rr");
        featureLogStream = createLogFile("features");
        writeLogFile("timestamp,heartrate,rmssd,sdnn,alpha1,filtered,samples,droppedPercent,",featureLogStream,"features");

        final MediaPlayer mp = MediaPlayer.create(this, R.raw.artifact);
        mp.setVolume(100,100);

        /*
        graph = (GraphView) findViewById(R.id.graph);
        LineGraphSeries<DataPoint> hrSeries = new LineGraphSeries<DataPoint>();
        LineGraphSeries<DataPoint> a1Series = new LineGraphSeries<DataPoint>();
        LineGraphSeries<DataPoint> hrvSeries = new LineGraphSeries<DataPoint>();
        graph.getViewport().setScrollable(true);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(25);
        graph.getViewport().setMaxY(175);
        graph.getGridLabelRenderer().setNumVerticalLabels(7);
        graph.addSeries(a1Series);
        graph.addSeries(hrSeries);
        // REQUIRED
        graph.getSecondScale().addSeries(hrvSeries);
        graph.getSecondScale().setMaxY(100);
        graph.getSecondScale().setMinY(0);
        a1Series.setColor(Color.GREEN);
        hrSeries.setColor(Color.RED);
        hrvSeries.setColor(Color.BLUE);
         */

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");

        api.setApiCallback(new PolarBleApiCallback() {

            @Override
            public void blePowerStateChanged(boolean powered) {
                Log.d(TAG, "BLE power: " + powered);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                text_view.setText("CONNECTED");
                Log.d(TAG, "Polar device CONNECTED: " + polarDeviceInfo.deviceId);
                DEVICE_ID = polarDeviceInfo.deviceId;
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "Polar device CONNECTING: " + polarDeviceInfo.deviceId);
                DEVICE_ID = polarDeviceInfo.deviceId;
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: " + polarDeviceInfo.deviceId);
                ecgDisposable = null;
                accDisposable = null;
                gyrDisposable = null;
                magDisposable = null;
                ppgDisposable = null;
                ppiDisposable = null;
            }

            @Override
            public void streamingFeaturesReady(@NonNull final String identifier,
                                               @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
                for(PolarBleApi.DeviceStreamingFeature feature : features) {
                    Log.d(TAG, "Streaming feature " + feature.toString() + " is ready");
                    text_view.setText("Streaming feature " + feature.toString() + " is ready");
                }
            }

            @Override
            public void hrFeatureReady(@NonNull String identifier) {
                Log.d(TAG, "HR READY: " + identifier);
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

            private int getNrSamples() {
                int nrSamples = (newestSample<oldestSample) ? (newestSample + maxrrs - oldestSample) : (newestSample - oldestSample);
                return nrSamples;
            }

            // extract current history from circular buffer (ugh)
            // FIXME: requires invariants
            public double[] copySamples() {
                double[] result = new double[getNrSamples()];
                int next = 0;
                // FIXME: unverified
                for (int i = oldestSample; i != newestSample; i = (i+1) % rr.length) {
                    result[next] = rr[i];
                    next++;
                }
                return result;
            }

            private int getNrArtifacts() {
                int result = 0;
                for (int i = oldestArtifactSample; i != newestArtifactSample; i = (i+1) % dropped_ts.length) {
                    result++;
                }
                return result;
            }

            @Override
            public void hrNotificationReceived(@NonNull String identifier, @NonNull PolarHrData data) {
                wakeLock.acquire();
                Log.d(TAG,"hrNotificationReceived");
                long currentTimeMS = System.currentTimeMillis();
                long timestamp = currentTimeMS;
                for (int rr : data.rrsMs) {
                    String msg = "" + timestamp + "," + rr + "," + logRRelapsedMS;
                    writeLogFile(msg, rrLogStream, "rr");
                    logRRelapsedMS += rr;
                    timestamp += rr;
                }
                if (!started) {
                    Log.d(TAG,"hrNotificationReceived: started!");
                    started = true;
                    starting = true;
                    firstSample = true;
                    firstSampleMS = currentTimeMS;
                }
                //
                // FILTERING / RECORDING RR intervals
                //
                String rejected = "";
                boolean haveRejections = false;
                List<Integer> rrsMs = data.rrsMs;
                for (int si = 0; si<data.rrsMs.size(); si++) {
                    double newrr = data.rrsMs.get(si);
                    double lowbound = prevrr * (1- artifactCorrectionThreshold);
                    double upbound = prevrr * (1+ artifactCorrectionThreshold);
                    Log.d(TAG, "prevrr "+prevrr+" lowbound "+lowbound+" upbound "+upbound);
                    if (firstSample || lowbound < newrr && newrr < upbound) {
                        Log.d(TAG, "accept "+newrr);
                        // if in_RRs[(i-1)]*(1-artifact_correction_threshold) < in_RRs[i] < in_RRs[(i-1)]*(1+artifact_correction_threshold):
                        rr[newestSample] = newrr;
                        rr_timestamp[newestSample] = currentTimeMS;
                        newestSample = (newestSample + 1) % maxrrs;
                        firstSample = false;
                    } else {
                        Log.d(TAG,"drop...");
                        dropped_ts[newestArtifactSample] = currentTimeMS;
                        newestArtifactSample = (newestArtifactSample + 1) % maxrrs;
                        Log.d(TAG, "reject artifact "+newrr);
                        rejected += "" + newrr;
                        haveRejections = true;
                        totalRejected++;
                    }
                    prevrr = newrr;
                }
                String rejMsg = haveRejections ? (", Rejected: " + rejected) : "";
                Log.d(TAG, "Polar device HR value: " + data.hr + " rrsMs: " + data.rrsMs + " rr: " + data.rrs + " contact: " + data.contactStatus + "," + data.contactStatusSupported+" "+rejMsg);
                int expired = 0;
                // expire old samples
                Log.d(TAG,"Expire old RRs");
                while (rr_timestamp[oldestSample] < currentTimeMS - rrWindowSize * 1000) {
                    oldestSample = (oldestSample + 1) % maxrrs;
                    expired++;
                }
                Log.d(TAG,"Expire old artifacts");
                while (oldestArtifactSample != newestArtifactSample && dropped_ts[oldestArtifactSample] < currentTimeMS - rrWindowSize * 1000) {
                    Log.d(TAG,"Expire at "+ oldestArtifactSample);
                    oldestArtifactSample = (oldestArtifactSample + 1) % maxrrs;
                }
                long elapsedMS = (currentTimeMS - firstSampleMS);
                Log.d(TAG,"elapsedMS "+elapsedMS);
                //
                long elapsedSeconds = elapsedMS / 1000;
                long absSeconds = Math.abs(elapsedSeconds);
                String positive = String.format(
                        "%02d:%02d:%02d",
                        absSeconds / 3600,
                        (absSeconds % 3600) / 60,
                        absSeconds % 60);
                text_time.setText(positive);

                //
                // FEATURES
                //
                long elapsed = elapsedMS / 1000;
                int nrSamples = getNrSamples();
                int nrArtifacts = getNrArtifacts();
                double[] samples = copySamples();
                Log.d(TAG, "Samples: "+v_toString(samples));
                /*
                int firstArtifactIndex = v_containsArtifacts(samples);
                if (firstArtifactIndex >= 0) {
                    String msg = "Contains artifact at "+firstArtifactIndex+": "+v_toString(samples);
                    text.setText("IllegalStateException: "+msg);
                    throw new IllegalStateException(msg);
                }
                */
                int rmssd = round(getRMSSD(samples));
                // Periodic actions: check alpha1 and issue voice update
                // - skip one period's worth after first HR update
                // - only within the first two seconds of this period window
                // - only when at least three seconds have elapsed since last invocation
                // FIXME: what precisely is required for alpha1 to be well-defined?
                // FIXME: The prev_a1_check now seems redundant
                if ((elapsed > alpha1EvalPeriod) && (elapsed % alpha1EvalPeriod <= 1) && (currentTimeMS > prev_a1_check + 3000)) {
                    alpha1 = dfa_alpha1(samples,2,4,30);
                    prev_a1_check = currentTimeMS;
                }
                a1_r = round(alpha1*100) / 100.0;
                //writeLogFile("timestamp,heartrate,rmssd,sdnn,alpha1,filtered,samples,droppedPercent,",featureLogStream,"features");
                writeLogFile(""+timestamp+","+","+rmssd+","+","+a1_r+","+nrArtifacts+","+nrSamples+","+ artifactsPercent,featureLogStream,"features");

                //
                // DISPLAY // AUDIO // LOGGING
                //
                if (haveRejections && soundEnabled) {
                    //spokenOutput("drop");
                    mp.start();
                }
                StringBuilder logmsg = new StringBuilder();
                logmsg.append(elapsed+"s");
                logmsg.append(", rrsMs: " + data.rrsMs);
                logmsg.append(rejMsg);
                logmsg.append(", total rejected: " + totalRejected);
                logmsg.append(" battery "+batteryLevel);
                String logstring = logmsg.toString();

                artifactsPercent = (int)round(nrArtifacts * 100 / (double)nrSamples);
                text_artifacts.setText(""+nrArtifacts+"/"+nrSamples+" ("+ artifactsPercent +"%)");
                if (haveRejections) {
                    text_artifacts.setBackgroundResource(R.color.colorHighlight);
                } else {
                    text_artifacts.setBackgroundResource(R.color.colorBackground);
                }
                text_view.setText(logstring);
                text_hr.setText(""+data.hr);
                text_hrv.setText(""+rmssd);
                text_a1.setText(""+a1_r);
                if (a1_r < 0.5) {
                    text_a1.setBackgroundResource(R.color.colorMaxIntensity);
                } else if (a1_r < 0.75) {
                    text_a1.setBackgroundResource(R.color.colorMedIntensity);
                } else if (a1_r < 1.0) {
                    text_a1.setBackgroundResource(R.color.colorFatMaxIntensity);
                } else {
                    text_a1.setBackgroundResource(R.color.colorEasyIntensity);
                }
                //hrSeries.appendData(new DataPoint(elapsedSeconds, data.hr), false, 65535);
                //a1Series.appendData(new DataPoint(elapsedSeconds, alpha1 * 100.0), false, 65535);
                //hrvSeries.appendData(new DataPoint(elapsedSeconds, rmssd), false, 65535);

                Log.d(TAG,data.hr+" "+a1_r+" "+rmssd);
                Log.d(TAG,logstring);
                Log.d(TAG,""+(elapsed % alpha1EvalPeriod));
                spokenUpdate(data, a1_r, rmssd, currentTimeMS);
                starting = false;
                wakeLock.release();
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
                Log.d(TAG, "FTP ready");
            }
        });

        speech_on.setOnClickListener(v -> {
            soundEnabled = true;
            spokenOutput("Speech enabled");
            text_view.setText("Speech enabled");
        });

        speech_off.setOnClickListener(v -> {
            soundEnabled = false;
            text_view.setText("Speech disabled");
        });

        broadcast.setOnClickListener(v -> {
            text_view.setText("Listening for broadcast...");
            if (broadcastDisposable == null) {
                broadcastDisposable = api.startListenForPolarHrBroadcasts(null)
                        .subscribe(polarBroadcastData -> Log.d(TAG, "Polar BROADCAST " +
                                        polarBroadcastData.polarDeviceInfo.deviceId + " HR: " +
                                        polarBroadcastData.hr + " batt: " +
                                        polarBroadcastData.batteryStatus),
                                error -> Log.e(TAG, "Broadcast listener failed. Reason " + error),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                broadcastDisposable.dispose();
                broadcastDisposable = null;
            }
        });

        connect.setOnClickListener(v -> {
            text_view.setText("Polar device: attempting CONNECT");
            tryPolarConnect();
        });

        disconnect.setOnClickListener(view -> {
            try {
                api.disconnectFromDevice(DEVICE_ID);
            } catch (PolarInvalidArgument polarInvalidArgument) {
                text_view.setText("PolarInvalidArgument: "+polarInvalidArgument);
                polarInvalidArgument.printStackTrace();
            }
        });

        test_feature.setOnClickListener(view -> {
        });

        autoConnect.setOnClickListener(view -> {
            if (autoConnectDisposable != null) {
                autoConnectDisposable.dispose();
                autoConnectDisposable = null;
            }
            autoConnectDisposable = api.autoConnectToDevice(-50, "H10", null)
                    .subscribe(
                            () -> Log.d(TAG, "auto connect search complete"),
                            throwable -> Log.e(TAG, "" + throwable.toString())
                    );
        });

        ecg.setOnClickListener(v -> {
            if (ecgDisposable == null) {
                ecgDisposable = api.requestStreamSettings(DEVICE_ID, PolarBleApi.DeviceStreamingFeature.ECG)
                        .toFlowable()
                        .flatMap((Function<PolarSensorSetting, Publisher<PolarEcgData>>) polarEcgSettings -> {
                            PolarSensorSetting sensorSetting = polarEcgSettings.maxSettings();
                            return api.startEcgStreaming(DEVICE_ID, sensorSetting);
                        }).subscribe(
                                polarEcgData -> {
                                    for (Integer microVolts : polarEcgData.samples) {
                                        Log.d(TAG, "    yV: " + microVolts);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                // NOTE stops streaming if it is "running"
                ecgDisposable.dispose();
                ecgDisposable = null;
            }
        });

        acc.setOnClickListener(v -> {
            if (accDisposable == null) {
                accDisposable = api.requestStreamSettings(DEVICE_ID, PolarBleApi.DeviceStreamingFeature.ACC)
                        .toFlowable()
                        .flatMap((Function<PolarSensorSetting, Publisher<PolarAccelerometerData>>) settings -> {
                            PolarSensorSetting sensorSetting = settings.maxSettings();
                            return api.startAccStreaming(DEVICE_ID, sensorSetting);
                        }).observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                polarAccelerometerData -> {
                                    for (PolarAccelerometerData.PolarAccelerometerDataSample data : polarAccelerometerData.samples) {
                                        Log.d(TAG, "    x: " + data.x + " y: " + data.y + " z: " + data.z);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                // NOTE dispose will stop streaming if it is "running"
                accDisposable.dispose();
                accDisposable = null;
            }
        });

        gyr.setOnClickListener(v -> {
            if (gyrDisposable == null) {
                gyrDisposable = api.requestStreamSettings(DEVICE_ID, PolarBleApi.DeviceStreamingFeature.GYRO)
                        .toFlowable()
                        .flatMap((Function<PolarSensorSetting, Publisher<PolarGyroData>>) settings -> {
                            PolarSensorSetting sensorSetting = settings.maxSettings();
                            return api.startGyroStreaming(DEVICE_ID, sensorSetting);
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                polarGyroData -> {
                                    for (PolarGyroData.PolarGyroDataSample data : polarGyroData.samples) {
                                        Log.d(TAG, "    x: " + data.x + " y: " + data.y + " z: " + data.z);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                // NOTE dispose will stop streaming if it is "running"
                gyrDisposable.dispose();
                gyrDisposable = null;
            }
        });

        mag.setOnClickListener(v -> {
            if (magDisposable == null) {
                magDisposable = api.requestStreamSettings(DEVICE_ID, PolarBleApi.DeviceStreamingFeature.MAGNETOMETER)
                        .toFlowable()
                        .flatMap((Function<PolarSensorSetting, Publisher<PolarMagnetometerData>>) settings -> {
                            PolarSensorSetting sensorSetting = settings.maxSettings();
                            return api.startMagnetometerStreaming(DEVICE_ID, sensorSetting);
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                polarMagData -> {
                                    for (PolarMagnetometerData.PolarMagnetometerDataSample data : polarMagData.samples) {
                                        Log.d(TAG, "    x: " + data.x + " y: " + data.y + " z: " + data.z);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                // NOTE dispose will stop streaming if it is "running"
                magDisposable.dispose();
                magDisposable = null;
            }
        });

        ppg.setOnClickListener(v -> {
            if (ppgDisposable == null) {
                ppgDisposable = api.requestStreamSettings(DEVICE_ID, PolarBleApi.DeviceStreamingFeature.PPG)
                        .toFlowable()
                        .flatMap((Function<PolarSensorSetting, Publisher<PolarOhrData>>) polarPPGSettings -> api.startOhrStreaming(DEVICE_ID, polarPPGSettings.maxSettings()))
                        .subscribe(
                                polarOhrPPGData -> {
                                    if (polarOhrPPGData.type == PPG3_AMBIENT1) {
                                        for (PolarOhrData.PolarOhrSample data : polarOhrPPGData.samples) {
                                            Log.d(TAG, "    ppg0: " + data.channelSamples.get(0)
                                                    + " ppg1: " + data.channelSamples.get(0)
                                                    + " ppg2: " + data.channelSamples.get(0)
                                                    + " ambient: " + data.channelSamples.get(0));
                                        }
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable.getLocalizedMessage()),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                ppgDisposable.dispose();
                ppgDisposable = null;
            }
        });

        ppi.setOnClickListener(v -> {
            if (ppiDisposable == null) {
                ppiDisposable = api.startOhrPPIStreaming(DEVICE_ID)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                ppiData -> {
                                    for (PolarOhrPPIData.PolarOhrPPISample sample : ppiData.samples) {
                                        Log.d(TAG, "    ppi: " + sample.ppi
                                                + " blocker: " + sample.blockerBit + " errorEstimate: " + sample.errorEstimate);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable.getLocalizedMessage()),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                ppiDisposable.dispose();
                ppiDisposable = null;
            }
        });

        scan.setOnClickListener(view -> {
            text_view.setText("SCAN");
            Log.d(TAG, "Polar SCAN");
            if (scanDisposable == null) {
                scanDisposable = api.searchForDevice()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                polarDeviceInfo -> Log.d(TAG, "Found id: " + polarDeviceInfo.deviceId + " address: " + polarDeviceInfo.address + " rssi: " + polarDeviceInfo.rssi + " name: " + polarDeviceInfo.name + " isConnectable: " + polarDeviceInfo.isConnectable),
                                throwable -> Log.d(TAG, "" + throwable.getLocalizedMessage()),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                scanDisposable.dispose();
                scanDisposable = null;
            }
        });

        list.setOnClickListener(v -> api.listExercises(DEVICE_ID)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        polarExerciseEntry -> {
                            Log.d(TAG, "next: " + polarExerciseEntry.date + " path: " + polarExerciseEntry.path + " id: " + polarExerciseEntry.identifier);
                            exerciseEntry = polarExerciseEntry;
                        },
                        throwable -> Log.e(TAG, "Failed to fetch exercises: " + throwable),
                        () -> Log.d(TAG, "fetch exercises complete")
                ));

        read.setOnClickListener(v -> {
            if (exerciseEntry != null) {
                api.fetchExercise(DEVICE_ID, exerciseEntry)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                polarExerciseData -> Log.d(TAG, "exercise data count: " + polarExerciseData.hrSamples.size() + " samples: " + polarExerciseData.hrSamples),
                                throwable -> Log.e(TAG, "Failed to read exercise: " + throwable.getLocalizedMessage())
                        );
            }
        });

        remove.setOnClickListener(v -> {
            if (exerciseEntry != null) {
                api.removeExercise(DEVICE_ID, exerciseEntry)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> Log.d(TAG, "ex removed ok"),
                                throwable -> Log.d(TAG, "ex remove failed: " + throwable.getLocalizedMessage())
                        );
            }
        });

        startH10Recording.setOnClickListener(view ->
                api.startRecording(DEVICE_ID, "TEST_APP_ID", PolarBleApi.RecordingInterval.INTERVAL_1S, PolarBleApi.SampleType.HR)
                        .subscribe(
                                () -> Log.d(TAG, "recording started"),
                                throwable -> Log.e(TAG, "recording start failed: " + throwable.getLocalizedMessage())
                        ));

        stopH10Recording.setOnClickListener(view ->
                api.stopRecording(DEVICE_ID)
                        .subscribe(
                                () -> Log.d(TAG, "recording stopped"),
                                throwable -> Log.e(TAG, "recording stop failed: " + throwable.getLocalizedMessage())
                        ));

        readH10RecordingStatus.setOnClickListener(view ->
                api.requestRecordingStatus(DEVICE_ID)
                        .subscribe(
                                pair -> Log.d(TAG, "recording on: " + pair.first + " ID: " + pair.second),
                                throwable -> Log.e(TAG, "recording status failed: " + throwable.getLocalizedMessage())
                        ));

        setTime.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            api.setLocalTime(DEVICE_ID, calendar)
                    .subscribe(
                            () -> Log.d(TAG, "time set to device"),
                            error -> Log.d(TAG, "set time failed: " + error));
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && savedInstanceState == null) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        
        // do we have to wait for some other setup tasks to finish...?
        tryPolarConnect();
    }

    private void tryPolarConnect() {
        try {
            text_view.setText("Searching for "+DEVICE_ID);
            api.connectToDevice(DEVICE_ID);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            text_view.setText("PolarInvalidArgument: "+polarInvalidArgument);
            polarInvalidArgument.printStackTrace();
        }
    }

    private void writeLogFile(String msg, FileWriter logStream, String tag) {
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

    private int getRMSSD(double[] samples) {
        double[] NNdiff = v_abs(v_differential(samples));
        //rmssd = round(np.sqrt(np.sum((NNdiff * NNdiff) / len(NNdiff))), 2)
        double rmssd = sqrt(v_sum(v_power_s2(NNdiff,2)) / NNdiff.length);
        return (int)round(rmssd);
    }

    // pre: no artifacts(?)
    private void testRMSSD_1() {
        Log.d(TAG,"testRMSSD_1 ...");
        double result = getRMSSD(samples1);
        Log.d(TAG,"testRMSSD_1 done");
    }

    // FIXME: get rid of params, except for currentTime_ms
    private void spokenUpdate(@NotNull PolarHrData data, double a1, int rmssd, long currentTime_ms) {
            long timeSinceLastSpokenUpdate_s = (long)(currentTime_ms - prevSpokenUpdate_ms) / 1000;
            long timeSinceLastSpokenArtifactsUpdate_s = (long)(currentTime_ms - prevSpokenArtifactsUpdate_ms) / 1000;
            //if (timeSinceLastSpokenUpdate_s < 10) {
            //    return;
            //}
            String artifactsUpdate = "";
            String featuresUpdate = "";
            if (timeSinceLastSpokenArtifactsUpdate_s > 10) {
                if (artifactsPercent>5 && data.hr>80
                        || artifactsPercent>20
                        || timeSinceLastSpokenArtifactsUpdate_s>60
                ) {
                    if (data.hr > 130 || a1 < 0.85) {
                        artifactsUpdate = " lost " + artifactsPercent;
                    } else {
                        artifactsUpdate = " lost " + artifactsPercent + " percent ";
                    }
                }
            }
            if (timeSinceLastSpokenUpdate_s > 10) {
                if (data.hr > 130 || a1 < 0.85) {
                    // abbreviated
                    artifactsUpdate = " lost " + artifactsPercent;
                    featuresUpdate = data.hr + " " + a1_r + " ";
                } else if ((data.hr > 120 || a1_r < 1.0) && timeSinceLastSpokenUpdate_s >= 10) {
                    featuresUpdate = "Heart rate " + data.hr + ", Alpha one, " + a1_r + ",";
                } else if (data.hr > 90 && timeSinceLastSpokenUpdate_s >= 60) {
                    featuresUpdate = "Heart rate " + data.hr + ". Alpha one, " + a1_r + ",";
                } else {
                    featuresUpdate = "Heart rate " + data.hr + ". HRV " + rmssd + ".";
                }
            }
            if (featuresUpdate.length()>0 || artifactsUpdate.length()>0) {
                if (artifactsPercent > 5) {
                    spokenOutput(artifactsUpdate + " " + featuresUpdate);
                } else {
                    spokenOutput( featuresUpdate + " " + artifactsUpdate);
                }
                if (featuresUpdate.length()>0) prevSpokenUpdate_ms = currentTime_ms;
                if (artifactsUpdate.length()>0) prevSpokenArtifactsUpdate_ms = currentTime_ms;
            }
    }

    private void spokenOutput(String update) {
        if (soundEnabled) {
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
            rrLogStream.close();
        } catch (IOException e) {
            text_view.setText("IOException "+e.toString());
            e.printStackTrace();
        }
        api.shutDown();
    }
}
