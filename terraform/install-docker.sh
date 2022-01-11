#!/bin/sh

sudo yum update -y

sudo amazon-linux-extras install docker -y

sudo service docker start

sudo chkconfig docker on