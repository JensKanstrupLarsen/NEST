#!/usr/bin/env python3
import argparse
import random
import socket
import time
import sys
import os

sys.path.insert(1, os.path.join(sys.path[0], '../nest_python'))
from monitor import SessionHandler, Role, Message

class Client(Role):
    name = "Client"
    id = 1
    ip = "10.0.1.1"

class Info(Role):
    name = "Info"
    id = 2
    ip = "10.0.2.2"

class Review(Role):
    name = "Review"
    id = 3
    ip = "10.0.3.3"

class Details(Role):
    name = "Details"
    id = 4
    ip = "10.0.4.4"

class Ratings(Role):
    name = "Ratings"
    id = 5
    ip = "10.0.5.5"

class Request(Message):
    id = 1
    label = "Request"

    def __init__(self, text=""):
       super().__init__(text)

class Response(Message):
    id = 8
    label = "Response"

    def __init__(self, text=""):
       super().__init__(text)

class RqsReview(Message):
    id = 2
    label = "RqsReview"

    def __init__(self, text=""):
       super().__init__(text)

class RspReview(Message):
    id = 6
    label = "RspReview"

    def __init__(self, text=""):
       super().__init__(text)

class RqsDetail(Message):
    id = 3
    label = "RqsDetail"

    def __init__(self, text=""):
       super().__init__(text)

class RspDetail(Message):
    id = 7
    label = "RspDetail"

    def __init__(self, text=""):
       super().__init__(text)

class RqsRatings(Message):
    id = 4
    label = "RqsRatings"

    def __init__(self, text=""):
       super().__init__(text)

class RspRatings(Message):
    id = 5
    label = "RspRatings"

    def __init__(self, text=""):
       super().__init__(text)

class BookReviewSessionHandler(SessionHandler):
    def __init__(self, role, max_sessions):
        super().__init__(role, "session", max_sessions)

    def toMessage(self, session_header):
        if (session_header.message_id == 1):
            return Request(session_header.message_text)
        elif (session_header.message_id == 8):
            return Response(session_header.message_text)
        elif (session_header.message_id == 2):
            return RqsReview(session_header.message_text)
        elif (session_header.message_id == 6):
            return RspReview(session_header.message_text)
        elif (session_header.message_id == 3):
            return RqsDetail(session_header.message_text)
        elif (session_header.message_id == 7):
            return RspDetail(session_header.message_text)
        elif (session_header.message_id == 4):
            return RqsRatings(session_header.message_text)
        elif (session_header.message_id == 5):
            return RspRatings(session_header.message_text)
        else:
            return Message()
