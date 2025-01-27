#!/bin/bash

# system path
#SYSTEM_PATH=$(dirname $(readlink -f "$0"))
SYSTEM_PATH="/web"
JAVA_HOME="$SYSTEM_PATH/jdk-17.0.4+8"
PROJECT_NAME="system-web-0.0.1-SNAPSHOT.jar"

export JAVA_HOME
PATH="$JAVA_HOME/bin:$PATH"
export PATH

java -version

echo "start server...."

echo $SYSTEM_PATH

pid_file="$SYSTEM_PATH/$PROJECT_NAME".pid

function dev() {
    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar "$SYSTEM_PATH/$PROJECT_NAME" --spring.config.location="$SYSTEM_PATH"/config/application.yml
    java_start
}

function noHupLog() {
    nohup java -jar "$SYSTEM_PATH/$PROJECT_NAME" --spring.config.location="$SYSTEM_PATH"/config/application.yml >"$SYSTEM_PATH"/log.log &
    java_start
}

function noHupNoLog() {
    nohup java -jar "$SYSTEM_PATH/$PROJECT_NAME" --spring.config.location="$SYSTEM_PATH"/config/application.yml >/dev/null 2>&1 &
    java_start
}

function java_start() {
    # shellcheck disable=SC2181
    if [[ $? -eq 0 ]]; then
        echo $! > ${pid_file}
    else exit 1
    fi
}


function java_stop() {
    # shellcheck disable=SC2046
    kill -9 $(cat ${pid_file})
    # shellcheck disable=SC2181
    if [[ $? -eq 0 ]]; then
        rm -f ${pid_file}
    else exit 1
    fi
    echo "java stop ok"
}

case "$1" in
  dev)
    dev
    ;;
  log)
    noHupLog
    ;;
	start)
		noHupNoLog
		;;
	stop)
		java_stop
		;;
	status)
		redis_status
		;;
	restart|reload)
		java_stop
		sleep 1
		noHupNoLog
		;;
	*)
		echo "Please use start or stop as first argument"
		;;
esac
