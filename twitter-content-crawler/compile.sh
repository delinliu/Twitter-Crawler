#!/bin/bash
javac ../lib/ArraySet.java
javac -cp .:../lib:../lib/jsoup-1.7.2.jar:../lib/json-20090211.jar TwitterCrawler.java
