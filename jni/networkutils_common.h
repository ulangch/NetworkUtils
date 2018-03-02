#ifndef __NETWORKUTILS_JNI_COMMON_H__
#define __NETWORKUTILS_JNI_COMMON_H__

#define LOGI(...) do { __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); } while(0)
#define LOGW(...) do { __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__); } while(0)
#define LOGE(...) do { __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__); } while(0)


#ifndef TEMP_FAILURE_RETRY
/* Used to retry syscalls that can return EINTR. */
#define TEMP_FAILURE_RETRY(exp) ({         \
        typeof (exp) _rc;                      \
        do {                                   \
            _rc = (exp);                       \
        } while (_rc == -1 && errno == EINTR); \
        _rc; })
#endif

#endif
