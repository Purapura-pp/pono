#!/bin/bash

platform='unknown'
unamestr=`uname`
case "$unamestr" in
	Linux)
		platform='linux'
		rootdir="$(dirname $(readlink -f $0))"
	;;
	Darwin)
		platform='mac'
		rootdir="$(cd $(dirname $0); pwd -P)"
	;;
esac

# Run from the directory this script is in, whatever directory you launched it from. The program
# reads VERSION.txt and writes its configuration relative to the working directory, so starting it
# from anywhere else gave an About dialog with no version in it.
cd "$rootdir" || exit 1

case "$platform" in
	mac)
		java -Xdock:name=Pono --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.color=ALL-UNNAMED -jar $rootdir/target/openpnp-gui-0.0.1-alpha-SNAPSHOT.jar
	;;
	linux)
		java $1 --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.color=ALL-UNNAMED -jar $rootdir/target/openpnp-gui-0.0.1-alpha-SNAPSHOT.jar
	;;
esac
