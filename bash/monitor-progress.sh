#!/bin/bash
#
#
#
#

(
    items=$(ls -l /mnt/workspace/dumps.wikimedia.org/other/pagecounts-raw/2007/2007-12/ |wc -l)
    processed=0
    while [ $processed -le $items ]; do
        pct=$(( $processed * 100 / $items ))
        echo "XXX"
        echo "Processing item $processed/$items"
        echo "XXX"
        echo "$pct"
        processed=$(hadoop fs -ls /user/impala/wike_data/2007-12|wc -l)
        sleep 0.1
    done
) | dialog --title "Gauge" --gauge "Wait please..." 10 60 0
