/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "networkutils"

#include <jni.h>
#include <android/log.h>
#include <sys/types.h>          /* See NOTES */
#include <sys/socket.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <termios.h>
#include <signal.h>
#include <stdio.h>

#include "networkutils_common.h"

static jclass fileDescriptorClass;
static jfieldID fileDescriptorDescriptorFiled;
static jmethodID fileDescriptorInitMethod;

static jclass structTimevalClass;

typedef unsigned short char16_t;
class String8 {
public:
    String8() {
        mString = 0;
    }

    ~String8() {
        if (mString) {
            free(mString);
        }
    }

    void set(const char16_t* o, size_t numChars) {
        if (mString) {
            free(mString);
        }
        mString = (char*) malloc(numChars + 1);
        if (!mString) {
            return;
        }
        for (size_t i = 0; i < numChars; i++) {
            mString[i] = (char) o[i];
        }
        mString[numChars] = '\0';
    }

    const char* string() {
        return mString;
    }
private:
    char* mString;
};

static int throwOutOfMemoryError(JNIEnv *env, const char *message)
{
    jclass exClass;
    const char *className = "java/lang/OutOfMemoryError";

    exClass = env->FindClass(className);
    return env->ThrowNew(exClass, message);
}

static int create_subprocess(const int rdt, const char *cmd, char *const argv[], 
    char *const envp[], const char* scripts, int* pProcessId)
{
    pid_t pid;
    int pfds[2], pfds2[2];

    pipe(pfds);
    if (rdt) {
      pipe(pfds2);
    }

    pid = fork();
    if(pid < 0) {
        LOGE("- fork failed: %s -\n", strerror(errno));
        return -1;
    }

    if(pid == 0){
    	setpgrp();
        signal(SIGPIPE, SIG_IGN);
        if (envp) {
            for (; *envp; ++envp) {
                putenv(*envp);
            }
        }
        dup2(pfds[0], 0);
        close(pfds[1]);
        if (rdt) {
          close(1);
          close(2);
          dup2(pfds2[1], 1);
          dup2(pfds2[1], 2);
          close(pfds2[0]);
        }
        execv(cmd, argv);
        fflush(NULL);
        exit(0);
    } else {
        signal(SIGPIPE, SIG_IGN);
        *pProcessId = (int) pid;
        dup2(pfds[1], 1);
        close(pfds[0]);
        write(pfds[1], scripts, strlen(scripts)+1);
        if (rdt) {
            close(pfds2[1]);
            return pfds2[0]; 
        } else {
            return -1;
        }
    }
}


