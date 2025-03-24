package com.example.esp32camviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.example.esp32camviewer.ui.theme.ESP32CamViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ESP32CamViewerTheme {
                ESP32CamViewerApp()
            }
        }
    }
}

// 设备类型枚举
enum class DeviceType(val displayName: String) {
    ESP32_CAM("ESP32-CAM"),
    ESP32_XIAO("ESP32 XIAO")
}

// ViewModel处理数据和业务逻辑
class ESP32CamViewModel : ViewModel() {
    // UI状态
    var ipAddress by mutableStateOf("")
        private set
    var deviceType by mutableStateOf(DeviceType.ESP32_CAM)
        private set
    var connectionStatus by mutableStateOf("未连接")
        private set
    var isConnected by mutableStateOf(false)
        private set
    var cameraBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var autoConnect by mutableStateOf(false)
        private set
    
    // 保存最后连接的IP地址
    private var serverIp = ""
    private var isStreaming = false
    
    // 方法：更新IP地址
    fun updateIpAddress(ip: String) {
        ipAddress = ip
    }
    
    // 方法：更新设备类型
    fun updateDeviceType(type: DeviceType) {
        deviceType = type
    }
    
    // 方法：更新自动连接状态
    fun updateAutoConnect(value: Boolean) {
        autoConnect = value
    }
    
