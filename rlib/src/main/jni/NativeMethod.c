#include <linux/input.h>
#include "jp_tkgktyk_wearablepadrlib_NativeMethod.h"

JNIEXPORT jint JNICALL Java_jp_tkgktyk_wearablepadrlib_NativeMethod_getInputEventHeaderSize
  (JNIEnv *env, jobject obj)
  {
    return sizeof(struct timeval);
  }
