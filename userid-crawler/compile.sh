#!/bin/bash
javac ../lib/ArraySet.java
javac -cp .:../lib:../lib/twitter4j-core-4.0.2.jar UserIdCrawler.java
