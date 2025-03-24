# ESP32摄像头查看器（Jetpack Compose版）

这是一个使用Jetpack Compose开发的Android应用程序，用于查看ESP32-CAM和ESP32 XIAO设备的实时视频流。

## 功能特点

- 使用现代的Jetpack Compose UI框架
- 支持ESP32-CAM和ESP32 XIAO两种设备类型
- 实时MJPEG视频流显示
- LED/闪光灯控制
- 设备重启功能
- 设置自动连接功能
- 设备IP和类型保存功能

## 技术架构

- **UI层**: Jetpack Compose
- **业务逻辑**: ViewModel + 协程
- **网络通信**: HttpURLConnection
- **数据持久化**: SharedPreferences

## 开发环境要求

- Android Studio Flamingo或更高版本
- Kotlin 1.8.0或更高版本
- Android SDK 34 (API级别34)
- Gradle 8.0或更高版本

## 构建和运行

1. 使用Android Studio打开项目
2. 等待Gradle同步完成
3. 连接Android设备或启动模拟器
4. 点击"运行"按钮

## 使用方法

1. 启动应用后，选择您的设备类型（ESP32-CAM或ESP32 XIAO）
2. 输入ESP32设备的IP地址
3. 点击"连接"按钮
4. 连接成功后，您将看到实时视频流
5. 使用控制按钮开启/关闭LED或闪光灯，或重启设备

## ESP32固件设置

要与此应用配合使用，您的ESP32设备需要运行兼容的固件：

### ESP32-CAM
- 使用`ESP32CAM_Stream.ino`程序
- 确保WiFi凭据正确配置

### ESP32 XIAO
- 使用`ESP32_XIAO_Camera_Stream.ino`程序
- 按照针脚定义连接摄像头模块

## 许可

MIT许可证

## 致谢

- Jetpack Compose团队
- ESP32/Arduino社区 