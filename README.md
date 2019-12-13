# EVS Android SDK

### 开发环境要求

* Git
* Android Studio 3.1.2 或以上
* 能够正常访问 jCenter、Google Maven 的网络连接
* 一部 Android 4.4 或以上的开发设备（或以 Android 平板代替）

> Demo 目前为了使用 CameraX 进行扫码，将 `minSdkVersion` 提高到了 21，若你不需要使用此功能且期望运行在 Android 4.4 上，则可以通过 [将 Demo 兼容到 Android 4.4](https://github.com/iFLYOS-OPEN/SDK-EVS-Android/wiki/%E5%B0%86-Demo-%E5%85%BC%E5%AE%B9%E5%88%B0-Android-4.4) 修改完成。SDK 的 `minSdkVersion` 仍为 19。

### 快速上手

#### 下载源码：

```sh
git clone https://github.com/iFLYOS-OPEN/SDK-EVS-Android.git
```

使用 Android Studio 打开 clone 下来的工程。首次打开需要等待 Gradle 联网下载一些依赖库，可能需要较长时间，下载过程中需要保持网络畅通。

项目中包含以下两个模块：

##### `evs_sdk`

EVS接入协议的 kotlin 实现，主要包括 *网络连接*、*认证授权*、*EVS 协议解析*、*模块接口定义和默认实现*几部分：

名称 | 说明
---|---
网络连接 | 与 iFLYOS 建立 WebSocket 连接，支持 ws 和 wss
认证授权 | 按 [认证授权协议](https://doc.iflyos.cn/device/auth.html#%E8%AE%A4%E8%AF%81%E4%B8%8E%E6%8E%88%E6%9D%83api) 对设备进行授权
EVS协议解析 | 向iFLYOS发送EVS request，解析response
模块接口定义和默认实现 | 提供EVS协议描述的 [功能模块](https://doc.iflyos.cn/device/evs/#%E5%8A%9F%E8%83%BD%E6%A8%A1%E5%9D%97%E8%AF%B4%E6%98%8E) 接口定义，并提供默认实现

SDK当前对 [EVS接入协议](https://doc.iflyos.cn/device/evs/#embedded-api-v1-%E4%BB%8B%E7%BB%8D) 中各模块的实现情况如下：

| 名称                  | 说明                             | 要求   | 消息是否实现                                                                                                                                                                                                                                                                                                                                                 |    |
|:--------------------|:-------------------------------|:-----|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---|
| recognizer          | 识别器，识别语音和文本                    | 必须实现 | <input type="checkbox" disabled checked>audio_in</input><br/><input type="checkbox" disabled checked>text_in</input><br/><input type="checkbox" disabled checked>expect_reply</input><br/><input type="checkbox" disabled checked>stop_capture</input><br/><input type="checkbox" disabled checked>intermediate_text</input><br/>                      |    |
| audio_player        | 音频播放器，播放的内容可能是音乐、新闻、闹钟响铃或TTS语音 | 必须实现 | <input type="checkbox" disabled checked>audio_out</input><br/><input type="checkbox" disabled checked>playback.progress_sync</input><br/><input type="checkbox" disabled checked>tts.progress_sync</input><br/><input type="checkbox" disabled checked>ring.progress_sync</input><br/><input type="checkbox" disabled checked>tts.text_in</input><br/> |    |
| system              | 系统相关                           | 必须实现 | <input type="checkbox" disabled checked>ping</input><br/><input type="checkbox" disabled checked>error</input><br/><input type="checkbox" disabled checked>state_sync</input><br/><input type="checkbox" disabled checked>exception</input><br/><input type="checkbox" disabled checked>revoke_authorization</input><br/>                              |    |
| alarm               | 设备本地闹钟                         | 可选实现 | <input type="checkbox" disabled checked>set_alarm</input><br/><input type="checkbox" disabled checked>delete_alarm</input><br/><input type="checkbox" disabled checked>system_sync</input><br/>                                                                                                                                                        |    |
| speaker             | 扬声器控制                          | 可选实现 | <input type="checkbox" disabled checked>set_volume</input>                                                                                                                                                                                                                                                                                             |    |
| video_player        | 视频播放器                          | 可选实现 | <input type="checkbox" disabled checked>video_out</input><br/><input type="checkbox" disabled checked>progress_sync</input><br/>                                                                                                                                                                                                                       |    |
| playback_controller | 播放控制，在部分场景下，用户可通过触控或按键控制音频播放进度 | 可选实现 | <input type="checkbox" disabled checked>control_command</input>                                                                                                                                                                                                                                                                                        |    |
| app_action          | APP操作，针对系统的第三方APP进行的操作         | 可选实现 | <input type="checkbox" disabled checked>execute</input><br/><input type="checkbox" disabled checked>check</input><br/><input type="checkbox" disabled checked>check_result</input><br/><input type="checkbox" disabled checked>execute_succeed</input><br/><input type="checkbox" disabled checked>execute_failed</input><br/>                         |    |
| screen              | 屏幕控制                           | 可选实现 | <input type="checkbox" disabled>set_state</input><br/><input type="checkbox" disabled>set_brightness</input><br/>                                                                                                                                                                                                                                      |    |
| template            | 模板展示，用于通过界面模板给用户反馈更丰富的信息音      | 可选实现 | <input type="checkbox" disabled>static_template</input><br/><input type="checkbox" disabled>playing_template</input><br/><input type="checkbox" disabled>custom_template</input><br/><input type="checkbox" disabled>exit</input><br/>                                                                                                                 |    |
| interceptor         | 自定义拦截器，用于实现自定义语义理解             | 可选实现 | <input type="checkbox" disabled>custom</input><br/><input type="checkbox" disabled>aiui</input><br/>                                                                                                                                                                                                                                                   |    |
| launcher            | 启动器，用于作为桌面的场景                  | 可选实现 | <input type="checkbox" disabled>start_activity</input><br/><input type="checkbox" disabled>back</input><br/><input type="checkbox" disabled>select</input><br/>                                                                                                                                                                                        |    |
| wakeword            | 唤醒词管理，用于接入了 OS 唤醒管理的设备         | 可选实现 | <input type="checkbox" disabled>set_wakeword</input><br/>                                                                                                                                                                                                                                                                                              |    |
SDK最外层接口被封装成 EvsService，以Android Service组件方式对外提供。

##### `demo`

一个最基础的 iFLYOS EVS 设备端实现，简单的SDK调用示例，用于演示 SDK 相关能力调用，体验 EVS 技能，以及调试查看交互协议通讯内容。

### SDK集成

集成文档见 https://doc.iflyos.cn/device/evs/sdk/android.html

### 其他问题

欢迎在 [Issues](https://github.com/iFLYOS-OPEN/SDK-EVS-Android/issues) 中提出你的建议或问题，部分问题参考 [WiKi Pages](https://github.com/iFLYOS-OPEN/SDK-EVS-Android/wiki) 中的介绍