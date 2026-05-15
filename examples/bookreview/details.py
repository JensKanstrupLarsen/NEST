#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys

from main import BookReviewSessionHandler, Info, Details, RqsDetail, RspDetail

def protocol(s):
    s.recvMsg(Info, RqsDetail)
    s.sendMsg(Info, RspDetail("result"))

if __name__ == '__main__':
    role = Details
    sh = BookReviewSessionHandler(role, 50)
    for i in range(50):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
