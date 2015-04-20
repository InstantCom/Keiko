#!/bin/sh
LOGDIR=/opt/keiko-sniffer/log
PATH=/bin:/usr/bin
# remove very old files
find $LOGDIR -type f -name "*.log.*" -mtime +6 -exec sh -c "rm {}" \;
# gzip old files
find $LOGDIR -type f -name "*.log.*" -mtime +0 -exec sh -c "gzip {}" \;
exit 0
