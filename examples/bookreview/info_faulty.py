#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys

from main import BookReviewSessionHandler, Client, Info, Review, Details, Request, RqsReview, RqsDetail, RspReview, RspDetail, Response

def protocol(s):
    s.recvMsg(Client,  Request)
    s.sendMsg(Review,  RqsDetail("isbn"))
    s.sendMsg(Review,  RqsReview("isbn"))
    s.sendMsg(Details, RqsReview("isbn"))
    s.sendMsg(Details, RqsDetail("isbn"))
    s.recvMsg(Review,  RspReview)
    s.recvMsg(Details, RspDetail)
    s.sendMsg(Client,  Response("result"))

if __name__ == '__main__':
    role = Info
    sh = BookReviewSessionHandler(role, 50)
    for i in range(50):
        sh.startSession(protocol, 0, [role])
    sh.closeAll()
