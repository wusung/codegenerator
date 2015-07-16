#!/usr/bin/env bash

SCRIPT="$0"

while [ -h "$SCRIPT" ] ; do
  ls=`ls -ld "$SCRIPT"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    SCRIPT="$link"
  else
    SCRIPT=`dirname "$SCRIPT"`/"$link"
  fi
done

if [ ! -d "${APP_DIR}" ]; then
  APP_DIR=`dirname "$SCRIPT"`/..
  APP_DIR=`cd "${APP_DIR}"; pwd`
fi

echo $APP_DIR
executable="${APP_DIR}/libs/codegenerator-1.0-SNAPSHOT.jar"
executable="${APP_DIR}/build/libs/codegenerator-1.0-SNAPSHOT.jar"

if [ ! -f "$executable" ]
then
  mvn clean package
fi

LIB_PATH=${APP_DIR}/build/libs/*:../build/libs/*:${APP_DIR}/libs/*

# if you've executed sbt assembly previously it will use that instead.
export JAVA_OPTS="${JAVA_OPTS} -XX:MaxPermSize=256M -Xmx1024M -DloggerPath=conf/log4j.properties -cp ${LIB_PATH}"
ags="$@ generate -t template -i ${APP_DIR}/conf/wsh.yml -l com.kyper.MyPythonCodeGenerator -o ${APP_DIR}/output -D aliasName=wsh"

java $JAVA_OPTS -jar $executable $ags       