static jobject android_os_Exec_createSubProcess(JNIEnv *env, jobject clazz,
    jint rdt, jstring cmd, jobjectArray args, jobjectArray envVars, jstring scripts,
    jintArray processIdArray)
{
    const jchar* str = cmd ? env->GetStringCritical(cmd, 0) : 0;
    String8 cmd_8;
    if (str) {
        cmd_8.set((const char16_t*)str, env->GetStringLength(cmd));
        env->ReleaseStringCritical(cmd, str);
    }

    const jchar* str_scripts = scripts ? env->GetStringCritical(scripts, 0) : 0;
    String8 scripts_8;
    if (str_scripts) {
        scripts_8.set((const char16_t*)str_scripts, env->GetStringLength(scripts));
        env->ReleaseStringCritical(scripts, str_scripts);
    }

    jsize size = args ? env->GetArrayLength(args) : 0;
    char **argv = NULL;
    String8 tmp_8;
    if (size > 0) {
        argv = (char **)malloc((size+1)*sizeof(char *));
        if (!argv) {
            throwOutOfMemoryError(env, "Couldn't allocate argv array");
            return NULL;
        }
        for (int i = 0; i < size; ++i) {
            jstring arg = reinterpret_cast<jstring>(env->GetObjectArrayElement(args, i));
            str = env->GetStringCritical(arg, 0);
            if (!str) {
                throwOutOfMemoryError(env, "Couldn't get argument from array");
                return NULL;
            }
            tmp_8.set((const char16_t *)str, env->GetStringLength(arg));
            env->ReleaseStringCritical(arg, str);
            argv[i] = strdup(tmp_8.string());
        }
        argv[size] = NULL;
    }

    size = envVars ? env->GetArrayLength(envVars) : 0;
    char **envp = NULL;
    if (size > 0) {
        envp = (char **)malloc((size+1)*sizeof(char *));
        if (!envp) {
            throwOutOfMemoryError(env, "Couldn't allocate envp array");
            return NULL;
        }
        for (int i = 0; i < size; ++i) {
            jstring var = reinterpret_cast<jstring>(env->GetObjectArrayElement(envVars, i));
            str = env->GetStringCritical(var, 0);
            if (!str) {
                throwOutOfMemoryError(env, "Couldn't get env var from array");
                return NULL;
            }
            tmp_8.set((const char16_t*)str, env->GetStringLength(var));
            env->ReleaseStringCritical(var, str);
            envp[i] = strdup(tmp_8.string());
        }
        envp[size] = NULL;
    }

    int procId;
    int ptm = create_subprocess(rdt, cmd_8.string(), argv, envp, scripts_8.string(), 
        &procId);

    if (argv) {
        for (char **tmp = argv; *tmp; ++tmp) {
            free(*tmp);
        }
        free(argv);
    }
    if (envp) {
        for (char **tmp = envp; *tmp; ++tmp) {
            free(*tmp);
        }
        free(envp);
    }

    if (processIdArray) {
        int procIdLen = env->GetArrayLength(processIdArray);
        if (procIdLen > 0) {
            jboolean isCopy;

            int* pProcId = (int*) env->GetPrimitiveArrayCritical(processIdArray, &isCopy);
            if (pProcId) {
                *pProcId = procId;
                env->ReleasePrimitiveArrayCritical(processIdArray, pProcId, 0);
            }
        }
    }

    jobject result = env->NewObject(fileDescriptorClass, fileDescriptorInitMethod);
    if (!result) {
        LOGE("Couldn't create a FileDescriptor.");
    }
    else {
        env->SetIntField(result, fileDescriptorDescriptorFiled, ptm);
    }

    return result;
}


static int android_os_Exec_waitFor(JNIEnv *env, jobject clazz,
    jint procId) {
    int status;
    waitpid(procId, &status, 0);
    int result = 0;
    if (WIFEXITED(status)) {
        result = WEXITSTATUS(status);
    }
    return result;
}

static void android_os_Exec_close(JNIEnv *env, jobject clazz, jobject fileDescriptor)
{
    int fd;

    fd = env->GetIntField(fileDescriptor, fileDescriptorDescriptorFiled);

    if (env->ExceptionOccurred() != NULL) {
        return;
    }

    close(fd);
}

static void android_os_Exec_hangupProcessGroup(JNIEnv *env, jobject clazz,
    jint procId)
{
    kill(-procId, SIGHUP);
}

static int getFDFromFileDescriptor(JNIEnv* env, jobject fileDescriptor)
{
    static jfieldID fid = env->GetFieldID(fileDescriptorClass, "descriptor", "I");
    if (fileDescriptor != NULL) {
        return env->GetIntField(fileDescriptor, fid);
    } else {
        return -1;
    }
}


static void android_os_Posix_setsockoptTimeval(JNIEnv* env, jobject, jobject javaFd, jint level, jint option, jobject javaTimeval)
{
    if (structTimevalClass == NULL) {
        return ;
    }
	static jfieldID tvSecFid = env->GetFieldID(structTimevalClass, "tv_sec", "J");
    static jfieldID tvUsecFid = env->GetFieldID(structTimevalClass, "tv_usec", "J");
    int fd = getFDFromFileDescriptor(env, javaFd);
    struct timeval value;
    value.tv_sec = env->GetLongField(javaTimeval, tvSecFid);
    value.tv_usec = env->GetLongField(javaTimeval, tvUsecFid);
    setsockopt(fd, level, option, &value, sizeof(value));
}

