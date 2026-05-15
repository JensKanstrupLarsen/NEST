#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import CDNSessionHandler, User, LocalDNS, IntAuthDNS, ExtAuthDNS, DNSQuery, AuthQuery, Hostname, IPAddr

def protocol(s):
    s.recvMsg(User, DNSQuery)
    s.sendMsg(IntAuthDNS, AuthQuery("params"))
    s.sendMsg(ExtAuthDNS, Hostname("name"))
    s.recvMsg(IntAuthDNS, Hostname)
    s.sendMsg(IntAuthDNS, IPAddr("addr"))
    s.sendMsg(ExtAuthDNS, AuthQuery("params"))
    s.recvMsg(ExtAuthDNS, IPAddr)
    s.sendMsg(User, IPAddr("addr"))

if __name__ == '__main__':
    role = LocalDNS
    sh = CDNSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [role, IntAuthDNS])
        time.sleep(0.1)
    sh.closeAll()
