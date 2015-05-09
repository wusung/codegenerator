#! /bin/bash 

while :
do 
	sudo -u impala hadoop fs -ls /user/impala/wike_data/2007-12 |wc -l
	sleep 10s 
done 