static jclass findClass(JNIEnv* env, const char* name) {
    jclass localRef_fileDescriptorClass = env->FindClass(name);
    if (localRef_fileDescriptorClass == NULL) {
        LOGE("Can't find class %s", name);
        return NULL;
    }

    jclass result = (jclass) env->NewGlobalRef(localRef_fileDescriptorClass);
    env->DeleteLocalRef(localRef_fileDescriptorClass);
    if (result == NULL) {
        LOGE("Can't get global ref to class %s", name);
        return NULL;
    }
    return result;
}

static int register_FileDescriptor(JNIEnv *env)
{
	fileDescriptorClass = findClass(env, "java/io/FileDescriptor");
	if (fileDescriptorClass == NULL) {
		LOGE("find class java/io/FileDescriptor error");
		return -1;
	}
    fileDescriptorDescriptorFiled = env->GetFieldID(fileDescriptorClass, "descriptor", "I");
    if (fileDescriptorDescriptorFiled == NULL) {
        LOGE("Can't find FileDescriptor.descriptor");
        return -1;
    }

    fileDescriptorInitMethod = env->GetMethodID(fileDescriptorClass, "<init>", "()V");
    if (fileDescriptorInitMethod == NULL) {
        LOGE("Can't find FileDescriptor.init");
        return -1;
     }
     return 0;
}

#define PACKAGE_PATH_NAME        "com/mi/networkutils/"
#define CLASS_PATH_NAME          PACKAGE_PATH_NAME"utils/Os"
#define STRUCT_TIMEVAL_CLASS     PACKAGE_PATH_NAME"utils/StructTimeval"

static int register_structTimeval(JNIEnv *env)
{
	structTimevalClass = findClass(env, STRUCT_TIMEVAL_CLASS);
	if (structTimevalClass == NULL) {
		LOGE("find class %s error", STRUCT_TIMEVAL_CLASS);
		return -1;
	}
	return 0;
}

static int os_init(JNIEnv *env)
{
	int ret = 0;

	ret = register_FileDescriptor(env);
	ret = register_structTimeval(env);

	return ret;
}

static JNINativeMethod method_table[] = {
    { "createSubprocess", "(ILjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;[I)Ljava/io/FileDescriptor;",
        (void*) android_os_Exec_createSubProcess },
    { "waitFor", "(I)I",
        (void*) android_os_Exec_waitFor},
    { "close", "(Ljava/io/FileDescriptor;)V",
        (void*) android_os_Exec_close},
    { "hangupProcessGroup", "(I)V",
        (void*) android_os_Exec_hangupProcessGroup},
    { "setsockoptTimeval", "(Ljava/io/FileDescriptor;IIL"STRUCT_TIMEVAL_CLASS";)V",
    	(void*) android_os_Posix_setsockoptTimeval},
};

/*
 * Register several native methods for one class.
 */
static int registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        LOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Register native methods for all classes we know about.
 *
 * returns JNI_TRUE on success.
 */
static int registerNatives(JNIEnv* env)
{
  if (!registerNativeMethods(env, CLASS_PATH_NAME, method_table,
                 sizeof(method_table) / sizeof(method_table[0]))) {
    return JNI_FALSE;
  }

  return JNI_TRUE;
}

// ----------------------------------------------------------------------------

/*
 * This is called by the VM when the shared library is first loaded.
 */

typedef union {
    JNIEnv* env;
    void* venv;
} UnionJNIEnvToVoid;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    UnionJNIEnvToVoid uenv;
    uenv.venv = NULL;
    jint result = -1;
    JNIEnv* env = NULL;

    LOGI("JNI_OnLoad");
    if (vm->GetEnv(&uenv.venv, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("ERROR: GetEnv failed");
        goto bail;
    }
    env = uenv.env;
    if ((result = os_init(env)) < 0) {
        LOGE("ERROR: os_init failed");
        goto bail;
    }
    if (registerNatives(env) != JNI_TRUE) {
        LOGE("ERROR: registerNatives failed");
        goto bail;
    }
    result = JNI_VERSION_1_4;
bail:
    return result;
}
