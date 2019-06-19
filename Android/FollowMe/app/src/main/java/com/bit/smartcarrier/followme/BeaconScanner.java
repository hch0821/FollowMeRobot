package com.bit.smartcarrier.followme;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

//기본 안드로이드 비콘 스캐너 api
public class BeaconScanner {
    private Kalmanfilter mKalmanFilter; // 칼만필터
    private boolean mKalmanOn;
    private SimpleDateFormat mSimpleDateFormat;
    private BluetoothLeScanner mBluetoothLeScanner;
    private MainActivity m;
    private double mCurRssi;
    private RssiScanner mRssiscanner;
    public final static String BEACON1 = "FB:80:C9:EF:91:1F";
    public final static String BEACON2 = "E4:59:B9:28:82:5E";
    public final static String BEACON3 = "D5:97:0B:88:B7:82";
    public final static String BEACON4 = "CF:F3:09:3A:AD:36";
    public final static String BEACON5 = "F1:F4:BE:EA:B3:59";

    /*
    estimote 비콘 정보
    ------------------
    비콘번호
    identifier
    MAC 주소
    uuid
    ------------------
    1
    10314c454232737e2c417e30408de402
    FB:80:C9:EF:91:1F
    0000FE9A-0000-1000-8000-00805F9B34FB
    ------------------
    2
    85126e18b4e65d280ec93effb3a8df0a
    E4:59:B9:28:82:5E
    0000FE9A-0000-1000-8000-00805F9B34FB
    ------------------
    3
    1aeac34b6aae6eae705ef9cd98e57121
    D5:97:0B:88:B7:82
    0000FE9A-0000-1000-8000-00805F9B34FB
    ------------------
    4
    c935292d241188a9e72effc262a72432
    CF:F3:09:3A:AD:36
    0000FE9A-0000-1000-8000-00805F9B34FB
    ------------------
    5
    e092f10689af4497a7959c0a5f35ba3e
    F1:F4:BE:EA:B3:59
    0000FE9A-0000-1000-8000-00805F9B34FB
    ------------------
    */


    private String mBeaconAddress;

    BeaconScanner(MainActivity m, double intervalTime, String beaconAddress) {
        init(m, intervalTime, beaconAddress);
    }

    //비콘 백그라운드 작업 생성시 호출
    public void init(MainActivity m, double intervalTime, String beaconAddress) {
        this.m = m;
        mRssiscanner = new RssiScanner(intervalTime);
        mKalmanFilter = new Kalmanfilter(0.0);
        mSimpleDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.KOREAN);
        mBluetoothLeScanner = this.m.mBluetoothAdapter.getBluetoothLeScanner(); //비콘 탐지 스캐너
        mBeaconAddress = beaconAddress;
    }

    void start() {
        List<ScanFilter> scanFilters;
        scanFilters = new Vector<>();
        ScanFilter.Builder scanFilter = new ScanFilter.Builder();
        scanFilter.setDeviceAddress(mBeaconAddress); //특정 MAC 주소를 가진 비콘만 검색
        ScanFilter filter = scanFilter.build();
        scanFilters.add(filter);
        //filter와 settings 기능을 사용할때 아래 코드 사용
        ScanSettings.Builder scanSettings = new ScanSettings.Builder();
        scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        //scanSettings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
        mBluetoothLeScanner.startScan(scanFilters, scanSettings.build(), mRssiscanner);
        // filter와 settings 기능을 사용하지 않을 때는 아래 코드 사용
        //mBluetoothLeScanner.startScan(mScanCallback);

        mRssiscanner.startTimer();
    }


    //비콘 스캔 작업 중지
    void stop() {
        if (mBluetoothLeScanner != null)
            mBluetoothLeScanner.stopScan(mRssiscanner); //비콘 스캔 중지

        if (mRssiscanner != null) {
            mRssiscanner.stopTimer();
        }

    }

    void setKalmanOn(boolean onoff) {
        mKalmanOn = onoff;
    }


    //비콘이 폰으로 rssi값을 쐈을 때 값을 누적했다가 평균값을 취해서
    //0.5초 간격으로
    //라즈베리파이로 보냄
    private class RssiScanner extends ScanCallback
            //private ScanCallback mScanCallback = new ScanCallback()
    {
        private Timer mRssiSendTimer;
        private ArrayList<Double> rssiBuffer = new ArrayList<Double>(); //rssi값을 누적시키는 배열
        private double mIntervalTime; //라즈베리로 rssi 평균값을 보내는 주기
        private boolean timerRunning;
        private final double INIT_VAL = 9999;
        private double prevRssi = INIT_VAL;

        RssiScanner(double intervalTime) {
            this.mIntervalTime = intervalTime;
        }

        private void startTimer() {
            mRssiSendTimer = new Timer();

            //타이머를 써서 0.5초 간격으로 rssi 평균값을 라즈베리파이로 보냄
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    if (rssiBuffer.isEmpty()) return;
                    double rssiSum = 0;
                    Iterator<Double> iter = rssiBuffer.iterator();

                    while (iter.hasNext()) {
                        rssiSum += iter.next();
                    }
                    rssiSum /= (double) rssiBuffer.size();
                    m.updateRssi(rssiSum, mBeaconAddress);
                    // m.updateRssi(mCurRssi, mBeaconAddress);
                    rssiBuffer.clear();
                }
            };
            mRssiSendTimer.schedule(timerTask, (int) (mIntervalTime * 1000), (int) (mIntervalTime * 1000));
            timerRunning = true;
        }

        private void stopTimer() {
            if (mRssiSendTimer != null && timerRunning) {
                mRssiSendTimer.cancel();
                timerRunning = false;
            }
            prevRssi = INIT_VAL;
        }

        //비콘에서 보낸 rssi를 받는 함수
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            //isNewRssi = true;
            int rssi = result.getRssi();
            if (rssi < 0 || prevRssi == INIT_VAL) {
                //rssi가 튀는 것을 방지
                //if (prevRssi - 5 < rssi && rssi < prevRssi + 5) {
                //칼만필터
                if (mKalmanOn) {
                    mCurRssi = mKalmanFilter.update(rssi); //칼만 필터 사용해서 튀는 rssi값을 잡아줌
                } else
                    mCurRssi = rssi;
                rssiBuffer.add(mCurRssi); //비콘 값을 평균내기위해 배열에 추가
                //}
            }
            prevRssi = rssi;
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d("onBatchScanResults", results.size() + "");
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d("onScanFailed()", "errorCode: " + errorCode);
        }
    }


}

