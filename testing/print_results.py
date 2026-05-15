import csv
import numpy as np
import matplotlib.pyplot as plt
import sys
import math

def get_packet_data(filename, test_nums, query):
    all_lines = []
    for i in range(1,test_nums+1):
        with open(filename+"_"+str(i)+"_monitorlogs.txt", "r") as f:
            reader = csv.reader(f, delimiter=" ")
            lines = 0
            for row in reader:
                if query in row:
                    lines += 1
            all_lines.append(lines)
    all_lines.sort()
    return all_lines[round(len(all_lines)/2)]

def print_test_result(example, testtype, filename_addon, test_nums):
    print(example+" "+testtype+"\nA\tR\tT")
    accepts = get_packet_data(".testdata/"+example+"_"+filename_addon, test_nums, "accepted,")
    rejects = get_packet_data(".testdata/"+example+"_"+filename_addon, test_nums, "Bad")
    rejects += get_packet_data(".testdata/"+example+"_"+filename_addon, test_nums, "Out-of-order")
    retransmits = get_packet_data(".testdata/"+example+"_"+filename_addon, test_nums, "retransmit")
    print(str(accepts)+"\t"+str(rejects)+"\t"+str(retransmits))

if len(sys.argv) != 3:
    print("Usage: print_results.py <testcase> <# of tests>")
    sys.exit(1)
else:
    print_test_result(sys.argv[1], "UDP, correct traffic", "udp_mon_good", int(sys.argv[2]))
    print_test_result(sys.argv[1], "UDP, faulty traffic", "udp_mon_bad", int(sys.argv[2]))
    print_test_result(sys.argv[1], "Reliable TCP, correct traffic", "tcp_mon_good", int(sys.argv[2]))
    print_test_result(sys.argv[1], "Reliable TCP, faulty traffic", "tcp_mon_bad", int(sys.argv[2]))
    print_test_result(sys.argv[1], "Unreliable TCP, correct traffic", "tcploss_mon_good", int(sys.argv[2]))
    print_test_result(sys.argv[1], "Unreliable TCP, faulty traffic", "tcploss_mon_bad", int(sys.argv[2]))

