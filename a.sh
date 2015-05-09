for f in $(find . -name '*.gz'); do gunzip $f; sudo -u impala hadoop fs -put ${f%.*} /user/impala/wike_data/2007-12; done
