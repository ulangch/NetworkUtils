#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This makefile shows how to build a shared library and an activity that
# bundles the shared library and calls it using JNI.

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

res_ext := $(shell ls $(LOCAL_PATH)/res-ext)
res_ext := $(addprefix res-ext/,$(res_ext))
res_dir := $(res_ext) res
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dir))

# LOCAL_PREBUILT_JNI_LIBS := \
    libs/mips/libexec.so

LOCAL_JNI_SHARED_LIBRARIES := libexec
# LOCAL_JNI_SHARED_LIBRARIES_ABI := \
    mips64

LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := NetworkUtils
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

include $(BUILD_PACKAGE)

# ============================================================
# include $(CLEAR_VARS)
# LOCAL_PREBUILT_JNI_LIBRARIES := \
    mips:libs/mips/libexec.so

# include $(BUILD_MULTI_PREBUILT)
# ============================================================
# Also build all of the sub-targets under this one: the shared library.
# include $(call all-makefiles-under,$(LOCAL_PATH))
