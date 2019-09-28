#!/system/bin/sh
export ANDROID_DATA=/data/local/tmp/
export CLASSPATH=/data/local/tmp/Main.jar
app_process /data/local/tmp Bindump "$@"