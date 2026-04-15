cmake_minimum_required(VERSION 3.6)

# 项目名称
project(IKEMEN_SFF_DECODER)

# 寻找安卓系统自带的 Log 库和 Zlib 库（用于处理压缩数据）
find_library(log-lib log)
find_library(z-lib z)
# 如果你的项目还需要用到 SDL2，保留它
find_library(SDL2 SDL2)

# 【核心】定义库的名字为 ikemen_sff_codec
# 这里的名字必须和 Java 里的 loadLibrary 保持一致
add_library(ikemen_sff_codec SHARED)

# 【核心】指定源代码文件，就是你刚才新建的 sff_codec.cpp
target_sources(ikemen_sff_codec PRIVATE sff_codec.cpp)

# 【极限优化】为 sff_codec.cpp 开启狂暴性能模式
set_source_files_properties(sff_codec.cpp PROPERTIES COMPILE_FLAGS "-O3 -ffast-math -ftree-vectorize")

# 链接必要的库
target_link_libraries(ikemen_sff_codec 
    ${log-lib} 
    ${z-lib} 
    SDL2
)
