import os
import sys
import time
import json
import  jwt
from functools import wraps
from flask import request 
import logging


JWT_SECRET = os.getenv('JWT_SECRET','BINGBONG') 
SALT_SECRET = os.getenv('SALT_SECRET','PEPPER')
TOKEN_VALID_SECS=600

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)
handler = logging.StreamHandler(sys.stdout)
handler.setLevel(logging.DEBUG)
formatter = logging.Formatter(
    '%(asctime)s - %(name)s - %(levelname)s - %(message)s')
handler.setFormatter(formatter)
logger.addHandler(handler)

accepted_accounts=[
    '0x323d6c9b6203e2893cdbe28d52b01e5d08a9bbae',
    ]

def encode_client_token(expire,acct):
    #if acct not in accepted_accounts:
    #    return None
    return jwt.encode({
        "expire": expire,
        "account":acct,
        "salt":SALT_SECRET }, JWT_SECRET, algorithm="HS256")

def decode_client_token(token):
    decoded = jwt.decode(token, JWT_SECRET,algorithms=["HS256"])
    return decoded['expire'], decoded['account'], decoded['salt']

def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None
        # jwt is passed in the request header
        if 'x-access-token' in request.headers:
            token = request.headers['x-access-token']
        # return 401 if token is not passed
        if not token:
            return json.dumps({'message' : 'Token is missing !!'}), 401
  
        try:
            # decoding the payload to fetch the stored details
            exp,acct,salt = decode_client_token(token)

            if exp < time.time():
                return json.dumps({'message' : 'Token is expired !!'}), 401
            if salt != SALT_SECRET:
                return json.dumps({'message' : 'Token is invalid !!'}), 401
            #TODO???
            request.account=acct
        except Exception  as e:
            logger.exception('unable to decode token')
            return json.dumps({
                'message' : 'Token is invalid !!'
            }), 401
        # returns the current logged in users contex to the routes
        return f(acct,*args,**kwargs)
  
    return decorated

def rate_limited(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None
        # jwt is passed in the request header
        if 'x-access-token' in request.headers:
            token = request.headers['x-access-token']
        # return 401 if token is not passed
        if not token:
            return json.dumps({'message' : 'Token is missing !!'}), 401
  
        try:
            # decoding the payload to fetch the stored details
            exp,acct,salt = decode_client_token(token)

            if exp < time.time():
                return json.dumps({'message' : 'Token is expired !!'}), 401
            if salt != SALT_SECRET:
                return json.dumps({'message' : 'Token is invalid !!'}), 401

        except Exception  as e:
            logger.exception('unable to decode token')
            return json.dumps({
                'message' : 'Token is invalid !!'
            }), 401
        # returns the current logged in users contex to the routes
        return f(acct,*args,**kwargs)
  
    return decorated