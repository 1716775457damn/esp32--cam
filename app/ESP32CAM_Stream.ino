#include "esp_camera.h"
#include <WiFi.h>
#include "esp_timer.h"
#include "img_converters.h"
#include "Arduino.h"
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"
#include "driver/rtc_io.h"

// 网络凭证 - 修改为您的WiFi信息
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

// Web服务器端口号
WiFiServer server(80);

// 摄像头模型选择
// ESP32-CAM (默认模块)
#define CAMERA_MODEL_AI_THINKER
//#define CAMERA_MODEL_M5STACK_PSRAM
//#define CAMERA_MODEL_M5STACK_WITHOUT_PSRAM

// 根据不同的摄像头模块定义引脚
#if defined(CAMERA_MODEL_AI_THINKER)
  // ESP32-CAM AI-Thinker标准引脚定义
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
  // ESP32-CAM AI-Thinker上有闪光灯
  #define FLASH_LED_PIN      4
#elif defined(CAMERA_MODEL_M5STACK_PSRAM)
  // M5Stack ESP32 Camera with PSRAM
  #define PWDN_GPIO_NUM     -1
  #define RESET_GPIO_NUM    15
  #define XCLK_GPIO_NUM     27
  #define SIOD_GPIO_NUM     25
  #define SIOC_GPIO_NUM     23
  #define Y9_GPIO_NUM       19
  #define Y8_GPIO_NUM       36
  #define Y7_GPIO_NUM       18
  #define Y6_GPIO_NUM       39
  #define Y5_GPIO_NUM        5
  #define Y4_GPIO_NUM       34
  #define Y3_GPIO_NUM       35
  #define Y2_GPIO_NUM       32
  #define VSYNC_GPIO_NUM    22
  #define HREF_GPIO_NUM     26
  #define PCLK_GPIO_NUM     21
  #define FLASH_LED_PIN     -1
#elif defined(CAMERA_MODEL_M5STACK_WITHOUT_PSRAM)
  // M5Stack ESP32 Camera without PSRAM
  #define PWDN_GPIO_NUM     -1
  #define RESET_GPIO_NUM    15
  #define XCLK_GPIO_NUM     27
  #define SIOD_GPIO_NUM     25
  #define SIOC_GPIO_NUM     23
  #define Y9_GPIO_NUM       19
  #define Y8_GPIO_NUM       36
  #define Y7_GPIO_NUM       18
  #define Y6_GPIO_NUM       39
  #define Y5_GPIO_NUM        5
  #define Y4_GPIO_NUM       34
  #define Y3_GPIO_NUM       35
  #define Y2_GPIO_NUM       17
  #define VSYNC_GPIO_NUM    22
  #define HREF_GPIO_NUM     26
  #define PCLK_GPIO_NUM     21
  #define FLASH_LED_PIN     -1
#else
  #error "摄像头型号未定义，请选择合适的摄像头模型"
#endif

