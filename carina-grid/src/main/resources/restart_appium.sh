#!/bin/bash
  
command=$(ps -p $1 -o args)
command=`echo ${command:5} | sed "s/{/'{/g" | sed "s/}/}'/g"`
kill -9 $1
eval "nohup $command &> appium.log &"