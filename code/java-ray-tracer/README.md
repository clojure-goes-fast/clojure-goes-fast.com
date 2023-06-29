# Java ray tracer

Used to experiment with Project Valhalla in this post:
http://clojure-goes-fast.com/blog/valhalla-vs-ray-tracer/

Requires [Early Access Buiid of Project
Valhalla](https://jdk.java.net/valhalla/) (in fact, it doesn't until you start
using `value` and `primitive` keywords). The version in the repository doesn't
contain those keywords, so you can remove `-XDenablePrimitiveClasses` and
`-XX:+EnablePrimitiveClasses` from `run.sh` and it will work on any JDK).

To run:

```sh
./run.sh 400 10 out.png
```

- First argument is the width of the image in pixels.
- Second argument is the number of samples per pixel (for smoothing and removing
  noise).
- Third argument is the output file.
