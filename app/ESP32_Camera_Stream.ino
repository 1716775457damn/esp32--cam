#include "esp_camera.h"
#include <WiFi.h>
#include "esp_timer.h"
#include "img_converters.h"
#include "Arduino.h"
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"
#include "driver/rtc_io.h"

// 网络凭证
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

// Web服务器端口号
WiFiServer server(80);

// 相机引脚定义 - 针对AI Thinker ESP32-CAM模块
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// LED闪光灯引脚
#define FLASH_LED_PIN 4

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0); // 禁用断电检测器
  
  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Serial.println();
  
  // 配置相机
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  
  // 相机图像质量设置
  if(psramFound()){
    config.frame_size = FRAMESIZE_VGA; // FRAMESIZE_UXGA;
    config.jpeg_quality = 10;
    config.fb_count = 2;
  } else {
    config.frame_size = FRAMESIZE_SVGA;
    config.jpeg_quality = 12;
    config.fb_count = 1;
  }
  
  // 初始化相机
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x", err);
    return;
  }
  
  // 设置Wi-Fi
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi connected");
  
  // 启动服务器
  server.begin();
  Serial.print("Camera Stream Ready! Connect to http://");
  Serial.println(WiFi.localIP());
  
  // 闪光灯设置为输出
  pinMode(FLASH_LED_PIN, OUTPUT);
  digitalWrite(FLASH_LED_PIN, LOW); // 默认关闭闪光灯
}

void loop() {
  // 检查是否有客户端连接
  WiFiClient client = server.available();
  if (client) {
    Serial.println("New Client.");
    String currentLine = "";
    
    while (client.connected()) {
      if (client.available()) {
        char c = client.read();
        Serial.write(c);
        
        if (c == '\n') {
          // 如果当前行是空行，则是HTTP请求的末尾
          if (currentLine.length() == 0) {
            // 发送HTTP响应头
            client.println("HTTP/1.1 200 OK");
            client.println("Content-Type: multipart/x-mixed-replace; boundary=frame");
            client.println();
            
            // 持续发送图像流
            while (client.connected()) {
              // 捕获图像
              camera_fb_t * fb = esp_camera_fb_get();
              if(!fb) {
                Serial.println("Camera capture failed");
                break;
              }
              
              // 构造HTTP帧头
              client.println("--frame");
              client.println("Content-Type: image/jpeg");
              client.println("Content-Length: " + String(fb->len));
              client.println();
              
              // 发送图像数据
              client.write((char *)fb->buf, fb->len);
              client.println();
              
              // 释放缓冲区
              esp_camera_fb_return(fb);
              
              // 简单的延迟以控制帧率
              delay(1);
            }
            break;
          } else {
            currentLine = "";
          }
        } else if (c != '\r') {
          currentLine += c;
        }
        
        // 处理闪光灯开关请求
        if (currentLine.endsWith("GET /flash/on")) {
          digitalWrite(FLASH_LED_PIN, HIGH);
        }
        if (currentLine.endsWith("GET /flash/off")) {
          digitalWrite(FLASH_LED_PIN, LOW);
        }
      }
    }
    
    // 关闭连接
    client.stop();
    Serial.println("Client Disconnected.");
  }
} 