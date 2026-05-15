rm -f ".testdata/$1_$2_$3_$4_$5_packets.txt"
rm -f ".testdata/$1_$2_$3_$4_$5_monitorlogs.txt"
rm -f ".testdata/$1_$2_$3_$4_$5_retransmissions.txt"
touch ".testdata/$1_$2_$3_$4_$5_packets.txt"
touch ".testdata/$1_$2_$3_$4_$5_monitorlogs.txt"
touch ".testdata/$1_$2_$3_$4_$5_retransmissions.txt"
#grep "^Frame" "$1"/log/*.packets > ".testdata/$1_$2_$3_$4_packets.txt"
grep "TCP payload" examples/"$1"/log/*.packets -B 90 | grep "Time since reference" > ".testdata/$1_$2_$3_$4_$5_packets.txt"
#grep "TCP payload" "$1"/log/*.packets > ".testdata/$1_$2_$3_$4_packets.txt"
grep "\[This frame is a (suspected) retransmission\]" examples/"$1"/log/* > ".testdata/$1_$2_$3_$4_$5_retransmissions.txt"
cat logs/*.log > ".testdata/$1_$2_$3_$4_$5_switchlogs.txt"

(echo "Total packets accepted:"; grep "\] \[LOG\]: > \[.*\] Message accepted" logs/*.log | wc -l; echo "Total packets rejected:"; (grep "\] \[LOG\]: > \[.*\] Bad message" logs/*.log; grep "\] \[LOG\]: > \[.*\] Out-of-order message" logs/*.log) | wc -l; echo "Total retransmissions seen:"; grep "\] \[LOG\]: > \[.*\] Low SSQN, possible retransmit" logs/*.log | wc -l) > ".testdata/$1_$2_$3_$4_$5_result.txt"

if [ $3 = "mon" ] ; then
    grep "\] \[LOG\]:" logs/*.log > ".testdata/$1_$2_$3_$4_$5_monitorlogs.txt"
fi
