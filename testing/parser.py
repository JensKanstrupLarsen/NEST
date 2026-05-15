import csv
import numpy as np
import matplotlib.pyplot as plt
import sys
import math

def get_packet_data(filename, test_nums):
    all_lines = []
    for i in range(1,test_nums+1):
        with open(filename+"_"+str(i)+"_packets.txt", "r") as f:
            reader = csv.reader(f, delimiter=" ")
            lines = 0
            for row in reader:
                lines += 1
            all_lines.append(lines)
    all_lines.sort()
    return all_lines

def get_timestamp_data(filename):
    with open(filename, "r") as f:
        reader = csv.reader(f, delimiter=" ")
        total_lines = 0
        timestamps = []
        for row in reader:
            total_lines += 1
            timestamps += [round(float(row[10]),2)]
        unq_timestamps, counts = np.unique(timestamps, return_counts=True)
        counts_sum = [counts[0]]
        for i in range(1, len(counts)):
            counts_sum += [counts[i] + counts_sum[i-1]]
        min_timestamp = min(unq_timestamps)
        eql_timestamps = [t-min_timestamp for t in unq_timestamps]
        return (eql_timestamps, counts_sum)

def get_network_data(filename, testtype, test_nums):
    pkts_udp =  get_packet_data(".testdata/" + filename + "_udp_" + testtype, test_nums)
    pkts_tcp =   get_packet_data(".testdata/" + filename + "_tcp_" + testtype, test_nums)
    pkts_tcploss = get_packet_data(".testdata/" + filename + "_tcploss_" + testtype, test_nums)
    return (pkts_udp, pkts_tcp, pkts_tcploss)

def make_barplot(filename, save, test_nums):
    network_types = ("Reliable UDP", "Reliable TCP", "Unreliable TCP")
    nm_good_pkts = get_network_data(filename, "nm_good", test_nums)
    nm_bad_pkts = get_network_data(filename, "nm_bad", test_nums)
    mon_good_pkts = get_network_data(filename, "mon_good", test_nums)
    mon_bad_pkts = get_network_data(filename, "mon_bad", test_nums)
    packet_data = {
        'Unmonitored, correct traffic': nm_good_pkts,
        'Unmonitored, faulty traffic': nm_bad_pkts,
        'Monitored, correct traffic': mon_good_pkts,
        'Monitored, faulty traffic': mon_bad_pkts,
    }
    testtype_colors = {
        'Unmonitored, correct traffic': "#FFC20A",
        'Unmonitored, faulty traffic': "#FFC20A",
        'Monitored, correct traffic': "#0C7BDC",
        'Monitored, faulty traffic': "#0C7BDC",
    }
    testtype_hatch = {
        'Unmonitored, correct traffic': None,
        'Unmonitored, faulty traffic': "//",
        'Monitored, correct traffic': None,
        'Monitored, faulty traffic': "//",
    }
    testtype_edgecolor = {
        'Unmonitored, correct traffic': None,
        'Unmonitored, faulty traffic': "White",
        'Monitored, correct traffic': None,
        'Monitored, faulty traffic': "White",
    }

    x = np.arange(len(network_types))  # the label locations
    width = 0.2  # the width of the bars
    multiplier = 0

    fig, ax = plt.subplots(layout='constrained')
    max_packets = 0

    for testtype, packets in packet_data.items():
        print(packets)
        offset = width * multiplier
        #if (testtype == 'Monitored, correct' or testtype == 'Monitored, faulty'):
        #    offset += .1
        median = [p[round(len(p)/2-1)] for p in packets]
        print(median)
        rects = ax.bar(
            x + offset,
            median,
            width,
            color=testtype_colors[testtype],
            label=testtype,
            hatch=testtype_hatch[testtype],
            edgecolor=testtype_edgecolor[testtype])
        lowest = [p[0] for p in packets]
        highest = [p[-1] for p in packets]
        lo_diff = [median[i] - lowest[i] for i in range(len(packets))]
        hi_diff = [highest[i] - median[i] for i in range(len(packets))]
        ax.errorbar(
            x + offset,
            median,
            yerr=[lo_diff, hi_diff],
            fmt='.',
            color='black')
        ax.bar_label(rects, padding=3)
        multiplier += 1
        max_packets = max(max_packets, max([max(p) for p in packets]))

        # Add some text for labels, title and custom x-axis tick labels, etc.
    ax.set_ylabel('Packets observed')
    ax.set_xticks(x + width, network_types)
    ax.legend(loc='upper left', ncols=2)
    ax.set_ylim(0, max_packets * 1.25)
    plt.title(filename)

    if save:
        plt.savefig("plots/"+filename+"_barplot.pdf")
        plt.clf()
    else:
        plt.show()

def make_single_timeseries(filename, nettype, title, save, test_num):
    offset = 10
    if nettype == "tcploss":
        offset = 6

    ts_nm_good,  counts_nm_good  = get_timestamp_data(".testdata/" + filename + "_" + nettype + "_nm_good_"+str(test_num)+"_packets.txt")
    ts_nm_bad,   counts_nm_bad   = get_timestamp_data(".testdata/" + filename + "_" + nettype + "_nm_bad_"+str(test_num)+"_packets.txt")
    ts_mon_good, counts_mon_good = get_timestamp_data(".testdata/" + filename + "_" + nettype + "_mon_good_"+str(test_num)+"_packets.txt")
    ts_mon_bad,  counts_mon_bad  = get_timestamp_data(".testdata/" + filename + "_" + nettype + "_mon_bad_"+str(test_num)+"_packets.txt")

    plt.plot(ts_nm_good,  counts_nm_good,  label="Unmonitored, correct traffic", color="red", linestyle="solid")
    plt.plot(ts_mon_good, counts_mon_good, label="Monitored, correct traffic", color="blue", linestyle="solid")
    plt.plot(ts_nm_bad,   counts_nm_bad,   label="Unmonitored, faulty traffic", color="red", linestyle="dashdot")
    plt.plot(ts_mon_bad,  counts_mon_bad,  label="Monitored, faulty traffic", color="blue", linestyle="dashdot")

    plt.legend()
    plt.title(title)
    plt.xlabel("Time (seconds)", fontsize=16)
    plt.ylabel("Packets observed", fontsize=16)
    ax = plt.gca()
    #if not xlimits is None:
    #    ax.set_xlim([0,xlimits])

    if save:
        plt.savefig("plots/"+filename+"_"+nettype+"_timeseries.pdf")
        plt.clf()
    else:
        plt.show()

def make_all_timeseries(filename, save, test_num):
    make_single_timeseries(filename, "udp", filename + ", UDP", save, test_num)
    make_single_timeseries(filename, "tcp", filename + ", reliable TCP", save, test_num)
    make_single_timeseries(filename, "tcploss", filename + ", unreliable TCP", save, test_num)

if len(sys.argv) != 4 and len(sys.argv) !=5:
    print("Usage: parser.py <testcase> <barplot | timeseries> <# of tests> [--save]")
    sys.exit(1)

if (sys.argv[2] == "barplot"):
    make_barplot(sys.argv[1], len(sys.argv) == 5 and sys.argv[4] == "--save", test_nums=int(sys.argv[3]))
elif (sys.argv[2] == "timeseries"):
    make_all_timeseries(sys.argv[1], len(sys.argv) == 5 and sys.argv[4] == "--save", test_num=int(sys.argv[3]))
else:
    print("Unknown plot type.")
    sys.exit(1)

