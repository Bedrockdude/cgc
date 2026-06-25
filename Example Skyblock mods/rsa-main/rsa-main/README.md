# rsa

This is an outdated version, for the latest features you can [purchase rsa](https://discord.gg/nfZ22Xqypp) for $25.

`rsa` is an addon for [`rsm`](https://github.com/rs-mod/rsm).

```bash
git clone https://github.com/rdbtcvs/rsa.git
cd rsa
git submodule update --init --recursive
cd rsm && ./gradlew --no-daemon remapJar && cd ..
./gradlew build
```
