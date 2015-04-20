#!/bin/sh
#NORMAL_USER=torrent
INTERFACE=bond0
NUMPACKETS=50000
SNIFFER_HOME=/opt/keiko-sniffer
TMPFILE="$SNIFFER_HOME/analyze-ngrep.tmp"
CONFIGFILE="$SNIFFER_HOME/conf/keiko-sniffer.conf"
JAVA_HOME=/opt/keiko/jdk/jdk
PATH=/bin:/usr/bin:/usr/sbin:$JAVA_HOME/bin

# do NOT modify anything below this line
#umask 0027
sudo ngrep -q -d $INTERFACE -n $NUMPACKETS -O $TMPFILE "(GET.*/announce.*info_hash=|d8:announce|Content-Type:.*application/x-bittorrent)" tcp >> /dev/null
NEWTRACKERS=`java -server -cp $SNIFFER_HOME/lib/keiko-sniffer.jar:$SNIFFER_HOME/lib/jpcap-0.7.jar net.instantcom.keikosniffer.analyzer.Analyzer $TMPFILE | grep "trackers="`
cp -f "$CONFIGFILE" "$CONFIGFILE".bak
#chown $NORMAL_USER.$NORMAL_USER "$CONFIGFILE".bak
sed "s/^trackers=.*$/$NEWTRACKERS/" "$CONFIGFILE".bak > "$CONFIGFILE"
#chown $NORMAL_USER.$NORMAL_USER "$CONFIGFILE"
rm -f $TMPFILE
sudo $SNIFFER_HOME/init/keiko-sniffer restart
exit 0
