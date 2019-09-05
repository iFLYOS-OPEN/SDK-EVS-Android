# EVS Android SDK

### 开发环境要求

* Git
* Android Studio 3.1.2 或以上
* 能够正常访问 jCenter、Google Maven 的网络连接
* 一部 Android 4.4 或以上的开发设备（或以 Android 平板代替）

### 快速上手

#### 下载源码：

```sh
git clone https://github.com/iFLYOS-OPEN/EVS-SDK-Android.git
```

使用 Android Studio打开clone下来的工程。首次打开需要等待Gradle联网下载一些依赖库，可能需要较长时间，下载过程中需要保持网络畅通。

项目中包含以下两个模块：

##### `evs_sdk`

EVS接入协议的kotlin实现，主要包括 *网络连接*、*认证授权*、*EVS协议解析*、*模块接口定义和默认实现*几部分：

名称 | 说明
---|---
网络连接 | 与iFLYOS建立WebSocket连接，支持ws和wss
认证授权 | 按[认证授权协议](https://doc.iflyos.cn/device/auth.html#%E8%AE%A4%E8%AF%81%E4%B8%8E%E6%8E%88%E6%9D%83api)对设备进行授权
EVS协议解析 | 向iFLYOS发送EVS request，解析response
模块接口定义和默认实现 | 提供EVS协议描述的[功能模块](https://doc.iflyos.cn/device/evs/#%E5%8A%9F%E8%83%BD%E6%A8%A1%E5%9D%97%E8%AF%B4%E6%98%8E)接口定义，并提供默认实现

SDK当前对[EVS接入协议](https://doc.iflyos.cn/device/evs/#embedded-api-v1-%E4%BB%8B%E7%BB%8D)中各模块的实现情况如下：

名称 | 说明 | 要求 | 消息 | 是否实现
---|---|---|---|---
recognizer | 识别器，识别语音和文本 | 必须实现 | audio_in<br/>text_in<br/>expect_reply<br/>stop_capture<br/>intermediate_text<br/> | 是<br/>是<br/>是<br/>是<br/>是<br/>
audio_player | 音频播放器，播放的内容可能是音乐、新闻、闹钟响铃或TTS语音 | 必须实现 | audio_out<br/>playback.progress_sync<br/>tts.progress_sync<br/>ring.progress_sync<br/>tts.text_in<br/> | 是<br/>是<br/>是<br/>是<br/>是<br/>是<br/>
system | 系统相关 | 必须实现 | ping<br/>error<br/>state_sync<br/>exception<br/>revoke_authorization<br/> | 是<br/>是<br/>是<br/>是<br/>是<br/>
alarm | 设备本地闹钟 | 可选实现 | set_alarm<br/>delete_alarm<br/>system_sync<br/> | 是<br/>是<br/>是<br/>
speaker | 扬声器控制 | 可选实现 | set_volume | 是
video_player | 视频播放器 | 可选实现 | video_out<br/>progress_sync<br/> | 是<br/>是<br/>
playback_controller | 播放控制，在部分场景下，用户可通过触控或按键控制音频播放进度 | 可选实现 | control_command | 是
app_action | APP操作，针对系统的第三方APP进行的操作 | 可选实现 | execute<br/>check<br/>check_result<br/>execute_succeed<br/>execute_failed<br/> | 是<br/>是<br/>是<br/>是<br/>是<br/>
screen | 屏幕控制 | 可选实现
template | 模板展示，用于通过界面模板给用户反馈更丰富的信息音 | 可选实现
interceptor | 自定义拦截器，用于实现自定义语义理解 | 可选实现

SDK最外层接口被封装成 EvsService，以Android Service组件方式对外提供。

##### `demo`

一个最基础的 iFLYOS EVS设备端实现，简单的SDK调用示例，用于演示SDK接口调用，体验EVS技能，以及调试查看交互协议。

### SDK集成

集成文档见https://doc.iflyos.cn/device/evs/sdk/android.html
