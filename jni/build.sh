# export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_144.jdk/Contents/Home/
export JAVA_HOME=$(/usr/libexec/java_home)

gcc -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin/" -o libtrolsoft.jnilib -shared ru_trolsoft_jni_NativeFileUtils.c
cp -f libtrolsoft.jnilib ../
jar cvf ../lib/runtime/trolsoft.jar libtrolsoft.jnilib
