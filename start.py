#!/usr/bin/python

__author__='wuxian'
__version__='1.0'

import os
import sys
from subprocess import *

CUT=":"

def jar_wrapper(args):
		process = Popen(args, stdout=PIPE, stderr=STDOUT)
		ret = []
		while True:
			out = process.stdout.read(1)
			if out == '' and process.poll() != None:
				break
			if out != '':
				sys.stdout.write(out)
				sys.stdout.flush()
		return ret

def get_jar_argument_under(dir):
	ret = ""
	for lists in os.listdir(dir):
		path=os.path.join(dir,lists)
		if os.path.isfile(path) and lists.endswith(".jar"):
			ret = ret+path+CUT
	return ret

if __name__=='__main__':
		current_path=os.path.dirname(os.path.abspath(__file__))
		lib_path=os.path.join(current_path,"lib")
		jar = get_jar_argument_under(lib_path)
		jar = jar+os.path.join(current_path,"SpiderSDK.jar")
		#print jar
		args=["java","-cp",jar,"wuxian.me.spidersdk.Main"]
		jar_wrapper(args)
		#print jar

