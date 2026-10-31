rm -f ".testdata/$1_$2_$3_$4_$6_result.txt"
rm -f ".testdata/$1_$2_$3_$4_$6_monitorlogs.txt"
touch ".testdata/$1_$2_$3_$4_$6_result.txt"
touch ".testdata/$1_$2_$3_$4_$6_monitorlogs.txt"

grep "Starting session" .example_logs/$1$5_* > .testdata/$1_$2_$3_$4_$6_total.txt
grep "Unexpected message" .example_logs/$1$5_* > .testdata/$1_$2_$3_$4_$6_crash.txt
grep "timed out after" .example_logs/$1$5_* > .testdata/$1_$2_$3_$4_$6_timeout.txt

(echo "Total sessions:"; wc -l .testdata/$1_$2_$3_$4_$6_total.txt; echo "Crashed due to unexpected packet:"; wc -l .testdata/$1_$2_$3_$4_$6_crash.txt; echo "Timeouts:"; wc -l .testdata/$1_$2_$3_$4_$6_timeout.txt) > ".testdata/$1_$2_$3_$4_$6_result.txt"

grep "\] \[LOG\]:" logs/*.log > ".testdata/$1_$2_$3_$4_$6_monitorlogs.txt"
