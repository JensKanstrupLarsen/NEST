#!/usr/bin/env python3
import argparse
import random
import socket
import time

from main import CDNSessionHandler, IntAuthDNS, LocalDNS, AuthQuery, Hostname

def protocol(s):
    s.recvMsg(LocalDNS, AuthQuery)
    s.sendMsg(LocalDNS, Hostname("name"))

if __name__ == '__main__':
    role = IntAuthDNS
    sh = CDNSessionHandler(role, 50)

    for session_id in range(sh.max_sessions):
        sh.startSession(protocol, 0, [])
        time.sleep(0.1)
    sh.closeAll()
