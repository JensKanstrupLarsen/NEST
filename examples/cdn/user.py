#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import CDNSessionHandler, User, LocalDNS, DNSQuery, IPAddr

def protocol(s):
    s.sendMsg(LocalDNS, DNSQuery("domain"))
    s.recvMsg(LocalDNS, IPAddr)

if __name__ == '__main__':
    role = User
    sh = CDNSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role])
        time.sleep(0.1)
    sh.closeAll()
