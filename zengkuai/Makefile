# 增块引擎 Makefile

CXX ?= g++
CXXFLAGS = -std=c++17 -O3 -march=native -Wall
INCLUDES = -I./include -I$(JAVA_HOME)/include
LDFLAGS = -shared -fPIC

# 自动检测OS
ifeq ($(OS),Windows_NT)
    INCLUDES += -I$(JAVA_HOME)/include/win32
    TARGET = zengkuai.dll
    CXX = cl
    CXXFLAGS = /std:c++17 /O2
    LDFLAGS = /LD
else
    UNAME_S := $(shell uname -s)
    ifeq ($(UNAME_S),Darwin)
        INCLUDES += -I$(JAVA_HOME)/include/darwin
    else
        INCLUDES += -I$(JAVA_HOME)/include/linux
    endif
    TARGET = libzengkuai.so
endif

SRCS = core/zengkuai.cpp core/jni_bridge.cpp
OBJS = $(SRCS:.cpp=.o)
BUILD_DIR = build

$(BUILD_DIR)/$(TARGET): $(SRCS)
	@mkdir -p $(BUILD_DIR)
ifeq ($(CXX),cl)
	$(CXX) $(CXXFLAGS) $(INCLUDES) $(SRCS) $(LDFLAGS) /Fe:$@
else
	$(CXX) $(CXXFLAGS) $(INCLUDES) $(SRCS) $(LDFLAGS) -o $@
endif
	@echo "Built: $@"

clean:
	rm -rf $(BUILD_DIR)

.PHONY: clean
