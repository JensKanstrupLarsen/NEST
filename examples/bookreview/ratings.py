#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys

from main import BookReviewSessionHandler, Review, Ratings, RqsRatings, RspRatings

def protocol(s):
    s.recvMsg(Review, RqsRatings)
    s.sendMsg(Review, RspRatings("result"))

if __name__ == '__main__':
    role = Ratings
    sh = BookReviewSessionHandler(role, 50)
    for i in range(50):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
