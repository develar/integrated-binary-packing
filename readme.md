Binary packing sorted 32- or 64-bit integers with differential coding. Inspired by [JavaFastPFOR](https://github.com/lemire/JavaFastPFOR).

Classes `IntPackTest` and `LongPackTest` generated by `generator/src/main.kt`.

32-bit integers encoded in blocks of 32. 64-bit integers encoded in blocks of 64. Input must be sorted.
If input is not aligned with block size, say 35 integers should be compressed, encode rest first using variable encoding (`compressVariable`) and then using binary packing (`compress`). Rest encoding logic is not part of this low-level library.