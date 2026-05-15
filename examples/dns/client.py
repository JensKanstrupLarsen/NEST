#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import DNSSessionHandler, Client, LocalDNS, RqsIP, RspIP

def protocol(s):
    s.sendMsg(LocalDNS, RqsIP("domain"))
    s.recvMsg(LocalDNS, RspIP)

if __name__ == '__main__':
    role = Client
    sh = DNSSessionHandler(role, 50)
    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, session_id+1, [role])
    sh.closeAll()
