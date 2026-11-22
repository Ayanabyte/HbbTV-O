#include <jni.h>
JNIEXPORT jint JNICALL
Java_com_ayanabyte_o_lovestv_TclHbbtvClient_nativeBits(JNIEnv *env, jclass cls) {
  (void)env; (void)cls;
  return (jint)(sizeof(void*) * 8);
}
