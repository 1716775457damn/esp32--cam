# ESP32 XIAO 摄像头实时视频传输项目

该项目实现了使用ESP32 XIAO开发板连接摄像头，并通过WiFi将视频流实时传输到Android手机上查看的功能。

## 项目组成

1. **ESP32 XIAO 摄像头服务器** - 使用ESP32 XIAO和摄像头模块构建的视频流服务器
2. **Android应用** - 用于在手机上查看ESP32摄像头视频流的Android应用程序

## 硬件需求

- ESP32 XIAO 开发板
- 兼容的摄像头模块（如OV2640）
- USB数据线（用于编程ESP32 XIAO）
- 电源（USB或电池）
- Android手机

## ESP32 XIAO 摄像头服务器设置

### 步骤1: 硬件连接

将摄像头模块连接到ESP32 XIAO开发板上，具体引脚连接请参照ESP32_XIAO_Camera_Stream.ino文件中的定义。根据您的实际硬件情况，可能需要调整引脚定义。

### 步骤2: 安装Arduino IDE和必要的库

1. 下载并安装Arduino IDE
2. 在Arduino IDE中添加ESP32开发板支持
3. 安装必要的库：
   - ESP32库
   - ESP32-CAM库

### 步骤3: 上传代码到ESP32 XIAO

1. 打开ESP32_XIAO_Camera_Stream.ino文件
2. 修改WiFi凭证为您自己的WiFi网络：
   ```cpp
   const char* ssid = "YOUR_WIFI_SSID";
   const char* password = "YOUR_WIFI_PASSWORD";
   ```
3. 检查并根据需要调整摄像头引脚定义
4. 将代码上传到ESP32 XIAO开发板
5. 打开串口监视器（波特率设为115200）观察输出信息
6. 记下ESP32 XIAO的IP地址（会在串口监视器中显示）

## Android应用设置

### 选项1: 使用提供的源代码构建应用

1. 使用Android Studio打开ESP32CamViewer文件夹
2. 根据需要调整代码
3. 构建并安装应用到您的Android手机

### 选项2: 使用现有MJPEG查看器应用

如果您不想构建自己的应用，可以在Google Play商店中搜索并下载MJPEG查看器应用，例如：
- IP Camera Viewer
- MJPEG Viewer
- tinyCam Monitor

使用这些应用时，只需输入ESP32 XIAO的IP地址即可（例如: http://192.168.1.100）。

## 使用方法

1. 确保ESP32 XIAO和您的手机连接到同一个WiFi网络
2. 给ESP32 XIAO供电，等待其连接到WiFi（可通过串口监视器确认）
3. 在Android应用中输入ESP32 XIAO的IP地址
4. 点击"连接"按钮查看视频流
5. 使用"开灯"和"关灯"按钮控制LED（如果配置了LED功能）

## 常见问题解决

1. **无法连接到ESP32 XIAO**
   - 确认ESP32 XIAO和手机在同一网络
   - 检查IP地址是否正确
   - 尝试重启ESP32 XIAO

2. **视频流卡顿或画质差**
   - 尝试调整ESP32_XIAO_Camera_Stream.ino中的图像质量设置
   - 确保WiFi信号强度良好
   - 降低帧率（增加delay值）

3. **摄像头初始化失败**
   - 检查硬件连接
   - 确认使用了正确的摄像头引脚定义
   - 检查摄像头模块是否兼容

## 定制与扩展

- 修改图像分辨率和质量设置以获得更好的性能
- 添加更多的控制功能，如调整摄像头参数、移动控制等
- 实现视频录制和截图功能
- 添加多摄像头支持

## 许可

本项目采用MIT许可证。 