#include <jni.h>
#include <cstdlib>
#include <android/log.h>

#define LOG_TAG "IkemenSffEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 【极限优化1】使用 inline 强制内联展开，使用 __restrict 告诉编译器指针绝对不会重叠，从而开启 CPU 疯狂向量化 (SIMD)
inline void lz5_decompress_hardcore(const uint8_t* __restrict src, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; // 跳过前4字节长度
    int dst_ptr = 0;
    while (dst_ptr < dst_len) {
        uint8_t ctrl = src[src_ptr++];
        for (int i = 0; i < 8 && dst_ptr < dst_len; ++i) {
            if (ctrl & (1 << i)) {
                uint16_t pos = src[src_ptr++] | (src[src_ptr++] << 8);
                int offset = (pos >> 5) + 1;
                int count = (pos & 0x1F) + 3;
                if (count == 34) count += src[src_ptr++];
                
                // 注意：这里绝对不能用 memcpy，因为 LZ 算法的字典复制存在内存重叠
                for (int j = 0; j < count; ++j) {
                    dst[dst_ptr] = dst[dst_ptr - offset];
                    dst_ptr++;
                }
            } else {
                dst[dst_ptr++] = src[src_ptr++];
            }
        }
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV2C(JNIEnv* env, jobject thiz, jbyteArray data, jint format, jint width, jint height, jbyteArray palette) {
    // 获取源数据指针
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    int dst_len = width * height;

    // 【极限优化2】放弃 C++ 的 std::vector，直接用 C 语言底层的 malloc 在堆区强行划一块内存，没有任何初始化开销
    uint8_t* pixels = (uint8_t*)malloc(dst_len);

    if (format == 4) { // LZ5 压缩
        lz5_decompress_hardcore((uint8_t*)src_buf, pixels, dst_len);
    }

    // 获取调色板指针
    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    
    // 创建返回给 Java 的数组
    jintArray result = env->NewIntArray(dst_len);

    // 【极限优化3】使用 Critical 获取 Java 数组的直接内存指针！这会短暂挂起 Java 垃圾回收器(GC)，实现零拷贝直接写入，速度提升数倍！
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        int idx = pixels[i] & 0xFF;
        if (idx == 0) {
            out_pixels[i] = 0; // 0 索引永远是透明色
        } else {
            int pal_idx = idx * 3;
            // 位运算拼接 ARGB (Alpha直接拉满0xFF)
            out_pixels[i] = (0xFF << 24) | ((pal_buf[pal_idx] & 0xFF) << 16) | ((pal_buf[pal_idx + 1] & 0xFF) << 8) | (pal_buf[pal_idx + 2] & 0xFF);
        }
    }

    // 释放 Critical 锁，恢复系统正常运行
    env->ReleasePrimitiveArrayCritical(result, out_pixels, 0);

    // 擦屁股：释放内存，防闪退
    free(pixels);
    env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(palette, pal_buf, JNI_ABORT);

    return result;
}
