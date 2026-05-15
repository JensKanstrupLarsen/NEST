#!/usr/bin/env python3
import argparse
import os
import sys
from time import sleep

import grpc

# Import P4Runtime lib from parent utils dir
# Probably there's a better way of doing this.
sys.path.append(
    os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 'utils/'))
import p4runtime_lib.bmv2
import p4runtime_lib.helper
from p4runtime_lib.error_utils import printGrpcError
from p4runtime_lib.switch import ShutdownAllSwitchConnections

def setup_switch(p4info_file_path, bmv2_file_path, nm, ip_addr, dev_id, log_file):
    # Instantiate a P4Runtime helper from the p4info file
    p4info_helper = p4runtime_lib.helper.P4InfoHelper(p4info_file_path)

    # Create a switch connection object for s1 and s2;
    # this is backed by a P4Runtime gRPC connection.
    # Also, dump all P4Runtime messages sent to switch to given txt files.
    print("Connecting to switch "+nm+" with address "+ip_addr+", device ID "+str(dev_id)+" and dump file "+log_file)
    sw = p4runtime_lib.bmv2.Bmv2SwitchConnection(
        name=nm,
        address=ip_addr,
        device_id=dev_id,
        proto_dump_file=log_file)

    sw.MasterArbitrationUpdate()

    sw.SetForwardingPipelineConfig(p4info=p4info_helper.p4info,
                                   bmv2_json_file_path=bmv2_file_path)

    return (sw,p4info_helper)

if __name__ == '__main__':
    if (len(sys.argv) == 3):
        switch_amount = int(sys.argv[1])
        switch_config = sys.argv[2]
        for i in range(switch_amount):
            switch_name = "s" + str(i+1)
            switch_addr = "127.0.0.1:" + str(50051 + i)
            setup_switch(
                switch_config+".p4.p4info.txt",
                switch_config+".json",
                switch_name,
                switch_addr,
                i,
                "./logs/" + switch_name + "_p4runtime_requests.txt")
        ShutdownAllSwitchConnections()
    else:
        print("Usage: setup.py <num-of-switches> <config-path>")
