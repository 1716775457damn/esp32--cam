package com.example.esp32camviewer;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ESP32CamViewer";
    
    private EditText ipAddressEditText;
    private Button connectButton;
    private Button ledOnButton;
    private Button ledOffButton;
    private ImageView streamImageView;
    
    private String serverIp = "";
    private boolean isStreaming = false;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化UI元素
        ipAddressEditText = findViewById(R.id.ip_address);
        connectButton = findViewById(R.id.connect_button);
        ledOnButton = findViewById(R.id.led_on_button);
        ledOffButton = findViewById(R.id.led_off_button);
        streamImageView = findViewById(R.id.stream_image);
        
        // 设置连接按钮点击事件
        connectButton.setOnClickListener(v -> {
            String ip = ipAddressEditText.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "请输入ESP32摄像头的IP地址", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 更新IP地址并开始/停止流
            if (!isStreaming) {
                serverIp = ip;
                startStreaming();
                connectButton.setText("断开连接");
                isStreaming = true;
            } else {
                stopStreaming();
                connectButton.setText("连接");
                isStreaming = false;
            }
        });
        
        // 设置LED控制按钮
        ledOnButton.setOnClickListener(v -> controlLED("on"));
        ledOffButton.setOnClickListener(v -> controlLED("off"));
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
            
            inputStream = connection.getInputStream();
            bufferedInputStream = new BufferedInputStream(inputStream);
            
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
            mainHandler.post(() -> Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show());
        } finally {
            try {
                if (bufferedInputStream != null) bufferedInputStream.close();
                if (inputStream != null) inputStream.close();
                if (connection != null) connection.disconnect();
            } catch (IOException e) {
                Log.e(TAG, "关闭连接时出错: " + e.getMessage());
            }
            
            mainHandler.post(() -> {
                connectButton.setText("连接");
                isStreaming = false;
            });
        }
    }
    
    private void controlLED(String status) {
        if (serverIp.isEmpty()) {
            Toast.makeText(this, "请先连接到ESP32摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        
        executorService.execute(() -> {
            try {
                URL url = new URL("http://" + serverIp + "/led/" + status);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                connection.connect();
                
                final int responseCode = connection.getResponseCode();
                mainHandler.post(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(MainActivity.this, 
                                "LED " + (status.equals("on") ? "已开启" : "已关闭"), 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, 
                                "控制LED失败，状态码: " + responseCode, 
                                Toast.LENGTH_SHORT).show();
                    }
                });
                
                connection.disconnect();
            } catch (IOException e) {
                final String errorMsg = "控制LED失败: " + e.getMessage();
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