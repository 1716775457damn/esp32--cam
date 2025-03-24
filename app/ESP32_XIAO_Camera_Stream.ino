#include "esp_camera.h"
#include <WiFi.h>
#include "esp_timer.h"
#include "img_converters.h"
#include "Arduino.h"
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"
#include "driver/rtc_io.h"

// 网络凭证 - 修改为您的WiFi信息
const char* ssid = "ovo";
const char* password = "twx20051";

// Web服务器端口号
WiFiServer server(80);

// ESP32 XIAO 连接OV2640摄像头引脚定义
// 注意：根据您的具体连接可能需要调整
#define PWDN_GPIO_NUM     -1 // 不适用于XIAO，设为-1
#define RESET_GPIO_NUM    -1 // 不适用于XIAO，设为-1
#define XCLK_GPIO_NUM     10 // XIAO的D10引脚
#define SIOD_GPIO_NUM     40 // XIAO的SDA引脚
#define SIOC_GPIO_NUM     39 // XIAO的SCL引脚
#define Y9_GPIO_NUM        1 // XIAO的D1引脚
#define Y8_GPIO_NUM        2 // XIAO的D2引脚
#define Y7_GPIO_NUM        3 // XIAO的D3引脚
#define Y6_GPIO_NUM        4 // XIAO的D4引脚
#define Y5_GPIO_NUM        5 // XIAO的D5引脚
#define Y4_GPIO_NUM        6 // XIAO的D6引脚
#define Y3_GPIO_NUM        7 // XIAO的D7引脚
#define Y2_GPIO_NUM        8 // XIAO的D8引脚
#define VSYNC_GPIO_NUM     9 // XIAO的D9引脚
#define HREF_GPIO_NUM     11 // XIAO的D11引脚
#define PCLK_GPIO_NUM     12 // XIAO的D12引脚

// LED引脚定义（如果XIAO上有可用的LED）
#define LED_PIN 13

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
    config.frame_size = FRAMESIZE_VGA; // 640x480分辨率
    config.jpeg_quality = 10; // 较低的值 = 更高的质量
    config.fb_count = 2;
  } else {
    config.frame_size = FRAMESIZE_SVGA;
    config.jpeg_quality = 12;
    config.fb_count = 1;
  }
  
  // 初始化相机
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("相机初始化失败，错误: 0x%x", err);
    return;
  }
  
  // 设置Wi-Fi
  WiFi.begin(ssid, password);
  Serial.print("连接到WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi已连接");
  Serial.print("IP地址: ");
  Serial.println(WiFi.localIP());
  
  // 启动服务器
  server.begin();
  Serial.print("摄像头流媒体服务器已就绪！连接到 http://");
  Serial.println(WiFi.localIP());
  
  // LED设置
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
}

void loop() {
  // 检查是否有客户端连接
  WiFiClient client = server.available();
  if (client) {
    Serial.println("新客户端连接");
    String currentLine = "";
    
    while (client.connected()) {
      if (client.available()) {
        char c = client.read();
        Serial.write(c);
        
        if (c == '\n') {
          // 如果当前行是空行，则是HTTP请求的末尾
          if (currentLine.length() == 0) {
            // 发送标准HTTP响应头
            client.println("HTTP/1.1 200 OK");
            client.println("Access-Control-Allow-Origin: *"); // 允许跨域访问
            client.println("Content-Type: multipart/x-mixed-replace; boundary=frame");
            client.println();
            
            // 持续发送图像流
            while (client.connected()) {
              // 捕获图像
              camera_fb_t * fb = esp_camera_fb_get();
              if(!fb) {
                Serial.println("图像捕获失败");
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
              delay(10);
            }
            break;
          } else {
            currentLine = "";
          }
        } else if (c != '\r') {
          currentLine += c;
        }
        
        // 处理LED控制请求
        if (currentLine.endsWith("GET /led/on")) {
          digitalWrite(LED_PIN, HIGH);
        }
        if (currentLine.endsWith("GET /led/off")) {
          digitalWrite(LED_PIN, LOW);
        }
      }
    }
    
    // 关闭连接
    client.stop();
    Serial.println("客户端已断开连接");
  }
} 