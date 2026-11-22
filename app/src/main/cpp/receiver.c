#include <jni.h>

JNIEXPORT jint JNICALL
Java_com_ayanabyte_hbbtvo_BroadcastBrowser_pointerBits(JNIEnv *env, jclass type) {
    return sizeof(void *) * 8;
}