    // 方法：连接/断开摄像头
    fun toggleConnection(context: Context) {
        if (!isConnected) {
            if (ipAddress.isEmpty()) {
                connectionStatus = "请输入ESP32设备的IP地址"
                return
            }
            
            // 保存IP地址到SharedPreferences
            val sharedPrefs = context.getSharedPreferences("ESP32CamPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("ip_address", ipAddress).apply()
            
            // 开始连接
            serverIp = ipAddress
            startStreaming()
            
        } else {
            // 断开连接
            stopStreaming()
        }
    }
    
    // 方法：控制LED/闪光灯
    fun controlLed(turnOn: Boolean, onResult: (Boolean, String) -> Unit) {
        if (serverIp.isEmpty()) {
            onResult(false, "请先连接到ESP32设备")
            return
        }
        
        val status = if (turnOn) "on" else "off"
        val endpoint = if (deviceType == DeviceType.ESP32_CAM) "/flash/" else "/led/"
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://$serverIp$endpoint$status")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.connect()
                
                val responseCode = connection.responseCode
                
                withContext(Dispatchers.Main) {
                    if (responseCode == 200) {
                        val deviceSpecificName = if (deviceType == DeviceType.ESP32_CAM) "闪光灯" else "LED"
                        val statusText = if (turnOn) "已开启" else "已关闭"
                        onResult(true, "$deviceSpecificName $statusText")
                    } else {
                        onResult(false, "控制失败，状态码: $responseCode")
                    }
                }
                
                connection.disconnect()
            } catch (e: IOException) {
                val errorMsg = "控制失败: ${e.message}"
                Log.e("ESP32CamViewer", errorMsg)
                withContext(Dispatchers.Main) {
                    onResult(false, errorMsg)
                }
            }
        }
    }
    
    // 方法：重启设备
    fun restartDevice(onResult: (Boolean, String) -> Unit) {
        if (serverIp.isEmpty()) {
            onResult(false, "请先连接到ESP32设备")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://$serverIp/restart")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.connect()
                
                val responseCode = connection.responseCode
                
                withContext(Dispatchers.Main) {
                    if (responseCode == 200) {
                        onResult(true, "设备正在重启...")
                        stopStreaming()
                    } else {
                        onResult(false, "重启失败: $responseCode")
                    }
                }
                
                connection.disconnect()
            } catch (e: IOException) {
                val errorMsg = "重启命令失败: ${e.message}"
                Log.e("ESP32CamViewer", errorMsg)
                withContext(Dispatchers.Main) {
                    onResult(false, errorMsg)
                }
            }
        }
    }
    
    // 方法：开始视频流接收
    private fun startStreaming() {
        isStreaming = true
        connectionStatus = "连接中..."
        isConnected = true
        
        viewModelScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            var inputStream: BufferedInputStream? = null
            
            try {
                val url = URL("http://$serverIp")
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                withContext(Dispatchers.Main) {
                    connectionStatus = "连接中..."
                }
                
                inputStream = BufferedInputStream(connection.inputStream)
                
                withContext(Dispatchers.Main) {
                    connectionStatus = "已连接到: $serverIp"
                }
                
                // MJPEG流解析变量
                val buffer = ByteArray(1024)
                val jpegBuffer = ByteArrayOutputStream()
                var headerFound = false
                
                while (isStreaming) {
                    val bytesRead = inputStream.read(buffer, 0, buffer.size)
                    if (bytesRead == -1) break
                    
                    for (i in 0 until bytesRead) {
                        jpegBuffer.write(buffer[i].toInt())
                        
                        // 检查JPEG标记以找到图像的开始和结束
                        if (jpegBuffer.size() >= 2 && 
                                jpegBuffer.toByteArray()[jpegBuffer.size() - 2] == 0xFF.toByte() &&
                                jpegBuffer.toByteArray()[jpegBuffer.size() - 1] == 0xD8.toByte()) {
                            // 找到JPEG开始标记
                            headerFound = true
                            jpegBuffer.reset()
                            jpegBuffer.write(0xFF)
                            jpegBuffer.write(0xD8)
                        } else if (headerFound && jpegBuffer.size() >= 2 &&
                                jpegBuffer.toByteArray()[jpegBuffer.size() - 2] == 0xFF.toByte() &&
                                jpegBuffer.toByteArray()[jpegBuffer.size() - 1] == 0xD9.toByte()) {
                            // 找到JPEG结束标记 - 处理完整的图像
                            headerFound = false
                            val jpegData = jpegBuffer.toByteArray()
                            val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                            
                            withContext(Dispatchers.Main) {
                                if (bitmap != null) {
                                    cameraBitmap = bitmap
                                }
                            }
                            
                            jpegBuffer.reset()
                        }
                    }
                }
                
            } catch (e: IOException) {
                val errorMsg = "流媒体错误: ${e.message}"
                Log.e("ESP32CamViewer", errorMsg)
                withContext(Dispatchers.Main) {
                    connectionStatus = "连接失败: ${e.message}"
                    isConnected = false
                    cameraBitmap = null
                }
            } finally {
                try {
                    inputStream?.close()
                    connection?.disconnect()
                } catch (e: IOException) {
                    Log.e("ESP32CamViewer", "关闭连接时出错: ${e.message}")
                }
                
                withContext(Dispatchers.Main) {
                    if (isStreaming) {
                        isStreaming = false
                        isConnected = false
                        connectionStatus = "未连接"
                        cameraBitmap = null
                    }
                }
            }
        }
    }
    
    // 方法：停止视频流接收
    private fun stopStreaming() {
        isStreaming = false
        isConnected = false
        connectionStatus = "未连接"
        cameraBitmap = null
    }
    
    // 加载保存的设置
    fun loadSavedSettings(context: Context) {
        val sharedPrefs = context.getSharedPreferences("ESP32CamPrefs", Context.MODE_PRIVATE)
        val savedIp = sharedPrefs.getString("ip_address", "") ?: ""
        val savedDeviceType = sharedPrefs.getString("device_type", DeviceType.ESP32_CAM.name) ?: DeviceType.ESP32_CAM.name
        val savedAutoConnect = sharedPrefs.getBoolean("auto_connect", false)
        
        ipAddress = savedIp
        deviceType = try {
            DeviceType.valueOf(savedDeviceType)
        } catch (e: IllegalArgumentException) {
            DeviceType.ESP32_CAM
        }
        autoConnect = savedAutoConnect
        
        // 如果设置了自动连接且IP不为空，则自动连接
        if (autoConnect && savedIp.isNotEmpty()) {
            serverIp = savedIp
            startStreaming()
        }
    }
    
    // 保存设备类型
    fun saveDeviceType(context: Context) {
        val sharedPrefs = context.getSharedPreferences("ESP32CamPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("device_type", deviceType.name).apply()
    }
    
    // 保存自动连接设置
    fun saveAutoConnectSetting(context: Context) {
        val sharedPrefs = context.getSharedPreferences("ESP32CamPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("auto_connect", autoConnect).apply()
    }
}

@Composable
fun ESP32CamViewerApp(viewModel: ESP32CamViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // 加载保存的设置
    LaunchedEffect(Unit) {
        viewModel.loadSavedSettings(context)
    }
    
    // 主界面
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "ESP32 摄像头查看器",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 设备类型选择器和自动连接开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("设备类型：", 
                    modifier = Modifier.padding(end = 8.dp),
                    fontSize = 16.sp
                )
                
                // 设备类型下拉菜单
                var expanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Button(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(viewModel.deviceType.displayName)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DeviceType.values().forEach { deviceType ->
                            DropdownMenuItem(
                                text = { Text(deviceType.displayName) },
                                onClick = {
                                    viewModel.updateDeviceType(deviceType)
                                    viewModel.saveDeviceType(context)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                // 自动连接开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("自动连接", fontSize = 14.sp)
                    Switch(
                        checked = viewModel.autoConnect,
                        onCheckedChange = { 
                            viewModel.updateAutoConnect(it)
                            viewModel.saveAutoConnectSetting(context)
                            if (it && viewModel.ipAddress.isNotEmpty() && !viewModel.isConnected) {
                                viewModel.toggleConnection(context)
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // IP地址输入和连接按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = viewModel.ipAddress,
                    onValueChange = { viewModel.updateIpAddress(it) },
                    label = { Text("ESP32设备IP地址") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                
                Button(
                    onClick = { viewModel.toggleConnection(context) },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(if (!viewModel.isConnected) "连接" else "断开连接")
                }
            }
            
            // 状态文本
            Text(
                text = viewModel.connectionStatus,
                color = if (viewModel.isConnected) Color.Green else Color.Gray,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Start
            )
            
            // 视频流显示区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                viewModel.cameraBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "摄像头画面",
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: run {
                    Text(
                        text = "未连接到摄像头",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 控制按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LED开启按钮
                Button(
                    onClick = {
                        viewModel.controlLed(true) { success, message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    },
                    enabled = viewModel.isConnected,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Text(
                        if (viewModel.deviceType == DeviceType.ESP32_CAM) "开启闪光灯" else "开启LED"
                    )
                }
                
                // LED关闭按钮
                Button(
                    onClick = {
                        viewModel.controlLed(false) { success, message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    },
                    enabled = viewModel.isConnected,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text(
                        if (viewModel.deviceType == DeviceType.ESP32_CAM) "关闭闪光灯" else "关闭LED"
                    )
                }
                
                // 重启设备按钮
                Button(
                    onClick = {
                        viewModel.restartDevice { success, message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    },
                    enabled = viewModel.isConnected,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Text("重启设备")
                }
            }
        }
    }
} 