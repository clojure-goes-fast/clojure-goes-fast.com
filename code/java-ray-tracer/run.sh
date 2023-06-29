#!/bin/bash
rm -rf target
mkdir target
javac -d target -XDenablePrimitiveClasses src/raytracer/*.java
OUT_FILE="${3:-out.png}"
time java -cp target -XX:+EnablePrimitiveClasses raytracer.Render $1 $2 $OUT_FILE
