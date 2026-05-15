#!/bin/bash

# Print commands and exit on errors
set -xe

apt-get update -y

export DEBIAN_FRONTEND=noninteractive

# Atom install steps came from this page on 2020-May-11:
# https://flight-manual.atom.io/getting-started/sections/installing-atom/#platform-linux

# echo "deb [arch=amd64] https://packagecloud.io/AtomEditor/atom/any/ any main" | sudo tee /etc/apt/sources.list.d/atom.list
# wget --no-check-certificate -qO - https://packagecloud.io/AtomEditor/atom/gpgkey | apt-key add -

# Add repository with P4 packages
# https://build.opensuse.org/project/show/home:p4lang

echo "deb http://download.opensuse.org/repositories/home:/p4lang/xUbuntu_20.04/ /" | sudo tee /etc/apt/sources.list.d/home:p4lang.list
wget -qO - "http://download.opensuse.org/repositories/home:/p4lang/xUbuntu_20.04/Release.key" | apt-key add -

echo 'Acquire::ForceIPv4 "true";' > /etc/apt/apt.conf.d/99force-ipv4

apt-get update -qq

apt-get -qq -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" upgrade
apt-get install -qq -y --no-install-recommends --fix-missing\
  ca-certificates \
  curl \
  emacs \
  git \
  iproute2 \
  lubuntu-desktop \
  net-tools \
  python3 \
  python3-pip \
  tcpdump \
  unzip \
  valgrind \
  vim \
  wget \
  xcscope-el \
  xterm \
  p4lang-p4c \
  p4lang-bmv2 \
  p4lang-pi

sudo pip3 install -U scapy ipaddr ptf psutil grpcio numpy matplotlib
sudo pip3 install --upgrade pyOpenSSL
sudo pip3 install "cryptography>=3.4"

sudo apt install -y default-jre

sudo apt-get install -y apt-transport-https curl gnupg -yqq
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo -H gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import
sudo chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg
sudo apt-get update -y
sudo apt-get install sbt -y