void setup() {
  // 禁用断电检测器
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
  
  // 初始化串口
  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Serial.println();
  Serial.println("ESP32-CAM 视频流服务器");
  
  // 配置相机参数
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
  
  // 检查PSRAM并设置相机质量参数
  if (psramFound()) {
    config.frame_size = FRAMESIZE_VGA; // 可选：FRAMESIZE_UXGA(1600x1200), FRAMESIZE_SXGA(1280x1024), FRAMESIZE_XGA(1024x768), FRAMESIZE_SVGA(800x600), FRAMESIZE_VGA(640x480)
    config.jpeg_quality = 10; // 0-63, 值越低质量越高
    config.fb_count = 2;
    Serial.println("PSRAM可用: 设置较高分辨率");
  } else {
    config.frame_size = FRAMESIZE_SVGA;
    config.jpeg_quality = 12;
    config.fb_count = 1;
    Serial.println("PSRAM不可用: 设置较低分辨率");
  }
  
  // 初始化相机
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("相机初始化失败，错误: 0x%x", err);
    delay(1000);
    ESP.restart();
    return;
  }
  
  // 设置相机参数
  sensor_t * s = esp_camera_sensor_get();
  if (s) {
    // 可以根据需要调整这些参数
    s->set_brightness(s, 0);     // -2 to 2
    s->set_contrast(s, 0);       // -2 to 2
    s->set_saturation(s, 0);     // -2 to 2
    s->set_special_effect(s, 0); // 0 = 无效果, 1 = 负片, 2 = 灰度, 3 = 偏红, 4 = 偏绿, 5 = 偏蓝, 6 = 复古
    s->set_whitebal(s, 1);       // 0 = 禁用, 1 = 启用
    s->set_awb_gain(s, 1);       // 0 = 禁用, 1 = 启用
    s->set_wb_mode(s, 0);        // 0 自动, 1 阳光, 2 阴天, 3 办公室, 4 家里
    s->set_exposure_ctrl(s, 1);  // 0 = 禁用, 1 = 启用
    s->set_aec2(s, 0);           // 0 = 禁用, 1 = 启用
    s->set_gain_ctrl(s, 1);      // 0 = 禁用, 1 = 启用
    s->set_agc_gain(s, 0);       // 0 - 30
    s->set_gainceiling(s, (gainceiling_t)0); // 0 - 6
    s->set_bpc(s, 0);            // 0 = 禁用, 1 = 启用
    s->set_wpc(s, 1);            // 0 = 禁用, 1 = 启用
    s->set_raw_gma(s, 1);        // 0 = 禁用, 1 = 启用
    s->set_lenc(s, 1);           // 0 = 禁用, 1 = 启用
    s->set_hmirror(s, 0);        // 0 = 禁用, 1 = 启用 (水平翻转)
    s->set_vflip(s, 0);          // 0 = 禁用, 1 = 启用 (垂直翻转)
    s->set_dcw(s, 1);            // 0 = 禁用, 1 = 启用
    s->set_colorbar(s, 0);       // 0 = 禁用, 1 = 启用 (测试彩条)
  }
  
  // 连接WiFi网络
  WiFi.begin(ssid, password);
  Serial.print("连接到WiFi");
  
  int connection_attempts = 0;
  while (WiFi.status() != WL_CONNECTED && connection_attempts < 20) {
    delay(500);
    Serial.print(".");
    connection_attempts++;
  }
  
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("\nWiFi连接失败，将重新启动");
    delay(1000);
    ESP.restart();
    return;
  }
  
  Serial.println();
  Serial.print("已连接! IP地址: ");
  Serial.println(WiFi.localIP());
  
  // 启动Web服务器
  server.begin();
  Serial.println("视频流服务器已启动");
  Serial.print("在浏览器中访问: http://");
  Serial.println(WiFi.localIP());
  
  // 设置闪光灯引脚为输出
  if (FLASH_LED_PIN >= 0) {
    pinMode(FLASH_LED_PIN, OUTPUT);
    digitalWrite(FLASH_LED_PIN, LOW); // 默认关闭闪光灯
    Serial.println("闪光灯控制可用");
  }
}

void loop() {
  // 检查是否有客户端连接
  WiFiClient client = server.available();
  
  if (client) {
    Serial.println("新客户端连接");
    String currentLine = "";
    
    // 当客户端保持连接时
    while (client.connected()) {
      if (client.available()) {
        char c = client.read();
        Serial.write(c);
        
        if (c == '\n') {
          // 如果当前行是空行，则是HTTP请求的末尾
          if (currentLine.length() == 0) {
            // 发送标准HTTP响应头
            client.println("HTTP/1.1 200 OK");
            client.println("Access-Control-Allow-Origin: *");
            client.println("Content-Type: multipart/x-mixed-replace; boundary=frame");
            client.println();
            
            // 持续发送图像流
            while (client.connected()) {
              // 捕获图像
              camera_fb_t * fb = esp_camera_fb_get();
              if (!fb) {
                Serial.println("图像捕获失败");
                break;
              }
              
              // 构造HTTP帧
              client.println("--frame");
              client.println("Content-Type: image/jpeg");
              client.println("Content-Length: " + String(fb->len));
              client.println();
              
              // 发送图像数据
              client.write((char *)fb->buf, fb->len);
              client.println();
              
              // 释放图像缓冲区
              esp_camera_fb_return(fb);
              
              // 轻微延迟以控制帧率
              delay(10);
            }
            break;
          } else {
            currentLine = "";
          }
        } else if (c != '\r') {
          currentLine += c;
        }
        
        // 处理闪光灯控制请求
        if (FLASH_LED_PIN >= 0) {
          if (currentLine.endsWith("GET /flash/on")) {
            digitalWrite(FLASH_LED_PIN, HIGH);
            Serial.println("闪光灯已开启");
          } else if (currentLine.endsWith("GET /flash/off")) {
            digitalWrite(FLASH_LED_PIN, LOW);
            Serial.println("闪光灯已关闭");
          }
        }
        
        // 处理重启请求
        if (currentLine.endsWith("GET /restart")) {
          client.println("HTTP/1.1 200 OK");
          client.println("Content-Type: text/plain");
          client.println("Connection: close");
          client.println();
          client.println("重启中...");
          delay(1000);
          ESP.restart();
        }
      }
    }
    
    // 关闭连接
    client.stop();
    Serial.println("客户端已断开连接");
  }
} 