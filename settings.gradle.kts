rootProject.name = "myco"

// 国内网络下 repo.maven.apache.org / dl.google.com 容易卡住（实测单文件下载挂起 15min+）；
// 优先走阿里云镜像，官方仓留作兜底。
// 注意：pluginManagement 块是独立编译阶段，不能引用脚本里定义的变量/函数，必须内联。

pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        google()
    }
}

include(":core")
include(":tools:fake-sse")
include(":app-desktop")
// include(":app-android")  // S0-b: 需要 AGP + Compose，下一步加
