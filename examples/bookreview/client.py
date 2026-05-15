#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys

from main import BookReviewSessionHandler, Client, Info, Request, Response

def protocol(s):
    s.sendMsg(Info, Request("isbn"))
    s.recvMsg(Info, Response)

if __name__ == '__main__':
    role = Client
    sh = BookReviewSessionHandler(role, 50)
    for session_id in range(sh.max_sessions):
        time.sleep(0.01)
        sh.startSession(protocol, session_id+1, [role])
    sh.closeAll()
