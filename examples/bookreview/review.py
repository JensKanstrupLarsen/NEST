#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys

from main import BookReviewSessionHandler, Info, Review, Ratings, RqsReview, RqsRatings, RspRatings, RspReview

def protocol(s):
    s.recvMsg(Info,    RqsReview)
    s.sendMsg(Ratings, RqsRatings("isbn"))
    s.recvMsg(Ratings, RspRatings)
    s.sendMsg(Info,    RspReview("result"))

if __name__ == '__main__':
    role = Review
    sh = BookReviewSessionHandler(role, 50)
    for i in range(50):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
