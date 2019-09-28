#!/usr/bin/env bash
gradle jar
$HOME/Android/sdk/build-tools/27.0.3/dx  --dex --output Main.jar ./build/libs/Bindump-1.0.jar