# 编译代理配置

## Gradle 代理设置
- 编译项目时需配置 HTTP 代理，将以下内容添加到 `gradle.properties` 中：

```
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

## 环境变量代理（Shell 中使用）
- 在终端中运行 Gradle 命令前，先设置环境变量：

```
set http_proxy=http://127.0.0.1:7890
set https_proxy=http://127.0.0.1:7890
```

## JAVA_HOME 配置
- 编译需要 JDK 含 jlink（支持 compileSdk 36），使用 Android Studio 自带 JDK：

```
set JAVA_HOME=E:\Android\Android Studio\jbr
```

或在 `gradle.properties` 中配置：

```
org.gradle.java.home=E\:/Android/Android Studio/jbr
```

## Release APK 构建

### 一键构建脚本
- 使用 `release_build.bat` 一键编译签名的 release APK 并输出到 `app/release/HypnoVibe.apk`
- 脚本会自动设置代理和 JAVA_HOME，无需手动配置环境变量

### 手动构建
```
.\gradlew assembleRelease --no-configuration-cache
```
- 构建产物：`app/build/outputs/apk/release/app-release.apk`
- 签名密钥：`app/hypnovibe.keystore`（配置文件：`keystore.properties`）
