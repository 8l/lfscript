# lfscript
Linux From Script (or 'LFScript') is an unofficial alternative for 'Automated Linux From Scratch

debootstrap chroot? sure.
```
apt-get install debootstrap schroot
debootstrap trusty lfs
mount -t proc proc lfs/proc/
mount -t sysfs sys lfs/sys/
mount -o bind /dev lfs/dev/
chroot lfs
```
requirements for build on a ubuntu base:
```
apt-get update
apt-get install git wget subversion patch build-essential libz-dev gawk bison texinfo m4 make g++ gcc
apt-get upgrade
ln -svf bash /bin/sh
git clone https://github.com/8l/lfscript lfs && cd lfs
./version-check.sh 
./lfscript -BS
```
