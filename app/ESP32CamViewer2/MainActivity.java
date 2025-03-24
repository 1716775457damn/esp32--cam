package com.example.esp32camviewer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ESP32CamViewer";
    private static final String PREFS_NAME = "ESP32CamPrefs";
    private static final String PREF_IP_ADDRESS = "ip_address";
    private static final String PREF_DEVICE_TYPE = "device_type";
    
    private EditText ipAddressEditText;
    private Button connectButton;
    private Button ledOnButton;
    private Button ledOffButton;
    private Button restartButton;
    private ImageView streamImageView;
    private TextView statusText;
    private Spinner deviceTypeSpinner;
    private SwitchCompat autoConnectSwitch;
    
    private String serverIp = "";
    private boolean isStreaming = false;
    private String deviceType = "ESP32-CAM"; // 默认为ESP32-CAM
    private boolean autoConnect = false;
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化SharedPreferences
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // 初始化UI元素
        ipAddressEditText = findViewById(R.id.ip_address);
        connectButton = findViewById(R.id.connect_button);
        ledOnButton = findViewById(R.id.led_on_button);
        ledOffButton = findViewById(R.id.led_off_button);
        restartButton = findViewById(R.id.restart_button);
        streamImageView = findViewById(R.id.stream_image);
        statusText = findViewById(R.id.status_text);
        deviceTypeSpinner = findViewById(R.id.device_type_spinner);
        autoConnectSwitch = findViewById(R.id.auto_connect_switch);
        
        // 设置设备类型选择器
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.device_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceTypeSpinner.setAdapter(adapter);
        
        // 加载保存的设置
        String savedIp = settings.getString(PREF_IP_ADDRESS, "");
        String savedDeviceType = settings.getString(PREF_DEVICE_TYPE, "ESP32-CAM");
        
        ipAddressEditText.setText(savedIp);
        
        // 设置设备类型
        if (savedDeviceType.equals("ESP32-CAM")) {
            deviceTypeSpinner.setSelection(0); // ESP32-CAM位置
        } else {
            deviceTypeSpinner.setSelection(1); // ESP32 XIAO位置
        }
        
        // 设置设备类型选择改变监听器
        deviceTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                deviceType = parent.getItemAtPosition(position).toString();
                
                // 保存设置
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREF_DEVICE_TYPE, deviceType);
                editor.apply();
                
                // 根据设备类型更新UI
                updateUIForDeviceType();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 什么都不做
            }
        });
        
        // 设置自动连接开关
        autoConnectSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            autoConnect = isChecked;
            if (autoConnect && !savedIp.isEmpty() && !isStreaming) {
                // 自动连接
                serverIp = savedIp;
                startStreaming();
                updateUIForConnection(true);
            }
        });
        
        // 设置连接按钮点击事件
        connectButton.setOnClickListener(v -> {
            if (!isStreaming) {
                String ip = ipAddressEditText.getText().toString().trim();
                if (ip.isEmpty()) {
                    Toast.makeText(this, "请输入ESP32设备的IP地址", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 保存IP地址
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREF_IP_ADDRESS, ip);
                editor.apply();
                
                // 更新IP地址并开始流
                serverIp = ip;
                startStreaming();
                updateUIForConnection(true);
            } else {
                stopStreaming();
                updateUIForConnection(false);
            }
        });
        
        // 设置LED控制按钮
        ledOnButton.setOnClickListener(v -> controlLED("on"));
        ledOffButton.setOnClickListener(v -> controlLED("off"));
        
        // 设置重启按钮
        restartButton.setOnClickListener(v -> {
            if (serverIp.isEmpty()) {
                Toast.makeText(this, "请先连接到ESP32设备", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 发送重启命令
            executorService.execute(() -> {
                try {
                    URL url = new URL("http://" + serverIp + "/restart");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(2000);
                    connection.setReadTimeout(2000);
                    
                    int responseCode = connection.getResponseCode();
                    mainHandler.post(() -> {
                        if (responseCode == 200) {
                            Toast.makeText(MainActivity.this, "设备正在重启...", Toast.LENGTH_LONG).show();
                            // 断开当前连接
                            stopStreaming();
                            updateUIForConnection(false);
                        } else {
                            Toast.makeText(MainActivity.this, "重启失败: " + responseCode, Toast.LENGTH_SHORT).show();
                        }
                    });
                    
                    connection.disconnect();
                } catch (IOException e) {
                    final String errorMsg = "重启命令失败: " + e.getMessage();
                    Log.e(TAG, errorMsg);
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show());
                }
            });
        });
        
        // 初次启动时根据设备类型更新UI
        updateUIForDeviceType();
        
        // 如果设置了自动连接且IP不为空，则自动连接
        if (!savedIp.isEmpty() && autoConnect) {
            serverIp = savedIp;
            startStreaming();
            updateUIForConnection(true);
            autoConnectSwitch.setChecked(true);
        }
    }
    
    private void updateUIForDeviceType() {
        // 根据不同设备类型可以进行UI调整
        // 例如：显示/隐藏特定按钮，改变提示文本等
        
        if (deviceType.equals("ESP32-CAM")) {
            restartButton.setVisibility(View.VISIBLE);
            ledOnButton.setText("开启闪光灯");
            ledOffButton.setText("关闭闪光灯");
        } else { // ESP32 XIAO
            restartButton.setVisibility(View.VISIBLE); // 两种设备都支持重启
            ledOnButton.setText("开启LED");
            ledOffButton.setText("关闭LED");
        }
    }
    
    private void updateUIForConnection(boolean connected) {
        isStreaming = connected;
        
        if (connected) {
            connectButton.setText("断开连接");
            statusText.setText("已连接到: " + serverIp);
            ledOnButton.setEnabled(true);
            ledOffButton.setEnabled(true);
            restartButton.setEnabled(true);
        } else {
            connectButton.setText("连接");
            statusText.setText("未连接");
            ledOnButton.setEnabled(false);
            ledOffButton.setEnabled(false);
            restartButton.setEnabled(false);
            // 清除图像
            streamImageView.setImageResource(android.R.color.transparent);
        }
    }
    
    private void startStreaming() {
        executorService.execute(this::streamVideo);
    }
    
    private void stopStreaming() {
        isStreaming = false;
    }
    
    private void streamVideo() {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        BufferedInputStream bufferedInputStream = null;
        
        try {
            URL url = new URL("http://" + serverIp);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            // 显示连接状态
            mainHandler.post(() -> statusText.setText("连接中..."));
            
            inputStream = connection.getInputStream();
            bufferedInputStream = new BufferedInputStream(inputStream);
            
            // 连接成功
            mainHandler.post(() -> statusText.setText("已连接到: " + serverIp));
            
            // MJPEG流解析变量
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream jpegBuffer = new ByteArrayOutputStream();
            boolean headerFound = false;
            
            while (isStreaming) {
                int bytesRead = bufferedInputStream.read(buffer, 0, buffer.length);
                if (bytesRead == -1) {
                    break;
                }
                
                for (int i = 0; i < bytesRead; i++) {
                    jpegBuffer.write(buffer[i]);
                    
                    // 检查JPEG标记以找到图像的开始和结束
                    if (jpegBuffer.size() >= 2 && 
                            (byte) jpegBuffer.toByteArray()[jpegBuffer.size() - 2] == (byte) 0xFF &&
                            (byte) jpegBuffer.toByteArray()[jpegBuffer.size() - 1] == (byte) 0xD8) {
                        // 找到JPEG开始标记
                        headerFound = true;
                        jpegBuffer.reset();
                        jpegBuffer.write(0xFF);
                        jpegBuffer.write(0xD8);
                    } else if (headerFound && jpegBuffer.size() >= 2 &&
                            (byte) jpegBuffer.toByteArray()[jpegBuffer.size() - 2] == (byte) 0xFF &&
                            (byte) jpegBuffer.toByteArray()[jpegBuffer.size() - 1] == (byte) 0xD9) {
                        // 找到JPEG结束标记 - 处理完整的图像
                        headerFound = false;
                        final byte[] jpegData = jpegBuffer.toByteArray();
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                        
                        if (bitmap != null) {
                            mainHandler.post(() -> streamImageView.setImageBitmap(bitmap));
                        }
                        
                        jpegBuffer.reset();
                    }
                }
            }
            
        } catch (IOException e) {
            final String errorMsg = "流媒体错误: " + e.getMessage();
            Log.e(TAG, errorMsg);
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                statusText.setText("连接失败: " + e.getMessage());
            });
        } finally {
            try {
                if (bufferedInputStream != null) bufferedInputStream.close();
                if (inputStream != null) inputStream.close();
                if (connection != null) connection.disconnect();
            } catch (IOException e) {
                Log.e(TAG, "关闭连接时出错: " + e.getMessage());
            }
            
            mainHandler.post(() -> updateUIForConnection(false));
        }
    }
    
    private void controlLED(String status) {
        if (serverIp.isEmpty()) {
            Toast.makeText(this, "请先连接到ESP32设备", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String endpoint = deviceType.equals("ESP32-CAM") ? "/flash/" : "/led/";
        
        executorService.execute(() -> {
            try {
                URL url = new URL("http://" + serverIp + endpoint + status);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                connection.connect();
                
                final int responseCode = connection.getResponseCode();
                mainHandler.post(() -> {
                    if (responseCode == 200) {
                        String deviceSpecificName = deviceType.equals("ESP32-CAM") ? "闪光灯" : "LED";
                        Toast.makeText(MainActivity.this, 
                                deviceSpecificName + " " + (status.equals("on") ? "已开启" : "已关闭"), 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, 
                                "控制失败，状态码: " + responseCode, 
                                Toast.LENGTH_SHORT).show();
                    }
                });
                
                connection.disconnect();
            } catch (IOException e) {
                final String errorMsg = "控制失败: " + e.getMessage();
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        stopStreaming();
        executorService.shutdownNow();
        super.onDestroy();
    }
} 